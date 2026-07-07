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
import treecmp.metrics.topological.MatchingSplitMetric;

import java.util.Arrays;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Stack;

public class MSIncrementalMetric implements IncrementalMetric {

    private Tree baseTree;
    private Tree targetTree;
    private double currentDistance;
    private int totalLeaves;
    private IdGroup idGroup;

    private final MatchingSplitMetric msMetricFull = new MatchingSplitMetric();

    private int dim;
    private short[][] assigncost;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private Map<Node, BitSet> currentSplits;
    private Map<Node, BitSet> targetSplits;
    private Map<Node, Integer> nodeToRow;

    private Node[] rowToNode;
    private Node[] colToNode;

    private final Stack<short[][]> costHistory = new Stack<>();
    private final Stack<int[]> rowsolHistory = new Stack<>();
    private final Stack<int[]> colsolHistory = new Stack<>();
    private final Stack<int[]> uHistory = new Stack<>();
    private final Stack<int[]> vHistory = new Stack<>();
    private final Stack<Double> distanceHistory = new Stack<>();
    private final Stack<Map<Node, BitSet>> splitHistory = new Stack<>();

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        this.baseTree = baseTree;
        this.targetTree = targetTree;

        if (baseTree != null && targetTree != null) {
            clearHistory();
            this.totalLeaves = baseTree.getExternalNodeCount();
            this.idGroup = TreeUtils.getLeafIdGroup(baseTree);

            int size1 = baseTree.getInternalNodeCount() - 1;
            int size2 = targetTree.getInternalNodeCount() - 1;
            this.dim = Math.max(size1, size2);

            this.assigncost = new short[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];
            this.u = new int[dim];
            this.v = new int[dim];

            this.currentSplits = new IdentityHashMap<>();
            extractSplits(baseTree.getRoot(), idGroup, this.currentSplits);

            this.targetSplits = new IdentityHashMap<>();
            extractSplits(targetTree.getRoot(), idGroup, this.targetSplits);

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

    private BitSet extractSplits(Node node, IdGroup idGroup, Map<Node, BitSet> map) {
        BitSet bs = new BitSet(totalLeaves);
        if (node.isLeaf()) {
            int id = idGroup.whichIdNumber(node.getIdentifier().getName());
            if (id >= 0) bs.set(id);
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                bs.or(extractSplits(node.getChild(i), idGroup, map));
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
                    this.assigncost[i][j] = calculateSplitDistance(currentSplits.get(n1), targetSplits.get(n2));
                } else if (n1 != null) {
                    this.assigncost[i][j] = calculateUnmatchedSplitCost(currentSplits.get(n1));
                } else if (n2 != null) {
                    this.assigncost[i][j] = calculateUnmatchedSplitCost(targetSplits.get(n2));
                } else {
                    this.assigncost[i][j] = 0;
                }
            }
        }
    }

    private short calculateSplitDistance(BitSet s1, BitSet s2) {
        int xorDist = ClusterDist.getDistXorBit(s1, s2);
        return (short) Math.min(xorDist, totalLeaves - xorDist);
    }

    private short calculateUnmatchedSplitCost(BitSet s) {
        int cardinality = s.cardinality();
        return (short) Math.min(cardinality, totalLeaves - cardinality);
    }

    private BitSet getSplit(Node n) {
        if (n.isLeaf()) {
            BitSet bs = new BitSet(totalLeaves);
            bs.set(idGroup.whichIdNumber(n.getIdentifier().getName()));
            return bs;
        } else {
            return currentSplits.get(n);
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
                BitSet bs = (BitSet) currentSplits.get(curr).clone();
                for (int id : movingSubtreeLeaves) bs.flip(id);
                for (int id : swapPartnerLeaves) bs.flip(id);
                currentSplits.put(curr, bs);

                Integer rowIndex = nodeToRow.get(curr);
                if (rowIndex != null) changedRowsList.add(rowIndex);
            }
            curr = curr.getParent();
        }

        curr = move.swapPartner.getParent();
        while (curr != null && curr != lca) {
            if (!curr.isRoot()) {
                BitSet bs = (BitSet) currentSplits.get(curr).clone();
                for (int id : movingSubtreeLeaves) bs.flip(id);
                for (int id : swapPartnerLeaves) bs.flip(id);
                currentSplits.put(curr, bs);

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
                    this.assigncost[i][j] = calculateSplitDistance(currentSplits.get(n1), targetSplits.get(n2));
                } else {
                    this.assigncost[i][j] = calculateUnmatchedSplitCost(currentSplits.get(n1));
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
            this.currentSplits = splitHistory.pop();
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
        for (int i = 0; i < 4; i++) sBits[i] = getSplit(boundarySubtrees[i]);

        BitSet newM1 = new BitSet(totalLeaves);
        BitSet newM2 = new BitSet(totalLeaves);
        BitSet newTop = new BitSet(totalLeaves);

        if (newTopology.isFork) {
            newM1.or(sBits[newTopology.indices[0]]); newM1.or(sBits[newTopology.indices[1]]);
            newM2.or(sBits[newTopology.indices[2]]); newM2.or(sBits[newTopology.indices[3]]);
            newTop.or(newM1); newTop.or(newM2);
        } else {
            newM2.or(sBits[newTopology.indices[2]]); newM2.or(sBits[newTopology.indices[3]]);
            newM1.or(sBits[newTopology.indices[1]]); newM1.or(newM2);
            newTop.or(sBits[newTopology.indices[0]]); newTop.or(newM1);
        }

        currentSplits.put(top, newTop);
        currentSplits.put(m1, newM1);
        currentSplits.put(m2, newM2);

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
                    this.assigncost[i][j] = calculateSplitDistance(currentSplits.get(n1), targetSplits.get(n2));
                } else {
                    this.assigncost[i][j] = calculateUnmatchedSplitCost(currentSplits.get(n1));
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
        for (int i = 0; i < 5; i++) sBits[i] = getSplit(boundarySubtrees[i]);

        List<Integer> changedRowsList = new ArrayList<>();

        // PRAWIDŁOWE MAPOWANIE PRE-ORDER
        SplitBuilder builder = new SplitBuilder();
        BitSet topSplit = builder.build(newTopology, sBits, cluster, currentSplits, changedRowsList);

        Node topNode = cluster.get(0);
        currentSplits.put(topNode, topSplit);
        Integer rTop = nodeToRow.get(topNode);
        if (rTop != null) changedRowsList.add(rTop);

        int[] changedRows = changedRowsList.stream().mapToInt(Integer::intValue).toArray();

        for (int i : changedRows) {
            Node n1 = rowToNode[i];
            for (int j = 0; j < dim; j++) {
                Node n2 = colToNode[j];
                if (n2 != null) {
                    this.assigncost[i][j] = calculateSplitDistance(currentSplits.get(n1), targetSplits.get(n2));
                } else {
                    this.assigncost[i][j] = calculateUnmatchedSplitCost(currentSplits.get(n1));
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

    private class SplitBuilder {
        int idx = 1;

        BitSet build(SubtreeEcr3Utils.TopologyTemplate3sECR temp, BitSet[] sBits, List<Node> cluster, Map<Node, BitSet> targetMap, List<Integer> changedRows) {
            BitSet bs = new BitSet(totalLeaves);

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

        splitHistory.push(new IdentityHashMap<>(currentSplits));
    }

    private void clearHistory() {
        costHistory.clear();
        rowsolHistory.clear();
        colsolHistory.clear();
        uHistory.clear();
        vHistory.clear();
        distanceHistory.clear();
        splitHistory.clear();
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
    @Override public double getDistance(Tree t1, Tree t2, int... indexes) { return msMetricFull.getDistance(t1, t2, indexes); }
    @Override public String getName() { return "Accelerated " + msMetricFull.getName(); }
    @Override public String getCommandLineName() { return msMetricFull.getCommandLineName(); }
    @Override public void setCommandLineName(String cln) { msMetricFull.setCommandLineName(cln); }
    @Override public void setName(String name) { msMetricFull.setName(name); }
    @Override public String getDescription() { return msMetricFull.getDescription(); }
    @Override public void setDescription(String d) { msMetricFull.setDescription(d); }
    @Override public void initData() { msMetricFull.initData(); }
    @Override public boolean isRooted() { return false; }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return msMetricFull.isDiffLeafSets(); }
    @Override public AlignInfo getAlignment() { return msMetricFull.getAlignment(); }
}