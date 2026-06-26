package treecmp.metrics.topological.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.AlignInfo;
import treecmp.common.LapSolver;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingTripletMetric;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Set;
import java.util.Stack;

public class MatchingTripletIncrementalMetric implements IncrementalMetric {

    private Tree baseTree;
    private Tree targetTree;
    private Tree currentVirtualTree;
    private double currentDistance;
    private int dim;
    private int intT1Num;
    private int intT2Num;
    private int N;

    private IdGroup baseIdGroup;
    private int[][] targetLcaMatrix;
    private int[] identityAlias;

    private int[][] assigncost;
    private int[] rowsol;
    private int[] colsol;

    private int[] currentT1TripletCount;
    private int[] t2IntTripletCount;

    private int[][] evalCost;
    private int[] evalRowsol;
    private int[] evalColsol;
    private int[] evalU;
    private int[] evalV;
    private int[][] evalIntersection;

    private final SprUtils sprUtils = new SprUtils();
    private final MatchingTripletMetric mtMetricFull = new MatchingTripletMetric();
    private final Stack<StateRecord> history = new Stack<>();

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        this.baseTree = baseTree;
        this.targetTree = targetTree;
        this.currentVirtualTree = baseTree;
        history.clear();

        if (baseTree != null && targetTree != null) {
            this.intT1Num = baseTree.getInternalNodeCount();
            this.intT2Num = targetTree.getInternalNodeCount();
            this.dim = Math.max(intT1Num, intT2Num);
            this.baseIdGroup = TreeUtils.getLeafIdGroup(baseTree);
            this.N = baseTree.getExternalNodeCount();

            this.assigncost = new int[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];

            this.evalCost = new int[dim][dim];
            this.evalRowsol = new int[dim];
            this.evalColsol = new int[dim];
            this.evalU = new int[dim];
            this.evalV = new int[dim];
            this.evalIntersection = new int[dim][dim];

            // Macierze pre-mapowane na baseIdGroup - NCV użyje wektora tożsamości
            int[][] lcaMatrix1 = TreeCmpUtils.calcLcaMatrix(baseTree, this.baseIdGroup);
            this.targetLcaMatrix = TreeCmpUtils.calcLcaMatrix(targetTree, this.baseIdGroup);

            this.identityAlias = new int[N];
            for (int i = 0; i < N; i++) this.identityAlias[i] = i;

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

            this.currentT1TripletCount = new int[dim];
            for (int i = 0; i < intT1Num; i++) {
                this.currentT1TripletCount[i] = coutTriplets(baseTree.getInternalNode(i), cSize1, verticesOutsideClade1);
            }

            int[][] initialIntersection = new int[intT1Num][intT2Num];
            for (int i = 0; i < N; i++) {
                for (int j = i + 1; j < N; j++) {
                    for (int k = j + 1; k < N; k++) {
                        int ind1 = TreeCmpUtils.getNcv(baseTree, i, j, k, lcaMatrix1, identityAlias);
                        int ind2 = TreeCmpUtils.getNcv(targetTree, i, j, k, this.targetLcaMatrix, identityAlias);
                        initialIntersection[ind1][ind2]++;
                    }
                }
            }

            for (int r = 0; r < dim; r++) {
                for (int c = 0; c < dim; c++) {
                    if (r < intT1Num && c < intT2Num) {
                        assigncost[r][c] = this.currentT1TripletCount[r] + t2IntTripletCount[c] - (initialIntersection[r][c] << 1);
                    } else if (r >= intT1Num && c < intT2Num) {
                        assigncost[r][c] = t2IntTripletCount[c];
                    } else if (r < intT1Num && c >= intT2Num) {
                        assigncost[r][c] = this.currentT1TripletCount[r];
                    } else {
                        assigncost[r][c] = 0;
                    }
                }
            }

            int[][] lapCost = new int[dim][dim];
            for (int i = 0; i < dim; i++) {
                System.arraycopy(assigncost[i], 0, lapCost[i], 0, dim);
            }
            int[] lapU = new int[dim];
            int[] lapV = new int[dim];

            int rawMetric = LapSolver.lap(dim, lapCost, rowsol, colsol, lapU, lapV);
            this.currentDistance = 0.5 * rawMetric;
        } else {
            this.currentDistance = 0;
        }
    }

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        Tree tNew = sprUtils.createSprTree(this.currentVirtualTree, pruneNode, targetNode);
        if (tNew == null || tNew.getExternalNodeCount() != N) return Double.POSITIVE_INFINITY;

        int[][] lcaNew = TreeCmpUtils.calcLcaMatrix(tNew, this.baseIdGroup);

        for (int[] row : evalIntersection) {
            Arrays.fill(row, 0);
        }

        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                for (int k = j + 1; k < N; k++) {
                    int ind1 = TreeCmpUtils.getNcv(tNew, i, j, k, lcaNew, identityAlias);
                    int ind2 = TreeCmpUtils.getNcv(targetTree, i, j, k, this.targetLcaMatrix, identityAlias);
                    evalIntersection[ind1][ind2]++;
                }
            }
        }

        int maxNodesNew = getSafeMaxNodeId(tNew);
        short[] cSizeNew = new short[maxNodesNew];
        Node[] postOrderNew = TreeCmpUtils.getNodesInPostOrder(tNew);
        TreeCmpUtils.calcCladeSizes(tNew, postOrderNew, cSizeNew);
        Set<Node>[] verticesOutsideCladeNew = TreeCmpUtils.getVerticesOutsideClade(tNew);

        int[] t1IntTripletCountNew = new int[intT1Num];
        for (int i = 0; i < intT1Num; i++) {
            t1IntTripletCountNew[i] = coutTriplets(tNew.getInternalNode(i), cSizeNew, verticesOutsideCladeNew);
        }

        for (int r = 0; r < dim; r++) {
            for (int c = 0; c < dim; c++) {
                if (r < intT1Num && c < intT2Num) {
                    evalCost[r][c] = t1IntTripletCountNew[r] + t2IntTripletCount[c] - (evalIntersection[r][c] << 1);
                } else if (r >= intT1Num && c < intT2Num) {
                    evalCost[r][c] = t2IntTripletCount[c];
                } else if (r < intT1Num && c >= intT2Num) {
                    evalCost[r][c] = t1IntTripletCountNew[r];
                } else {
                    evalCost[r][c] = 0;
                }
            }
            evalU[r] = 0;
            evalV[r] = 0;
        }

        int metric = LapSolver.lap(dim, evalCost, evalRowsol, evalColsol, evalU, evalV);
        return (double) metric;
    }

    @Override public void applySprPrune(Node pruneNode) {}
    @Override public void undoSprPrune(Node pruneNode) {}
    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) {}
    @Override public void undoSprRegraftStep() { }

    private double pushUnchangedState() {
        history.push(new StateRecord(assigncost, rowsol, colsol, currentDistance, currentVirtualTree));
        return this.currentDistance;
    }

    @Override
    public double applyNni(NniMove move) {
        Node virtMoving = getMappedNode(currentVirtualTree, move.movingSubtree);
        Node virtSwapPartner = getMappedNode(currentVirtualTree, move.swapPartner);

        if (virtMoving == null || virtSwapPartner == null) return pushUnchangedState();

        Node virtSibling = getSibling(virtMoving);
        if (virtSibling == null) return pushUnchangedState();

        Tree tNew = sprUtils.createSprTree(currentVirtualTree, virtSibling, virtSwapPartner);
        if (tNew == null || tNew.getExternalNodeCount() != N) return pushUnchangedState();

        int[][] lcaNew = TreeCmpUtils.calcLcaMatrix(tNew, this.baseIdGroup);

        int[][] newIntersection = new int[intT1Num][intT2Num];
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                for (int k = j + 1; k < N; k++) {
                    int ind1 = TreeCmpUtils.getNcv(tNew, i, j, k, lcaNew, identityAlias);
                    int ind2 = TreeCmpUtils.getNcv(targetTree, i, j, k, this.targetLcaMatrix, identityAlias);
                    newIntersection[ind1][ind2]++;
                }
            }
        }

        int maxNodesNew = getSafeMaxNodeId(tNew);
        short[] cSizeNew = new short[maxNodesNew];
        Node[] postOrderNew = TreeCmpUtils.getNodesInPostOrder(tNew);
        TreeCmpUtils.calcCladeSizes(tNew, postOrderNew, cSizeNew);
        Set<Node>[] verticesOutsideCladeNew = TreeCmpUtils.getVerticesOutsideClade(tNew);

        int[] t1IntTripletCountNew = new int[intT1Num];
        for (int i = 0; i < intT1Num; i++) {
            t1IntTripletCountNew[i] = coutTriplets(tNew.getInternalNode(i), cSizeNew, verticesOutsideCladeNew);
        }

        int[][] tempAssigncost = new int[dim][dim];
        for (int r = 0; r < dim; r++) {
            for (int c = 0; c < dim; c++) {
                if (r < intT1Num && c < intT2Num) {
                    tempAssigncost[r][c] = t1IntTripletCountNew[r] + t2IntTripletCount[c] - (newIntersection[r][c] << 1);
                } else if (r >= intT1Num && c < intT2Num) {
                    tempAssigncost[r][c] = t2IntTripletCount[c];
                } else if (r < intT1Num && c >= intT2Num) {
                    tempAssigncost[r][c] = t1IntTripletCountNew[r];
                } else {
                    tempAssigncost[r][c] = 0;
                }
            }
        }

        history.push(new StateRecord(assigncost, rowsol, colsol, currentDistance, currentVirtualTree));

        this.assigncost = tempAssigncost;
        this.currentVirtualTree = tNew;

        int[][] lapCost = new int[dim][dim];
        for (int i = 0; i < dim; i++) {
            System.arraycopy(assigncost[i], 0, lapCost[i], 0, dim);
        }
        int[] lapU = new int[dim];
        int[] lapV = new int[dim];
        int[] lapRowsol = new int[dim];
        int[] lapColsol = new int[dim];

        int metric = LapSolver.lap(dim, lapCost, lapRowsol, lapColsol, lapU, lapV);
        this.currentDistance = 0.5 * metric;

        return this.currentDistance;
    }

    @Override
    public void undoNni(NniMove move) {
        if (!history.isEmpty()) {
            StateRecord r = history.pop();
            this.assigncost = r.oldAssigncost;
            this.rowsol = r.rowsol;
            this.colsol = r.colsol;
            this.currentDistance = r.distance;
            this.currentVirtualTree = r.oldTree;
        }
    }

    private Node getMappedNode(Tree virtTree, Node targetNode) {
        if (targetNode == null) return null;
        if (targetNode.isLeaf()) {
            return TreeUtils.getNodeByName(virtTree, targetNode.getIdentifier().getName());
        }
        BitSet targetCluster = new BitSet();
        populateCluster(targetNode, targetCluster);

        for (int i = 0; i < virtTree.getInternalNodeCount(); i++) {
            Node n = virtTree.getInternalNode(i);
            BitSet cluster = new BitSet();
            populateCluster(n, cluster);
            if (cluster.equals(targetCluster)) return n;
        }
        return null;
    }

    private void populateCluster(Node n, BitSet cluster) {
        if (n.isLeaf()) {
            int id = this.baseIdGroup.whichIdNumber(n.getIdentifier().getName());
            if (id >= 0) cluster.set(id);
        } else {
            for (int i = 0; i < n.getChildCount(); i++) {
                populateCluster(n.getChild(i), cluster);
            }
        }
    }

    private int getSafeMaxNodeId(Tree tree) {
        int maxId = 0;
        Node[] allNodes = TreeCmpUtils.getAllNodes(tree);
        for (Node n : allNodes) {
            if (n != null && n.getNumber() > maxId) maxId = n.getNumber();
        }
        return maxId + 1;
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

    private Node getSibling(Node node) {
        Node parent = node.getParent();
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) != node) return parent.getChild(i);
        }
        return null;
    }

    private static class StateRecord {
        int[][] oldAssigncost;
        int[] rowsol, colsol;
        double distance;
        Tree oldTree;

        StateRecord(int[][] assigncost, int[] rs, int[] cs, double d, Tree oldTree) {
            this.oldAssigncost = assigncost;
            this.rowsol = rs.clone();
            this.colsol = cs.clone();
            this.distance = d;
            this.oldTree = oldTree;
        }
    }

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { history.clear(); }
    @Override public double getDistance(Tree t1, Tree t2, int... indexes) { return mtMetricFull.getDistance(t1, t2, indexes); }
    @Override public String getName() { return "Zero-Alloc SPR " + mtMetricFull.getName(); }
    @Override public String getCommandLineName() { return mtMetricFull.getCommandLineName(); }
    @Override public void setCommandLineName(String cln) { mtMetricFull.setCommandLineName(cln); }
    @Override public void setName(String name) { mtMetricFull.setName(name); }
    @Override public String getDescription() { return mtMetricFull.getDescription(); }
    @Override public void setDescription(String d) { mtMetricFull.setDescription(d); }
    @Override public void initData() { mtMetricFull.initData(); }
    @Override public boolean isRooted() { return mtMetricFull.isRooted(); }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return mtMetricFull.isDiffLeafSets(); }
    @Override public AlignInfo getAlignment() { return mtMetricFull.getAlignment(); }
}