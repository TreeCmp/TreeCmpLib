package treecmp.metrics.topological.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import pal.tree.SimpleTree;
import treecmp.common.AlignInfo;
import treecmp.common.LapSolver;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingTripletMetric;

import java.util.*;

public class M3IncrementalMetric implements IncrementalMetric {

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

    // PREALOKOWANE BUFORY ROBOCZE (Scratchpad Buffers) - eliminacja alokacji w gorących ścieżkach SPR i NNI
    private int[] scratchOldRow;
    private int[] scratchOldU;
    private int[] scratchOldV;
    private int[] scratchOldRowsol;
    private int[] scratchOldColsol;
    private int[] scratchChangedRow;
    private int[] scratchChangedRows2;
    private int[] scratchChangedRowAll;
    private int[] scratchIntersections;

    private BitSet scratchSetA;
    private BitSet scratchSetB;
    private BitSet scratchSetC;
    private BitSet[] scratchSets;

    private final MatchingTripletMetric mtMetricFull = new MatchingTripletMetric();
    private final Stack<StateRecord> history = new Stack<>();
    private final Stack<LapStateDelta> deltaStack = new Stack<>();

    private static class LapStateDelta {
        final int[] rows;
        final int[][] oldRows;
        final int[] oldTripletCounts;
        final int[] oldU, oldV, oldRowsol, oldColsol;
        final double oldDistance;
        final Map<Node, BitSet> oldSplits;

        LapStateDelta(int[] rows, int[][] oldRows, int[] oldTripletCounts,
                      int[] oldU, int[] oldV, int[] oldRowsol, int[] oldColsol, double oldDistance, Map<Node, BitSet> oldSplits) {
            this.rows = rows; this.oldRows = oldRows; this.oldTripletCounts = oldTripletCounts;
            this.oldU = oldU; this.oldV = oldV; this.oldRowsol = oldRowsol; this.oldColsol = oldColsol;
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
            this.baseTree = createCleanCopy(baseTree);
            this.targetTree = createCleanCopy(targetTree);
            this.currentVirtualTree = createCleanCopy(this.baseTree);

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
            this.scratchChangedRows2 = new int[2];
            this.scratchChangedRowAll = new int[dim];
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

            // KLUCZOWA POPRAWKA: Rejestrujemy wiersze ZARÓWNO dla baseTree, jak i currentVirtualTree!
            for (int i = 0; i < intT1Num; i++) {
                Node nBase = this.baseTree.getInternalNode(i);
                Node nVirt = this.currentVirtualTree.getInternalNode(i);
                baseIdToRow[nBase.getNumber()] = i;
                nodeToRow.put(nBase, i);
                nodeToRow.put(nVirt, i);
            }

            Node[] allNodesBaseCopy = TreeCmpUtils.getAllNodes(this.baseTree);
            for (Node n : allNodesBaseCopy) {
                BitSet split = getLeaves(n, baseIdGroup);
                baseSplits.put(n, split);
                currentSplits.put(n, (BitSet) split.clone());
            }

            // KLUCZOWA POPRAWKA: Rejestrujemy maski bitowe dla każdego węzła w currentVirtualTree!
            Node[] allNodesVirtCopy = TreeCmpUtils.getAllNodes(this.currentVirtualTree);
            for (Node n : allNodesVirtCopy) {
                BitSet split = getLeaves(n, baseIdGroup);
                baseSplits.put(n, split);
                currentSplits.put(n, (BitSet) split.clone());
            }

            Node[] allNodesOriginal = TreeCmpUtils.getAllNodes(this.originalBaseTree);
            for (Node nOrig : allNodesOriginal) {
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

            int[][] lcaMatrix1 = TreeCmpUtils.calcLcaMatrix(this.baseTree, this.baseIdGroup);
            this.targetLcaMatrix = TreeCmpUtils.calcLcaMatrix(this.targetTree, this.baseIdGroup);

            short[] cSize2 = new short[maxNodesT2];
            Node[] postOrderT2 = TreeCmpUtils.getNodesInPostOrder(this.targetTree);
            TreeCmpUtils.calcCladeSizes(this.targetTree, postOrderT2, cSize2);

            this.t2IntTripletCount = new int[dim];
            for (int i = 0; i < intT2Num; i++) {
                this.t2IntTripletCount[i] = coutTriplets(this.targetTree.getInternalNode(i), cSize2);
            }

            short[] cSize1 = new short[maxNodesT1];
            Node[] postOrderT1 = TreeCmpUtils.getNodesInPostOrder(this.baseTree);
            TreeCmpUtils.calcCladeSizes(this.baseTree, postOrderT1, cSize1);

            this.currentT1TripletCount = new int[dim];
            for (int i = 0; i < intT1Num; i++) {
                this.currentT1TripletCount[i] = coutTriplets(this.baseTree.getInternalNode(i), cSize1);
            }

            int[][] initialIntersection = new int[dim][dim];
            for (int i = 0; i < N; i++) {
                int[] lcaRow1I = lcaMatrix1[i];
                int[] lcaRowTargetI = this.targetLcaMatrix[i];
                for (int j = i + 1; j < N; j++) {
                    int i_j_1 = lcaRow1I[j];
                    int i_j_target = lcaRowTargetI[j];
                    int[] lcaRow1J = lcaMatrix1[j];
                    int[] lcaRowTargetJ = this.targetLcaMatrix[j];
                    for (int k = j + 1; k < N; k++) {
                        int i_k_1 = lcaRow1I[k];
                        int j_k_1 = lcaRow1J[k];
                        int ind1;
                        if (i_j_1 == i_k_1) ind1 = j_k_1;
                        else if (i_j_1 == j_k_1) ind1 = i_k_1;
                        else ind1 = i_j_1;

                        int i_k_target = lcaRowTargetI[k];
                        int j_k_target = lcaRowTargetJ[k];
                        int ind2;
                        if (i_j_target == i_k_target) ind2 = j_k_target;
                        else if (i_j_target == j_k_target) ind2 = i_k_target;
                        else ind2 = i_j_target;

                        if (ind1 >= 0 && ind1 < baseIdToRow.length && ind2 >= 0 && ind2 < this.targetIdToCol.length) {
                            int r = baseIdToRow[ind1];
                            int c = this.targetIdToCol[ind2];
                            if (r >= 0 && c >= 0) {
                                initialIntersection[r][c]++;
                            }
                        }
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

            int rawMetric = LapSolver.lap(dim, lapCost, rowsol, colsol, this.u, this.v);
            this.currentDistance = 0.5 * rawMetric;
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

    @Override public void applySprPrune(Node pruneNode) { this.activePruneNode = pruneNode; }
    @Override public void undoSprPrune(Node pruneNode) { this.activePruneNode = null; }
    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) { throw new UnsupportedOperationException(); }
    @Override public void undoSprRegraftStep() { throw new UnsupportedOperationException(); }

    public boolean applyNniStep(Node nodeToUpdate, BitSet bitsOut, BitSet bitsIn) {
        Node v = nodeToUpdate;
        Node u = v.getParent();

        Integer rVIndex = getRowForNode(v);
        Integer rUIndex = getRowForNode(u);

        if (rVIndex == null && rUIndex == null) return false;

        BitSet newSplitV = (BitSet) getSplitForNode(v).clone();
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
        oldSplits.put(v, currentSplits.get(v));

        deltaStack.push(new LapStateDelta(rows, oldRows, oldTripletCounts,
                Arrays.copyOf(this.u, dim), Arrays.copyOf(this.v, dim),
                Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance, oldSplits));

        currentSplits.put(v, newSplitV);

        if (rVIndex != null) computeRowCost(rVIndex, getPartitionsForNode(v));
        if (rUIndex != null && !rUIndex.equals(rVIndex)) computeRowCost(rUIndex, getPartitionsForNode(u));

        if (rows.length > 0) {
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, this.u, this.v, rows);
            this.currentDistance = 0.5 * rawMetric;
        }

        return true;
    }

    public void undoNniStep() {
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

        computeRowCost(r_w, scratchSets);

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
            cSets[idx++] = pSet;
        }

        return cSets;
    }

    private void computeRowCost(int row, BitSet[] sets) {
        if (sets == null || sets.length == 0) {
            currentT1TripletCount[row] = 0;
            return;
        }

        Arrays.fill(scratchIntersections, 0);
        int tripletCount = 0;

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

                    tripletCount += cardA * cardB * cardC;

                    for (int l1 = sA.nextSetBit(0); l1 >= 0; l1 = sA.nextSetBit(l1 + 1)) {
                        int[] lcaRow1 = targetLcaMatrix[l1];
                        for (int l2 = sB.nextSetBit(0); l2 >= 0; l2 = sB.nextSetBit(l2 + 1)) {
                            int lca12 = lcaRow1[l2];
                            int[] lcaRow2 = targetLcaMatrix[l2];

                            for (int l3 = sC.nextSetBit(0); l3 >= 0; l3 = sC.nextSetBit(l3 + 1)) {
                                int lca13 = lcaRow1[l3];
                                int lca23 = lcaRow2[l3];

                                int ind2;
                                if (lca12 == lca13) ind2 = lca23;
                                else if (lca12 == lca23) ind2 = lca13;
                                else ind2 = lca12;

                                if (ind2 >= 0) {
                                    int col = targetIdToCol[ind2];
                                    if (col >= 0) scratchIntersections[col]++;
                                }
                            }
                        }
                    }
                }
            }
        }

        currentT1TripletCount[row] = tripletCount;

        for (int c = 0; c < dim; c++) {
            if (c < intT2Num) {
                assigncost[row][c] = tripletCount + t2IntTripletCount[c] - (scratchIntersections[c] << 1);
            } else if (c >= intT2Num) {
                assigncost[row][c] = tripletCount;
            }
        }
    }

    private static BitSet getLeaves(Node n, IdGroup idGroup) {
        BitSet bs = new BitSet(); populate(n, bs, idGroup); return bs;
    }

    private static void populate(Node n, BitSet bs, IdGroup idGroup) {
        if (n.isLeaf()) bs.set(idGroup.whichIdNumber(n.getIdentifier().getName()));
        else for (int i = 0; i < n.getChildCount(); i++) populate(n.getChild(i), bs, idGroup);
    }

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        Tree tempTree = new UsprUtils().createUsprTree(this.baseTree, pruneNode, targetNode);
        if (tempTree != null) {
            return mtMetricFull.getDistance(tempTree, this.targetTree);
        }
        return Double.POSITIVE_INFINITY;
    }

    private double calculateCleanSlateDistance(SimpleTree tNew, boolean isCommit, int maxCostBound) {

        Map<String, Integer> sigToOldRow = new HashMap<>((intT1Num * 4) / 3 + 1);
        for (int i = 0; i < intT1Num; i++) {
            Node n = this.currentVirtualTree.getInternalNode(i);
            Signature sig = new Signature(n, N, baseIdGroup);
            sigToOldRow.put(sig.hash, i);
        }

        Tree tPerfect = createCleanCopy(tNew);

        int[] newToOld = new int[dim];
        int[] oldToNew = new int[dim];
        Arrays.fill(newToOld, -1);
        Arrays.fill(oldToNew, -1);

        for (int r_new = 0; r_new < intT1Num; r_new++) {
            Node n = tPerfect.getInternalNode(r_new);
            Signature sig = new Signature(n, N, baseIdGroup);
            Integer r_old = sigToOldRow.get(sig.hash);
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
            newT1TripletCount[r_new] = coutTriplets(tPerfect.getInternalNode(r_new), cSizeNew);
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
        if (changedRows.length > 0 && changedRows.length < dim && maxCostBound >= 0) {
            rawMetric = LapSolver.lapUpdateBoundedInt(dim, lapCost, tempRowsol, tempColsol, tempU, tempV, changedRows, maxCostBound);
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
            Node[] allNodesOriginal = TreeCmpUtils.getAllNodes(this.originalBaseTree);
            for (Node nOrig : allNodesOriginal) {
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

    private void refreshAllRowsInPlace() {
        int[] countWrap = {0};
        postOrderRefresh(this.currentVirtualTree.getRoot(), countWrap);
        if (countWrap[0] > 0) {
            int[] rowsToUpdate = Arrays.copyOf(scratchChangedRowAll, countWrap[0]);
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, rowsToUpdate);
            this.currentDistance = 0.5 * rawMetric;
        }
    }

    private void postOrderRefresh(Node n, int[] countWrap) {
        if (n == null || n.isLeaf()) return;
        for (int i = 0; i < n.getChildCount(); i++) {
            postOrderRefresh(n.getChild(i), countWrap);
        }
        BitSet split = new BitSet(N);
        for (int i = 0; i < n.getChildCount(); i++) {
            split.or(getSplitForNode(n.getChild(i)));
        }
        currentSplits.put(n, split);

        Integer row = getRowForNode(n);
        if (row != null) {
            scratchChangedRowAll[countWrap[0]++] = row;
            computeRowCost(row, getPartitionsForNode(n));
        }
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

        history.push(new StateRecord(assigncost, rowsol, colsol, u, v, currentDistance, currentVirtualTree, currentT1TripletCount, currentSplits, nodeToRow, virtMoving, virtPartner));

        p1.setChild(idx1, virtPartner); virtPartner.setParent(p1);
        p2.setChild(idx2, virtMoving); virtMoving.setParent(p2);

        refreshAllRowsInPlace();

        return this.currentDistance;
    }

    @Override
    public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        return internalApply2sEcrMove(top, m1, m2, boundarySubtrees, template, false);
    }

    @Override
    public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        return internalApply2sEcrMove(top, m1, m2, boundarySubtrees, template, true);
    }

    private double internalApply2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR template, boolean isCommit) {
        SimpleTree tNew = (SimpleTree) createCleanCopy(currentVirtualTree);

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

        return calculateCleanSlateDistance(tNew, isCommit, N * N * N);
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
        SimpleTree tNew = (SimpleTree) createCleanCopy(currentVirtualTree);

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

        return calculateCleanSlateDistance(tNew, isCommit, N * N * N);
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

    private Tree createCleanCopy(Tree original) {
        SimpleTree copy = new SimpleTree(original);
        copy.createNodeList();
        TreeUtils.computeParentPointers(copy.getRoot());
        return copy;
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

    private static class Signature {
        String hash;
        public Signature(Node n, int N, IdGroup idGroup) {
            List<BitSet> parts = new ArrayList<>();
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
            String[] strParts = new String[parts.size()];
            for (int i = 0; i < parts.size(); i++) {
                strParts[i] = parts.get(i).toString();
            }
            Arrays.sort(strParts);
            this.hash = Arrays.toString(strParts);
        }
        @Override
        public int hashCode() { return hash.hashCode(); }
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Signature)) return false;
            return this.hash.equals(((Signature)obj).hash);
        }
        @Override
        public String toString() { return hash; }
    }

    private int getSafeMaxNodeId(Tree tree) {
        int maxId = 0;
        Node[] allNodes = TreeCmpUtils.getAllNodes(tree);
        for (Node n : allNodes) {
            if (n != null && n.getNumber() > maxId) maxId = n.getNumber();
        }
        return maxId + 1;
    }

    private int coutTriplets(Node n, short[] clustSizeTab) {
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

    private int getNcvByCanonicalId(int i, int j, int k, int[][] lcaMatrix) {
        int i_j_lca = lcaMatrix[i][j];
        int i_k_lca = lcaMatrix[i][k];
        int j_k_lca = lcaMatrix[j][k];

        if (i_j_lca == i_k_lca) {
            return j_k_lca;
        } else if (i_j_lca == j_k_lca) {
            return i_k_lca;
        } else {
            return i_j_lca;
        }
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