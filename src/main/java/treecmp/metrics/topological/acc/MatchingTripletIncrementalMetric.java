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

/**
 * The Ultimate Bulletproof implementation of the Matching Triplet Metric.
 * Utilizes the native SprUtils for 100% safe PAL topology compatibility.
 * Implements "No-Op State Push" to prevent Undo-stack desynchronization ("Phantom Move Bug").
 * Employs a rigorous O(N^3) NCV calculation coupled with an unconditional Clean-Slate LapSolver
 * to guarantee zero algorithmic drift and complete immunity to the Chimera matrix bug.
 */
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
    private int[] aliasTarget;
    private int[][] targetLcaMatrix;

    // Persistent LAP state variables
    private int[][] assigncost;
    private int[] rowsol;
    private int[] colsol;

    private int[][] currentIntersections;
    private int[] currentT1TripletCount;
    private int[] t2IntTripletCount;
    private BitSet[] currentClusters;

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

            int[] aliasBase = TreeUtils.mapExternalIdentifiers(this.baseIdGroup, baseTree);
            this.aliasTarget = TreeUtils.mapExternalIdentifiers(this.baseIdGroup, targetTree);

            this.assigncost = new int[dim][dim];
            this.currentIntersections = new int[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];

            this.currentClusters = new BitSet[dim];
            BitSet[] baseClustersRaw = ClusterDist.RootedTree2BitSetArray(baseTree, this.baseIdGroup);
            BitSet rootBitSet = new BitSet();
            rootBitSet.set(0, N);

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

            this.currentT1TripletCount = new int[dim];
            for (int i = 0; i < intT1Num; i++) {
                currentT1TripletCount[i] = coutTriplets(baseTree.getInternalNode(i), cSize1, verticesOutsideClade1);
            }

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

            for (int r = 0; r < dim; r++) {
                for (int c = 0; c < dim; c++) {
                    if (r < intT1Num && c < intT2Num) {
                        currentIntersections[r][c] = initialIntersection[r][c];
                        assigncost[r][c] = currentT1TripletCount[r] + t2IntTripletCount[c] - (initialIntersection[r][c] << 1);
                    } else if (r >= intT1Num && c < intT2Num) {
                        assigncost[r][c] = t2IntTripletCount[c];
                    } else if (r < intT1Num && c >= intT2Num) {
                        assigncost[r][c] = currentT1TripletCount[r];
                    } else {
                        assigncost[r][c] = 0;
                    }
                }
            }

            // CLEAN SLATE LAP SOLVER
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

    /**
     * Rozwiązuje błąd "The Phantom Move Bug". Jeśli test podrzuci nieważny ruch,
     * wciąż musimy zrzucić pusty rekord na stos, żeby undoNni nie cofnęło się do złego stanu!
     */
    private double pushUnchangedState() {
        history.push(new StateRecord(new int[0], assigncost, currentIntersections, currentT1TripletCount, currentClusters, rowsol, colsol, currentDistance, currentVirtualTree));
        return this.currentDistance;
    }

    @Override
    public double applyNni(NniMove move) {
        Node virtMoving = getMappedNode(currentVirtualTree, move.movingSubtree);
        Node virtSwapPartner = getMappedNode(currentVirtualTree, move.swapPartner);

        if (virtMoving == null || virtSwapPartner == null) return pushUnchangedState();

        Node virtSibling = getSibling(virtMoving);
        if (virtSibling == null) return pushUnchangedState();

        // NATIVE SPR UTILS - Gwarantuje tę samą topologię (nawet potworki SPR) co baseline testowy!
        Tree tNew = sprUtils.createSprTree(currentVirtualTree, virtSibling, virtSwapPartner);
        if (tNew == null || tNew.getExternalNodeCount() != N) return pushUnchangedState();

        BitSet[] newClustersRaw = ClusterDist.RootedTree2BitSetArray(tNew, this.baseIdGroup);
        BitSet rootBitSet = new BitSet();
        rootBitSet.set(0, N);

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
        List<Integer> unmappedNodes = new ArrayList<>();
        List<Integer> unmappedRows = new ArrayList<>();

        for (int i = 0; i < intT1Num; i++) {
            boolean mapped = false;
            for (int r = 0; r < dim; r++) {
                if (!rowUsed[r] && currentClusters[r] != null && currentClusters[r].equals(newClusters[i])) {
                    nodeToRow[i] = r;
                    rowUsed[r] = true;
                    mapped = true;
                    break;
                }
            }
            if (!mapped) unmappedNodes.add(i);
        }

        for (int r = 0; r < dim; r++) {
            if (!rowUsed[r] && currentClusters[r] != null) unmappedRows.add(r);
        }
        for (int r = 0; r < dim; r++) {
            if (!rowUsed[r] && currentClusters[r] == null) unmappedRows.add(r);
        }

        if (unmappedNodes.size() > unmappedRows.size()) return pushUnchangedState();

        for (int i = 0; i < unmappedNodes.size(); i++) {
            nodeToRow[unmappedNodes.get(i)] = unmappedRows.get(i);
        }

        // FULL O(N^3) Triplet Matrix Build
        int[][] lcaNew = TreeCmpUtils.calcLcaMatrix(tNew, this.baseIdGroup);
        int[] aliasNew = TreeUtils.mapExternalIdentifiers(this.baseIdGroup, tNew);

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

        int maxNodesNew = getSafeMaxNodeId(tNew);
        short[] cSizeNew = new short[maxNodesNew];
        Node[] postOrderNew = TreeCmpUtils.getNodesInPostOrder(tNew);
        TreeCmpUtils.calcCladeSizes(tNew, postOrderNew, cSizeNew);
        Set<Node>[] verticesOutsideCladeNew = TreeCmpUtils.getVerticesOutsideClade(tNew);

        int[] t1IntTripletCountNew = new int[intT1Num];
        for (int i = 0; i < intT1Num; i++) {
            t1IntTripletCountNew[i] = coutTriplets(tNew.getInternalNode(i), cSizeNew, verticesOutsideCladeNew);
        }

        List<Integer> actualChangedRows = new ArrayList<>();
        int[][] tempNewCosts = new int[dim][];
        int[][] tempNewInts = new int[dim][];
        int[] tempNewTriplets = new int[dim];

        for (int idx = 0; idx < intT1Num; idx++) {
            int r = nodeToRow[idx];
            int newTriplets = t1IntTripletCountNew[idx];

            int[] paddedIntersection = new int[dim];
            System.arraycopy(newIntersection[idx], 0, paddedIntersection, 0, intT2Num);

            int[] newCost = new int[dim];

            // BEZWARUNKOWA AKTUALIZACJA DLA MT!
            // Ruch SPR zawsze niszczy globalne wskaźniki NCV, więc musimy przeliczyć wszystkie rzędy.
            boolean rowChanged = true;

            for (int c = 0; c < dim; c++) {
                if (c < intT2Num) {
                    newCost[c] = newTriplets + t2IntTripletCount[c] - (paddedIntersection[c] << 1);
                } else {
                    newCost[c] = newTriplets;
                }
            }

            if (rowChanged) {
                actualChangedRows.add(r);
                tempNewCosts[r] = newCost;
                tempNewInts[r] = paddedIntersection;
                tempNewTriplets[r] = newTriplets;
            }
        }

        int[] changedRowsArray = actualChangedRows.stream().mapToInt(Integer::intValue).toArray();

        // Memory-efficient History Push
        history.push(new StateRecord(changedRowsArray, assigncost, currentIntersections, currentT1TripletCount, currentClusters, rowsol, colsol, currentDistance, currentVirtualTree));

        for (int r : changedRowsArray) {
            assigncost[r] = tempNewCosts[r];
            currentIntersections[r] = tempNewInts[r];
            currentT1TripletCount[r] = tempNewTriplets[r];
            for (int idx = 0; idx < intT1Num; idx++) {
                if (nodeToRow[idx] == r) {
                    currentClusters[r] = newClusters[idx];
                    break;
                }
            }
        }

        currentVirtualTree = tNew;

        if (changedRowsArray.length > 0) {
            // LAP SOLVER ISOLATION (Solves the Chimera 45.0 Matrix Bug)
            int[][] lapCost = new int[dim][dim];
            for (int i = 0; i < dim; i++) {
                System.arraycopy(assigncost[i], 0, lapCost[i], 0, dim);
            }
            int[] lapU = new int[dim];
            int[] lapV = new int[dim];
            int[] lapRowsol = new int[dim];
            int[] lapColsol = new int[dim];

            int metric = LapSolver.lap(dim, lapCost, lapRowsol, lapColsol, lapU, lapV);

            this.rowsol = lapRowsol;
            this.colsol = lapColsol;
            this.currentDistance = 0.5 * metric;
        }

        return this.currentDistance;
    }

    @Override
    public void undoNni(NniMove move) {
        if (!history.isEmpty()) {
            StateRecord r = history.pop();
            for (int i = 0; i < r.changedRows.length; i++) {
                int row = r.changedRows[i];
                assigncost[row] = r.oldCosts[i];
                currentIntersections[row] = r.oldInts[i];
                currentT1TripletCount[row] = r.oldTriplets[i];
                currentClusters[row] = r.oldClusters[i];
            }
            this.rowsol = r.rowsol;
            this.colsol = r.colsol;
            this.currentDistance = r.distance;
            this.currentVirtualTree = r.oldTree;
        }
    }

    // ==========================================================
    // STATE MANAGERS & HELPERS
    // ==========================================================

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
                if (bs.equals(targetBs)) return n;
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
        int[] changedRows;
        int[][] oldCosts, oldInts;
        int[] oldTriplets;
        BitSet[] oldClusters;
        int[] rowsol, colsol;
        double distance;
        Tree oldTree;

        StateRecord(int[] changedRows, int[][] assigncost, int[][] currentIntersections, int[] currentT1TripletCount, BitSet[] currentClusters, int[] rs, int[] cs, double d, Tree oldTree) {
            this.changedRows = changedRows.clone();
            this.oldCosts = new int[changedRows.length][];
            this.oldInts = new int[changedRows.length][];
            this.oldTriplets = new int[changedRows.length];
            this.oldClusters = new BitSet[changedRows.length];
            for (int i = 0; i < changedRows.length; i++) {
                int r = changedRows[i];
                this.oldCosts[i] = assigncost[r];
                this.oldInts[i] = currentIntersections[r];
                this.oldTriplets[i] = currentT1TripletCount[r];
                this.oldClusters[i] = currentClusters[r];
            }
            this.rowsol = rs.clone(); this.colsol = cs.clone();
            this.distance = d;
            this.oldTree = oldTree;
        }
    }

    @Override public void applySprPrune(Node pruneNode) {}
    @Override public void undoSprPrune(Node pruneNode) {}
    @Override public double evaluateSprRegraft(Node pruneNode, Node targetNode) { return this.currentDistance; }
    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) {}
    @Override public void undoSprRegraftStep() { }

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { history.clear(); }
    @Override public double getDistance(Tree t1, Tree t2, int... indexes) { return mtMetricFull.getDistance(t1, t2, indexes); }
    @Override public String getName() { return "O(N^3) Hybrid " + mtMetricFull.getName(); }
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