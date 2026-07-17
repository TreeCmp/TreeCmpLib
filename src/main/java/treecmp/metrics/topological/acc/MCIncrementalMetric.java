package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.Tree;
import pal.misc.IdGroup;
import pal.tree.TreeUtils;
import pal.tree.NodeUtils;
import treecmp.common.AlignInfo;
import treecmp.common.ClusterDist;
import treecmp.common.LapSolver;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.acc.IncrementalSprWalker;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingClusterMetric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class MCIncrementalMetric implements IncrementalMetric, IncrementalSprWalker.RootedMetric {

    private Tree baseTree;
    private Tree targetTree;
    private double currentDistance;
    private IdGroup idGroup;

    private final MatchingClusterMetric mcMetricFull = new MatchingClusterMetric();

    private int dim;
    private short[][] assigncost;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private Map<Node, BitSet> currentClusters;
    private Map<Node, BitSet> targetClusters;
    private Map<Node, Integer> nodeToRow;

    private Node[] rowToNode;
    private Node[] colToNode;

    private final Stack<short[][]> costHistory = new Stack<>();
    private final Stack<int[]> rowsolHistory = new Stack<>();
    private final Stack<int[]> colsolHistory = new Stack<>();
    private final Stack<int[]> uHistory = new Stack<>();
    private final Stack<int[]> vHistory = new Stack<>();
    private final Stack<Double> distanceHistory = new Stack<>();
    private final Stack<Map<Node, BitSet>> clusterHistory = new Stack<>();

    // ========================================================================
    // DELTA STOS - PAMIĘĆ O(N)
    // ========================================================================
    private static class LapStateDelta {
        final int[] rows;
        final short[][] oldRows;
        final int[] oldU, oldV, oldRowsol, oldColsol;
        final double oldDistance;
        final Map<Node, BitSet> oldClust;

        LapStateDelta(int[] rows, short[][] oldRows, int[] oldU, int[] oldV, int[] oldRowsol, int[] oldColsol, double oldDistance, Map<Node, BitSet> oldClust) {
            this.rows = rows;
            this.oldRows = oldRows;
            this.oldU = oldU;
            this.oldV = oldV;
            this.oldRowsol = oldRowsol;
            this.oldColsol = oldColsol;
            this.oldDistance = oldDistance;
            this.oldClust = oldClust;
        }
    }

    private final Stack<LapStateDelta> deltaStack = new Stack<>();

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        this.baseTree = baseTree;
        this.targetTree = targetTree;

        if (baseTree != null && targetTree != null) {
            clearHistory();
            this.idGroup = TreeUtils.getLeafIdGroup(baseTree);

            int size1 = baseTree.getInternalNodeCount() - 1;
            int size2 = targetTree.getInternalNodeCount() - 1;
            this.dim = Math.max(size1, size2);

            this.assigncost = new short[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];
            this.u = new int[dim];
            this.v = new int[dim];

            this.currentClusters = new IdentityHashMap<>();
            extractClusters(baseTree.getRoot(), idGroup, this.currentClusters);

            this.targetClusters = new IdentityHashMap<>();
            extractClusters(targetTree.getRoot(), idGroup, this.targetClusters);

            this.rowToNode = new Node[dim];
            this.colToNode = new Node[dim];
            this.nodeToRow = new IdentityHashMap<>();

            int r = 0;
            for (int i = 0; i < baseTree.getInternalNodeCount(); i++) {
                Node n = baseTree.getInternalNode(i);
                if (!n.isRoot() && r < dim) {
                    rowToNode[r] = n;
                    nodeToRow.put(n, r);
                    r++;
                }
            }

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

    private BitSet extractClusters(Node node, IdGroup idGroup, Map<Node, BitSet> map) {
        BitSet bs = new BitSet();
        if (node.isLeaf()) {
            int id = idGroup.whichIdNumber(node.getIdentifier().getName());
            if (id >= 0) bs.set(id);
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                bs.or(extractClusters(node.getChild(i), idGroup, map));
            }
            map.put(node, (BitSet) bs.clone());
        }
        return bs;
    }

    private void buildInitialCostMatrix() {
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                Node n1 = rowToNode[i];
                Node n2 = colToNode[j];

                if (n1 != null && n2 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistXorBit(currentClusters.get(n1), targetClusters.get(n2));
                } else if (n1 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistToOAsMinBit(currentClusters.get(n1));
                } else if (n2 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistToOAsMinBit(targetClusters.get(n2));
                } else {
                    this.assigncost[i][j] = 0;
                }
            }
        }
    }

    private BitSet getCluster(Node n) {
        if (n.isLeaf()) {
            BitSet bs = new BitSet();
            int id = idGroup.whichIdNumber(n.getIdentifier().getName());
            if (id >= 0) bs.set(id);
            return bs;
        } else {
            return currentClusters.get(n);
        }
    }

    // ========================================================================
    // NOWE METODY DLA ROOTED SPR WALKERA (TRANSAKCYJNE)
    // ========================================================================

    /**
     * Bezpieczna, transakcyjna aktualizacja macierzy. Najpierw zapisuje prawdziwy,
     * stary stan na Delta Stos, a dopiero potem aplikuje modyfikacje.
     */
    private void updateRowSafelyAndSave(Map<Integer, BitSet> rowUpdates) {
        int[] rows = new int[rowUpdates.size()];
        short[][] oldRows = new short[rows.length][dim];
        Map<Node, BitSet> oldClust = new IdentityHashMap<>();

        int idx = 0;
        for (Map.Entry<Integer, BitSet> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            rows[idx] = r;
            // BEZPIECZEŃSTWO: Zapisujemy starą kopię z matrixa ZANIM go nadpiszemy!
            oldRows[idx] = Arrays.copyOf(assigncost[r], dim);

            Node n = rowToNode[r];
            if (n != null && currentClusters.containsKey(n)) {
                oldClust.put(n, (BitSet) currentClusters.get(n).clone());
            }
            idx++;
        }

        // Push to stack
        deltaStack.push(new LapStateDelta(rows, oldRows, Arrays.copyOf(u, dim), Arrays.copyOf(v, dim),
                Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance, oldClust));

        // NOW modify the physical matrix and clusters
        for (Map.Entry<Integer, BitSet> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            BitSet newCluster = entry.getValue();

            Node n = rowToNode[r];
            if (n != null) currentClusters.put(n, newCluster);

            for (int j = 0; j < dim; j++) {
                Node n2 = colToNode[j];
                if (n2 != null) {
                    this.assigncost[r][j] = (short) ClusterDist.getDistXorBit(newCluster, targetClusters.get(n2));
                } else {
                    this.assigncost[r][j] = (short) ClusterDist.getDistToOAsMinBit(newCluster);
                }
            }
        }

        // RUN LAP UPDATE
        if (rows.length > 0) {
            this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, rows);
        }
    }

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        BitSet P = getCluster(pruneNode);
        Map<Integer, BitSet> updates = new HashMap<>();

        Node curr = pruneNode.getParent().getParent();
        while (curr != null) {
            Integer r = nodeToRow.get(curr);
            if (r != null) {
                BitSet bs = (BitSet) currentClusters.get(curr).clone();
                bs.andNot(P);
                updates.put(r, bs);
            }
            curr = curr.getParent();
        }

        Integer r_p = nodeToRow.get(wanderingSource);
        if (r_p != null) {
            BitSet empty = new BitSet();
            updates.put(r_p, empty);
        }
        updateRowSafelyAndSave(updates);
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node wanderingSource) {
        BitSet P = getCluster(pruneNode);
        Map<Integer, BitSet> updates = new HashMap<>();

        Integer r_p = nodeToRow.get(wanderingSource);
        if (r_p != null) {
            BitSet newRp = (BitSet) getCluster(baseTree.getRoot()).clone();
            newRp.andNot(P);
            updates.put(r_p, newRp);
        }
        updateRowSafelyAndSave(updates);
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        BitSet P = getCluster(pruneNode);
        Map<Integer, BitSet> updates = new HashMap<>();

        Integer r_p = nodeToRow.get(wanderingSource);
        if (r_p != null) {
            BitSet childCluster = currentClusters.containsKey(childTarget) ? currentClusters.get(childTarget) : getCluster(childTarget);
            BitSet newRp = (BitSet) childCluster.clone();
            newRp.or(P);
            updates.put(r_p, newRp);
        }

        Integer r_parent = nodeToRow.get(parentTarget);
        if (r_parent != null && parentTarget != wanderingSource) {
            BitSet parentCluster = currentClusters.get(parentTarget);
            BitSet newParent = (BitSet) parentCluster.clone();
            newParent.or(P);
            updates.put(r_parent, newParent);
        }

        updateRowSafelyAndSave(updates);
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        undoDeltaStack();
    }

    @Override
    public void revertPrunedState(Node pruneNode, Node wanderingSource) {
        undoDeltaStack(); // Cofa setTargetRoot
        undoDeltaStack(); // Cofa setPrunedState
    }

    private void undoDeltaStack() {
        LapStateDelta delta = deltaStack.pop();
        for (int i = 0; i < delta.rows.length; i++) {
            System.arraycopy(delta.oldRows[i], 0, assigncost[delta.rows[i]], 0, dim);
        }
        System.arraycopy(delta.oldU, 0, u, 0, dim);
        System.arraycopy(delta.oldV, 0, v, 0, dim);
        System.arraycopy(delta.oldRowsol, 0, rowsol, 0, dim);
        System.arraycopy(delta.oldColsol, 0, colsol, 0, dim);

        for (Map.Entry<Node, BitSet> e : delta.oldClust.entrySet()) {
            currentClusters.put(e.getKey(), e.getValue());
        }
        this.currentDistance = delta.oldDistance;
    }

    // ========================================================================
    // KLASYCZNE METODY (ECR / NNI)
    // ========================================================================
    @Override public double applyNni(NniMove move) { return 0; /* Nieużywane w nowym systemie */ }
    @Override public void undoNni(NniMove move) { }
    @Override
    public void applySprPrune(Node pruneNode) {
        saveCurrentStateToHistory();
    }

    @Override
    public void undoSprPrune(Node pruneNode) {
        undoSprRegraftStep();
    }

    @Override
    public void applySprRegraftStep(Node pruneNode, Node currentNode) {
        saveCurrentStateToHistory();
    }

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

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        Tree tempTree = new SprUtils().createSprTree(this.baseTree, pruneNode, targetNode);
        if (tempTree != null) {
            if (tempTree instanceof pal.tree.SimpleTree) {
                ((pal.tree.SimpleTree) tempTree).createNodeList(); // Zabezpieczenie przed błędem Label
            }
            return mcMetricFull.getDistance(tempTree, this.targetTree);
        }
        return Double.POSITIVE_INFINITY;
    }

    @Override public double evaluate2sEcrMove(Node t, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR n) { return 0; }
    @Override public double commit2sEcrMove(Node t, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR n) { return 0; }
    @Override public double evaluate3sEcrMove(List<Node> c, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR n) { return 0; }
    @Override public double commit3sEcrMove(List<Node> c, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR n) { return 0; }

    private void clearHistory() {
        costHistory.clear();
        rowsolHistory.clear();
        colsolHistory.clear();
        uHistory.clear();
        vHistory.clear();
        distanceHistory.clear();
        clusterHistory.clear();
        deltaStack.clear();
    }

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

        // Głęboka kopia (deep clone) klastrów - chroni przed mutacją referencji!
        IdentityHashMap<Node, BitSet> clustersCopy = new IdentityHashMap<>();
        for (Map.Entry<Node, BitSet> entry : currentClusters.entrySet()) {
            clustersCopy.put(entry.getKey(), (BitSet) entry.getValue().clone());
        }
        clusterHistory.push(clustersCopy);
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