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
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingClusterMetric;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class MCIncrementalMetric implements IncrementalMetric {

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

    @Override
    public double applyNni(NniMove move) {
        saveCurrentStateToHistory();

        List<Integer> movingSubtreeLeaves = new ArrayList<>();
        collectLeafIds(move.movingSubtree, idGroup, movingSubtreeLeaves);
        List<Integer> swapPartnerLeaves = new ArrayList<>();
        collectLeafIds(move.swapPartner, idGroup, swapPartnerLeaves);

        Node lca = NodeUtils.getFirstCommonAncestor(move.movingSubtree, move.swapPartner);
        List<Integer> changedRowsList = new ArrayList<>();

        Node curr = move.movingSubtree.getParent();
        while (curr != null && curr != lca) {
            if (!curr.isRoot()) {
                BitSet bs = (BitSet) currentClusters.get(curr).clone();
                for (int id : movingSubtreeLeaves) bs.flip(id);
                for (int id : swapPartnerLeaves) bs.flip(id);
                currentClusters.put(curr, bs);

                Integer rowIndex = nodeToRow.get(curr);
                if (rowIndex != null) changedRowsList.add(rowIndex);
            }
            curr = curr.getParent();
        }

        curr = move.swapPartner.getParent();
        while (curr != null && curr != lca) {
            if (!curr.isRoot()) {
                BitSet bs = (BitSet) currentClusters.get(curr).clone();
                for (int id : movingSubtreeLeaves) bs.flip(id);
                for (int id : swapPartnerLeaves) bs.flip(id);
                currentClusters.put(curr, bs);

                Integer rowIndex = nodeToRow.get(curr);
                if (rowIndex != null) changedRowsList.add(rowIndex);
            }
            curr = curr.getParent();
        }

        int[] changedRows = changedRowsList.stream().distinct().mapToInt(Integer::intValue).toArray();

        for (int i : changedRows) {
            Node n1 = rowToNode[i];
            for (int j = 0; j < dim; j++) {
                Node n2 = colToNode[j];
                if (n2 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistXorBit(currentClusters.get(n1), targetClusters.get(n2));
                } else {
                    this.assigncost[i][j] = (short) ClusterDist.getDistToOAsMinBit(currentClusters.get(n1));
                }
            }
        }

        if (changedRows.length > 0) {
            this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRows);
        }
        return this.currentDistance;
    }

    @Override public void undoNni(NniMove move) { undoSprRegraftStep(); }
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

    @Override
    public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR newTopology) {
        return internalApply2sEcrMove(top, m1, m2, boundarySubtrees, newTopology, false);
    }

    @Override
    public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR newTopology) {
        return internalApply2sEcrMove(top, m1, m2, boundarySubtrees, newTopology, true);
    }

    private double internalApply2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR newTopology, boolean isCommit) {
        saveCurrentStateToHistory();

        BitSet[] sBits = new BitSet[4];
        for (int i = 0; i < 4; i++) sBits[i] = getCluster(boundarySubtrees[i]);

        BitSet newM1 = new BitSet();
        BitSet newM2 = new BitSet();
        BitSet newTop = new BitSet();

        if (newTopology.isFork) {
            newM1.or(sBits[newTopology.indices[0]]); newM1.or(sBits[newTopology.indices[1]]);
            newM2.or(sBits[newTopology.indices[2]]); newM2.or(sBits[newTopology.indices[3]]);
            newTop.or(newM1); newTop.or(newM2);
        } else {
            newM2.or(sBits[newTopology.indices[2]]); newM2.or(sBits[newTopology.indices[3]]);
            newM1.or(sBits[newTopology.indices[1]]); newM1.or(newM2);
            newTop.or(sBits[newTopology.indices[0]]); newTop.or(newM1);
        }

        currentClusters.put(top, newTop);
        currentClusters.put(m1, newM1);
        currentClusters.put(m2, newM2);

        List<Integer> changedRowsList = new ArrayList<>();
        Integer rTop = nodeToRow.get(top); if (rTop != null) changedRowsList.add(rTop);
        Integer rM1 = nodeToRow.get(m1); if (rM1 != null) changedRowsList.add(rM1);
        Integer rM2 = nodeToRow.get(m2); if (rM2 != null) changedRowsList.add(rM2);
        int[] changedRows = changedRowsList.stream().mapToInt(Integer::intValue).toArray();

        for (int i : changedRows) {
            Node n1 = rowToNode[i];
            for (int j = 0; j < dim; j++) {
                Node n2 = colToNode[j];
                if (n2 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistXorBit(currentClusters.get(n1), targetClusters.get(n2));
                } else {
                    this.assigncost[i][j] = (short) ClusterDist.getDistToOAsMinBit(currentClusters.get(n1));
                }
            }
        }

        if (changedRows.length > 0) {
            this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRows);
        }

        double evaluatedDist = this.currentDistance;

        if (!isCommit) {
            undoSprRegraftStep();
        }

        return evaluatedDist;
    }

    @Override
    public double evaluate3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR newTopology) {
        return internalApply3sEcrMove(cluster, boundarySubtrees, newTopology, false);
    }

    @Override
    public double commit3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR newTopology) {
        return internalApply3sEcrMove(cluster, boundarySubtrees, newTopology, true);
    }

    private double internalApply3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR newTopology, boolean isCommit) {
        saveCurrentStateToHistory();

        BitSet[] sBits = new BitSet[5];
        for (int i = 0; i < 5; i++) sBits[i] = getCluster(boundarySubtrees[i]);

        List<Integer> changedRowsList = new ArrayList<>();

        // PRAWIDŁOWE MAPOWANIE PRE-ORDER
        ClusterBuilder builder = new ClusterBuilder();
        BitSet topCluster = builder.build(newTopology, sBits, cluster, currentClusters, changedRowsList);

        Node topNode = cluster.get(0);
        currentClusters.put(topNode, topCluster);
        Integer rTop = nodeToRow.get(topNode);
        if (rTop != null) changedRowsList.add(rTop);

        int[] changedRows = changedRowsList.stream().mapToInt(Integer::intValue).toArray();

        for (int i : changedRows) {
            Node n1 = rowToNode[i];
            for (int j = 0; j < dim; j++) {
                Node n2 = colToNode[j];
                if (n2 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistXorBit(currentClusters.get(n1), targetClusters.get(n2));
                } else {
                    this.assigncost[i][j] = (short) ClusterDist.getDistToOAsMinBit(currentClusters.get(n1));
                }
            }
        }

        if (changedRows.length > 0) {
            this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRows);
        }

        double evaluatedDist = this.currentDistance;

        if (!isCommit) {
            undoSprRegraftStep();
        }

        return evaluatedDist;
    }

    private class ClusterBuilder {
        int idx = 1;

        BitSet build(SubtreeEcr3Utils.TopologyTemplate3sECR temp, BitSet[] sBits, List<Node> cluster, Map<Node, BitSet> targetMap, List<Integer> changedRows) {
            BitSet bs = new BitSet();

            if (temp.left.leafIndex != -1) {
                bs.or(sBits[temp.left.leafIndex]);
            } else {
                Node leftNode = cluster.get(idx++);
                BitSet leftBs = build(temp.left, sBits, cluster, targetMap, changedRows);
                targetMap.put(leftNode, leftBs);
                Integer rIndex = nodeToRow.get(leftNode);
                if (rIndex != null) changedRows.add(rIndex);
                bs.or(leftBs);
            }

            if (temp.right.leafIndex != -1) {
                bs.or(sBits[temp.right.leafIndex]);
            } else {
                Node rightNode = cluster.get(idx++);
                BitSet rightBs = build(temp.right, sBits, cluster, targetMap, changedRows);
                targetMap.put(rightNode, rightBs);
                Integer rIndex = nodeToRow.get(rightNode);
                if (rIndex != null) changedRows.add(rIndex);
                bs.or(rightBs);
            }

            return bs;
        }
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

        clusterHistory.push(new IdentityHashMap<>(currentClusters));
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