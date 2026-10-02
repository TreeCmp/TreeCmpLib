package treecmp.metrics.topological.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import pal.tree.SimpleTree;
import treecmp.common.AlignInfo;
import treecmp.common.LapSolver;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.heuristics.tbr.acc.RootedTbrMetric;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingTripletMetric;

import java.util.*;

public class M3IncrementalMetric implements IncrementalMetric, RootedTbrMetric {

    private Tree originalBaseTree;
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
    private int[] targetIdToCol;

    private Map<Node, BitSet> baseSplits;
    private Map<Node, BitSet> currentSplits;
    private Node activePruneNode = null;
    private Map<Node, Integer> nodeToRow;

    private int[][] assigncost;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private int[] currentT1TripletCount;
    private int[] t2IntTripletCount;

    // Struktury do szybkiego przejścia O(N) po T2 (Tree DP indeksowane pozycją post-order)
    private int postOrderT2Count;
    private int[] t2PostOrderChild0;
    private int[] t2PostOrderChild1;
    private int[] t2PostOrderChild2;
    private int[] t2PostOrderLeafId;
    private boolean[] t2PostOrderIsRoot;
    private int[] t2PostOrderCol;

    // Pule liczników przecięć (Zero-Allocation w gorących pętlach)
    private int[] t2CountA;
    private int[] t2CountB;
    private int[] t2CountC;

    private int[] scratchOldRow;
    private int[] scratchOldU;
    private int[] scratchOldV;
    private int[] scratchOldRowsol;
    private int[] scratchOldColsol;
    private int[] scratchChangedRow;
    private int[] scratchIntersections;

    private BitSet scratchSetA;
    private BitSet scratchSetB;
    private BitSet scratchSetC;
    private BitSet[] scratchSets;

    private BitSet currentPrunedLeaves;

    private final MatchingTripletMetric mtMetricFull = new MatchingTripletMetric();
    private final Stack<StateRecord> history = new Stack<>();
    private final Stack<LapStateDelta> deltaStack = new Stack<>();

    private final UTbrUtils utbrUtils = new UTbrUtils();
    private final UsprUtils usprUtils = new UsprUtils();

    // Słownik mapujący trójpartycje (Signature) bazowego drzewa na indeksy wierszy macierzy kosztów
    private Map<Signature, Integer> baseSigToRow;

    // Prealokowane bufory robocze dla Zero-Allocation w pętli TBR / SPR
    private int[][] scratchSavedRows;
    private int[] scratchSavedTripletCounts;
    private int[] scratchChangedRows;
    private Signature[] scratchNewSignatures;
    private boolean[] scratchOldRowUsed;
    private int[] scratchSavedU;
    private int[] scratchSavedV;
    private int[] scratchSavedRowsol;
    private int[] scratchSavedColsol;
    private int[][] cachedKArrays;

    public BitSet getSplit(Node n) {
        return getSplitForNode(n);
    }

    private static class LapStateDelta {
        final int[] rows;
        final int[][] oldRows;
        final int[] oldTripletCounts;
        final int[] oldU, oldV, oldRowsol, oldColsol;
        final double oldDistance;
        final Map<Node, BitSet> oldSplits;

        LapStateDelta(int[] rows, int[][] oldRows, int[] oldTripletCounts,
                      int[] oldU, int[] oldV, int[] oldRowsol, int[] oldColsol,
                      double oldDistance, Map<Node, BitSet> oldSplits) {
            this.rows = rows;
            this.oldRows = oldRows;
            this.oldTripletCounts = oldTripletCounts;
            this.oldU = oldU;
            this.oldV = oldV;
            this.oldRowsol = oldRowsol;
            this.oldColsol = oldColsol;
            this.oldDistance = oldDistance;
            this.oldSplits = oldSplits;
        }
    }

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        history.clear();
        deltaStack.clear();

        if (baseTree != null && targetTree != null) {
            this.originalBaseTree = baseTree;
            this.baseIdGroup = TreeUtils.getLeafIdGroup(baseTree);
            this.baseTree = TreeNeighborhoodUtils.fastTreeClone(baseTree);
            this.targetTree = TreeNeighborhoodUtils.fastTreeClone(targetTree);
            this.currentVirtualTree = TreeNeighborhoodUtils.fastTreeClone(this.baseTree);

            this.intT1Num = this.baseTree.getInternalNodeCount();
            this.intT2Num = this.targetTree.getInternalNodeCount();
            this.dim = Math.max(intT1Num, intT2Num);
            this.N = this.baseTree.getExternalNodeCount();

            this.assigncost = new int[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];
            this.u = new int[dim];
            this.v = new int[dim];

            this.scratchOldRow = new int[dim];
            this.scratchOldU = new int[dim];
            this.scratchOldV = new int[dim];
            this.scratchOldRowsol = new int[dim];
            this.scratchOldColsol = new int[dim];
            this.scratchChangedRow = new int[1];
            this.scratchIntersections = new int[dim];

            this.scratchSetA = new BitSet(N);
            this.scratchSetB = new BitSet(N);
            this.scratchSetC = new BitSet(N);
            this.scratchSets = new BitSet[]{scratchSetA, scratchSetB, scratchSetC};

            int expectedMapSize = (N * 2 + 5) * 4 / 3;
            this.nodeToRow = new IdentityHashMap<>(expectedMapSize);
            this.baseSplits = new IdentityHashMap<>(expectedMapSize);
            this.currentSplits = new IdentityHashMap<>(expectedMapSize);

            int maxNodesT2 = getSafeMaxNodeId(this.targetTree);
            this.targetIdToCol = new int[maxNodesT2];
            Arrays.fill(this.targetIdToCol, -1);
            for (int i = 0; i < intT2Num; i++) {
                this.targetIdToCol[this.targetTree.getInternalNode(i).getNumber()] = i;
            }

            int maxNodesT1 = getSafeMaxNodeId(this.baseTree);
            int[] baseIdToRow = new int[maxNodesT1];
            Arrays.fill(baseIdToRow, -1);

            for (int i = 0; i < intT1Num; i++) {
                Node nBase = this.baseTree.getInternalNode(i);
                Node nVirt = this.currentVirtualTree.getInternalNode(i);
                baseIdToRow[nBase.getNumber()] = i;
                nodeToRow.put(nBase, i);
                nodeToRow.put(nVirt, i);
            }

            for (Node n : TreeCmpUtils.getAllNodes(this.baseTree)) {
                BitSet split = getLeaves(n, baseIdGroup);
                baseSplits.put(n, split);
                currentSplits.put(n, (BitSet) split.clone());
            }

            for (Node n : TreeCmpUtils.getAllNodes(this.currentVirtualTree)) {
                BitSet split = getLeaves(n, baseIdGroup);
                baseSplits.put(n, split);
                currentSplits.put(n, (BitSet) split.clone());
            }

            for (Node nOrig : TreeCmpUtils.getAllNodes(this.originalBaseTree)) {
                Node nCopy = getMappedNode(this.baseTree, nOrig);
                if (nCopy != null) {
                    Integer row = nodeToRow.get(nCopy);
                    if (row != null) {
                        nodeToRow.put(nOrig, row);
                    }
                    baseSplits.put(nOrig, baseSplits.get(nCopy));
                    currentSplits.put(nOrig, (BitSet) currentSplits.get(nCopy).clone());
                }
            }

            // Inicjalizacja topologii T2 indeksowanej post-order (bez kolizji numeracji PAL)
            Node[] postOrder = TreeCmpUtils.getNodesInPostOrder(this.targetTree);
            this.postOrderT2Count = postOrder.length;

            this.t2PostOrderChild0 = new int[postOrderT2Count];
            this.t2PostOrderChild1 = new int[postOrderT2Count];
            this.t2PostOrderChild2 = new int[postOrderT2Count];
            this.t2PostOrderLeafId = new int[postOrderT2Count];
            this.t2PostOrderIsRoot = new boolean[postOrderT2Count];
            this.t2PostOrderCol = new int[postOrderT2Count];

            this.t2CountA = new int[postOrderT2Count];
            this.t2CountB = new int[postOrderT2Count];
            this.t2CountC = new int[postOrderT2Count];

            Map<Node, Integer> nodeToPostOrder = new IdentityHashMap<>(postOrderT2Count * 2);
            for (int i = 0; i < postOrderT2Count; i++) {
                nodeToPostOrder.put(postOrder[i], i);
            }

            for (int i = 0; i < postOrderT2Count; i++) {
                Node n = postOrder[i];
                t2PostOrderIsRoot[i] = n.isRoot();

                if (n.isLeaf()) {
                    t2PostOrderLeafId[i] = baseIdGroup.whichIdNumber(n.getIdentifier().getName());
                    t2PostOrderCol[i] = -1;
                    t2PostOrderChild0[i] = -1;
                    t2PostOrderChild1[i] = -1;
                    t2PostOrderChild2[i] = -1;
                } else {
                    t2PostOrderLeafId[i] = -1;
                    t2PostOrderCol[i] = targetIdToCol[n.getNumber()];
                    int chCount = n.getChildCount();
                    t2PostOrderChild0[i] = (chCount > 0) ? nodeToPostOrder.get(n.getChild(0)) : -1;
                    t2PostOrderChild1[i] = (chCount > 1) ? nodeToPostOrder.get(n.getChild(1)) : -1;
                    t2PostOrderChild2[i] = (chCount > 2) ? nodeToPostOrder.get(n.getChild(2)) : -1;
                }
            }

            short[] cSize2 = new short[maxNodesT2];
            TreeCmpUtils.calcCladeSizes(this.targetTree, postOrder, cSize2);
            this.t2IntTripletCount = new int[dim];
            for (int i = 0; i < intT2Num; i++) {
                this.t2IntTripletCount[i] = countTriplets(this.targetTree.getInternalNode(i), cSize2);
            }

            this.currentT1TripletCount = new int[dim];
            this.targetLcaMatrix = TreeCmpUtils.calcLcaMatrix(this.targetTree, this.baseIdGroup);

            // Inicjalizacja macierzy kosztów w czasie O(N^2)
            for (int r = 0; r < intT1Num; r++) {
                Node nBase = this.baseTree.getInternalNode(r);
                computeRowCostFast(r, getPartitionsForNode(nBase));
            }

            for (int r = intT1Num; r < dim; r++) {
                for (int c = 0; c < dim; c++) {
                    assigncost[r][c] = (c < intT2Num) ? t2IntTripletCount[c] : 0;
                }
            }

            int[][] lapCost = new int[dim][dim];
            for (int i = 0; i < dim; i++) {
                System.arraycopy(assigncost[i], 0, lapCost[i], 0, dim);
            }

            int rawMetric = LapSolver.lap(dim, lapCost, rowsol, colsol, this.u, this.v);
            this.currentDistance = 0.5 * rawMetric;

            // Inicjalizacja prealokowanych struktur do szybkiej ewaluacji uTBR i uSPR
            this.baseSigToRow = new HashMap<>((intT1Num * 4) / 3 + 1);
            for (int r = 0; r < intT1Num; r++) {
                Node n = this.baseTree.getInternalNode(r);
                this.baseSigToRow.put(new Signature(n, N, baseIdGroup), r);
            }

            this.scratchSavedRows = new int[dim][dim];
            this.scratchSavedTripletCounts = new int[dim];
            this.scratchChangedRows = new int[dim];
            this.scratchNewSignatures = new Signature[dim];
            this.scratchOldRowUsed = new boolean[dim];
            this.scratchSavedU = new int[dim];
            this.scratchSavedV = new int[dim];
            this.scratchSavedRowsol = new int[dim];
            this.scratchSavedColsol = new int[dim];

            int maxK = Math.max(64, dim + 1);
            this.cachedKArrays = new int[maxK][];
            for (int i = 0; i < maxK; i++) {
                this.cachedKArrays[i] = new int[i];
            }

        } else {
            this.currentDistance = 0;
        }
    }

    private Integer getRowForNode(Node n) {
        if (n == null || n.isLeaf()) return null;
        Integer row = nodeToRow.get(n);
        if (row != null) return row;
        Node mapped = getMappedNode(this.currentVirtualTree, n);
        if (mapped == null) mapped = getMappedNode(this.baseTree, n);
        if (mapped != null && !mapped.isLeaf()) {
            row = nodeToRow.get(mapped);
            if (row != null) {
                nodeToRow.put(n, row);
                return row;
            }
        }
        return null;
    }

    private BitSet getSplitForNode(Node n) {
        if (n == null) return new BitSet(N);
        BitSet bs = currentSplits.get(n);
        if (bs != null) return bs;
        Node mapped = getMappedNode(this.baseTree, n);
        if (mapped != null) {
            bs = currentSplits.get(mapped);
            if (bs != null) {
                currentSplits.put(n, bs);
                return bs;
            }
        }
        return getLeaves(n, baseIdGroup);
    }

    private void updateRowsSafelyAndSave(Map<Integer, BitSet[]> rowUpdates) {
        int[] rows = new int[rowUpdates.size()];
        int[][] oldRows = new int[rows.length][dim];
        int[] oldTripletCounts = new int[rows.length];

        int idx = 0;
        for (Map.Entry<Integer, BitSet[]> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            rows[idx] = r;
            oldRows[idx] = Arrays.copyOf(assigncost[r], dim);
            oldTripletCounts[idx] = currentT1TripletCount[r];
            idx++;
        }

        deltaStack.push(new LapStateDelta(
                rows, oldRows, oldTripletCounts,
                Arrays.copyOf(u, dim), Arrays.copyOf(v, dim),
                Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim),
                currentDistance, new IdentityHashMap<>()
        ));

        for (Map.Entry<Integer, BitSet[]> entry : rowUpdates.entrySet()) {
            computeRowCostFast(entry.getKey(), entry.getValue());
        }

        if (rows.length > 0) {
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, rows);
            this.currentDistance = 0.5 * rawMetric;
        }
    }

    private void undoDeltaStack() {
        if (deltaStack.isEmpty()) return;
        LapStateDelta delta = deltaStack.pop();

        for (int i = 0; i < delta.rows.length; i++) {
            System.arraycopy(delta.oldRows[i], 0, assigncost[delta.rows[i]], 0, dim);
            currentT1TripletCount[delta.rows[i]] = delta.oldTripletCounts[i];
        }

        System.arraycopy(delta.oldU, 0, u, 0, dim);
        System.arraycopy(delta.oldV, 0, v, 0, dim);
        System.arraycopy(delta.oldRowsol, 0, rowsol, 0, dim);
        System.arraycopy(delta.oldColsol, 0, colsol, 0, dim);

        for (Map.Entry<Node, BitSet> e : delta.oldSplits.entrySet()) {
            currentSplits.put(e.getKey(), e.getValue());
        }

        this.currentDistance = delta.oldDistance;
    }

    // =========================================================================
    // AKCELERATOR KOSZTU WIERSZA O(N) POPRZEZ DRZEWNE DP I PERMANENT 3x3
    // =========================================================================

    private void computeRowCostFast(int row, BitSet[] sets) {
        if (sets == null || sets.length < 3) {
            currentT1TripletCount[row] = 0;
            int[] costRow = assigncost[row];
            for (int c = 0; c < intT2Num; c++) {
                costRow[c] = t2IntTripletCount[c];
            }
            for (int c = intT2Num; c < dim; c++) {
                costRow[c] = 0;
            }
            return;
        }

        if (sets.length == 3) {
            BitSet sA = sets[0];
            BitSet sB = sets[1];
            BitSet sC = sets[2];

            if (sA == null || sB == null || sC == null || sA.isEmpty() || sB.isEmpty() || sC.isEmpty()) {
                currentT1TripletCount[row] = 0;
                int[] costRow = assigncost[row];
                for (int c = 0; c < intT2Num; c++) {
                    costRow[c] = t2IntTripletCount[c];
                }
                for (int c = intT2Num; c < dim; c++) {
                    costRow[c] = 0;
                }
                return;
            }

            int totalA = sA.cardinality();
            int totalB = sB.cardinality();
            int totalC = sC.cardinality();

            int tripletCount = totalA * totalB * totalC;
            currentT1TripletCount[row] = tripletCount;
            int[] costRow = assigncost[row];

            for (int i = 0; i < postOrderT2Count; i++) {
                int leafId = t2PostOrderLeafId[i];

                if (leafId >= 0) {
                    t2CountA[i] = sA.get(leafId) ? 1 : 0;
                    t2CountB[i] = sB.get(leafId) ? 1 : 0;
                    t2CountC[i] = sC.get(leafId) ? 1 : 0;
                } else {
                    int c0 = t2PostOrderChild0[i];
                    int c1 = t2PostOrderChild1[i];
                    int c2 = t2PostOrderChild2[i];

                    int a1 = t2CountA[c0];
                    int b1 = t2CountB[c0];
                    int c1Count = t2CountC[c0];

                    int a2 = t2CountA[c1];
                    int b2 = t2CountB[c1];
                    int c2Count = t2CountC[c1];

                    int a3, b3, c3Count;
                    if (t2PostOrderIsRoot[i]) {
                        if (c2 >= 0) {
                            a3 = t2CountA[c2];
                            b3 = t2CountB[c2];
                            c3Count = t2CountC[c2];
                        } else {
                            a3 = b3 = c3Count = 0;
                        }
                        t2CountA[i] = a1 + a2 + a3;
                        t2CountB[i] = b1 + b2 + b3;
                        t2CountC[i] = c1Count + c2Count + c3Count;
                    } else {
                        int myA = a1 + a2;
                        int myB = b1 + b2;
                        int myC = c1Count + c2Count;
                        t2CountA[i] = myA;
                        t2CountB[i] = myB;
                        t2CountC[i] = myC;
                        a3 = totalA - myA;
                        b3 = totalB - myB;
                        c3Count = totalC - myC;
                    }

                    int col = t2PostOrderCol[i];
                    if (col >= 0) {
                        int inter = a1 * (b2 * c3Count + b3 * c2Count)
                                + a2 * (b1 * c3Count + b3 * c1Count)
                                + a3 * (b1 * c2Count + b2 * c1Count);
                        costRow[col] = tripletCount + t2IntTripletCount[col] - (inter << 1);
                    }
                }
            }

            for (int c = intT2Num; c < dim; c++) {
                costRow[c] = tripletCount;
            }
            return;
        }

        computeRowCostMultiSets(row, sets);
    }

    private void computeRowCostMultiSets(int row, BitSet[] sets) {
        Arrays.fill(scratchIntersections, 0);
        int totalTriplets = 0;

        for (int i = 0; i < sets.length; i++) {
            BitSet sA = sets[i];
            if (sA == null || sA.isEmpty()) continue;
            int cardA = sA.cardinality();

            for (int j = i + 1; j < sets.length; j++) {
                BitSet sB = sets[j];
                if (sB == null || sB.isEmpty()) continue;
                int cardB = sB.cardinality();

                for (int k = j + 1; k < sets.length; k++) {
                    BitSet sC = sets[k];
                    if (sC == null || sC.isEmpty()) continue;
                    int cardC = sC.cardinality();

                    totalTriplets += cardA * cardB * cardC;
                    accumulateTripletPermanent(sA, sB, sC, cardA, cardB, cardC);
                }
            }
        }

        currentT1TripletCount[row] = totalTriplets;
        int[] costRow = assigncost[row];
        for (int c = 0; c < intT2Num; c++) {
            costRow[c] = totalTriplets + t2IntTripletCount[c] - (scratchIntersections[c] << 1);
        }
        for (int c = intT2Num; c < dim; c++) {
            costRow[c] = totalTriplets;
        }
    }

    private void accumulateTripletPermanent(BitSet sA, BitSet sB, BitSet sC, int totalA, int totalB, int totalC) {
        for (int i = 0; i < postOrderT2Count; i++) {
            int leafId = t2PostOrderLeafId[i];

            if (leafId >= 0) {
                t2CountA[i] = sA.get(leafId) ? 1 : 0;
                t2CountB[i] = sB.get(leafId) ? 1 : 0;
                t2CountC[i] = sC.get(leafId) ? 1 : 0;
            } else {
                int c0 = t2PostOrderChild0[i];
                int c1 = t2PostOrderChild1[i];
                int c2 = t2PostOrderChild2[i];

                int a1 = t2CountA[c0]; int b1 = t2CountB[c0]; int c1Count = t2CountC[c0];
                int a2 = t2CountA[c1]; int b2 = t2CountB[c1]; int c2Count = t2CountC[c1];

                int a3, b3, c3Count;
                if (t2PostOrderIsRoot[i]) {
                    if (c2 >= 0) {
                        a3 = t2CountA[c2]; b3 = t2CountB[c2]; c3Count = t2CountC[c2];
                    } else {
                        a3 = b3 = c3Count = 0;
                    }
                    t2CountA[i] = a1 + a2 + a3;
                    t2CountB[i] = b1 + b2 + b3;
                    t2CountC[i] = c1Count + c2Count + c3Count;
                } else {
                    int myA = a1 + a2; int myB = b1 + b2; int myC = c1Count + c2Count;
                    t2CountA[i] = myA; t2CountB[i] = myB; t2CountC[i] = myC;
                    a3 = totalA - myA; b3 = totalB - myB; c3Count = totalC - myC;
                }

                int col = t2PostOrderCol[i];
                if (col >= 0) {
                    scratchIntersections[col] += a1 * (b2 * c3Count + b3 * c2Count)
                            + a2 * (b1 * c3Count + b3 * c1Count)
                            + a3 * (b1 * c2Count + b2 * c1Count);
                }
            }
        }
    }

    // =========================================================================
    // SZYBKA EWALUACJA DRZEWA SĄSIEDNIEGO (ZERO-ALLOCATION, WARM-START LAP)
    // =========================================================================

    private double evaluateTempTreeDistance(Tree tempTree) {
        if (tempTree instanceof SimpleTree) {
            ((SimpleTree) tempTree).createNodeList();
            pal.tree.TreeUtils.computeParentPointers(tempTree.getRoot());
        }

        int tempIntCount = tempTree.getInternalNodeCount();
        if (tempIntCount != this.intT1Num) {
            try {
                return mtMetricFull.getDistance(tempTree, this.targetTree);
            } catch (Exception ignored) {
                return Double.POSITIVE_INFINITY;
            }
        }

        Arrays.fill(scratchOldRowUsed, 0, dim, false);
        int newSigCount = 0;

        // 1. Identyfikacja, które wierzchołki nie uległy zmianie, a które są nowe
        for (int i = 0; i < tempIntCount; i++) {
            Node n = tempTree.getInternalNode(i);
            Signature sig = new Signature(n, N, baseIdGroup);
            Integer r = baseSigToRow.get(sig);
            if (r != null && !scratchOldRowUsed[r]) {
                scratchOldRowUsed[r] = true;
            } else {
                scratchNewSignatures[newSigCount++] = sig;
            }
        }

        // Drzewo jest izomorficzne (brak zmian topologicznych)
        if (newSigCount == 0) {
            return this.currentDistance;
        }

        int k = newSigCount;
        int idx = 0;
        for (int r = 0; r < intT1Num; r++) {
            if (!scratchOldRowUsed[r]) {
                scratchChangedRows[idx++] = r;
            }
        }

        if (idx != k) {
            try {
                return mtMetricFull.getDistance(tempTree, this.targetTree);
            } catch (Exception ignored) {
                return Double.POSITIVE_INFINITY;
            }
        }

        // 2. Kopia zapasowa potencjałów podwójnych i skojarzenia (bez alokacji obiektów)
        System.arraycopy(u, 0, scratchSavedU, 0, dim);
        System.arraycopy(v, 0, scratchSavedV, 0, dim);
        System.arraycopy(rowsol, 0, scratchSavedRowsol, 0, dim);
        System.arraycopy(colsol, 0, scratchSavedColsol, 0, dim);

        // 3. Przeliczenie wyłącznie zmienionych k wierszy za pomocą szybkiego O(N) DP
        for (int i = 0; i < k; i++) {
            int r = scratchChangedRows[i];
            System.arraycopy(assigncost[r], 0, scratchSavedRows[i], 0, dim);
            scratchSavedTripletCounts[i] = currentT1TripletCount[r];
            computeRowCostFast(r, scratchNewSignatures[i].canonicalParts);
        }

        int[] changedRowsParam = (k < cachedKArrays.length) ? cachedKArrays[k] : new int[k];
        System.arraycopy(scratchChangedRows, 0, changedRowsParam, 0, k);

        // 4. Ciepły start solvera LAP
        int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
        double dist = 0.5 * rawMetric;

        // 5. Przywrócenie stanu pierwotnego (brak efektów ubocznych w strukturach)
        for (int i = 0; i < k; i++) {
            int r = scratchChangedRows[i];
            System.arraycopy(scratchSavedRows[i], 0, assigncost[r], 0, dim);
            currentT1TripletCount[r] = scratchSavedTripletCounts[i];
        }
        System.arraycopy(scratchSavedU, 0, u, 0, dim);
        System.arraycopy(scratchSavedV, 0, v, 0, dim);
        System.arraycopy(scratchSavedRowsol, 0, rowsol, 0, dim);
        System.arraycopy(scratchSavedColsol, 0, colsol, 0, dim);

        return dist;
    }

    // =========================================================================
    // PRZYROSTOWA EWALUACJA uTBR I uSPR
    // =========================================================================

    public double evaluateExactUTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        if (pruneNode == null || rerootNode == null || targetNode == null || this.targetTree == null) {
            return Double.POSITIVE_INFINITY;
        }

        Tree tree = this.baseTree;
        if (pruneNode.getParent() != null) {
            Node r = pruneNode;
            while (r.getParent() != null) r = r.getParent();
            if (this.originalBaseTree != null && r == this.originalBaseTree.getRoot()) {
                tree = this.originalBaseTree;
            } else if (this.baseTree != null && r == this.baseTree.getRoot()) {
                tree = this.baseTree;
            } else {
                tree = new SimpleTree(r);
            }
        }

        Tree tempTree;
        try {
            tempTree = utbrUtils.createUtbrTree(tree, pruneNode, rerootNode, targetNode);
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }
        if (tempTree == null) {
            return Double.POSITIVE_INFINITY;
        }

        return evaluateTempTreeDistance(tempTree);
    }

    public double evaluateExactUtbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        return evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, movingBits);
    }

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        return evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, movingBits);
    }

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        if (pruneNode == null || targetNode == null || this.targetTree == null) {
            return Double.POSITIVE_INFINITY;
        }

        Tree tree = this.baseTree;
        if (pruneNode.getParent() != null) {
            Node r = pruneNode;
            while (r.getParent() != null) r = r.getParent();
            if (this.originalBaseTree != null && r == this.originalBaseTree.getRoot()) {
                tree = this.originalBaseTree;
            } else if (this.baseTree != null && r == this.baseTree.getRoot()) {
                tree = this.baseTree;
            } else {
                tree = new SimpleTree(r);
            }
        }

        Tree tempTree = null;
        try {
            tempTree = usprUtils.createUsprTree(tree, pruneNode, targetNode);
        } catch (Exception ignored) {
        }

        if (tempTree == null) {
            try {
                tempTree = utbrUtils.createUtbrTree(tree, pruneNode, pruneNode, targetNode);
            } catch (Exception ignored) {
            }
        }

        if (tempTree == null) {
            return Double.POSITIVE_INFINITY;
        }

        return evaluateTempTreeDistance(tempTree);
    }

    // =========================================================================
    // KONTRAKT RootedTbrMetric DLA TBR WALKER
    // =========================================================================

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        this.currentPrunedLeaves = (BitSet) getSplitForNode(pruneNode).clone();
        BitSet P = this.currentPrunedLeaves;
        Map<Integer, BitSet[]> updates = new HashMap<>();

        Node curr = pruneNode.getParent().getParent();
        while (curr != null) {
            Integer r = getRowForNode(curr);
            if (r != null) {
                int chCount = curr.getChildCount();
                int numNeighbors = (curr.getParent() == null) ? chCount : chCount + 1;
                BitSet[] cSets = new BitSet[numNeighbors];
                BitSet childrenUnion = new BitSet(N);

                for (int i = 0; i < chCount; i++) {
                    cSets[i] = (BitSet) getSplitForNode(curr.getChild(i)).clone();
                    cSets[i].andNot(P);
                    childrenUnion.or(cSets[i]);
                }
                if (curr.getParent() != null) {
                    BitSet pSet = new BitSet(N);
                    pSet.set(0, N);
                    pSet.andNot(P);
                    pSet.andNot(childrenUnion);
                    cSets[chCount] = pSet;
                }
                updates.put(r, cSets);
            }
            curr = curr.getParent();
        }

        Integer r_floating = getRowForNode(pruneNode.getParent());
        if (r_floating != null) {
            updates.put(r_floating, new BitSet[0]);
        }

        updateRowsSafelyAndSave(updates);
    }

    public void setTargetRoot(Node pruneNode, Node wanderingSource) {
        BitSet P = (this.currentPrunedLeaves != null) ? this.currentPrunedLeaves : getSplitForNode(pruneNode);
        Map<Integer, BitSet[]> updates = new HashMap<>();

        Integer r_floating = getRowForNode(pruneNode.getParent());
        if (r_floating != null) {
            Node root = baseTree.getRoot();
            Node chosenChild = root.getChild(0);
            BitSet childLeaves = (BitSet) getSplitForNode(chosenChild).clone();
            childLeaves.andNot(P);

            for (int i = 1; i < root.getChildCount() && childLeaves.isEmpty(); i++) {
                chosenChild = root.getChild(i);
                childLeaves = (BitSet) getSplitForNode(chosenChild).clone();
                childLeaves.andNot(P);
            }

            BitSet rest = new BitSet(N);
            rest.set(0, N);
            rest.andNot(P);
            rest.andNot(childLeaves);

            updates.put(r_floating, new BitSet[]{P, childLeaves, rest});
        }
        updateRowsSafelyAndSave(updates);
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {
        setTargetRoot(pruneNode, wanderingSource);
    }

    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        BitSet P = (this.currentPrunedLeaves != null) ? this.currentPrunedLeaves : getSplitForNode(pruneNode);
        Map<Integer, BitSet[]> updates = new HashMap<>();

        Integer r_floating = getRowForNode(pruneNode.getParent());
        if (r_floating != null) {
            BitSet childLeaves = (BitSet) getSplitForNode(childTarget).clone();
            childLeaves.andNot(P);

            BitSet rest = new BitSet(N);
            rest.set(0, N);
            rest.andNot(P);
            rest.andNot(childLeaves);

            updates.put(r_floating, new BitSet[]{P, childLeaves, rest});
        }

        Integer r_p = getRowForNode(parentTarget);
        if (r_p != null && !r_p.equals(r_floating)) {
            int chCount = parentTarget.getChildCount();
            int numNeighbors = (parentTarget.getParent() == null) ? chCount : chCount + 1;
            BitSet[] cSets = new BitSet[numNeighbors];
            BitSet targetLeaves = getSplitForNode(childTarget);
            BitSet childrenUnion = new BitSet(N);

            for (int i = 0; i < chCount; i++) {
                cSets[i] = (BitSet) getSplitForNode(parentTarget.getChild(i)).clone();
                cSets[i].andNot(P);
                if (cSets[i].intersects(targetLeaves)) {
                    cSets[i].or(P);
                }
                childrenUnion.or(cSets[i]);
            }

            if (parentTarget.getParent() != null) {
                BitSet pSet = new BitSet(N);
                pSet.set(0, N);
                pSet.andNot(childrenUnion);
                cSets[chCount] = pSet;
            }

            updates.put(r_p, cSets);
        }

        updateRowsSafelyAndSave(updates);
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        moveTargetDown(parentTarget, childTarget, pruneNode, wanderingSource);
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        undoDeltaStack();
    }

    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        undoDeltaStack();
    }

    @Override
    public void revertPrunedState(Node pruneNode, Node wanderingSource) {
        undoDeltaStack();
        undoDeltaStack();
    }

    @Override
    public void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode) {
        Map<Integer, BitSet[]> updates = new HashMap<>();

        if (currentPrunedLeaves != null) {
            BitSet outsideP = new BitSet(N);
            outsideP.set(0, N);
            outsideP.andNot(currentPrunedLeaves);

            Integer rChild = getRowForNode(childReroot);
            Integer rParent = getRowForNode(parentReroot);

            if (rChild != null) {
                int chCount = childReroot.getChildCount();
                BitSet[] cSets = new BitSet[chCount + 1];
                BitSet union = new BitSet(N);

                for (int i = 0; i < chCount; i++) {
                    cSets[i] = (BitSet) getSplitForNode(childReroot.getChild(i)).clone();
                    union.or(cSets[i]);
                }
                BitSet restInP = (BitSet) currentPrunedLeaves.clone();
                restInP.andNot(union);

                updates.put(rChild, new BitSet[]{union, restInP, outsideP});
            }

            if (rParent != null && parentReroot != childReroot) {
                BitSet[] pSets = getPartitionsForNode(parentReroot);
                if (pSets.length >= 3) {
                    updates.put(rParent, pSets);
                }
            }
        }

        if (!updates.isEmpty()) {
            updateRowsSafelyAndSave(updates);
        } else {
            deltaStack.push(new LapStateDelta(new int[0], new int[0][0], new int[0],
                    Arrays.copyOf(u, dim), Arrays.copyOf(v, dim),
                    Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance, new IdentityHashMap<>()));
        }
    }

    @Override
    public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {
        undoDeltaStack();
    }

    // =========================================================================
    // SPR & NNI
    // =========================================================================

    public boolean applyNniStep(Node nodeToUpdate, BitSet bitsOut, BitSet bitsIn) {
        Node vNode = nodeToUpdate;
        Node uNode = vNode.getParent();

        Integer rVIndex = getRowForNode(vNode);
        Integer rUIndex = getRowForNode(uNode);

        if (rVIndex == null && rUIndex == null) return false;

        BitSet newSplitV = (BitSet) getSplitForNode(vNode).clone();
        if (bitsOut != null) newSplitV.andNot(bitsOut);
        if (bitsIn != null) newSplitV.or(bitsIn);

        List<Integer> rowsToUpdate = new ArrayList<>();
        if (rVIndex != null) rowsToUpdate.add(rVIndex);
        if (rUIndex != null && !rUIndex.equals(rVIndex)) rowsToUpdate.add(rUIndex);

        int[] rows = rowsToUpdate.stream().mapToInt(i -> i).toArray();
        int[][] oldRows = new int[rows.length][dim];
        int[] oldTripletCounts = new int[rows.length];

        for (int i = 0; i < rows.length; i++) {
            oldRows[i] = Arrays.copyOf(assigncost[rows[i]], dim);
            oldTripletCounts[i] = currentT1TripletCount[rows[i]];
        }

        Map<Node, BitSet> oldSplits = new IdentityHashMap<>();
        oldSplits.put(vNode, currentSplits.get(vNode));

        deltaStack.push(new LapStateDelta(rows, oldRows, oldTripletCounts,
                Arrays.copyOf(this.u, dim), Arrays.copyOf(this.v, dim),
                Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance, oldSplits));

        currentSplits.put(vNode, newSplitV);

        if (rVIndex != null) computeRowCostFast(rVIndex, getPartitionsForNode(vNode));
        if (rUIndex != null && !rUIndex.equals(rVIndex)) computeRowCostFast(rUIndex, getPartitionsForNode(uNode));

        if (rows.length > 0) {
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, this.u, this.v, rows);
            this.currentDistance = 0.5 * rawMetric;
        }

        return true;
    }

    public void undoNniStep() {
        undoDeltaStack();
    }

    public double getFixedDistanceForRegraft(Node targetNode, Node wanderingSource, BitSet pruneMask, Node pruneNode) {
        Integer r_w = getRowForNode(wanderingSource);
        if (r_w == null) {
            return evaluateSprRegraft(pruneNode, targetNode);
        }

        scratchSetA.clear();
        scratchSetA.or(pruneMask);

        scratchSetB.clear();
        scratchSetB.or(getSplitForNode(targetNode));
        scratchSetB.andNot(scratchSetA);

        scratchSetC.clear();
        scratchSetC.set(0, N);
        scratchSetC.andNot(scratchSetA);
        scratchSetC.andNot(scratchSetB);

        System.arraycopy(assigncost[r_w], 0, scratchOldRow, 0, dim);
        System.arraycopy(u, 0, scratchOldU, 0, dim);
        System.arraycopy(v, 0, scratchOldV, 0, dim);
        System.arraycopy(rowsol, 0, scratchOldRowsol, 0, dim);
        System.arraycopy(colsol, 0, scratchOldColsol, 0, dim);
        int oldTripletCount = currentT1TripletCount[r_w];

        computeRowCostFast(r_w, scratchSets);

        scratchChangedRow[0] = r_w;
        int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, scratchChangedRow);
        double fixedDist = 0.5 * rawMetric;

        System.arraycopy(scratchOldRow, 0, assigncost[r_w], 0, dim);
        System.arraycopy(scratchOldU, 0, u, 0, dim);
        System.arraycopy(scratchOldV, 0, v, 0, dim);
        System.arraycopy(scratchOldRowsol, 0, rowsol, 0, dim);
        System.arraycopy(scratchOldColsol, 0, colsol, 0, dim);
        currentT1TripletCount[r_w] = oldTripletCount;

        return fixedDist;
    }

    @Override
    public double applyNni(NniMove move) {
        Node virtMoving = getMappedNode(this.currentVirtualTree, move.movingSubtree);
        Node virtPartner = getMappedNode(this.currentVirtualTree, move.swapPartner);

        if (virtMoving == null || virtPartner == null) return pushUnchangedState();

        Node p1 = virtMoving.getParent();
        Node p2 = virtPartner.getParent();
        if (p1 == null || p2 == null || p1 == p2) return pushUnchangedState();

        int idx1 = findChildPos(virtMoving, p1);
        int idx2 = findChildPos(virtPartner, p2);
        if (idx1 == -1 || idx2 == -1) return pushUnchangedState();

        Integer r1 = getRowForNode(p1);
        Integer r2 = getRowForNode(p2);
        if (r1 == null && r2 == null) return pushUnchangedState();

        history.push(new StateRecord(assigncost, rowsol, colsol, u, v, currentDistance, currentVirtualTree, currentT1TripletCount, currentSplits, nodeToRow, virtMoving, virtPartner));

        p1.setChild(idx1, virtPartner); virtPartner.setParent(p1);
        p2.setChild(idx2, virtMoving); virtMoving.setParent(p2);

        if (p2.getParent() == p1) {
            refreshNodeSplit(p2);
            refreshNodeSplit(p1);
        } else {
            refreshNodeSplit(p1);
            refreshNodeSplit(p2);
        }

        List<Integer> changedList = new ArrayList<>(2);
        if (r1 != null) {
            computeRowCostFast(r1, getPartitionsForNode(p1));
            changedList.add(r1);
        }
        if (r2 != null && !r2.equals(r1)) {
            computeRowCostFast(r2, getPartitionsForNode(p2));
            changedList.add(r2);
        }

        int[] changedRows = changedList.stream().mapToInt(i -> i).toArray();
        if (changedRows.length > 0) {
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, changedRows);
            this.currentDistance = 0.5 * rawMetric;
        }

        return this.currentDistance;
    }

    private void refreshNodeSplit(Node n) {
        BitSet b = new BitSet(N);
        for (int i = 0; i < n.getChildCount(); i++) {
            b.or(getSplitForNode(n.getChild(i)));
        }
        currentSplits.put(n, b);
    }

    @Override
    public void undoNni(NniMove move) {
        if (!history.isEmpty()) {
            StateRecord r = history.pop();
            this.assigncost = r.oldAssigncost;
            this.rowsol = r.rowsol;
            this.colsol = r.colsol;
            this.u = r.u;
            this.v = r.v;
            this.currentDistance = r.distance;
            this.currentT1TripletCount = r.oldTripletCount;

            if (r.nniMovingNode != null && r.nniPartnerNode != null) {
                Node p1 = r.nniPartnerNode.getParent();
                Node p2 = r.nniMovingNode.getParent();
                if (p1 != null && p2 != null) {
                    int i1 = findChildPos(r.nniPartnerNode, p1);
                    int i2 = findChildPos(r.nniMovingNode, p2);
                    if (i1 != -1 && i2 != -1) {
                        p1.setChild(i1, r.nniMovingNode); r.nniMovingNode.setParent(p1);
                        p2.setChild(i2, r.nniPartnerNode); r.nniPartnerNode.setParent(p2);
                    }
                }
            } else {
                this.currentVirtualTree = r.oldTree;
            }

            this.currentSplits.clear();
            this.currentSplits.putAll(r.oldSplits);
            this.nodeToRow = new IdentityHashMap<>(r.oldNodeToRow);
        }
    }

    // =========================================================================
    // IMPLEMENTACJA ECR
    // =========================================================================

    @Override
    public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        return internalApply2sEcrMove(top, m1, m2, boundarySubtrees, template, false);
    }

    @Override
    public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        return internalApply2sEcrMove(top, m1, m2, boundarySubtrees, template, true);
    }

    private double internalApply2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR template, boolean isCommit) {
        SimpleTree tNew = (SimpleTree) TreeNeighborhoodUtils.fastTreeClone(currentVirtualTree);

        Node vTop = getMappedNode(tNew, top);
        Node vM1 = getMappedNode(tNew, m1);
        Node vM2 = getMappedNode(tNew, m2);

        if (vTop == null || vM1 == null || vM2 == null) return isCommit ? pushUnchangedState() : this.currentDistance;

        Node[] vBounds = new Node[4];
        for (int i = 0; i < 4; i++) {
            vBounds[i] = getMappedNode(tNew, boundarySubtrees[i]);
            if (vBounds[i] == null) return isCommit ? pushUnchangedState() : this.currentDistance;
        }

        boolean isOriginalFork = (m2.getParent() == top);
        int portA = -1, portB = -1;
        for (int i = 0; i < vTop.getChildCount(); i++) {
            if (vTop.getChild(i) == (isOriginalFork ? vM1 : vBounds[0])) portA = i;
            if (vTop.getChild(i) == (isOriginalFork ? vM2 : vM1)) portB = i;
        }
        if (portA == -1) portA = 0;
        if (portB == -1) portB = 1;
        if (portA == portB) { portA = 0; portB = 1; }

        if (template.isFork) {
            vTop.setChild(portA, vM1); vM1.setParent(vTop);
            vTop.setChild(portB, vM2); vM2.setParent(vTop);
            vM1.setChild(0, vBounds[template.indices[0]]); vBounds[template.indices[0]].setParent(vM1);
            vM1.setChild(1, vBounds[template.indices[1]]); vBounds[template.indices[1]].setParent(vM1);
            vM2.setChild(0, vBounds[template.indices[2]]); vBounds[template.indices[2]].setParent(vM2);
            vM2.setChild(1, vBounds[template.indices[3]]); vBounds[template.indices[3]].setParent(vM2);
        } else {
            vTop.setChild(portA, vBounds[template.indices[0]]); vBounds[template.indices[0]].setParent(vTop);
            vTop.setChild(portB, vM1); vM1.setParent(vTop);
            vM1.setChild(0, vBounds[template.indices[1]]); vBounds[template.indices[1]].setParent(vM1);
            vM1.setChild(1, vM2); vM2.setParent(vM1);
            vM2.setChild(0, vBounds[template.indices[2]]); vBounds[template.indices[2]].setParent(vM2);
            vM2.setChild(1, vBounds[template.indices[3]]); vBounds[template.indices[3]].setParent(vM2);
        }

        return calculateCleanSlateDistance(tNew, isCommit);
    }

    @Override
    public double evaluate3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR template) {
        return internalApply3sEcrMove(cluster, boundarySubtrees, template, false);
    }

    @Override
    public double commit3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR template) {
        return internalApply3sEcrMove(cluster, boundarySubtrees, template, true);
    }

    private double internalApply3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR template, boolean isCommit) {
        SimpleTree tNew = (SimpleTree) TreeNeighborhoodUtils.fastTreeClone(currentVirtualTree);

        Node[] vAvailable = new Node[4];
        for (int i = 0; i < 4; i++) {
            vAvailable[i] = getMappedNode(tNew, cluster.get(i));
            if (vAvailable[i] == null) return isCommit ? pushUnchangedState() : this.currentDistance;
        }

        Node[] vBounds = new Node[5];
        for (int i = 0; i < 5; i++) {
            vBounds[i] = getMappedNode(tNew, boundarySubtrees[i]);
            if (vBounds[i] == null) return isCommit ? pushUnchangedState() : this.currentDistance;
        }

        for (int i = 0; i < 4; i++) {
            while (vAvailable[i].getChildCount() > 0) vAvailable[i].removeChild(0);
        }

        bindMapped3sEcrTemplate(template, vAvailable[0], vAvailable, 1, vBounds);

        return calculateCleanSlateDistance(tNew, isCommit);
    }

    private int bindMapped3sEcrTemplate(SubtreeEcr3Utils.TopologyTemplate3sECR temp, Node currentInternal, Node[] available, int nextAvailIdx, Node[] newS) {
        int idx = nextAvailIdx;
        if (temp.left.leafIndex != -1) {
            currentInternal.insertChild(newS[temp.left.leafIndex], 0);
            newS[temp.left.leafIndex].setParent(currentInternal);
        } else {
            Node nextInt = available[idx++];
            currentInternal.insertChild(nextInt, 0); nextInt.setParent(currentInternal);
            idx = bindMapped3sEcrTemplate(temp.left, nextInt, available, idx, newS);
        }
        if (temp.right.leafIndex != -1) {
            currentInternal.insertChild(newS[temp.right.leafIndex], 1);
            newS[temp.right.leafIndex].setParent(currentInternal);
        } else {
            Node nextInt = available[idx++];
            currentInternal.insertChild(nextInt, 1); nextInt.setParent(currentInternal);
            idx = bindMapped3sEcrTemplate(temp.right, nextInt, available, idx, newS);
        }
        return idx;
    }

    private double calculateCleanSlateDistance(SimpleTree tNew, boolean isCommit) {
        Map<Signature, Integer> sigToOldRow = new HashMap<>((intT1Num * 4) / 3 + 1);
        for (int i = 0; i < intT1Num; i++) {
            Node n = this.currentVirtualTree.getInternalNode(i);
            Signature sig = new Signature(n, N, baseIdGroup);
            sigToOldRow.put(sig, i);
        }

        Tree tPerfect = TreeNeighborhoodUtils.fastTreeClone(tNew);

        int[] newToOld = new int[dim];
        int[] oldToNew = new int[dim];
        Arrays.fill(newToOld, -1);
        Arrays.fill(oldToNew, -1);

        for (int r_new = 0; r_new < intT1Num; r_new++) {
            Node n = tPerfect.getInternalNode(r_new);
            Signature sig = new Signature(n, N, baseIdGroup);
            Integer r_old = sigToOldRow.get(sig);
            if (r_old != null && oldToNew[r_old] == -1) {
                newToOld[r_new] = r_old;
                oldToNew[r_old] = r_new;
            }
        }

        int unmappedOld = 0;
        for (int r_new = 0; r_new < dim; r_new++) {
            if (newToOld[r_new] == -1) {
                while (unmappedOld < dim && oldToNew[unmappedOld] != -1) unmappedOld++;
                if (unmappedOld < dim) {
                    newToOld[r_new] = unmappedOld;
                    oldToNew[unmappedOld] = r_new;
                }
            }
        }

        int maxNodesNew = getSafeMaxNodeId(tPerfect);
        int[] idToRow = new int[maxNodesNew];
        Arrays.fill(idToRow, -1);
        for (int r_new = 0; r_new < intT1Num; r_new++) {
            idToRow[tPerfect.getInternalNode(r_new).getNumber()] = r_new;
        }

        int[][] lcaNew = TreeCmpUtils.calcLcaMatrix(tPerfect, this.baseIdGroup);
        int[][] newIntersection = new int[dim][dim];

        for (int i = 0; i < N; i++) {
            int[] lcaRowNewI = lcaNew[i];
            int[] lcaRowTargetI = this.targetLcaMatrix[i];
            for (int j = i + 1; j < N; j++) {
                int i_j_new = lcaRowNewI[j];
                int i_j_target = lcaRowTargetI[j];
                int[] lcaRowNewJ = lcaNew[j];
                int[] lcaRowTargetJ = this.targetLcaMatrix[j];
                for (int k = j + 1; k < N; k++) {
                    int i_k_new = lcaRowNewI[k];
                    int j_k_new = lcaRowNewJ[k];
                    int ind1;
                    if (i_j_new == i_k_new) ind1 = j_k_new;
                    else if (i_j_new == j_k_new) ind1 = i_k_new;
                    else ind1 = i_j_new;

                    int i_k_target = lcaRowTargetI[k];
                    int j_k_target = lcaRowTargetJ[k];
                    int ind2;
                    if (i_j_target == i_k_target) ind2 = j_k_target;
                    else if (i_j_target == j_k_target) ind2 = i_k_target;
                    else ind2 = i_j_target;

                    if (ind1 >= 0 && ind1 < idToRow.length && ind2 >= 0 && ind2 < this.targetIdToCol.length) {
                        int r_new = idToRow[ind1];
                        int c = this.targetIdToCol[ind2];
                        if (r_new >= 0 && c >= 0) {
                            newIntersection[r_new][c]++;
                        }
                    }
                }
            }
        }

        short[] cSizeNew = new short[maxNodesNew];
        Node[] postOrderNew = TreeCmpUtils.getNodesInPostOrder(tPerfect);
        TreeCmpUtils.calcCladeSizes(tPerfect, postOrderNew, cSizeNew);

        int[] newT1TripletCount = new int[dim];
        for (int r_new = 0; r_new < intT1Num; r_new++) {
            newT1TripletCount[r_new] = countTriplets(tPerfect.getInternalNode(r_new), cSizeNew);
        }

        int[][] tempAssigncost = new int[dim][dim];
        for (int r = 0; r < dim; r++) {
            for (int c = 0; c < dim; c++) {
                if (r < intT1Num && c < intT2Num) {
                    tempAssigncost[r][c] = newT1TripletCount[r] + t2IntTripletCount[c] - (newIntersection[r][c] << 1);
                } else if (r >= intT1Num && c < intT2Num) {
                    tempAssigncost[r][c] = t2IntTripletCount[c];
                } else if (r < intT1Num && c >= intT2Num) {
                    tempAssigncost[r][c] = newT1TripletCount[r];
                } else {
                    tempAssigncost[r][c] = 0;
                }
            }
        }

        int[][] lapCost = new int[dim][dim];
        for (int i = 0; i < dim; i++) {
            System.arraycopy(tempAssigncost[i], 0, lapCost[i], 0, dim);
        }

        int[] tempRowsol = new int[dim];
        int[] tempColsol = new int[dim];
        int[] tempU = new int[dim];
        int[] tempV = this.v.clone();

        for (int r_new = 0; r_new < dim; r_new++) {
            int r_old = newToOld[r_new];
            tempU[r_new] = this.u[r_old];
            tempRowsol[r_new] = this.rowsol[r_old];
        }

        for (int c = 0; c < dim; c++) {
            int r_old = this.colsol[c];
            tempColsol[c] = oldToNew[r_old];
        }

        int[][] mappedOldAssigncost = new int[dim][dim];
        for (int r_new = 0; r_new < dim; r_new++) {
            mappedOldAssigncost[r_new] = this.assigncost[newToOld[r_new]];
        }

        List<Integer> changedRowsList = new ArrayList<>();
        for (int r_new = 0; r_new < dim; r_new++) {
            if (!Arrays.equals(mappedOldAssigncost[r_new], tempAssigncost[r_new])) {
                changedRowsList.add(r_new);
            }
        }
        int[] changedRows = changedRowsList.stream().mapToInt(i -> i).toArray();

        int rawMetric;
        if (changedRows.length == 0) {
            rawMetric = (int) Math.round(this.currentDistance * 2.0);
        } else if (changedRows.length <= 2) {
            rawMetric = LapSolver.lapUpdate(dim, lapCost, tempRowsol, tempColsol, tempU, tempV, changedRows);
        } else {
            rawMetric = LapSolver.lap(dim, lapCost, tempRowsol, tempColsol, tempU, tempV);
        }

        double dist = 0.5 * rawMetric;

        if (isCommit) {
            history.push(new StateRecord(this.assigncost, this.rowsol, this.colsol, this.u, this.v, this.currentDistance, this.currentVirtualTree, this.currentT1TripletCount, this.currentSplits, this.nodeToRow, null, null));

            this.assigncost = tempAssigncost;
            this.currentVirtualTree = tPerfect;
            this.currentT1TripletCount = newT1TripletCount;
            this.rowsol = tempRowsol;
            this.colsol = tempColsol;
            this.u = tempU;
            this.v = tempV;
            this.currentDistance = dist;

            this.currentSplits.clear();
            this.nodeToRow.clear();
            Node[] allNodesPerfect = TreeCmpUtils.getAllNodes(tPerfect);
            for (Node n : allNodesPerfect) {
                BitSet split = getLeaves(n, baseIdGroup);
                this.currentSplits.put(n, split);
                if (!n.isLeaf()) {
                    for (int i = 0; i < intT1Num; i++) {
                        if (tPerfect.getInternalNode(i) == n) {
                            this.nodeToRow.put(n, i);
                            break;
                        }
                    }
                }
            }
            for (Node nOrig : TreeCmpUtils.getAllNodes(this.originalBaseTree)) {
                Node nCopy = getMappedNode(tPerfect, nOrig);
                if (nCopy != null) {
                    this.currentSplits.put(nOrig, (BitSet) this.currentSplits.get(nCopy).clone());
                    Integer row = this.nodeToRow.get(nCopy);
                    if (row != null) {
                        this.nodeToRow.put(nOrig, row);
                    }
                }
            }
        }

        return dist;
    }

    private double pushUnchangedState() {
        history.push(new StateRecord(assigncost, rowsol, colsol, u, v, currentDistance, currentVirtualTree, currentT1TripletCount, currentSplits, nodeToRow, null, null));
        return this.currentDistance;
    }

    private static class StateRecord {
        int[][] oldAssigncost;
        int[] rowsol, colsol, u, v;
        double distance;
        Tree oldTree;
        int[] oldTripletCount;
        Map<Node, BitSet> oldSplits;
        Map<Node, Integer> oldNodeToRow;
        Node nniMovingNode;
        Node nniPartnerNode;

        StateRecord(int[][] assigncost, int[] rs, int[] cs, int[] u, int[] v, double d, Tree oldTree,
                    int[] tc, Map<Node, BitSet> currentSplits, Map<Node, Integer> nodeToRow,
                    Node nniMovingNode, Node nniPartnerNode) {
            this.oldAssigncost = new int[assigncost.length][];
            for (int i = 0; i < assigncost.length; i++) {
                this.oldAssigncost[i] = assigncost[i].clone();
            }
            this.rowsol = rs.clone();
            this.colsol = cs.clone();
            this.u = u.clone();
            this.v = v.clone();
            this.distance = d;
            this.oldTree = oldTree;
            this.oldTripletCount = tc.clone();
            this.oldSplits = new IdentityHashMap<>(currentSplits);
            this.oldNodeToRow = new IdentityHashMap<>(nodeToRow);
            this.nniMovingNode = nniMovingNode;
            this.nniPartnerNode = nniPartnerNode;
        }
    }

    private static BitSet getLeaves(Node n, IdGroup idGroup) {
        BitSet bs = new BitSet();
        populate(n, bs, idGroup);
        return bs;
    }

    private static void populate(Node n, BitSet bs, IdGroup idGroup) {
        if (n.isLeaf()) bs.set(idGroup.whichIdNumber(n.getIdentifier().getName()));
        else for (int i = 0; i < n.getChildCount(); i++) populate(n.getChild(i), bs, idGroup);
    }

    private int findChildPos(Node child, Node parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) == child) return i;
        }
        return -1;
    }

    private Node getMappedNode(Tree destTree, Node srcNode) {
        if (srcNode == null) return null;
        if (srcNode.isLeaf()) return TreeUtils.getNodeByName(destTree, srcNode.getIdentifier().getName());
        Signature targetSig = new Signature(srcNode, N, baseIdGroup);
        for (int i = 0; i < destTree.getInternalNodeCount(); i++) {
            Signature sig = new Signature(destTree.getInternalNode(i), N, baseIdGroup);
            if (sig.equals(targetSig)) return destTree.getInternalNode(i);
        }
        return null;
    }

    private static final Comparator<BitSet> BITSET_COMPARATOR = (a, b) -> {
        if (a == b) return 0;
        int cA = a.cardinality();
        int cB = b.cardinality();
        if (cA != cB) return Integer.compare(cA, cB);

        int i = a.nextSetBit(0);
        int j = b.nextSetBit(0);
        while (i >= 0 && j >= 0) {
            if (i != j) return Integer.compare(i, j);
            i = a.nextSetBit(i + 1);
            j = b.nextSetBit(j + 1);
        }
        return 0;
    };

    private static class Signature {
        private final BitSet[] canonicalParts;
        private final int cachedHashCode;

        public Signature(Node n, int N, IdGroup idGroup) {
            List<BitSet> parts = new ArrayList<>(n.getChildCount() + 1);
            for (int i = 0; i < n.getChildCount(); i++) {
                parts.add(getLeaves(n.getChild(i), idGroup));
            }
            if (n.getParent() != null) {
                BitSet parentPart = new BitSet(N);
                parentPart.set(0, N);
                for (int i = 0; i < n.getChildCount(); i++) {
                    parentPart.andNot(parts.get(i));
                }
                if (!parentPart.isEmpty()) {
                    parts.add(parentPart);
                }
            }
            this.canonicalParts = parts.toArray(new BitSet[0]);
            Arrays.sort(this.canonicalParts, BITSET_COMPARATOR);
            this.cachedHashCode = Arrays.hashCode(this.canonicalParts);
        }

        @Override
        public int hashCode() {
            return cachedHashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Signature)) return false;
            Signature other = (Signature) obj;
            if (this.cachedHashCode != other.cachedHashCode) return false;
            return Arrays.equals(this.canonicalParts, other.canonicalParts);
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

    private int countTriplets(Node n, short[] clustSizeTab) {
        int chCount = n.getChildCount();
        int[] chSize = new int[chCount + 1];

        for (int i = 0; i < chCount; i++) {
            Node chNode = n.getChild(i);
            if (chNode.isLeaf()) chSize[i] = 1;
            else chSize[i] = clustSizeTab[chNode.getNumber()];
        }

        chSize[chCount] = this.N - clustSizeTab[n.getNumber()];

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

    private BitSet[] getPartitionsForNode(Node n) {
        if (activePruneNode != null && n == activePruneNode.getParent()) {
            return new BitSet[0];
        }

        int chCount = n.getChildCount();
        int numNeighbors = (n.getParent() == null) ? chCount : chCount + 1;
        BitSet[] cSets = new BitSet[numNeighbors];

        int idx = 0;
        BitSet childrenUnion = new BitSet(N);

        for (int i = 0; i < chCount; i++) {
            Node child = n.getChild(i);
            if (activePruneNode != null && child == activePruneNode) {
                cSets[idx] = new BitSet(N);
            } else {
                cSets[idx] = (BitSet) getSplitForNode(child).clone();
                childrenUnion.or(cSets[idx]);
            }
            idx++;
        }

        if (n.getParent() != null) {
            BitSet pSet = new BitSet(N);
            pSet.set(0, N);
            pSet.andNot(childrenUnion);
            cSets[idx] = pSet;
        }

        return cSets;
    }

    @Override public void applySprPrune(Node pruneNode) { this.activePruneNode = pruneNode; }
    @Override public void undoSprPrune(Node pruneNode) { this.activePruneNode = null; }
    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) { throw new UnsupportedOperationException(); }
    @Override public void undoSprRegraftStep() { throw new UnsupportedOperationException(); }

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { history.clear(); deltaStack.clear(); }
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