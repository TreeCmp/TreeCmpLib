package treecmp.metrics.topological.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.AlignInfo;
import treecmp.common.ClusterDist;
import treecmp.common.LapSolver;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingTripletMetric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class MatchingTripletIncrementalMetric implements IncrementalMetric {

    private Tree baseTree;
    private Tree targetTree;
    private Tree currentVirtualTree;
    private double currentDistance;
    private int dim;

    private IdGroup baseIdGroup;
    private int[] aliasTarget;

    private int[][] assigncost;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private BitSet[] currentClusters;
    private int[][] targetLcaMatrix;
    private int[] t2IntTripletCount;

    private final SprUtils sprUtils = new SprUtils();
    private final MatchingTripletMetric mtMetricFull = new MatchingTripletMetric();

    private final Stack<int[][]> costHistory = new Stack<>();
    private final Stack<int[]> rowsolHistory = new Stack<>();
    private final Stack<int[]> colsolHistory = new Stack<>();
    private final Stack<int[]> uHistory = new Stack<>();
    private final Stack<int[]> vHistory = new Stack<>();
    private final Stack<Double> distanceHistory = new Stack<>();
    private final Stack<BitSet[]> clusterHistory = new Stack<>();
    private final Stack<Tree> treeHistory = new Stack<>();

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        this.baseTree = baseTree;
        this.targetTree = targetTree;
        this.currentVirtualTree = baseTree;

        if (baseTree != null && targetTree != null) {
            clearHistory();

            int intT1Num = baseTree.getInternalNodeCount();
            int intT2Num = targetTree.getInternalNodeCount();
            this.dim = Math.max(intT1Num, intT2Num);

            this.baseIdGroup = TreeUtils.getLeafIdGroup(baseTree);
            int[] aliasBase = TreeUtils.mapExternalIdentifiers(this.baseIdGroup, baseTree);
            this.aliasTarget = TreeUtils.mapExternalIdentifiers(this.baseIdGroup, targetTree);

            this.assigncost = new int[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];
            this.u = new int[dim];
            this.v = new int[dim];

            this.currentClusters = new BitSet[dim];
            BitSet[] baseClustersRaw = ClusterDist.RootedTree2BitSetArray(baseTree, this.baseIdGroup);

            BitSet rootBitSet = new BitSet();
            rootBitSet.set(0, baseTree.getExternalNodeCount());

            for (int i = 0; i < intT1Num; i++) {
                Node n = baseTree.getInternalNode(i);
                int num = n.getNumber();
                if (num >= 0 && num < baseClustersRaw.length && baseClustersRaw[num] != null) {
                    this.currentClusters[i] = baseClustersRaw[num];
                } else {
                    this.currentClusters[i] = (BitSet) rootBitSet.clone();
                }
            }

            this.targetLcaMatrix = TreeCmpUtils.calcLcaMatrix(targetTree, this.baseIdGroup);
            int[][] lcaMatrix1 = TreeCmpUtils.calcLcaMatrix(baseTree, this.baseIdGroup);

            int maxNodesT2 = getSafeMaxNodeId(targetTree);
            short[] cSize2 = new short[maxNodesT2];
            Node[] postOrderT2 = TreeCmpUtils.getNodesInPostOrder(targetTree);
            TreeCmpUtils.calcCladeSizes(targetTree, postOrderT2, cSize2);
            Set<Node>[] verticesOutsideClade2 = TreeCmpUtils.getVerticesOutsideClade(targetTree);

            this.t2IntTripletCount = new int[dim];
            for (int i = 0; i < intT2Num; i++) {
                t2IntTripletCount[i] = coutTriplets(targetTree.getInternalNode(i), cSize2, verticesOutsideClade2);
            }

            int maxNodesT1 = getSafeMaxNodeId(baseTree);
            short[] cSize1 = new short[maxNodesT1];
            Node[] postOrderT1 = TreeCmpUtils.getNodesInPostOrder(baseTree);
            TreeCmpUtils.calcCladeSizes(baseTree, postOrderT1, cSize1);
            Set<Node>[] verticesOutsideClade1 = TreeCmpUtils.getVerticesOutsideClade(baseTree);

            int[] t1IntTripletCount = new int[dim];
            for (int i = 0; i < intT1Num; i++) {
                t1IntTripletCount[i] = coutTriplets(baseTree.getInternalNode(i), cSize1, verticesOutsideClade1);
            }

            int N = baseTree.getExternalNodeCount();
            int[][] initialIntersection = new int[intT1Num][intT2Num];

            for (int i = 0; i < N; i++) {
                for (int j = i + 1; j < N; j++) {
                    for (int k = j + 1; k < N; k++) {
                        int ind1 = TreeCmpUtils.getNcv(baseTree, i, j, k, lcaMatrix1, aliasBase);
                        int ind2 = TreeCmpUtils.getNcv(targetTree, i, j, k, targetLcaMatrix, this.aliasTarget);
                        initialIntersection[ind1][ind2]++;
                    }
                }
            }

            for (int i = 0; i < dim; i++) {
                for (int j = 0; j < dim; j++) {
                    if (i < intT1Num && j < intT2Num) {
                        assigncost[i][j] = t1IntTripletCount[i] + t2IntTripletCount[j] - (initialIntersection[i][j] << 1);
                    } else if (i >= intT1Num && j < intT2Num) {
                        assigncost[i][j] = t2IntTripletCount[j];
                    } else if (i < intT1Num && j >= intT2Num) {
                        assigncost[i][j] = t1IntTripletCount[i];
                    } else {
                        assigncost[i][j] = 0;
                    }
                }
            }

            int rawMetric = LapSolver.lap(dim, assigncost, rowsol, colsol, u, v);
            this.currentDistance = 0.5 * rawMetric;
        } else {
            this.currentDistance = 0;
        }
    }

    @Override
    public double applyNni(NniMove move) {
        saveCurrentStateToHistory();

        Node virtMoving = getMappedNode(currentVirtualTree, move.movingSubtree);
        Node virtSwapPartner = getMappedNode(currentVirtualTree, move.swapPartner);

        if (virtMoving == null || virtSwapPartner == null) {
            return this.currentDistance;
        }

        Node virtSibling = getSibling(virtMoving);
        if (virtSibling == null) {
            return this.currentDistance;
        }

        Tree tNew = sprUtils.createSprTree(currentVirtualTree, virtSibling, virtSwapPartner);

        // FAIL-SAFE: Ochrona przed patologicznymi ruchami gubiącymi liście!
        if (tNew == null || tNew.getExternalNodeCount() != baseTree.getExternalNodeCount()) {
            return this.currentDistance;
        }

        int intT1Num = tNew.getInternalNodeCount();
        int intT2Num = targetTree.getInternalNodeCount();

        BitSet[] newClustersRaw = ClusterDist.RootedTree2BitSetArray(tNew, this.baseIdGroup);
        BitSet rootBitSet = new BitSet();
        rootBitSet.set(0, tNew.getExternalNodeCount());

        BitSet[] newClusters = new BitSet[intT1Num];
        for (int i = 0; i < intT1Num; i++) {
            Node n = tNew.getInternalNode(i);
            int num = n.getNumber();
            if (num >= 0 && num < newClustersRaw.length && newClustersRaw[num] != null) {
                newClusters[i] = newClustersRaw[num];
            } else {
                newClusters[i] = (BitSet) rootBitSet.clone();
            }
        }

        int[] nodeToRow = new int[intT1Num];
        Arrays.fill(nodeToRow, -1);
        boolean[] rowUsed = new boolean[dim];

        for (int i = 0; i < intT1Num; i++) {
            for (int r = 0; r < dim; r++) {
                if (!rowUsed[r] && currentClusters[r] != null && currentClusters[r].equals(newClusters[i])) {
                    nodeToRow[i] = r;
                    rowUsed[r] = true;
                    break;
                }
            }
        }

        List<Integer> availableRows = new ArrayList<>();
        for (int r = 0; r < dim; r++) {
            if (!rowUsed[r] && currentClusters[r] != null) availableRows.add(r);
        }
        for (int r = 0; r < dim; r++) {
            if (!rowUsed[r] && currentClusters[r] == null) availableRows.add(r);
        }

        int unusedIdx = 0;
        for (int i = 0; i < intT1Num; i++) {
            if (nodeToRow[i] == -1) {
                int r = availableRows.get(unusedIdx++);
                nodeToRow[i] = r;
                currentClusters[r] = newClusters[i];
            }
        }

        int[][] lcaNew = TreeCmpUtils.calcLcaMatrix(tNew, this.baseIdGroup);
        int[] aliasNew = TreeUtils.mapExternalIdentifiers(this.baseIdGroup, tNew);
        Node[] postOrder = TreeCmpUtils.getNodesInPostOrder(tNew);

        int maxNodesNew = getSafeMaxNodeId(tNew);
        short[] cSizeNew = new short[maxNodesNew];
        TreeCmpUtils.calcCladeSizes(tNew, postOrder, cSizeNew);
        Set<Node>[] verticesOutsideCladeNew = TreeCmpUtils.getVerticesOutsideClade(tNew);

        int[] t1IntTripletCount = new int[intT1Num];
        for (int i = 0; i < intT1Num; i++) {
            t1IntTripletCount[i] = coutTriplets(tNew.getInternalNode(i), cSizeNew, verticesOutsideCladeNew);
        }

        int N = tNew.getExternalNodeCount();
        int[][] newIntersection = new int[intT1Num][intT2Num];
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                for (int k = j + 1; k < N; k++) {
                    int ind1 = TreeCmpUtils.getNcv(tNew, i, j, k, lcaNew, aliasNew);
                    int ind2 = TreeCmpUtils.getNcv(targetTree, i, j, k, targetLcaMatrix, this.aliasTarget);
                    newIntersection[ind1][ind2]++;
                }
            }
        }

        List<Integer> actualChangedRows = new ArrayList<>();
        for (int i = 0; i < intT1Num; i++) {
            int r = nodeToRow[i];
            boolean rowChanged = false;
            for (int c = 0; c < dim; c++) {
                int newValue;
                if (c < intT2Num) {
                    newValue = t1IntTripletCount[i] + t2IntTripletCount[c] - (newIntersection[i][c] << 1);
                } else {
                    newValue = t1IntTripletCount[i];
                }
                if (assigncost[r][c] != newValue) {
                    assigncost[r][c] = newValue;
                    rowChanged = true;
                }
            }
            if (rowChanged) {
                actualChangedRows.add(r);
            }
        }

        int[] changedRows = actualChangedRows.stream().mapToInt(Integer::intValue).toArray();
        currentVirtualTree = tNew;

        if (changedRows.length > 0) {
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, changedRows);
            this.currentDistance = 0.5 * rawMetric;
        }

        return this.currentDistance;
    }

    @Override
    public void undoNni(NniMove move) {
        undoSprRegraftStep();
    }

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
            this.currentVirtualTree = treeHistory.pop();
        }
    }

    private int coutTriplets(Node n, short[] clustSizeTab, Set<Node>[] verticesOutsideClade) {
        int chCount = n.getChildCount();
        int[] chSize = new int[chCount + 1];

        for (int i = 0; i < chCount; i++) {
            Node chNode = n.getChild(i);
            if (chNode.isLeaf()) {
                chSize[i] = 1;
            } else {
                chSize[i] = clustSizeTab[chNode.getNumber()];
            }
        }

        if (verticesOutsideClade != null && n.getNumber() < verticesOutsideClade.length && verticesOutsideClade[n.getNumber()] != null) {
            chSize[chCount] = verticesOutsideClade[n.getNumber()].size();
        } else {
            chSize[chCount] = 0;
        }

        int pairCount = 0;
        for (int i = 0; i < chSize.length; i++) {
            for (int j = i + 1; j < chSize.length; j++) {
                for (int k = j + 1; k < chSize.length; k++) {
                    pairCount += (chSize[i] * chSize[j] * chSize[k]);
                }
            }
        }
        return pairCount;
    }

    private Node getMappedNode(Tree virtTree, Node targetNode) {
        if (targetNode == null) return null;
        if (targetNode.isLeaf()) {
            return TreeUtils.getNodeByName(virtTree, targetNode.getIdentifier().getName());
        }
        BitSet targetBs = new BitSet();
        populateBitSet(targetNode, targetBs);
        Node[] allNodes = TreeCmpUtils.getAllNodes(virtTree);
        for (Node n : allNodes) {
            if (!n.isLeaf()) {
                BitSet bs = new BitSet();
                populateBitSet(n, bs);
                if (bs.equals(targetBs)) {
                    return n;
                }
            }
        }
        return null;
    }

    private void populateBitSet(Node n, BitSet bs) {
        if (n.isLeaf()) {
            int id = this.baseIdGroup.whichIdNumber(n.getIdentifier().getName());
            if (id >= 0) bs.set(id);
        } else {
            for (int i = 0; i < n.getChildCount(); i++) {
                populateBitSet(n.getChild(i), bs);
            }
        }
    }

    private int getSafeMaxNodeId(Tree tree) {
        int maxId = 0;
        Node[] allNodes = TreeCmpUtils.getAllNodes(tree);
        for (Node n : allNodes) {
            if (n != null && n.getNumber() > maxId) {
                maxId = n.getNumber();
            }
        }
        return maxId + 1;
    }

    private void saveCurrentStateToHistory() {
        int[][] costCopy = new int[dim][dim];
        for (int i = 0; i < dim; i++) {
            System.arraycopy(this.assigncost[i], 0, costCopy[i], 0, dim);
        }
        costHistory.push(costCopy);
        rowsolHistory.push(this.rowsol.clone());
        colsolHistory.push(this.colsol.clone());
        uHistory.push(this.u.clone());
        vHistory.push(this.v.clone());
        distanceHistory.push(this.currentDistance);

        BitSet[] clusterCopy = new BitSet[dim];
        for (int i = 0; i < dim; i++) {
            if (currentClusters[i] != null) clusterCopy[i] = (BitSet) currentClusters[i].clone();
        }
        clusterHistory.push(clusterCopy);
        treeHistory.push(this.currentVirtualTree);
    }

    private void clearHistory() {
        costHistory.clear();
        rowsolHistory.clear();
        colsolHistory.clear();
        uHistory.clear();
        vHistory.clear();
        distanceHistory.clear();
        clusterHistory.clear();
        treeHistory.clear();
    }

    private Node getSibling(Node node) {
        Node parent = node.getParent();
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) != node) {
                return parent.getChild(i);
            }
        }
        return null;
    }

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { clearHistory(); }
    @Override public double getDistance(Tree t1, Tree t2, int... indexes) { return mtMetricFull.getDistance(t1, t2, indexes); }
    @Override public String getName() { return "Accelerated " + mtMetricFull.getName(); }
    @Override public String getCommandLineName() { return mtMetricFull.getCommandLineName(); }
    @Override public void setCommandLineName(String cln) { mtMetricFull.setCommandLineName(cln); }
    @Override public void setName(String name) { mtMetricFull.setName(name); }
    @Override public String getDescription() { return mtMetricFull.getDescription(); }
    @Override public void setDescription(String d) { mtMetricFull.setDescription(d); }
    @Override public void initData() { mtMetricFull.initData(); }
    @Override public boolean isRooted() { return false; }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return mtMetricFull.isDiffLeafSets(); }
    @Override public AlignInfo getAlignment() { return mtMetricFull.getAlignment(); }
}