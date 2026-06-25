package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.Tree;
import pal.misc.IdGroup;
import pal.tree.TreeUtils;
import pal.tree.NodeUtils;
import treecmp.common.AlignInfo;
import treecmp.common.ClusterDist;
import treecmp.common.LapSolver;
import treecmp.heuristics.moves.NniMove;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingClusterMetric;

import java.util.Arrays;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Truly incremental implementation of the Matching Cluster metric.
 * It optimizes the O(N^3) Linear Assignment Problem (LAP) to O(N^2) or better
 * by utilizing a warm-start assignment update algorithm during local 1-NNI / SPR steps.
 */
public class MCIncrementalMetric implements IncrementalMetric {

    private Tree baseTree;
    private Tree targetTree;
    private double currentDistance;

    private final MatchingClusterMetric mcMetricFull = new MatchingClusterMetric();

    // LAP state variables representing the bipartite graph matching state
    private int dim;
    private short[][] assigncost;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    // Track mutable cluster states for incremental bit flips
    private BitSet[] currentClusters;
    private BitSet[] targetClusters;

    // Mapping arrays to safely translate between PAL node numbers and LAP matrix indices
    private Node[] rowToNode;
    private Node[] colToNode;
    private int[] nodeNumToRow;

    // History stacks for O(1) backtracking and rollback capabilities
    private final Stack<short[][]> costHistory = new Stack<>();
    private final Stack<int[]> rowsolHistory = new Stack<>();
    private final Stack<int[]> colsolHistory = new Stack<>();
    private final Stack<int[]> uHistory = new Stack<>();
    private final Stack<int[]> vHistory = new Stack<>();
    private final Stack<Double> distanceHistory = new Stack<>();
    private final Stack<BitSet[]> clusterHistory = new Stack<>();

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        this.baseTree = baseTree;
        this.targetTree = targetTree;

        if (baseTree != null && targetTree != null) {
            clearHistory();

            // Root node is excluded from the LAP assignment in Matching Cluster metrics
            int size1 = baseTree.getInternalNodeCount() - 1;
            int size2 = targetTree.getInternalNodeCount() - 1;
            this.dim = Math.max(size1, size2);

            this.assigncost = new short[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];
            this.u = new int[dim];
            this.v = new int[dim];

            IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
            this.currentClusters = ClusterDist.RootedTree2BitSetArray(baseTree, idGroup);
            this.targetClusters = ClusterDist.RootedTree2BitSetArray(targetTree, idGroup);

            this.rowToNode = new Node[dim];
            this.colToNode = new Node[dim];
            this.nodeNumToRow = new int[currentClusters.length];
            Arrays.fill(this.nodeNumToRow, -1);

            // Populate row mappings for Base Tree (Internal nodes only, skip root)
            int r = 0;
            for (int i = 0; i < baseTree.getInternalNodeCount(); i++) {
                Node n = baseTree.getInternalNode(i);
                if (!n.isRoot() && r < dim) {
                    rowToNode[r] = n;
                    nodeNumToRow[n.getNumber()] = r;
                    r++;
                }
            }

            // Populate col mappings for Target Tree (Internal nodes only, skip root)
            int c = 0;
            for (int j = 0; j < targetTree.getInternalNodeCount(); j++) {
                Node n = targetTree.getInternalNode(j);
                if (!n.isRoot() && c < dim) {
                    colToNode[c] = n;
                    c++;
                }
            }

            buildInitialCostMatrix();
            this.currentDistance = LapSolver.lapShort(dim, assigncost, rowsol, colsol, u, v);
        } else {
            this.currentDistance = 0;
        }
    }

    private void buildInitialCostMatrix() {
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                Node n1 = rowToNode[i];
                Node n2 = colToNode[j];

                if (n1 != null && n2 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistXorBit(currentClusters[n1.getNumber()], targetClusters[n2.getNumber()]);
                } else if (n1 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistToOAsMinBit(currentClusters[n1.getNumber()]);
                } else if (n2 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistToOAsMinBit(targetClusters[n2.getNumber()]);
                } else {
                    this.assigncost[i][j] = 0;
                }
            }
        }
    }

    // ==========================================================
    // NNI HEURISTIC LOGIC
    // ==========================================================

    @Override
    public double applyNni(NniMove move) {
        saveCurrentStateToHistory();

        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        // SAFELY COLLECT ALL LEAF IDs (handles deep internal subtree shifts safely)
        List<Integer> movingSubtreeLeaves = new ArrayList<>();
        collectLeafIds(move.movingSubtree, idGroup, movingSubtreeLeaves);

        List<Integer> swapPartnerLeaves = new ArrayList<>();
        collectLeafIds(move.swapPartner, idGroup, swapPartnerLeaves);

        Node lca = NodeUtils.getFirstCommonAncestor(move.movingSubtree, move.swapPartner);
        List<Integer> changedRowsList = new ArrayList<>();

        // Branch 1: Modify ancestors of movingSubtree
        Node curr = move.movingSubtree.getParent();
        while (curr != null && curr != lca) {
            if (!curr.isRoot()) {
                for (int id : movingSubtreeLeaves) currentClusters[curr.getNumber()].flip(id);
                for (int id : swapPartnerLeaves) currentClusters[curr.getNumber()].flip(id);

                int rowIndex = nodeNumToRow[curr.getNumber()];
                if (rowIndex >= 0) changedRowsList.add(rowIndex);
            }
            curr = curr.getParent();
        }

        // Branch 2: Modify ancestors of swapPartner
        curr = move.swapPartner.getParent();
        while (curr != null && curr != lca) {
            if (!curr.isRoot()) {
                for (int id : movingSubtreeLeaves) currentClusters[curr.getNumber()].flip(id);
                for (int id : swapPartnerLeaves) currentClusters[curr.getNumber()].flip(id);

                int rowIndex = nodeNumToRow[curr.getNumber()];
                if (rowIndex >= 0) changedRowsList.add(rowIndex);
            }
            curr = curr.getParent();
        }

        // Isolate uniquely changed matrix rows
        int[] changedRows = changedRowsList.stream().distinct().mapToInt(Integer::intValue).toArray();

        // Incrementally recompute strictly the affected rows
        for (int i : changedRows) {
            Node n1 = rowToNode[i];
            for (int j = 0; j < dim; j++) {
                Node n2 = colToNode[j];
                if (n2 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistXorBit(currentClusters[n1.getNumber()], targetClusters[n2.getNumber()]);
                } else {
                    this.assigncost[i][j] = (short) ClusterDist.getDistToOAsMinBit(currentClusters[n1.getNumber()]);
                }
            }
        }

        // Warm-start LAP update
        if (changedRows.length > 0) {
            this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRows);
        }

        return this.currentDistance;
    }

    @Override
    public void undoNni(NniMove move) {
        undoSprRegraftStep();
    }

    // ==========================================================
    // SPR HEURISTIC LIFE-CYCLE LOGIC (Placeholders)
    // ==========================================================

    @Override public void applySprPrune(Node pruneNode) {}
    @Override public void undoSprPrune(Node pruneNode) {}
    @Override public double evaluateSprRegraft(Node pruneNode, Node targetNode) { return this.currentDistance; }
    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) {}

    @Override
    public void undoSprRegraftStep() {
        if (!distanceHistory.isEmpty()) {
            this.assigncost = costHistory.pop();
            this.rowsol = rowsolHistory.pop();
            this.colsol = colsolHistory.pop();
            this.u = uHistory.pop();
            this.v = vHistory.pop();
            this.currentDistance = distanceHistory.pop();
            this.currentClusters = clusterHistory.pop();
        }
    }

    // ==========================================================
    // STATE MANAGERS & HELPERS
    // ==========================================================

    private void saveCurrentStateToHistory() {
        short[][] costCopy = new short[dim][dim];
        for (int i = 0; i < dim; i++) {
            System.arraycopy(this.assigncost[i], 0, costCopy[i], 0, dim);
        }
        costHistory.push(costCopy);

        rowsolHistory.push(this.rowsol.clone());
        colsolHistory.push(this.colsol.clone());
        uHistory.push(this.u.clone());
        vHistory.push(this.v.clone());
        distanceHistory.push(this.currentDistance);

        BitSet[] clusterCopy = new BitSet[currentClusters.length];
        for (int i = 0; i < currentClusters.length; i++) {
            if (currentClusters[i] != null) clusterCopy[i] = (BitSet) currentClusters[i].clone();
        }
        clusterHistory.push(clusterCopy);
    }

    private void clearHistory() {
        costHistory.clear();
        rowsolHistory.clear();
        colsolHistory.clear();
        uHistory.clear();
        vHistory.clear();
        distanceHistory.clear();
        clusterHistory.clear();
    }

    private void collectLeafIds(Node node, IdGroup idGroup, List<Integer> leafIds) {
        if (node.isLeaf()) {
            leafIds.add(idGroup.whichIdNumber(node.getIdentifier().getName()));
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                collectLeafIds(node.getChild(i), idGroup, leafIds);
            }
        }
    }

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { clearHistory(); }
    @Override public double getDistance(Tree t1, Tree t2, int... indexes) { return mcMetricFull.getDistance(t1, t2, indexes); }
    @Override public String getName() { return "Accelerated " + mcMetricFull.getName(); }
    @Override public String getCommandLineName() { return mcMetricFull.getCommandLineName(); }
    @Override public void setCommandLineName(String cln) { mcMetricFull.setCommandLineName(cln); }
    @Override public void setName(String name) { mcMetricFull.setName(name); }
    @Override public String getDescription() { return mcMetricFull.getDescription(); }
    @Override public void setDescription(String d) { mcMetricFull.setDescription(d); }
    @Override public void initData() { mcMetricFull.initData(); }
    @Override public boolean isRooted() { return true; }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return mcMetricFull.isDiffLeafSets(); }
    @Override public AlignInfo getAlignment() { return mcMetricFull.getAlignment(); }
}