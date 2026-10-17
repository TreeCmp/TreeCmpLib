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
import treecmp.heuristics.ecr.acc.Ecr2IncrementalMetric;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalMetric;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.nni.acc.NniIncrementalMetric;
import treecmp.heuristics.spr.acc.RootedSprIncrementalMetric;
import treecmp.heuristics.tbr.acc.RootedTbrMetric;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.BaseMetric;
import treecmp.metrics.topological.MatchingPairMetric;

import java.util.*;

public class MPIncrementalMetric extends BaseMetric implements IncrementalMetric,
        NniIncrementalMetric,
        RootedSprIncrementalMetric,
        RootedTbrMetric,
        Ecr2IncrementalMetric,
        Ecr3IncrementalMetric {

    private Tree baseTree;
    private Tree targetTree;
    private Tree currentVirtualTree;
    private double currentDistance;
    private int dim;
    private int intT1Num;
    private int intT2Num;
    private int N;

    private IdGroup baseIdGroup;
    private int[][] targetLcaColMatrix;
    private int[] targetIdToCol;

    private Map<Node, BitSet> baseSplits;
    private Map<Node, BitSet> currentSplits;
    private Node activePruneNode = null;

    private int[][] assigncost;
    private int[][] lapCostScratch;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private int[] t2IntPairCount;
    private int[] currentT1PairCount;
    private Map<Node, Integer> nodeToRow;

    // PREALOKOWANE BUFORY ROBOCZE (Zero-Allocation w gorących ścieżkach)
    private int[] scratchIntersections;
    private int[] scratchChangedRowAll;
    private int[] leafBufA;
    private int[] leafBufB;
    private int[][] cachedKArrays;

    private BitSet currentPrunedLeaves;
    private BitSet tbrScratch1;
    private BitSet tbrScratch2;
    private BitSet tbrScratch3;
    private BitSet[] tbrChildSetsPool;
    private int[] scratchPruneRows;

    // PREALOKOWANY STOS DELTA (Zero-Allocation)
    private int deltaMaxDepth = 128;
    private int deltaPointer = 0;
    private int[] deltaRowsCount;
    private int[][] deltaRows;
    private int[][][] deltaOldRows;
    private int[][] deltaOldPairCounts;
    private int[][] deltaOldU;
    private int[][] deltaOldV;
    private int[][] deltaOldRowsol;
    private int[][] deltaOldColsol;
    private double[] deltaOldDistance;

    private final MatchingPairMetric mpMetricFull = new MatchingPairMetric();
    private final Stack<StateRecord> history = new Stack<>();
    private final treecmp.heuristics.tbr.TbrUtils tbrUtils = new treecmp.heuristics.tbr.TbrUtils();

    public BitSet getCluster(Node n) {
        return getBaseSplit(n);
    }

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode) {
        if (pruneNode == null || targetNode == null || this.targetTree == null) {
            return Double.POSITIVE_INFINITY;
        }
        try {
            Node root = pruneNode;
            while (root.getParent() != null) {
                root = root.getParent();
            }
            SimpleTree tree = new SimpleTree(root);

            Tree tempTree = tbrUtils.createTbrTree(tree, pruneNode, rerootNode, targetNode);
            if (tempTree != null) {
                if (tempTree instanceof SimpleTree) {
                    pal.tree.TreeUtils.computeParentPointers(tempTree.getRoot());
                    ((SimpleTree) tempTree).createNodeList();
                }
                double dist = mpMetricFull.getDistance(tempTree, this.targetTree);
                tbrUtils.clearCosts();
                return dist;
            }
        } catch (Exception ignored) {
        }
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        history.clear();
        deltaPointer = 0;

        if (baseTree != null && targetTree != null) {
            this.baseIdGroup = TreeUtils.getLeafIdGroup(baseTree);
            this.baseTree = createCleanCopy(baseTree);
            this.targetTree = createCleanCopy(targetTree);
            this.currentVirtualTree = createCleanCopy(this.baseTree);

            this.intT1Num = this.baseTree.getInternalNodeCount();
            this.intT2Num = this.targetTree.getInternalNodeCount();
            this.dim = Math.max(intT1Num, intT2Num);
            this.N = this.baseTree.getExternalNodeCount();

            this.scratchIntersections = new int[dim];
            this.scratchChangedRowAll = new int[dim];
            this.leafBufA = new int[N];
            this.leafBufB = new int[N];

            int maxK = Math.max(64, dim + 1);
            this.cachedKArrays = new int[maxK][];
            for (int i = 0; i < maxK; i++) {
                this.cachedKArrays[i] = new int[i];
            }

            int depth = Math.max(128, N * 4);
            this.deltaMaxDepth = depth;
            this.deltaPointer = 0;
            this.deltaRowsCount = new int[depth];
            this.deltaRows = new int[depth][dim];
            this.deltaOldRows = new int[depth][dim][dim];
            this.deltaOldPairCounts = new int[depth][dim];
            this.deltaOldU = new int[depth][dim];
            this.deltaOldV = new int[depth][dim];
            this.deltaOldRowsol = new int[depth][dim];
            this.deltaOldColsol = new int[depth][dim];
            this.deltaOldDistance = new double[depth];

            this.currentPrunedLeaves = new BitSet(N);
            this.tbrScratch1 = new BitSet(N);
            this.tbrScratch2 = new BitSet(N);
            this.tbrScratch3 = new BitSet(N);
            this.scratchPruneRows = new int[dim];
            this.tbrChildSetsPool = new BitSet[Math.max(16, N)];
            for (int i = 0; i < tbrChildSetsPool.length; i++) {
                this.tbrChildSetsPool[i] = new BitSet(N);
            }

            this.assigncost = new int[dim][dim];
            this.lapCostScratch = new int[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];
            this.u = new int[dim];
            this.v = new int[dim];
            this.currentT1PairCount = new int[dim];

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

            Node[] allNodesBaseCopy = TreeCmpUtils.getAllNodes(this.baseTree);
            for (Node n : allNodesBaseCopy) {
                BitSet split = getLeaves(n, baseIdGroup);
                baseSplits.put(n, split);
                currentSplits.put(n, (BitSet) split.clone());
            }

            Node[] allNodesVirtCopy = TreeCmpUtils.getAllNodes(this.currentVirtualTree);
            for (Node n : allNodesVirtCopy) {
                BitSet split = getLeaves(n, baseIdGroup);
                baseSplits.put(n, split);
                currentSplits.put(n, (BitSet) split.clone());
            }

            Node[] origAllNodes = TreeCmpUtils.getAllNodes(baseTree);
            for (Node nOrig : origAllNodes) {
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
            int[][] rawTargetLca = TreeCmpUtils.calcLcaMatrix(this.targetTree, this.baseIdGroup);

            // Bezpośrednie zmapowanie macierzy LCA drzewa T2 na indeksy kolumn (O(1) odczyt w pętli!)
            this.targetLcaColMatrix = new int[N][N];
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    int lcaId = rawTargetLca[i][j];
                    this.targetLcaColMatrix[i][j] = (lcaId >= 0 && lcaId < targetIdToCol.length) ? targetIdToCol[lcaId] : -1;
                }
            }

            short[] cSize2 = new short[maxNodesT2];
            Node[] postOrderT2 = TreeCmpUtils.getNodesInPostOrder(this.targetTree);
            TreeCmpUtils.calcCladeSizes(this.targetTree, postOrderT2, cSize2);

            this.t2IntPairCount = new int[dim];
            for (int i = 0; i < intT2Num; i++) {
                this.t2IntPairCount[i] = coutChildrenPairsFast(this.targetTree.getInternalNode(i), cSize2);
            }

            short[] cSize1 = new short[maxNodesT1];
            Node[] postOrderT1 = TreeCmpUtils.getNodesInPostOrder(this.baseTree);
            TreeCmpUtils.calcCladeSizes(this.baseTree, postOrderT1, cSize1);

            for (int i = 0; i < intT1Num; i++) {
                this.currentT1PairCount[i] = coutChildrenPairsFast(this.baseTree.getInternalNode(i), cSize1);
            }

            int[][] initialIntersection = new int[dim][dim];
            for (int i = 0; i < N; i++) {
                for (int j = i + 1; j < N; j++) {
                    int ind1 = lcaMatrix1[i][j];
                    int c = this.targetLcaColMatrix[i][j];
                    if (ind1 >= 0 && ind1 < baseIdToRow.length && c >= 0) {
                        int r = baseIdToRow[ind1];
                        if (r >= 0) {
                            initialIntersection[r][c]++;
                        }
                    }
                }
            }

            for (int r = 0; r < dim; r++) {
                for (int c = 0; c < dim; c++) {
                    if (r < intT1Num && c < intT2Num) {
                        assigncost[r][c] = currentT1PairCount[r] + t2IntPairCount[c] - (initialIntersection[r][c] << 1);
                    } else if (r >= intT1Num && c < intT2Num) {
                        assigncost[r][c] = t2IntPairCount[c];
                    } else if (r < intT1Num && c >= intT2Num) {
                        assigncost[r][c] = currentT1PairCount[r];
                    } else {
                        assigncost[r][c] = 0;
                    }
                }
            }

            solveLap();
        } else {
            this.currentDistance = 0;
        }
    }

    private void solveLap() {
        for (int i = 0; i < dim; i++) {
            System.arraycopy(assigncost[i], 0, lapCostScratch[i], 0, dim);
        }
        int rawMetric = LapSolver.lap(dim, lapCostScratch, rowsol, colsol, u, v);
        this.currentDistance = 0.5 * rawMetric;
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

    private BitSet getBaseSplit(Node n) {
        BitSet bs = baseSplits.get(n);
        if (bs != null) return bs;
        if (n.isLeaf()) {
            BitSet leafBs = new BitSet(N);
            leafBs.set(baseIdGroup.whichIdNumber(n.getIdentifier().getName()));
            baseSplits.put(n, leafBs);
            return leafBs;
        }
        return null;
    }

    // =========================================================================
    // ZERO-ALLOCATION STOS DELTA I SZYBKIE OBLICZANIE KOSZTU WIERSZY
    // =========================================================================

    private void ensureDeltaCapacity() {
        if (deltaPointer >= deltaMaxDepth) {
            int newDepth = deltaMaxDepth * 2;
            deltaRowsCount = Arrays.copyOf(deltaRowsCount, newDepth);
            deltaRows = Arrays.copyOf(deltaRows, newDepth);
            deltaOldRows = Arrays.copyOf(deltaOldRows, newDepth);
            deltaOldPairCounts = Arrays.copyOf(deltaOldPairCounts, newDepth);
            deltaOldU = Arrays.copyOf(deltaOldU, newDepth);
            deltaOldV = Arrays.copyOf(deltaOldV, newDepth);
            deltaOldRowsol = Arrays.copyOf(deltaOldRowsol, newDepth);
            deltaOldColsol = Arrays.copyOf(deltaOldColsol, newDepth);
            deltaOldDistance = Arrays.copyOf(deltaOldDistance, newDepth);

            for (int d = deltaMaxDepth; d < newDepth; d++) {
                deltaRows[d] = new int[dim];
                deltaOldRows[d] = new int[dim][dim];
                deltaOldPairCounts[d] = new int[dim];
                deltaOldU[d] = new int[dim];
                deltaOldV[d] = new int[dim];
                deltaOldRowsol[d] = new int[dim];
                deltaOldColsol[d] = new int[dim];
            }
            deltaMaxDepth = newDepth;
        }
    }

    private void pushEmptyDelta() {
        ensureDeltaCapacity();
        int d = deltaPointer;
        deltaRowsCount[d] = 0;
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;
    }

    private void saveRowInCurrentDelta(int d, int idx, int r) {
        deltaRows[d][idx] = r;
        System.arraycopy(assigncost[r], 0, deltaOldRows[d][idx], 0, dim);
        deltaOldPairCounts[d][idx] = currentT1PairCount[r];
    }

    private void finalizeDeltaPush(int d, int count) {
        deltaRowsCount[d] = count;
        if (count > 0) {
            System.arraycopy(u, 0, deltaOldU[d], 0, dim);
            System.arraycopy(v, 0, deltaOldV[d], 0, dim);
            System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
            System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        }
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;
    }

    private void runLapUpdateForCurrentDelta(int d, int count) {
        if (count > 0) {
            int[] changedRowsParam = (count < cachedKArrays.length) ? cachedKArrays[count] : new int[count];
            System.arraycopy(deltaRows[d], 0, changedRowsParam, 0, count);
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
            this.currentDistance = 0.5 * rawMetric;
        }
    }

    private void undoDeltaStack() {
        if (deltaPointer <= 0) return;
        deltaPointer--;
        int d = deltaPointer;
        int count = deltaRowsCount[d];

        if (count > 0) {
            for (int i = 0; i < count; i++) {
                int r = deltaRows[d][i];
                System.arraycopy(deltaOldRows[d][i], 0, assigncost[r], 0, dim);
                currentT1PairCount[r] = deltaOldPairCounts[d][i];
            }
            System.arraycopy(deltaOldU[d], 0, u, 0, dim);
            System.arraycopy(deltaOldV[d], 0, v, 0, dim);
            System.arraycopy(deltaOldRowsol[d], 0, rowsol, 0, dim);
            System.arraycopy(deltaOldColsol[d], 0, colsol, 0, dim);
        }
        this.currentDistance = deltaOldDistance[d];
    }

    private void computeRowCostEmpty(int row) {
        currentT1PairCount[row] = 0;
        int[] costRow = assigncost[row];
        System.arraycopy(t2IntPairCount, 0, costRow, 0, intT2Num);
        for (int c = intT2Num; c < dim; c++) {
            costRow[c] = 0;
        }
    }

    /**
     * Bezalokacyjne obliczanie kosztu wiersza dla węzła binarnego (2 podzbiory liści).
     * Używa płaskich tablic int[] zamiast wielokrotnego wywoływania BitSet.nextSetBit().
     */
    private void computeRowCostTwoSets(int row, BitSet setA, BitSet setB) {
        if (setA == null || setB == null || setA.isEmpty() || setB.isEmpty()) {
            computeRowCostEmpty(row);
            return;
        }

        int lenA = 0;
        for (int i = setA.nextSetBit(0); i >= 0; i = setA.nextSetBit(i + 1)) {
            leafBufA[lenA++] = i;
        }
        int lenB = 0;
        for (int i = setB.nextSetBit(0); i >= 0; i = setB.nextSetBit(i + 1)) {
            leafBufB[lenB++] = i;
        }

        Arrays.fill(scratchIntersections, 0, intT2Num, 0);
        int pairsCount = lenA * lenB;

        for (int i = 0; i < lenA; i++) {
            int[] lcaColRow = targetLcaColMatrix[leafBufA[i]];
            for (int j = 0; j < lenB; j++) {
                int c = lcaColRow[leafBufB[j]];
                if (c >= 0) {
                    scratchIntersections[c]++;
                }
            }
        }

        currentT1PairCount[row] = pairsCount;
        int[] costRow = assigncost[row];
        for (int c = 0; c < intT2Num; c++) {
            costRow[c] = pairsCount + t2IntPairCount[c] - (scratchIntersections[c] << 1);
        }
        for (int c = intT2Num; c < dim; c++) {
            costRow[c] = pairsCount;
        }
    }

    private void computeRowCost(int row, BitSet[] sets) {
        computeRowCostMulti(row, sets, sets.length);
    }

    private void computeRowCostMulti(int row, BitSet[] sets, int count) {
        if (count < 2) {
            computeRowCostEmpty(row);
            return;
        }
        if (count == 2) {
            computeRowCostTwoSets(row, sets[0], sets[1]);
            return;
        }

        Arrays.fill(scratchIntersections, 0, intT2Num, 0);
        int pairsCount = 0;
        int lenUnion = 0;

        for (int sIdx = 0; sIdx < count; sIdx++) {
            BitSet setB = sets[sIdx];
            if (setB == null || setB.isEmpty()) continue;

            int lenB = 0;
            for (int l = setB.nextSetBit(0); l >= 0; l = setB.nextSetBit(l + 1)) {
                leafBufB[lenB++] = l;
            }

            if (lenUnion > 0) {
                pairsCount += lenUnion * lenB;
                for (int i = 0; i < lenUnion; i++) {
                    int[] lcaColRow = targetLcaColMatrix[leafBufA[i]];
                    for (int j = 0; j < lenB; j++) {
                        int c = lcaColRow[leafBufB[j]];
                        if (c >= 0) {
                            scratchIntersections[c]++;
                        }
                    }
                }
            }

            System.arraycopy(leafBufB, 0, leafBufA, lenUnion, lenB);
            lenUnion += lenB;
        }

        currentT1PairCount[row] = pairsCount;
        int[] costRow = assigncost[row];
        for (int c = 0; c < intT2Num; c++) {
            costRow[c] = pairsCount + t2IntPairCount[c] - (scratchIntersections[c] << 1);
        }
        for (int c = intT2Num; c < dim; c++) {
            costRow[c] = pairsCount;
        }
    }

    // =========================================================================
    // ZERO-ALLOCATION 2D-DFS TBR (RootedTbrMetric)
    // =========================================================================

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        this.currentPrunedLeaves.clear();
        this.currentPrunedLeaves.or(getBaseSplit(pruneNode));
        BitSet P = this.currentPrunedLeaves;

        ensureDeltaCapacity();
        int d = deltaPointer;
        int count = 0;

        Node curr = pruneNode.getParent().getParent();
        while (curr != null) {
            Integer r = getRowForNode(curr);
            if (r != null) {
                saveRowInCurrentDelta(d, count, r);
                count++;
            }
            curr = curr.getParent();
        }

        Integer r_floating = getRowForNode(pruneNode.getParent());
        if (r_floating != null) {
            saveRowInCurrentDelta(d, count, r_floating);
            count++;
        }

        finalizeDeltaPush(d, count);

        curr = pruneNode.getParent().getParent();
        while (curr != null) {
            Integer r = getRowForNode(curr);
            if (r != null) {
                int chCount = curr.getChildCount();
                if (chCount == 2) {
                    tbrScratch1.clear();
                    tbrScratch1.or(getBaseSplit(curr.getChild(0)));
                    tbrScratch1.andNot(P);

                    tbrScratch2.clear();
                    tbrScratch2.or(getBaseSplit(curr.getChild(1)));
                    tbrScratch2.andNot(P);

                    computeRowCostTwoSets(r, tbrScratch1, tbrScratch2);
                } else {
                    for (int i = 0; i < chCount; i++) {
                        tbrChildSetsPool[i].clear();
                        tbrChildSetsPool[i].or(getBaseSplit(curr.getChild(i)));
                        tbrChildSetsPool[i].andNot(P);
                    }
                    computeRowCostMulti(r, tbrChildSetsPool, chCount);
                }
            }
            curr = curr.getParent();
        }

        if (r_floating != null) {
            computeRowCostEmpty(r_floating);
        }

        runLapUpdateForCurrentDelta(d, count);
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node wanderingSource) {
        BitSet P = this.currentPrunedLeaves;
        Integer r_floating = getRowForNode(pruneNode.getParent());

        if (r_floating != null) {
            ensureDeltaCapacity();
            int d = deltaPointer;
            saveRowInCurrentDelta(d, 0, r_floating);
            finalizeDeltaPush(d, 1);

            tbrScratch1.set(0, N);
            tbrScratch1.andNot(P);
            computeRowCostTwoSets(r_floating, P, tbrScratch1);

            runLapUpdateForCurrentDelta(d, 1);
        } else {
            pushEmptyDelta();
        }
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {
        setTargetRoot(pruneNode, wanderingSource);
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        BitSet P = this.currentPrunedLeaves;
        Integer r_floating = getRowForNode(pruneNode.getParent());
        Integer r_p = getRowForNode(parentTarget);
        boolean updateParent = (r_p != null && !r_p.equals(r_floating));

        if (r_floating == null && !updateParent) {
            pushEmptyDelta();
            return;
        }

        ensureDeltaCapacity();
        int d = deltaPointer;
        int count = 0;
        if (r_floating != null) {
            saveRowInCurrentDelta(d, count++, r_floating);
        }
        if (updateParent) {
            saveRowInCurrentDelta(d, count++, r_p);
        }
        finalizeDeltaPush(d, count);

        if (r_floating != null) {
            tbrScratch1.clear();
            tbrScratch1.or(getBaseSplit(childTarget));
            tbrScratch1.andNot(P);
            computeRowCostTwoSets(r_floating, P, tbrScratch1);
        }

        if (updateParent) {
            int chCount = parentTarget.getChildCount();
            BitSet targetLeaves = getBaseSplit(childTarget);

            if (chCount == 2) {
                tbrScratch1.clear();
                tbrScratch1.or(getBaseSplit(parentTarget.getChild(0)));
                tbrScratch1.andNot(P);
                if (tbrScratch1.intersects(targetLeaves)) tbrScratch1.or(P);

                tbrScratch2.clear();
                tbrScratch2.or(getBaseSplit(parentTarget.getChild(1)));
                tbrScratch2.andNot(P);
                if (tbrScratch2.intersects(targetLeaves)) tbrScratch2.or(P);

                computeRowCostTwoSets(r_p, tbrScratch1, tbrScratch2);
            } else {
                for (int i = 0; i < chCount; i++) {
                    BitSet cs = tbrChildSetsPool[i];
                    cs.clear();
                    cs.or(getBaseSplit(parentTarget.getChild(i)));
                    cs.andNot(P);
                    if (cs.intersects(targetLeaves)) cs.or(P);
                }
                computeRowCostMulti(r_p, tbrChildSetsPool, chCount);
            }
        }

        runLapUpdateForCurrentDelta(d, count);
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        moveTargetDown(parentTarget, childTarget, pruneNode, wanderingSource);
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        undoDeltaStack();
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        undoDeltaStack();
    }

    @Override
    public void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode) {
        if (currentPrunedLeaves == null) {
            pushEmptyDelta();
            return;
        }

        Integer rPrune = getRowForNode(pruneNode);
        Integer rParent = (parentReroot != pruneNode) ? getRowForNode(parentReroot) : null;
        if (rParent != null && rParent.equals(rPrune)) {
            rParent = null;
        }

        if (rPrune == null && rParent == null) {
            pushEmptyDelta();
            return;
        }

        ensureDeltaCapacity();
        int d = deltaPointer;
        int count = 0;
        if (rPrune != null) saveRowInCurrentDelta(d, count++, rPrune);
        if (rParent != null) saveRowInCurrentDelta(d, count++, rParent);
        finalizeDeltaPush(d, count);

        if (rPrune != null) {
            BitSet cLeaves = getBaseSplit(childReroot);
            tbrScratch1.clear();
            tbrScratch1.or(currentPrunedLeaves);
            tbrScratch1.andNot(cLeaves);
            computeRowCostTwoSets(rPrune, cLeaves, tbrScratch1);
        }

        if (rParent != null) {
            tbrScratch1.clear();
            for (int i = 0; i < parentReroot.getChildCount(); i++) {
                Node ch = parentReroot.getChild(i);
                if (ch != childReroot) {
                    tbrScratch1.or(getBaseSplit(ch));
                }
            }
            tbrScratch2.clear();
            tbrScratch2.or(currentPrunedLeaves);
            tbrScratch2.andNot(getBaseSplit(parentReroot));

            computeRowCostTwoSets(rParent, tbrScratch1, tbrScratch2);
        }

        runLapUpdateForCurrentDelta(d, count);
    }

    @Override
    public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {
        undoDeltaStack();
    }

    @Override
    public void revertPrunedState(Node pruneNode, Node wanderingSource) {
        undoDeltaStack();
        undoDeltaStack();
    }

    // =========================================================================
    // NNI, SPR & ECR
    // =========================================================================

    public void applySprPrune(Node pruneNode) { this.activePruneNode = pruneNode; }
    public void undoSprPrune(Node pruneNode) { this.activePruneNode = null; }

    public double applyNniStep(Node nodeToUpdate, BitSet bitsOut, BitSet bitsIn) {
        Node v = nodeToUpdate;
        Node u = v.getParent();

        Integer rVIndex = getRowForNode(v);
        Integer rUIndex = getRowForNode(u);
        if (rVIndex == null || rUIndex == null) return this.currentDistance;

        int rowV = rVIndex;
        int rowU = rUIndex;

        ensureDeltaCapacity();
        int d = deltaPointer;
        saveRowInCurrentDelta(d, 0, rowV);
        saveRowInCurrentDelta(d, 1, rowU);
        finalizeDeltaPush(d, 2);

        Node currentPos = (bitsIn != null) ? v : u;

        updateRowPairs(rowV, v, currentPos);
        updateRowPairs(rowU, u, currentPos);

        solveLap();

        return this.currentDistance;
    }

    public void undoNniStep() {
        undoDeltaStack();
    }

    private void updateRowPairs(int row, Node n, Node currentPos) {
        if (n == currentPos) {
            BitSet pSplit = getBaseSplit(activePruneNode);
            tbrScratch1.clear();
            tbrScratch1.or(getBaseSplit(n));
            tbrScratch1.andNot(pSplit);
            computeRowCostTwoSets(row, pSplit, tbrScratch1);
        } else if (n == activePruneNode.getParent()) {
            computeRowCostEmpty(row);
        } else {
            int chCount = n.getChildCount();
            for (int i = 0; i < chCount; i++) {
                tbrChildSetsPool[i].clear();
                tbrChildSetsPool[i].or(getCurrentCluster(n.getChild(i), currentPos, activePruneNode));
            }
            computeRowCostMulti(row, tbrChildSetsPool, chCount);
        }
    }

    private BitSet getCurrentCluster(Node x, Node currentPos, Node pruneNode) {
        if (x == pruneNode) return new BitSet(N);
        BitSet res = (BitSet) getBaseSplit(x).clone();

        boolean pInside = isDescendantOrSelf(pruneNode, x);
        boolean cInside = isDescendantOrSelf(currentPos, x);

        if (pInside && !cInside) {
            res.andNot(getBaseSplit(pruneNode));
        } else if (!pInside && cInside) {
            res.or(getBaseSplit(pruneNode));
        }
        return res;
    }

    private BitSet[] getPartitionsForNode(Node n) {
        if (activePruneNode != null && n == activePruneNode.getParent()) {
            return new BitSet[0];
        }
        int chCount = n.getChildCount();
        BitSet[] cSets = new BitSet[chCount];
        for (int i = 0; i < chCount; i++) {
            Node child = n.getChild(i);
            if (activePruneNode != null && child == activePruneNode) {
                cSets[i] = new BitSet(N);
            } else {
                cSets[i] = getSplitForNode(child);
            }
        }
        return cSets;
    }

    private boolean isDescendantOrSelf(Node descendant, Node ancestor) {
        Node curr = descendant;
        while (curr != null) {
            if (curr == ancestor) return true;
            curr = curr.getParent();
        }
        return false;
    }

    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        SimpleTree tNew = (SimpleTree) createCleanCopy(currentVirtualTree);
        Node P = getMappedNode(tNew, pruneNode);
        Node T = getMappedNode(tNew, targetNode);

        if (P == null || T == null) return Double.POSITIVE_INFINITY;
        Node p = P.getParent();
        if (p == null || p == T || p == T.getParent() || isDescendantOrSelf(T, P)) return Double.POSITIVE_INFINITY;

        Node pp = p.getParent();
        Node sibling = (p.getChild(0) == P) ? p.getChild(1) : p.getChild(0);

        Node newRoot = tNew.getRoot();
        if (pp != null) {
            int pIdx = findChildPos(p, pp);
            pp.setChild(pIdx, sibling);
            sibling.setParent(pp);
        } else {
            newRoot = sibling;
            sibling.setParent(null);
        }

        Node q = T.getParent();
        if (q != null) {
            int vIdx = findChildPos(T, q);
            q.setChild(vIdx, p);
            p.setParent(q);
        } else {
            newRoot = p;
            p.setParent(null);
        }

        p.setChild(0, P);
        P.setParent(p);
        p.setChild(1, T);
        T.setParent(p);

        SimpleTree finalTree = new SimpleTree(newRoot);
        finalTree.createNodeList();
        pal.tree.TreeUtils.computeParentPointers(finalTree.getRoot());
        pal.tree.TreeUtils.mapExternalIdentifiers(this.baseIdGroup, finalTree);

        return mpMetricFull.getDistance(finalTree, this.targetTree);
    }

    public void applySprRegraftStep(Node pruneNode, Node currentNode) { throw new UnsupportedOperationException(); }
    public void undoSprRegraftStep() { throw new UnsupportedOperationException(); }

    private void refreshAllRowsInPlace() {
        int[] countWrap = {0};
        postOrderRefresh(this.currentVirtualTree.getRoot(), countWrap);
        if (countWrap[0] > 0) {
            solveLap();
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

    private double pushUnchangedState() {
        history.push(new StateRecord(assigncost, currentT1PairCount, rowsol, colsol, u, v, currentDistance, currentVirtualTree, currentSplits, nodeToRow, null, null));
        return this.currentDistance;
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

        history.push(new StateRecord(assigncost, currentT1PairCount, rowsol, colsol, u, v, currentDistance, currentVirtualTree, currentSplits, nodeToRow, virtMoving, virtPartner));

        p1.setChild(idx1, virtPartner); virtPartner.setParent(p1);
        p2.setChild(idx2, virtMoving); virtMoving.setParent(p2);

        refreshAllRowsInPlace();

        return this.currentDistance;
    }

    @Override
    public void undoNni(NniMove move) {
        if (!history.isEmpty()) {
            StateRecord r = history.pop();
            this.assigncost = r.oldAssigncost;
            this.currentT1PairCount = r.oldPairCount;
            this.rowsol = r.rowsol;
            this.colsol = r.colsol;
            this.u = r.u;
            this.v = r.v;
            this.currentDistance = r.distance;

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

    @Override
    public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR newTopology) {
        return internalApply2sEcrMove(top, m1, m2, boundarySubtrees, newTopology, false);
    }

    @Override
    public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR newTopology) {
        return internalApply2sEcrMove(top, m1, m2, boundarySubtrees, newTopology, true);
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

        while (vTop.getChildCount() > 0) vTop.removeChild(0);
        while (vM1.getChildCount() > 0) vM1.removeChild(0);
        while (vM2.getChildCount() > 0) vM2.removeChild(0);

        if (template.isFork) {
            vTop.insertChild(vM1, 0); vM1.setParent(vTop);
            vTop.insertChild(vM2, 1); vM2.setParent(vTop);
            vM1.insertChild(vBounds[template.indices[0]], 0); vBounds[template.indices[0]].setParent(vM1);
            vM1.insertChild(vBounds[template.indices[1]], 1); vBounds[template.indices[1]].setParent(vM1);
            vM2.insertChild(vBounds[template.indices[2]], 0); vBounds[template.indices[2]].setParent(vM2);
            vM2.insertChild(vBounds[template.indices[3]], 1); vBounds[template.indices[3]].setParent(vM2);
        } else {
            vTop.insertChild(vBounds[template.indices[0]], 0); vBounds[template.indices[0]].setParent(vTop);
            vTop.insertChild(vM1, 1); vM1.setParent(vTop);
            vM1.insertChild(vBounds[template.indices[1]], 0); vBounds[template.indices[1]].setParent(vM1);
            vM1.insertChild(vM2, 1); vM2.setParent(vM1);
            vM2.insertChild(vBounds[template.indices[2]], 0); vBounds[template.indices[2]].setParent(vM2);
            vM2.insertChild(vBounds[template.indices[3]], 1); vBounds[template.indices[3]].setParent(vM2);
        }

        return calculateCleanSlateDistance(tNew, isCommit, N * N * 10);
    }

    @Override
    public double evaluate3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR newTopology) {
        return internalApply3sEcrMove(cluster, boundarySubtrees, newTopology, false);
    }

    @Override
    public double commit3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR newTopology) {
        return internalApply3sEcrMove(cluster, boundarySubtrees, newTopology, true);
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

        return calculateCleanSlateDistance(tNew, isCommit, N * N * 10);
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

    private int findChildPos(Node child, Node parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) == child) return i;
        }
        return -1;
    }

    private Tree createCleanCopy(Tree original) {
        SimpleTree copy = new SimpleTree(original);
        copy.createNodeList();
        TreeUtils.computeParentPointers(copy.getRoot());
        return copy;
    }

    private int getSafeMaxNodeId(Tree tree) {
        int maxId = 0;
        Node[] allNodes = TreeCmpUtils.getAllNodes(tree);
        for (Node n : allNodes) {
            if (n != null && n.getNumber() > maxId) maxId = n.getNumber();
        }
        return maxId + 1;
    }

    private int coutChildrenPairsFast(Node n, short[] clustSizeTab) {
        int chCount = n.getChildCount();
        int sum = 0;
        int sumSq = 0;

        for (int i = 0; i < chCount; i++) {
            Node chNode = n.getChild(i);
            int size = chNode.isLeaf() ? 1 : clustSizeTab[chNode.getNumber()];
            sum += size;
            sumSq += size * size;
        }
        return (sum * sum - sumSq) >> 1;
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
            int chCount = n.getChildCount();
            this.canonicalParts = new BitSet[chCount];
            for (int i = 0; i < chCount; i++) {
                canonicalParts[i] = getLeaves(n.getChild(i), idGroup);
            }
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

        @Override
        public String toString() {
            return Arrays.toString(canonicalParts);
        }
    }

    private static BitSet getLeaves(Node n, IdGroup idGroup) {
        BitSet bs = new BitSet(); populate(n, idGroup, bs); return bs;
    }

    private static void populate(Node n, IdGroup idGroup, BitSet bs) {
        if (n.isLeaf()) bs.set(idGroup.whichIdNumber(n.getIdentifier().getName()));
        else for (int i = 0; i < n.getChildCount(); i++) populate(n.getChild(i), idGroup, bs);
    }

    private static class StateRecord {
        int[][] oldAssigncost;
        int[] oldPairCount;
        int[] rowsol, colsol, u, v;
        double distance;
        Tree oldTree;
        Map<Node, BitSet> oldSplits;
        Map<Node, Integer> oldNodeToRow;
        Node nniMovingNode;
        Node nniPartnerNode;

        StateRecord(int[][] assigncost, int[] pairCount, int[] rs, int[] cs, int[] u, int[] v, double d, Tree oldTree,
                    Map<Node, BitSet> currentSplits, Map<Node, Integer> nodeToRow, Node nniMovingNode, Node nniPartnerNode) {
            this.oldAssigncost = new int[assigncost.length][];
            for (int i = 0; i < assigncost.length; i++) {
                this.oldAssigncost[i] = assigncost[i].clone();
            }
            this.oldPairCount = pairCount.clone();
            this.rowsol = rs.clone();
            this.colsol = cs.clone();
            this.u = u.clone();
            this.v = v.clone();
            this.distance = d;
            this.oldTree = oldTree;
            this.oldSplits = new IdentityHashMap<>(currentSplits);
            this.oldNodeToRow = new IdentityHashMap<>(nodeToRow);
            this.nniMovingNode = nniMovingNode;
            this.nniPartnerNode = nniPartnerNode;
        }
    }

    private double calculateCleanSlateDistance(SimpleTree tNew, boolean isCommit, int maxCostBound) {
        Map<Signature, Integer> sigToOldRow = new HashMap<>();
        for (int i = 0; i < intT1Num; i++) {
            Node n = this.currentVirtualTree.getInternalNode(i);
            Signature sig = new Signature(n, N, baseIdGroup);
            sigToOldRow.put(sig, i);
        }

        Tree tPerfect = createCleanCopy(tNew);
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

        pal.tree.TreeUtils.mapExternalIdentifiers(this.baseIdGroup, tPerfect);
        int[][] lcaNew = TreeCmpUtils.calcLcaMatrix(tPerfect, this.baseIdGroup);
        int[][] newIntersection = new int[dim][dim];

        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                int ind1 = lcaNew[i][j];
                int c = this.targetLcaColMatrix[i][j];
                if (ind1 >= 0 && ind1 < idToRow.length && c >= 0) {
                    int r_new = idToRow[ind1];
                    if (r_new >= 0) newIntersection[r_new][c]++;
                }
            }
        }

        short[] cSizeNew = new short[maxNodesNew];
        Node[] postOrderNew = TreeCmpUtils.getNodesInPostOrder(tPerfect);
        TreeCmpUtils.calcCladeSizes(tPerfect, postOrderNew, cSizeNew);

        int[] newT1PairCount = new int[dim];
        for (int r_new = 0; r_new < intT1Num; r_new++) {
            newT1PairCount[r_new] = coutChildrenPairsFast(tPerfect.getInternalNode(r_new), cSizeNew);
        }

        int[][] tempAssigncost = new int[dim][dim];
        for (int r = 0; r < dim; r++) {
            for (int c = 0; c < dim; c++) {
                if (r < intT1Num && c < intT2Num) {
                    tempAssigncost[r][c] = newT1PairCount[r] + t2IntPairCount[c] - (newIntersection[r][c] << 1);
                } else if (r >= intT1Num && c < intT2Num) {
                    tempAssigncost[r][c] = t2IntPairCount[c];
                } else if (r < intT1Num && c >= intT2Num) {
                    tempAssigncost[r][c] = newT1PairCount[r];
                } else {
                    tempAssigncost[r][c] = 0;
                }
            }
        }

        int[][] lapCost = new int[dim][dim];
        for (int i = 0; i < dim; i++) System.arraycopy(tempAssigncost[i], 0, lapCost[i], 0, dim);

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
            history.push(new StateRecord(this.assigncost, this.currentT1PairCount, this.rowsol, this.colsol, this.u, this.v, this.currentDistance, this.currentVirtualTree, this.currentSplits, this.nodeToRow, null, null));
            this.assigncost = tempAssigncost;
            this.currentT1PairCount = newT1PairCount;
            this.currentVirtualTree = tPerfect;
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
            Node[] allNodesOriginal = TreeCmpUtils.getAllNodes(this.baseTree);
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

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { history.clear(); deltaPointer = 0; }
    @Override public double getDistance(Tree t1, Tree t2, int... indexes) { return mpMetricFull.getDistance(t1, t2, indexes); }
    @Override public String getName() { return "Accelerated " + mpMetricFull.getName(); }
    @Override public String getCommandLineName() { return mpMetricFull.getCommandLineName(); }
    @Override public void setCommandLineName(String cln) { mpMetricFull.setCommandLineName(cln); }
    @Override public void setName(String name) { mpMetricFull.setName(name); }
    @Override public String getDescription() { return mpMetricFull.getDescription(); }
    @Override public void setDescription(String d) { mpMetricFull.setDescription(d); }
    @Override public void initData() { mpMetricFull.initData(); }
    @Override public boolean isRooted() { return true; }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return mpMetricFull.isDiffLeafSets(); }
    @Override public AlignInfo getAlignment() { return mpMetricFull.getAlignment(); }
}