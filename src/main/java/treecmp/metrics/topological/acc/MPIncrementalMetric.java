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
    private Tree lastRawTargetTree;
    private Tree currentVirtualTree;
    private boolean virtualTreeInitialized = false;

    private double currentDistance;
    private int dim;
    private int intT1Num;
    private int intT2Num;
    private int N;

    private int allocatedDim = -1;
    private int allocatedN = -1;

    private IdGroup baseIdGroup;
    private int[][] targetLcaColMatrix;
    private int[] targetIdToCol;

    private Map<Node, BitSet> baseSplits;
    private Map<Node, BitSet> currentSplits;
    private Node activePruneNode = null;

    private int[][] assigncost;
    private int[][] lapCostScratch;
    private int[][] initialIntersectionScratch;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private int[] t2IntPairCount;
    private int[] currentT1PairCount;
    private Map<Node, Integer> nodeToRow;
    private int[] baseIdToRow;
    private short[] cladeSizeScratch1;
    private short[] cladeSizeScratch2;

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

    // Bufory dla bezalokacyjnego 2s-ECR i 3s-ECR
    private final BitSet[] ecrBBits = new BitSet[5];
    private final int[] ecrRows = new int[4];
    private final BitSet[] ecrLeftSets = new BitSet[4];
    private final BitSet[] ecrRightSets = new BitSet[4];
    private final Node[] ecrNodes = new Node[4];
    private final BitSet[] ecrUnionSets = new BitSet[4];
    private int ecrNextAvailIdx = 1;
    private int ecrUpdateCount = 0;

    // PREALOKOWANY STOS DELTA (Zero-Allocation)
    private int deltaMaxDepth = 0;
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
        activePruneNode = null;
        virtualTreeInitialized = false;

        if (baseTree != null && targetTree != null) {
            boolean sameTarget = (targetTree == this.lastRawTargetTree && this.targetTree != null && this.baseIdGroup != null);
            if (!sameTarget) {
                this.baseIdGroup = TreeUtils.getLeafIdGroup(baseTree);
                this.targetTree = createCleanCopy(targetTree);
                this.lastRawTargetTree = targetTree;
            }

            if (baseTree instanceof SimpleTree && baseTree.getRoot().getParent() == null) {
                this.baseTree = baseTree;
            } else {
                this.baseTree = createCleanCopy(baseTree);
            }
            this.currentVirtualTree = this.baseTree;

            this.intT1Num = this.baseTree.getInternalNodeCount();
            this.intT2Num = this.targetTree.getInternalNodeCount();
            this.dim = Math.max(intT1Num, intT2Num);
            this.N = this.baseTree.getExternalNodeCount();

            boolean needRealloc = (this.dim != this.allocatedDim) || (this.N != this.allocatedN) || (this.assigncost == null);

            if (needRealloc) {
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
                for (int i = 0; i < 4; i++) {
                    this.ecrLeftSets[i] = new BitSet(N);
                    this.ecrRightSets[i] = new BitSet(N);
                    this.ecrUnionSets[i] = new BitSet(N);
                }

                this.assigncost = new int[dim][dim];
                this.lapCostScratch = new int[dim][dim];
                this.initialIntersectionScratch = new int[dim][dim];
                this.rowsol = new int[dim];
                this.colsol = new int[dim];
                this.u = new int[dim];
                this.v = new int[dim];
                this.currentT1PairCount = new int[dim];
                this.t2IntPairCount = new int[dim];
                this.targetLcaColMatrix = new int[N][N];

                int expectedMapSize = (N * 2 + 5) * 4 / 3;
                this.nodeToRow = new IdentityHashMap<>(expectedMapSize);
                this.baseSplits = new IdentityHashMap<>(expectedMapSize);
                this.currentSplits = new IdentityHashMap<>(expectedMapSize);

                int safeMaxIds = N * 2 + 16;
                this.baseIdToRow = new int[safeMaxIds];
                this.targetIdToCol = new int[safeMaxIds];
                this.cladeSizeScratch1 = new short[safeMaxIds];
                this.cladeSizeScratch2 = new short[safeMaxIds];

                this.allocatedDim = this.dim;
                this.allocatedN = this.N;
                sameTarget = false;
            } else {
                for (int r = 0; r < dim; r++) {
                    Arrays.fill(this.initialIntersectionScratch[r], 0);
                }
                Arrays.fill(this.currentT1PairCount, 0);
                this.nodeToRow.clear();
                this.baseSplits.clear();
                this.currentSplits.clear();
            }

            if (!sameTarget) {
                int maxNodesT2 = getSafeMaxNodeId(this.targetTree);
                if (this.targetIdToCol.length < maxNodesT2) {
                    this.targetIdToCol = new int[maxNodesT2];
                    this.cladeSizeScratch2 = new short[maxNodesT2];
                }
                Arrays.fill(this.targetIdToCol, 0, maxNodesT2, -1);
                for (int i = 0; i < intT2Num; i++) {
                    this.targetIdToCol[this.targetTree.getInternalNode(i).getNumber()] = i;
                }

                int[][] rawTargetLca = TreeCmpUtils.calcLcaMatrix(this.targetTree, this.baseIdGroup);
                for (int i = 0; i < N; i++) {
                    int[] rawRow = rawTargetLca[i];
                    int[] colRow = this.targetLcaColMatrix[i];
                    for (int j = 0; j < N; j++) {
                        int lcaId = rawRow[j];
                        colRow[j] = (lcaId >= 0 && lcaId < maxNodesT2) ? this.targetIdToCol[lcaId] : -1;
                    }
                }

                Arrays.fill(this.cladeSizeScratch2, 0, maxNodesT2, (short) 0);
                Node[] postOrderT2 = TreeCmpUtils.getNodesInPostOrder(this.targetTree);
                TreeCmpUtils.calcCladeSizes(this.targetTree, postOrderT2, this.cladeSizeScratch2);

                Arrays.fill(this.t2IntPairCount, 0);
                for (int i = 0; i < intT2Num; i++) {
                    this.t2IntPairCount[i] = coutChildrenPairsFast(this.targetTree.getInternalNode(i), this.cladeSizeScratch2);
                }
            }

            int maxNodesT1 = getSafeMaxNodeId(this.baseTree);
            if (this.baseIdToRow.length < maxNodesT1) {
                this.baseIdToRow = new int[maxNodesT1];
                this.cladeSizeScratch1 = new short[maxNodesT1];
            }
            Arrays.fill(this.baseIdToRow, 0, maxNodesT1, -1);

            for (int i = 0; i < intT1Num; i++) {
                Node nBase = this.baseTree.getInternalNode(i);
                this.baseIdToRow[nBase.getNumber()] = i;
                this.nodeToRow.put(nBase, i);
            }

            extractSplitsPostOrder(this.baseTree.getRoot());

            if (this.baseTree != baseTree) {
                Node[] origAllNodes = TreeCmpUtils.getAllNodes(baseTree);
                for (Node nOrig : origAllNodes) {
                    Node nCopy = getMappedNode(this.baseTree, nOrig);
                    if (nCopy != null) {
                        Integer row = nodeToRow.get(nCopy);
                        if (row != null) nodeToRow.put(nOrig, row);
                        BitSet bs = baseSplits.get(nCopy);
                        baseSplits.put(nOrig, bs);
                        currentSplits.put(nOrig, bs);
                    }
                }
            }

            int[][] lcaMatrix1 = TreeCmpUtils.calcLcaMatrix(this.baseTree, this.baseIdGroup);

            Arrays.fill(this.cladeSizeScratch1, 0, maxNodesT1, (short) 0);
            Node[] postOrderT1 = TreeCmpUtils.getNodesInPostOrder(this.baseTree);
            TreeCmpUtils.calcCladeSizes(this.baseTree, postOrderT1, this.cladeSizeScratch1);

            for (int i = 0; i < intT1Num; i++) {
                this.currentT1PairCount[i] = coutChildrenPairsFast(this.baseTree.getInternalNode(i), this.cladeSizeScratch1);
            }

            for (int i = 0; i < N; i++) {
                int[] lcaRow1 = lcaMatrix1[i];
                int[] targetColRow = this.targetLcaColMatrix[i];
                for (int j = i + 1; j < N; j++) {
                    int ind1 = lcaRow1[j];
                    int c = targetColRow[j];
                    if (ind1 >= 0 && ind1 < maxNodesT1 && c >= 0) {
                        int r = this.baseIdToRow[ind1];
                        if (r >= 0) {
                            this.initialIntersectionScratch[r][c]++;
                        }
                    }
                }
            }

            for (int r = 0; r < dim; r++) {
                int[] costRow = this.assigncost[r];
                int[] interRow = this.initialIntersectionScratch[r];
                int t1Pairs = this.currentT1PairCount[r];
                for (int c = 0; c < dim; c++) {
                    if (r < intT1Num && c < intT2Num) {
                        costRow[c] = t1Pairs + t2IntPairCount[c] - (interRow[c] << 1);
                    } else if (r >= intT1Num && c < intT2Num) {
                        costRow[c] = t2IntPairCount[c];
                    } else if (r < intT1Num && c >= intT2Num) {
                        costRow[c] = t1Pairs;
                    } else {
                        costRow[c] = 0;
                    }
                }
            }

            solveLap();
        } else {
            this.currentDistance = 0;
        }
    }

    private BitSet extractSplitsPostOrder(Node node) {
        BitSet bs = new BitSet(N);
        if (node.isLeaf()) {
            int id = baseIdGroup.whichIdNumber(node.getIdentifier().getName());
            if (id >= 0) bs.set(id);
        } else {
            int chCount = node.getChildCount();
            for (int i = 0; i < chCount; i++) {
                bs.or(extractSplitsPostOrder(node.getChild(i)));
            }
        }
        baseSplits.put(node, bs);
        currentSplits.put(node, bs);
        return bs;
    }

    private void ensureVirtualTreeInitialized() {
        if (!virtualTreeInitialized && this.baseTree != null) {
            this.currentVirtualTree = createCleanCopy(this.baseTree);
            for (int i = 0; i < intT1Num; i++) {
                Node nBase = this.baseTree.getInternalNode(i);
                Node nVirt = this.currentVirtualTree.getInternalNode(i);
                Integer row = nodeToRow.get(nBase);
                if (row != null) {
                    nodeToRow.put(nVirt, row);
                }
            }
            extractVirtualSplitsPostOrder(this.currentVirtualTree.getRoot());
            virtualTreeInitialized = true;
        }
    }

    private BitSet extractVirtualSplitsPostOrder(Node node) {
        BitSet bs = new BitSet(N);
        if (node.isLeaf()) {
            int id = baseIdGroup.whichIdNumber(node.getIdentifier().getName());
            if (id >= 0) bs.set(id);
        } else {
            int chCount = node.getChildCount();
            for (int i = 0; i < chCount; i++) {
                bs.or(extractVirtualSplitsPostOrder(node.getChild(i)));
            }
        }
        baseSplits.put(node, bs);
        currentSplits.put(node, (BitSet) bs.clone());
        return bs;
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
        return getSplitForNode(n);
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
    // NNI, SPR & ECR (100% ZERO-ALLOCATION NA STOSIE DELTA!)
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

        runLapUpdateForCurrentDelta(d, 2);

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
                populateCurrentClusterInto(n.getChild(i), currentPos, activePruneNode, tbrChildSetsPool[i]);
            }
            computeRowCostMulti(row, tbrChildSetsPool, chCount);
        }
    }

    private void populateCurrentClusterInto(Node x, Node currentPos, Node pruneNode, BitSet dest) {
        dest.clear();
        if (x == pruneNode) return;
        dest.or(getBaseSplit(x));

        boolean pInside = isDescendantOrSelf(pruneNode, x);
        boolean cInside = isDescendantOrSelf(currentPos, x);

        if (pInside && !cInside) {
            dest.andNot(getBaseSplit(pruneNode));
        } else if (!pInside && cInside) {
            dest.or(getBaseSplit(pruneNode));
        }
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
        ensureVirtualTreeInitialized();
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
        ensureVirtualTreeInitialized();
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

    // =========================================================================
    // BEZALOKACYJNY 2s-ECR ORAZ 3s-ECR NA STOSIE DELTA (O(N^2) ZAMIAST O(N^3))
    // =========================================================================

    @Override
    public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        double dist = apply2sEcrDelta(top, m1, m2, b, template, false);
        undoDeltaStack();
        return dist;
    }

    @Override
    public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        return apply2sEcrDelta(top, m1, m2, b, template, true);
    }

    private double apply2sEcrDelta(Node top, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR template, boolean updateSplits) {
        for (int i = 0; i < 4; i++) {
            ecrBBits[i] = getBaseSplit(b[i]);
        }

        BitSet b0 = ecrBBits[template.indices[0]];
        BitSet b1 = ecrBBits[template.indices[1]];
        BitSet b2 = ecrBBits[template.indices[2]];
        BitSet b3 = ecrBBits[template.indices[3]];

        if (template.isFork) {
            ecrLeftSets[1].clear(); ecrLeftSets[1].or(b0);
            ecrRightSets[1].clear(); ecrRightSets[1].or(b1);

            ecrLeftSets[2].clear(); ecrLeftSets[2].or(b2);
            ecrRightSets[2].clear(); ecrRightSets[2].or(b3);

            ecrLeftSets[0].clear(); ecrLeftSets[0].or(b0); ecrLeftSets[0].or(b1);
            ecrRightSets[0].clear(); ecrRightSets[0].or(b2); ecrRightSets[0].or(b3);
        } else {
            ecrLeftSets[2].clear(); ecrLeftSets[2].or(b2);
            ecrRightSets[2].clear(); ecrRightSets[2].or(b3);

            ecrLeftSets[1].clear(); ecrLeftSets[1].or(b1);
            ecrRightSets[1].clear(); ecrRightSets[1].or(b2); ecrRightSets[1].or(b3);

            ecrLeftSets[0].clear(); ecrLeftSets[0].or(b0);
            ecrRightSets[0].clear(); ecrRightSets[0].or(b1); ecrRightSets[0].or(ecrRightSets[1]);
        }

        ecrNodes[0] = top;
        ecrNodes[1] = m1;
        ecrNodes[2] = m2;

        ensureDeltaCapacity();
        int d = deltaPointer;
        int count = 0;

        for (int i = 0; i < 3; i++) {
            Integer r = getRowForNode(ecrNodes[i]);
            if (r != null) {
                ecrRows[i] = r;
                saveRowInCurrentDelta(d, count++, r);
            } else {
                ecrRows[i] = -1;
            }
        }

        finalizeDeltaPush(d, count);

        for (int i = 0; i < 3; i++) {
            int r = ecrRows[i];
            if (r >= 0) {
                computeRowCostTwoSets(r, ecrLeftSets[i], ecrRightSets[i]);
            }
            if (updateSplits && i > 0 && ecrNodes[i] != null) {
                BitSet bs = baseSplits.get(ecrNodes[i]);
                if (bs != null) {
                    bs.clear();
                    bs.or(ecrLeftSets[i]);
                    bs.or(ecrRightSets[i]);
                }
            }
        }

        runLapUpdateForCurrentDelta(d, count);
        return this.currentDistance;
    }

    @Override
    public double evaluate3sEcrMove(List<Node> cluster, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR template) {
        double dist = apply3sEcrDelta(cluster, b, template, false);
        undoDeltaStack();
        return dist;
    }

    @Override
    public double commit3sEcrMove(List<Node> cluster, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR template) {
        return apply3sEcrDelta(cluster, b, template, true);
    }

    private double apply3sEcrDelta(List<Node> cluster, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR template, boolean updateSplits) {
        for (int i = 0; i < 5; i++) {
            ecrBBits[i] = getBaseSplit(b[i]);
        }

        ecrNextAvailIdx = 1;
        ecrUpdateCount = 0;

        compute3sEcrPairsFast(template, 0, cluster);

        ensureDeltaCapacity();
        int d = deltaPointer;
        int count = 0;

        for (int i = 0; i < ecrUpdateCount; i++) {
            int r = ecrRows[i];
            if (r >= 0) {
                saveRowInCurrentDelta(d, count++, r);
            }
        }

        finalizeDeltaPush(d, count);

        for (int i = 0; i < ecrUpdateCount; i++) {
            int r = ecrRows[i];
            if (r >= 0) {
                computeRowCostTwoSets(r, ecrLeftSets[i], ecrRightSets[i]);
            }
            if (updateSplits && ecrNodes[i] != cluster.get(0) && ecrNodes[i] != null) {
                BitSet bs = baseSplits.get(ecrNodes[i]);
                if (bs != null) {
                    bs.clear();
                    bs.or(ecrUnionSets[i]);
                }
            }
        }

        runLapUpdateForCurrentDelta(d, count);
        return this.currentDistance;
    }

    private BitSet compute3sEcrPairsFast(SubtreeEcr3Utils.TopologyTemplate3sECR temp, int clusterIdx, List<Node> cluster) {
        Node currNode = cluster.get(clusterIdx);
        BitSet leftBits;
        if (temp.left.leafIndex != -1) {
            leftBits = ecrBBits[temp.left.leafIndex];
        } else {
            int cSize = cluster.size();
            int nextIdx = (ecrNextAvailIdx < cSize) ? ecrNextAvailIdx++ : cSize - 1;
            leftBits = compute3sEcrPairsFast(temp.left, nextIdx, cluster);
        }

        BitSet rightBits;
        if (temp.right.leafIndex != -1) {
            rightBits = ecrBBits[temp.right.leafIndex];
        } else {
            int cSize = cluster.size();
            int nextIdx = (ecrNextAvailIdx < cSize) ? ecrNextAvailIdx++ : cSize - 1;
            rightBits = compute3sEcrPairsFast(temp.right, nextIdx, cluster);
        }

        int slot = ecrUpdateCount++;
        ecrNodes[slot] = currNode;
        Integer r = getRowForNode(currNode);
        ecrRows[slot] = (r != null) ? r : -1;

        ecrLeftSets[slot].clear();
        ecrLeftSets[slot].or(leftBits);

        ecrRightSets[slot].clear();
        ecrRightSets[slot].or(rightBits);

        BitSet union = ecrUnionSets[slot];
        union.clear();
        union.or(leftBits);
        union.or(rightBits);
        return union;
    }

    private Node getMappedNode(Tree destTree, Node srcNode) {
        if (srcNode == null || destTree == null) return null;
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
        int extCount = tree.getExternalNodeCount();
        for (int i = 0; i < extCount; i++) {
            int num = tree.getExternalNode(i).getNumber();
            if (num > maxId) maxId = num;
        }
        int intCount = tree.getInternalNodeCount();
        for (int i = 0; i < intCount; i++) {
            int num = tree.getInternalNode(i).getNumber();
            if (num > maxId) maxId = num;
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