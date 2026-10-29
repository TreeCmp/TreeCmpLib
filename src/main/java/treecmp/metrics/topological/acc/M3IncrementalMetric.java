package treecmp.metrics.topological.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.AlignInfo;
import treecmp.common.LapSolver;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalMetric;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalMetric;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.nni.acc.NniIncrementalMetric;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.heuristics.tbr.acc.RootedTbrMetric;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingTripletMetric;

import java.util.*;

public class M3IncrementalMetric implements IncrementalMetric,
        NniIncrementalMetric,
        Ecr2IncrementalMetric,
        Ecr3IncrementalMetric,
        RootedTbrMetric {

    private Tree originalBaseTree;
    private Tree baseTree;
    private Tree targetTree;
    private Tree lastRawTargetTree;
    private Tree currentVirtualTree;
    private boolean virtualTreeInitialized = false;
    private boolean baseSigToRowInitialized = false;

    private double currentDistance;
    private int dim;
    private int intT1Num;
    private int intT2Num;
    private int N;

    private int allocatedDim = -1;
    private int allocatedN = -1;

    private IdGroup baseIdGroup;
    private int[][] targetLcaMatrix;
    private int[] targetIdToCol;

    private Map<Node, BitSet> baseSplits;
    private Map<Node, BitSet> currentSplits;
    private Map<Node, Node> nodeToVirt;
    private Node activePruneNode = null;
    private Map<Node, Integer> nodeToRow;

    private int[][] assigncost;
    private int[][] lapCostScratch;
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

    private final UTbrUtils utbrUtils = new UTbrUtils();
    private final UsprUtils usprUtils = new UsprUtils();

    // =========================================================================
    // PREALOKOWANY STOS DELTA (ZERO-ALLOCATION DLA 2D-DFS uTBR, NNI, 2s/3s-ECR)
    // =========================================================================
    private int deltaMaxDepth = 0;
    private int deltaPointer = 0;
    private int[] deltaRowsCount;
    private int[][] deltaRows;
    private int[][][] deltaOldRows;
    private int[][] deltaOldTripletCounts;
    private int[][] deltaOldU;
    private int[][] deltaOldV;
    private int[][] deltaOldRowsol;
    private int[][] deltaOldColsol;
    private double[] deltaOldDistance;
    private Node[] deltaOldSplitNode;
    private BitSet[] deltaOldSplitBits;
    private Node[] deltaOldSplitNode2;
    private BitSet[] deltaOldSplitBits2;
    private Node[] deltaNniMoving;
    private Node[] deltaNniPartner;
    private Node[] deltaNniP1;
    private Node[] deltaNniP2;
    private int[] deltaNniIdx1;
    private int[] deltaNniIdx2;

    // Prealokowane bufory trójpartycji dla 2D-DFS uTBR oraz ECR (zero alokacji)
    private final BitSet[] fastTripletSets1 = new BitSet[3];
    private final BitSet[] fastTripletSets2 = new BitSet[3];
    private final BitSet[][] ecrTripletSets = new BitSet[4][3];
    private final BitSet[] ecrBBits = new BitSet[5];
    private final int[] ecrRows = new int[4];
    private final Node[] ecrNodes = new Node[4];
    private final BitSet[] ecrUnionSets = new BitSet[4];
    private int ecrNextAvailIdx = 1;
    private int ecrUpdateCount = 0;

    private BitSet tbrScratchUnion;
    private BitSet[] tbrBitSetPool;
    private int tbrPoolIdx = 0;

    // Słownik mapujący trójpartycje (Signature) bazowego drzewa na indeksy wierszy macierzy kosztów
    private Map<Signature, Integer> baseSigToRow;

    // Prealokowane bufory robocze dla ewaluacji drzew tymczasowych
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

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        deltaPointer = 0;
        activePruneNode = null;
        virtualTreeInitialized = false;
        baseSigToRowInitialized = false;

        if (baseTree != null && targetTree != null) {
            this.originalBaseTree = baseTree;
            boolean sameTarget = (targetTree == this.lastRawTargetTree && this.targetTree != null && this.baseIdGroup != null);

            if (!sameTarget) {
                this.baseIdGroup = TreeUtils.getLeafIdGroup(baseTree);
                this.targetTree = TreeNeighborhoodUtils.fastTreeClone(targetTree);
                this.lastRawTargetTree = targetTree;
            }

            if (baseTree instanceof SimpleTree && baseTree.getRoot().getParent() == null) {
                this.baseTree = baseTree;
            } else {
                this.baseTree = TreeNeighborhoodUtils.fastTreeClone(baseTree);
            }
            this.currentVirtualTree = this.baseTree;

            this.intT1Num = this.baseTree.getInternalNodeCount();
            this.intT2Num = this.targetTree.getInternalNodeCount();
            this.dim = Math.max(intT1Num, intT2Num);
            this.N = this.baseTree.getExternalNodeCount();

            boolean needRealloc = (this.dim != this.allocatedDim) || (this.N != this.allocatedN) || (this.assigncost == null);

            if (needRealloc) {
                this.assigncost = new int[dim][dim];
                this.lapCostScratch = new int[dim][dim];
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

                for (int i = 0; i < 3; i++) {
                    this.fastTripletSets1[i] = new BitSet(N);
                    this.fastTripletSets2[i] = new BitSet(N);
                }
                for (int i = 0; i < 4; i++) {
                    this.ecrUnionSets[i] = new BitSet(N);
                    for (int j = 0; j < 3; j++) {
                        this.ecrTripletSets[i][j] = new BitSet(N);
                    }
                }
                this.tbrScratchUnion = new BitSet(N);

                int maxPoolSize = Math.max(128, N * 4);
                this.tbrBitSetPool = new BitSet[maxPoolSize];
                for (int i = 0; i < maxPoolSize; i++) {
                    this.tbrBitSetPool[i] = new BitSet(N);
                }

                int depth = Math.max(128, N * 4);
                this.deltaMaxDepth = depth;
                this.deltaRowsCount = new int[depth];
                this.deltaRows = new int[depth][dim];
                this.deltaOldRows = new int[depth][][];
                this.deltaOldTripletCounts = new int[depth][dim];
                this.deltaOldU = new int[depth][dim];
                this.deltaOldV = new int[depth][dim];
                this.deltaOldRowsol = new int[depth][dim];
                this.deltaOldColsol = new int[depth][dim];
                this.deltaOldDistance = new double[depth];
                this.deltaOldSplitNode = new Node[depth];
                this.deltaOldSplitBits = new BitSet[depth];
                this.deltaOldSplitNode2 = new Node[depth];
                this.deltaOldSplitBits2 = new BitSet[depth];
                this.deltaNniMoving = new Node[depth];
                this.deltaNniPartner = new Node[depth];
                this.deltaNniP1 = new Node[depth];
                this.deltaNniP2 = new Node[depth];
                this.deltaNniIdx1 = new int[depth];
                this.deltaNniIdx2 = new int[depth];

                for (int d = 0; d < depth; d++) {
                    int rowSlots = (d < 2) ? dim : 4;
                    this.deltaOldRows[d] = new int[rowSlots][dim];
                    this.deltaOldSplitBits[d] = new BitSet(N);
                    this.deltaOldSplitBits2[d] = new BitSet(N);
                }

                int expectedMapSize = (N * 2 + 5) * 4 / 3;
                this.nodeToRow = new IdentityHashMap<>(expectedMapSize);
                this.baseSplits = new IdentityHashMap<>(expectedMapSize);
                this.currentSplits = new IdentityHashMap<>(expectedMapSize);
                this.nodeToVirt = new IdentityHashMap<>(expectedMapSize * 2);
                this.baseSigToRow = new HashMap<>((intT1Num * 4) / 3 + 1);

                this.currentT1TripletCount = new int[dim];
                this.t2IntTripletCount = new int[dim];

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

                this.allocatedDim = this.dim;
                this.allocatedN = this.N;
                sameTarget = false;
            } else {
                for (int i = 0; i < dim; i++) {
                    Arrays.fill(this.assigncost[i], 0);
                }
                Arrays.fill(this.rowsol, 0);
                Arrays.fill(this.colsol, 0);
                Arrays.fill(this.u, 0);
                Arrays.fill(this.v, 0);
                Arrays.fill(this.currentT1TripletCount, 0);
                this.nodeToRow.clear();
                this.baseSplits.clear();
                this.currentSplits.clear();
                this.nodeToVirt.clear();
                this.baseSigToRow.clear();
            }

            int maxNodesT2 = getSafeMaxNodeId(this.targetTree);
            if (!sameTarget) {
                if (this.targetIdToCol == null || this.targetIdToCol.length < maxNodesT2) {
                    this.targetIdToCol = new int[maxNodesT2];
                }
                Arrays.fill(this.targetIdToCol, 0, maxNodesT2, -1);
                for (int i = 0; i < intT2Num; i++) {
                    this.targetIdToCol[this.targetTree.getInternalNode(i).getNumber()] = i;
                }

                Node[] postOrder = TreeCmpUtils.getNodesInPostOrder(this.targetTree);
                this.postOrderT2Count = postOrder.length;

                if (this.t2PostOrderChild0 == null || this.t2PostOrderChild0.length < postOrderT2Count) {
                    this.t2PostOrderChild0 = new int[postOrderT2Count];
                    this.t2PostOrderChild1 = new int[postOrderT2Count];
                    this.t2PostOrderChild2 = new int[postOrderT2Count];
                    this.t2PostOrderLeafId = new int[postOrderT2Count];
                    this.t2PostOrderIsRoot = new boolean[postOrderT2Count];
                    this.t2PostOrderCol = new int[postOrderT2Count];

                    this.t2CountA = new int[postOrderT2Count];
                    this.t2CountB = new int[postOrderT2Count];
                    this.t2CountC = new int[postOrderT2Count];
                }

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
                Arrays.fill(this.t2IntTripletCount, 0);
                for (int i = 0; i < intT2Num; i++) {
                    this.t2IntTripletCount[i] = countTriplets(this.targetTree.getInternalNode(i), cSize2);
                }

                this.targetLcaMatrix = TreeCmpUtils.calcLcaMatrix(this.targetTree, this.baseIdGroup);
            }

            for (int i = 0; i < intT1Num; i++) {
                Node nBase = this.baseTree.getInternalNode(i);
                nodeToRow.put(nBase, i);
            }

            extractSplitsPostOrder(this.baseTree.getRoot());

            if (this.baseTree != baseTree) {
                mapOrigToBaseRecursive(this.originalBaseTree.getRoot(), this.baseTree.getRoot());
            }

            // Inicjalizacja macierzy kosztów w czasie O(N^2) bez alokacji tablic w pętli
            for (int r = 0; r < intT1Num; r++) {
                Node nBase = this.baseTree.getInternalNode(r);
                computeNodeRowCostInPlace(r, nBase);
            }

            for (int r = intT1Num; r < dim; r++) {
                int[] costRow = assigncost[r];
                for (int c = 0; c < dim; c++) {
                    costRow[c] = (c < intT2Num) ? t2IntTripletCount[c] : 0;
                }
            }

            for (int i = 0; i < dim; i++) {
                System.arraycopy(assigncost[i], 0, lapCostScratch[i], 0, dim);
            }

            int rawMetric = LapSolver.lap(dim, lapCostScratch, rowsol, colsol, this.u, this.v);
            this.currentDistance = 0.5 * rawMetric;

        } else {
            this.currentDistance = 0;
        }
    }

    private void mapOrigToBaseRecursive(Node nOrig, Node nCopy) {
        if (nOrig == null || nCopy == null) return;
        Integer row = nodeToRow.get(nCopy);
        if (row != null) {
            nodeToRow.put(nOrig, row);
        }
        BitSet bs = baseSplits.get(nCopy);
        if (bs != null) {
            baseSplits.put(nOrig, bs);
            currentSplits.put(nOrig, (BitSet) bs.clone());
        }
        int chCount = Math.min(nOrig.getChildCount(), nCopy.getChildCount());
        for (int i = 0; i < chCount; i++) {
            mapOrigToBaseRecursive(nOrig.getChild(i), nCopy.getChild(i));
        }
    }

    private void mapTreeNodesRecursive(Node src, Node dst) {
        if (src == null || dst == null) return;
        this.nodeToVirt.put(src, dst);
        this.nodeToVirt.put(dst, dst);
        int chCount = Math.min(src.getChildCount(), dst.getChildCount());
        for (int i = 0; i < chCount; i++) {
            mapTreeNodesRecursive(src.getChild(i), dst.getChild(i));
        }
    }

    private void ensureBaseSigToRowInitialized() {
        if (!baseSigToRowInitialized && this.baseTree != null) {
            this.baseSigToRow.clear();
            for (int r = 0; r < intT1Num; r++) {
                Node n = this.baseTree.getInternalNode(r);
                this.baseSigToRow.put(new Signature(n, N, baseIdGroup), r);
            }
            baseSigToRowInitialized = true;
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
        currentSplits.put(node, (BitSet) bs.clone());
        return bs;
    }

    private void ensureVirtualTreeInitialized() {
        if (!virtualTreeInitialized && this.baseTree != null) {
            this.currentVirtualTree = TreeNeighborhoodUtils.fastTreeClone(this.baseTree);
            this.nodeToVirt.clear();
            mapTreeNodesRecursive(this.baseTree.getRoot(), this.currentVirtualTree.getRoot());
            if (this.originalBaseTree != null && this.originalBaseTree != this.baseTree) {
                mapTreeNodesRecursive(this.originalBaseTree.getRoot(), this.currentVirtualTree.getRoot());
            }
            for (int i = 0; i < intT1Num; i++) {
                Node nBase = this.baseTree.getInternalNode(i);
                Node nVirt = this.nodeToVirt.get(nBase);
                if (nVirt == null) {
                    nVirt = this.currentVirtualTree.getInternalNode(i);
                }
                Integer row = nodeToRow.get(nBase);
                if (row != null) {
                    nodeToRow.put(nVirt, row);
                }
            }
            extractVirtualSplitsPostOrder(this.currentVirtualTree.getRoot());
            virtualTreeInitialized = true;
        }
    }

    private Node getVirtualNode(Node srcNode) {
        if (srcNode == null) return null;
        Node virt = this.nodeToVirt.get(srcNode);
        if (virt != null) return virt;
        virt = getMappedNode(this.currentVirtualTree, srcNode);
        if (virt != null) {
            this.nodeToVirt.put(srcNode, virt);
        }
        return virt;
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

    private BitSet getTbrScratchBitSet() {
        if (tbrPoolIdx >= tbrBitSetPool.length) {
            int newCap = tbrBitSetPool.length * 2;
            BitSet[] newPool = new BitSet[newCap];
            System.arraycopy(tbrBitSetPool, 0, newPool, 0, tbrBitSetPool.length);
            for (int i = tbrBitSetPool.length; i < newCap; i++) {
                newPool[i] = new BitSet(N);
            }
            tbrBitSetPool = newPool;
        }
        BitSet bs = tbrBitSetPool[tbrPoolIdx++];
        bs.clear();
        return bs;
    }

    private void ensureDeltaCapacity(int requiredRows) {
        if (deltaPointer >= deltaMaxDepth) {
            int newDepth = deltaMaxDepth * 2;
            deltaRowsCount = Arrays.copyOf(deltaRowsCount, newDepth);
            deltaRows = Arrays.copyOf(deltaRows, newDepth);
            deltaOldRows = Arrays.copyOf(deltaOldRows, newDepth);
            deltaOldTripletCounts = Arrays.copyOf(deltaOldTripletCounts, newDepth);
            deltaOldU = Arrays.copyOf(deltaOldU, newDepth);
            deltaOldV = Arrays.copyOf(deltaOldV, newDepth);
            deltaOldRowsol = Arrays.copyOf(deltaOldRowsol, newDepth);
            deltaOldColsol = Arrays.copyOf(deltaOldColsol, newDepth);
            deltaOldDistance = Arrays.copyOf(deltaOldDistance, newDepth);
            deltaOldSplitNode = Arrays.copyOf(deltaOldSplitNode, newDepth);
            deltaOldSplitBits = Arrays.copyOf(deltaOldSplitBits, newDepth);
            deltaOldSplitNode2 = Arrays.copyOf(deltaOldSplitNode2, newDepth);
            deltaOldSplitBits2 = Arrays.copyOf(deltaOldSplitBits2, newDepth);
            deltaNniMoving = Arrays.copyOf(deltaNniMoving, newDepth);
            deltaNniPartner = Arrays.copyOf(deltaNniPartner, newDepth);
            deltaNniP1 = Arrays.copyOf(deltaNniP1, newDepth);
            deltaNniP2 = Arrays.copyOf(deltaNniP2, newDepth);
            deltaNniIdx1 = Arrays.copyOf(deltaNniIdx1, newDepth);
            deltaNniIdx2 = Arrays.copyOf(deltaNniIdx2, newDepth);

            for (int d = deltaMaxDepth; d < newDepth; d++) {
                deltaRows[d] = new int[dim];
                deltaOldRows[d] = new int[4][dim];
                deltaOldTripletCounts[d] = new int[dim];
                deltaOldU[d] = new int[dim];
                deltaOldV[d] = new int[dim];
                deltaOldRowsol[d] = new int[dim];
                deltaOldColsol[d] = new int[dim];
                deltaOldSplitBits[d] = new BitSet(N);
                deltaOldSplitBits2[d] = new BitSet(N);
            }
            deltaMaxDepth = newDepth;
        }
        if (deltaOldRows[deltaPointer].length < requiredRows) {
            deltaOldRows[deltaPointer] = new int[Math.max(requiredRows, dim)][dim];
        }
        deltaOldSplitNode[deltaPointer] = null;
        deltaOldSplitNode2[deltaPointer] = null;
        deltaNniMoving[deltaPointer] = null;
    }

    private void pushEmptyDelta() {
        ensureDeltaCapacity(0);
        int d = deltaPointer;
        deltaRowsCount[d] = 0;
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;
    }

    private void updateSingleRowFast(int r, BitSet[] sets) {
        ensureDeltaCapacity(1);
        int d = deltaPointer;
        deltaRowsCount[d] = 1;
        deltaRows[d][0] = r;
        System.arraycopy(assigncost[r], 0, deltaOldRows[d][0], 0, dim);
        deltaOldTripletCounts[d][0] = currentT1TripletCount[r];

        System.arraycopy(u, 0, deltaOldU[d], 0, dim);
        System.arraycopy(v, 0, deltaOldV[d], 0, dim);
        System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
        System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;

        computeRowCostFast(r, sets);

        int[] changedRowsParam = cachedKArrays[1];
        changedRowsParam[0] = r;
        int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
        this.currentDistance = 0.5 * rawMetric;
    }

    private void updateTwoRowsFast(int r1, BitSet[] s1, int r2, BitSet[] s2) {
        ensureDeltaCapacity(2);
        int d = deltaPointer;
        deltaRowsCount[d] = 2;
        deltaRows[d][0] = r1;
        deltaRows[d][1] = r2;
        System.arraycopy(assigncost[r1], 0, deltaOldRows[d][0], 0, dim);
        System.arraycopy(assigncost[r2], 0, deltaOldRows[d][1], 0, dim);
        deltaOldTripletCounts[d][0] = currentT1TripletCount[r1];
        deltaOldTripletCounts[d][1] = currentT1TripletCount[r2];

        System.arraycopy(u, 0, deltaOldU[d], 0, dim);
        System.arraycopy(v, 0, deltaOldV[d], 0, dim);
        System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
        System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;

        computeRowCostFast(r1, s1);
        computeRowCostFast(r2, s2);

        int[] changedRowsParam = cachedKArrays[2];
        changedRowsParam[0] = r1;
        changedRowsParam[1] = r2;
        int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
        this.currentDistance = 0.5 * rawMetric;
    }

    private void updateRowsSafelyAndSave(Map<Integer, BitSet[]> rowUpdates) {
        int count = (rowUpdates != null) ? rowUpdates.size() : 0;
        if (count == 0) {
            pushEmptyDelta();
            return;
        }

        ensureDeltaCapacity(count);
        int d = deltaPointer;
        deltaRowsCount[d] = count;

        int idx = 0;
        for (Map.Entry<Integer, BitSet[]> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            deltaRows[d][idx] = r;
            System.arraycopy(assigncost[r], 0, deltaOldRows[d][idx], 0, dim);
            deltaOldTripletCounts[d][idx] = currentT1TripletCount[r];
            idx++;
        }

        System.arraycopy(u, 0, deltaOldU[d], 0, dim);
        System.arraycopy(v, 0, deltaOldV[d], 0, dim);
        System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
        System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;

        for (Map.Entry<Integer, BitSet[]> entry : rowUpdates.entrySet()) {
            computeRowCostFast(entry.getKey(), entry.getValue());
        }

        int[] changedRowsParam = (count < cachedKArrays.length) ? cachedKArrays[count] : new int[count];
        System.arraycopy(deltaRows[d], 0, changedRowsParam, 0, count);
        int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
        this.currentDistance = 0.5 * rawMetric;
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
                currentT1TripletCount[r] = deltaOldTripletCounts[d][i];
            }
            System.arraycopy(deltaOldU[d], 0, u, 0, dim);
            System.arraycopy(deltaOldV[d], 0, v, 0, dim);
            System.arraycopy(deltaOldRowsol[d], 0, rowsol, 0, dim);
            System.arraycopy(deltaOldColsol[d], 0, colsol, 0, dim);
        }

        if (deltaNniMoving[d] != null) {
            Node p1 = deltaNniP1[d];
            Node p2 = deltaNniP2[d];
            Node moving = deltaNniMoving[d];
            Node partner = deltaNniPartner[d];
            p1.setChild(deltaNniIdx1[d], moving);
            moving.setParent(p1);
            p2.setChild(deltaNniIdx2[d], partner);
            partner.setParent(p2);
            deltaNniMoving[d] = null;
            deltaNniPartner[d] = null;
            deltaNniP1[d] = null;
            deltaNniP2[d] = null;
        }

        if (deltaOldSplitNode[d] != null) {
            Node vNode = deltaOldSplitNode[d];
            BitSet bs = currentSplits.get(vNode);
            if (bs == null) {
                bs = new BitSet(N);
                currentSplits.put(vNode, bs);
            }
            bs.clear();
            bs.or(deltaOldSplitBits[d]);
            deltaOldSplitNode[d] = null;
        }

        if (deltaOldSplitNode2[d] != null) {
            Node uNode = deltaOldSplitNode2[d];
            BitSet bs2 = currentSplits.get(uNode);
            if (bs2 == null) {
                bs2 = new BitSet(N);
                currentSplits.put(uNode, bs2);
            }
            bs2.clear();
            bs2.or(deltaOldSplitBits2[d]);
            deltaOldSplitNode2[d] = null;
        }

        this.currentDistance = deltaOldDistance[d];
    }

    private Integer getRowForNode(Node n) {
        if (n == null || n.isLeaf()) return null;
        Integer row = nodeToRow.get(n);
        if (row != null) return row;
        Node mapped = (virtualTreeInitialized) ? nodeToVirt.get(n) : null;
        if (mapped == null) mapped = getMappedNode(this.currentVirtualTree, n);
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
        Node mapped = (virtualTreeInitialized) ? nodeToVirt.get(n) : null;
        if (mapped == null) mapped = getMappedNode(this.baseTree, n);
        if (mapped != null) {
            bs = currentSplits.get(mapped);
            if (bs != null) {
                currentSplits.put(n, bs);
                return bs;
            }
        }
        return getLeaves(n, baseIdGroup);
    }

    // =========================================================================
    // AKCELERATOR KOSZTU WIERSZA O(N) POPRZEZ DRZEWNE DP I PERMANENT 3x3
    // =========================================================================

    private void computeNodeRowCostInPlace(int row, Node n) {
        if (activePruneNode != null && n == activePruneNode.getParent()) {
            computeRowCostFast(row, null);
            return;
        }

        int chCount = n.getChildCount();
        int numNeighbors = (n.getParent() == null) ? chCount : chCount + 1;

        if (numNeighbors == 3) {
            tbrScratchUnion.clear();
            for (int i = 0; i < chCount; i++) {
                Node child = n.getChild(i);
                BitSet s = fastTripletSets1[i];
                s.clear();
                if (activePruneNode == null || child != activePruneNode) {
                    s.or(getSplitForNode(child));
                    tbrScratchUnion.or(s);
                }
            }
            if (n.getParent() != null) {
                BitSet pSet = fastTripletSets1[chCount];
                pSet.clear();
                pSet.set(0, N);
                pSet.andNot(tbrScratchUnion);
            }
            computeRowCostFast(row, fastTripletSets1);
        } else {
            computeRowCostFast(row, getPartitionsForNode(n));
        }
    }

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
    // KONTRAKT RootedTbrMetric DLA 2D-DFS uTBR (ZERO-ALLOCATION)
    // =========================================================================

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        tbrPoolIdx = 0;
        BitSet P = getTbrScratchBitSet();
        P.or(getSplitForNode(pruneNode));
        this.currentPrunedLeaves = P;

        Map<Integer, BitSet[]> updates = new HashMap<>();

        Node curr = (pruneNode.getParent() != null) ? pruneNode.getParent().getParent() : null;
        while (curr != null) {
            Integer r = getRowForNode(curr);
            if (r != null) {
                int chCount = curr.getChildCount();
                int numNeighbors = (curr.getParent() == null) ? chCount : chCount + 1;
                BitSet[] cSets = new BitSet[numNeighbors];
                tbrScratchUnion.clear();

                for (int i = 0; i < chCount; i++) {
                    BitSet b = getTbrScratchBitSet();
                    b.or(getSplitForNode(curr.getChild(i)));
                    b.andNot(P);
                    cSets[i] = b;
                    tbrScratchUnion.or(b);
                }
                if (curr.getParent() != null) {
                    BitSet pSet = getTbrScratchBitSet();
                    pSet.set(0, N);
                    pSet.andNot(tbrScratchUnion);
                    cSets[chCount] = pSet;
                }
                updates.put(r, cSets);
            }
            curr = curr.getParent();
        }

        Integer rFloating = getRowForNode(pruneNode.getParent());
        if (rFloating != null) {
            updates.put(rFloating, new BitSet[0]);
        }

        updateRowsSafelyAndSave(updates);
    }

    public void setTargetRoot(Node pruneNode, Node wanderingSource) {
        BitSet P = (this.currentPrunedLeaves != null) ? this.currentPrunedLeaves : getSplitForNode(pruneNode);
        Integer rFloating = getRowForNode(pruneNode.getParent());

        if (rFloating != null) {
            Node root = baseTree.getRoot();
            BitSet s0 = fastTripletSets1[0];
            s0.clear();
            s0.or(P);

            BitSet s1 = fastTripletSets1[1];
            s1.clear();
            for (int i = 0; i < root.getChildCount(); i++) {
                s1.clear();
                s1.or(getSplitForNode(root.getChild(i)));
                s1.andNot(P);
                if (!s1.isEmpty()) break;
            }

            BitSet s2 = fastTripletSets1[2];
            s2.clear();
            s2.set(0, N);
            s2.andNot(P);
            s2.andNot(s1);

            updateSingleRowFast(rFloating, fastTripletSets1);
        } else {
            pushEmptyDelta();
        }
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {
        setTargetRoot(pruneNode, wanderingSource);
    }

    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        BitSet P = (this.currentPrunedLeaves != null) ? this.currentPrunedLeaves : getSplitForNode(pruneNode);

        Integer rFloating = getRowForNode(pruneNode.getParent());
        BitSet[] setsFloating = null;
        if (rFloating != null) {
            BitSet s0 = fastTripletSets1[0];
            s0.clear();
            s0.or(P);

            BitSet s1 = fastTripletSets1[1];
            s1.clear();
            s1.or(getSplitForNode(childTarget));
            s1.andNot(P);

            BitSet s2 = fastTripletSets1[2];
            s2.clear();
            s2.set(0, N);
            s2.andNot(P);
            s2.andNot(s1);

            setsFloating = fastTripletSets1;
        }

        Integer rParent = getRowForNode(parentTarget);
        BitSet[] setsParent = null;
        if (rParent != null && !rParent.equals(rFloating)) {
            int chCount = parentTarget.getChildCount();
            int numNeighbors = (parentTarget.getParent() == null) ? chCount : chCount + 1;
            setsParent = (numNeighbors == 3) ? fastTripletSets2 : new BitSet[numNeighbors];

            BitSet targetLeaves = getSplitForNode(childTarget);
            tbrScratchUnion.clear();

            for (int i = 0; i < chCount; i++) {
                BitSet b = (numNeighbors == 3) ? setsParent[i] : (setsParent[i] = getTbrScratchBitSet());
                b.clear();
                b.or(getSplitForNode(parentTarget.getChild(i)));
                b.andNot(P);
                if (b.intersects(targetLeaves)) {
                    b.or(P);
                }
                tbrScratchUnion.or(b);
            }

            if (parentTarget.getParent() != null) {
                BitSet pSet = (numNeighbors == 3) ? setsParent[chCount] : (setsParent[chCount] = getTbrScratchBitSet());
                pSet.clear();
                pSet.set(0, N);
                pSet.andNot(tbrScratchUnion);
            }
        }

        if (rFloating != null && rParent != null && !rFloating.equals(rParent) && setsParent != null) {
            updateTwoRowsFast(rFloating, setsFloating, rParent, setsParent);
        } else if (rFloating != null && setsFloating != null) {
            updateSingleRowFast(rFloating, setsFloating);
        } else if (rParent != null && setsParent != null) {
            updateSingleRowFast(rParent, setsParent);
        } else {
            pushEmptyDelta();
        }
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
    public void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode) {
        if (parentReroot == pruneNode || currentPrunedLeaves == null) {
            pushEmptyDelta();
            return;
        }

        Integer rPrune = getRowForNode(pruneNode);
        Integer rParent = getRowForNode(parentReroot);

        BitSet[] setsPrune = null;
        if (rPrune != null) {
            BitSet s0 = fastTripletSets1[0];
            s0.clear();
            s0.or(getSplitForNode(childReroot));

            BitSet s1 = fastTripletSets1[1];
            s1.clear();
            s1.or(currentPrunedLeaves);
            s1.andNot(s0);

            BitSet s2 = fastTripletSets1[2];
            s2.clear();
            s2.set(0, N);
            s2.andNot(currentPrunedLeaves);

            setsPrune = fastTripletSets1;
        }

        BitSet[] setsParent = null;
        if (rParent != null && !rParent.equals(rPrune)) {
            int chCount = parentReroot.getChildCount();
            int numNeighbors = chCount + 1;
            setsParent = (numNeighbors == 3) ? fastTripletSets2 : new BitSet[numNeighbors];

            for (int i = 0; i < chCount; i++) {
                Node ch = parentReroot.getChild(i);
                BitSet b = (numNeighbors == 3) ? setsParent[i] : (setsParent[i] = getTbrScratchBitSet());
                b.clear();
                b.or(getSplitForNode(ch));
                if (ch == childReroot) {
                    if (setsPrune != null) {
                        b.or(setsPrune[2]);
                    } else {
                        tbrScratchUnion.clear();
                        tbrScratchUnion.set(0, N);
                        tbrScratchUnion.andNot(currentPrunedLeaves);
                        b.or(tbrScratchUnion);
                    }
                }
            }

            BitSet pSet = (numNeighbors == 3) ? setsParent[chCount] : (setsParent[chCount] = getTbrScratchBitSet());
            pSet.clear();
            pSet.or(currentPrunedLeaves);
            pSet.andNot(getSplitForNode(parentReroot));
        }

        if (rPrune != null && rParent != null && !rPrune.equals(rParent) && setsParent != null) {
            updateTwoRowsFast(rPrune, setsPrune, rParent, setsParent);
        } else if (rPrune != null && setsPrune != null) {
            updateSingleRowFast(rPrune, setsPrune);
        } else if (rParent != null && setsParent != null) {
            updateSingleRowFast(rParent, setsParent);
        } else {
            pushEmptyDelta();
        }
    }

    @Override
    public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {
        undoDeltaStack();
    }

    @Override
    public void revertPrunedState(Node pruneNode, Node wanderingSource) {
        undoDeltaStack();
        undoDeltaStack();
        this.currentPrunedLeaves = null;
        this.tbrPoolIdx = 0;
    }

    // =========================================================================
    // SZYBKA EWALUACJA DRZEWA SĄSIEDNIEGO (FALLBACK / SPR)
    // =========================================================================

    private double evaluateTempTreeDistance(Tree tempTree) {
        ensureBaseSigToRowInitialized();
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

        System.arraycopy(u, 0, scratchSavedU, 0, dim);
        System.arraycopy(v, 0, scratchSavedV, 0, dim);
        System.arraycopy(rowsol, 0, scratchSavedRowsol, 0, dim);
        System.arraycopy(colsol, 0, scratchSavedColsol, 0, dim);

        for (int i = 0; i < k; i++) {
            int r = scratchChangedRows[i];
            System.arraycopy(assigncost[r], 0, scratchSavedRows[i], 0, dim);
            scratchSavedTripletCounts[i] = currentT1TripletCount[r];
            computeRowCostFast(r, scratchNewSignatures[i].canonicalParts);
        }

        int[] changedRowsParam = (k < cachedKArrays.length) ? cachedKArrays[k] : new int[k];
        System.arraycopy(scratchChangedRows, 0, changedRowsParam, 0, k);

        int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
        double dist = 0.5 * rawMetric;

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
            if (tempTree != null) {
                TreeCmpUtils.unrootTreeIfNeeded(tempTree);
            }
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
    // SPR & NNI (100% ZERO-ALLOCATION NA STOSIE DELTA!)
    // =========================================================================

    public boolean applyNniStep(Node nodeToUpdate, BitSet bitsOut, BitSet bitsIn) {
        Node vNode = nodeToUpdate;
        Node uNode = vNode.getParent();

        Integer rVIndex = getRowForNode(vNode);
        Integer rUIndex = getRowForNode(uNode);

        if (rVIndex == null && rUIndex == null) return false;

        int count = 0;
        if (rVIndex != null) count++;
        if (rUIndex != null && !rUIndex.equals(rVIndex)) count++;

        ensureDeltaCapacity(count);
        int d = deltaPointer;
        deltaRowsCount[d] = count;

        int idx = 0;
        if (rVIndex != null) {
            deltaRows[d][idx] = rVIndex;
            System.arraycopy(assigncost[rVIndex], 0, deltaOldRows[d][idx], 0, dim);
            deltaOldTripletCounts[d][idx] = currentT1TripletCount[rVIndex];
            idx++;
        }
        if (rUIndex != null && !rUIndex.equals(rVIndex)) {
            deltaRows[d][idx] = rUIndex;
            System.arraycopy(assigncost[rUIndex], 0, deltaOldRows[d][idx], 0, dim);
            deltaOldTripletCounts[d][idx] = currentT1TripletCount[rUIndex];
            idx++;
        }

        System.arraycopy(this.u, 0, deltaOldU[d], 0, dim);
        System.arraycopy(this.v, 0, deltaOldV[d], 0, dim);
        System.arraycopy(this.rowsol, 0, deltaOldRowsol[d], 0, dim);
        System.arraycopy(this.colsol, 0, deltaOldColsol[d], 0, dim);
        deltaOldDistance[d] = currentDistance;

        BitSet oldSplitV = getSplitForNode(vNode);
        deltaOldSplitNode[d] = vNode;
        deltaOldSplitBits[d].clear();
        if (oldSplitV != null) {
            deltaOldSplitBits[d].or(oldSplitV);
        }
        deltaPointer++;

        if (bitsOut != null) oldSplitV.andNot(bitsOut);
        if (bitsIn != null) oldSplitV.or(bitsIn);

        if (rVIndex != null) computeNodeRowCostInPlace(rVIndex, vNode);
        if (rUIndex != null && !rUIndex.equals(rVIndex)) computeNodeRowCostInPlace(rUIndex, uNode);

        if (count > 0) {
            int[] changedRowsParam = cachedKArrays[count];
            for (int i = 0; i < count; i++) {
                changedRowsParam[i] = deltaRows[d][i];
            }
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, this.u, this.v, changedRowsParam);
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

    private void refreshNodeSplitInPlace(Node n) {
        BitSet b = currentSplits.get(n);
        if (b == null) {
            b = new BitSet(N);
            currentSplits.put(n, b);
        } else {
            b.clear();
        }
        int chCount = n.getChildCount();
        for (int i = 0; i < chCount; i++) {
            b.or(getSplitForNode(n.getChild(i)));
        }
    }

    @Override
    public double applyNni(NniMove move) {
        ensureVirtualTreeInitialized();
        Node virtMoving = getVirtualNode(move.movingSubtree);
        Node virtPartner = getVirtualNode(move.swapPartner);

        if (virtMoving == null || virtPartner == null) {
            pushEmptyDelta();
            return this.currentDistance;
        }

        Node p1 = virtMoving.getParent();
        Node p2 = virtPartner.getParent();
        if (p1 == null || p2 == null || p1 == p2) {
            pushEmptyDelta();
            return this.currentDistance;
        }

        int idx1 = findChildPos(virtMoving, p1);
        int idx2 = findChildPos(virtPartner, p2);
        if (idx1 == -1 || idx2 == -1) {
            pushEmptyDelta();
            return this.currentDistance;
        }

        Integer r1 = getRowForNode(p1);
        Integer r2 = getRowForNode(p2);
        if (r1 == null && r2 == null) {
            pushEmptyDelta();
            return this.currentDistance;
        }

        int count = 0;
        if (r1 != null) count++;
        if (r2 != null && !r2.equals(r1)) count++;

        ensureDeltaCapacity(count);
        int d = deltaPointer;
        deltaRowsCount[d] = count;

        int idx = 0;
        if (r1 != null) {
            deltaRows[d][idx] = r1;
            System.arraycopy(assigncost[r1], 0, deltaOldRows[d][idx], 0, dim);
            deltaOldTripletCounts[d][idx] = currentT1TripletCount[r1];
            idx++;
        }
        if (r2 != null && !r2.equals(r1)) {
            deltaRows[d][idx] = r2;
            System.arraycopy(assigncost[r2], 0, deltaOldRows[d][idx], 0, dim);
            deltaOldTripletCounts[d][idx] = currentT1TripletCount[r2];
            idx++;
        }

        if (count > 0) {
            System.arraycopy(this.u, 0, deltaOldU[d], 0, dim);
            System.arraycopy(this.v, 0, deltaOldV[d], 0, dim);
            System.arraycopy(this.rowsol, 0, deltaOldRowsol[d], 0, dim);
            System.arraycopy(this.colsol, 0, deltaOldColsol[d], 0, dim);
        }
        deltaOldDistance[d] = currentDistance;

        BitSet oldSplitP1 = getSplitForNode(p1);
        deltaOldSplitNode[d] = p1;
        deltaOldSplitBits[d].clear();
        if (oldSplitP1 != null) {
            deltaOldSplitBits[d].or(oldSplitP1);
        }

        BitSet oldSplitP2 = getSplitForNode(p2);
        deltaOldSplitNode2[d] = p2;
        deltaOldSplitBits2[d].clear();
        if (oldSplitP2 != null) {
            deltaOldSplitBits2[d].or(oldSplitP2);
        }

        deltaNniMoving[d] = virtMoving;
        deltaNniPartner[d] = virtPartner;
        deltaNniP1[d] = p1;
        deltaNniP2[d] = p2;
        deltaNniIdx1[d] = idx1;
        deltaNniIdx2[d] = idx2;
        deltaPointer++;

        p1.setChild(idx1, virtPartner);
        virtPartner.setParent(p1);
        p2.setChild(idx2, virtMoving);
        virtMoving.setParent(p2);

        if (p2.getParent() == p1) {
            refreshNodeSplitInPlace(p2);
            refreshNodeSplitInPlace(p1);
        } else {
            refreshNodeSplitInPlace(p1);
            refreshNodeSplitInPlace(p2);
        }

        if (r1 != null) {
            computeNodeRowCostInPlace(r1, p1);
        }
        if (r2 != null && !r2.equals(r1)) {
            computeNodeRowCostInPlace(r2, p2);
        }

        if (count > 0) {
            int[] changedRows = cachedKArrays[count];
            for (int i = 0; i < count; i++) {
                changedRows[i] = deltaRows[d][i];
            }
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, changedRows);
            this.currentDistance = 0.5 * rawMetric;
        }

        return this.currentDistance;
    }

    @Override
    public void undoNni(NniMove move) {
        undoDeltaStack();
    }

    // =========================================================================
    // BEZALOKACYJNA IMPLEMENTACJA 2s-ECR ORAZ 3s-ECR NA STOSIE DELTA!
    // =========================================================================

    private void fillTripletSets(BitSet[] dest3, BitSet left, BitSet right) {
        dest3[0].clear();
        dest3[0].or(left);

        dest3[1].clear();
        dest3[1].or(right);

        dest3[2].clear();
        dest3[2].set(0, N);
        dest3[2].andNot(left);
        dest3[2].andNot(right);
    }

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
            ecrBBits[i] = getSplitForNode(b[i]);
        }

        BitSet b0 = ecrBBits[template.indices[0]];
        BitSet b1 = ecrBBits[template.indices[1]];
        BitSet b2 = ecrBBits[template.indices[2]];
        BitSet b3 = ecrBBits[template.indices[3]];

        if (template.isFork) {
            fillTripletSets(ecrTripletSets[0], b0, b1);
            ecrNodes[0] = m1;
            Integer r1 = getRowForNode(m1);
            ecrRows[0] = (r1 != null) ? r1 : -1;

            fillTripletSets(ecrTripletSets[1], b2, b3);
            ecrNodes[1] = m2;
            Integer r2 = getRowForNode(m2);
            ecrRows[1] = (r2 != null) ? r2 : -1;

            tbrScratchUnion.clear();
            tbrScratchUnion.or(b0);
            tbrScratchUnion.or(b1);

            scratchSetA.clear();
            scratchSetA.or(b2);
            scratchSetA.or(b3);

            fillTripletSets(ecrTripletSets[2], tbrScratchUnion, scratchSetA);
            ecrNodes[2] = top;
            Integer rTop = getRowForNode(top);
            ecrRows[2] = (rTop != null) ? rTop : -1;
        } else {
            fillTripletSets(ecrTripletSets[1], b2, b3);
            ecrNodes[1] = m2;
            Integer r2 = getRowForNode(m2);
            ecrRows[1] = (r2 != null) ? r2 : -1;

            tbrScratchUnion.clear();
            tbrScratchUnion.or(b2);
            tbrScratchUnion.or(b3);
            fillTripletSets(ecrTripletSets[0], b1, tbrScratchUnion);
            ecrNodes[0] = m1;
            Integer r1 = getRowForNode(m1);
            ecrRows[0] = (r1 != null) ? r1 : -1;

            scratchSetA.clear();
            scratchSetA.or(b1);
            scratchSetA.or(tbrScratchUnion);

            fillTripletSets(ecrTripletSets[2], b0, scratchSetA);
            ecrNodes[2] = top;
            Integer rTop = getRowForNode(top);
            ecrRows[2] = (rTop != null) ? rTop : -1;
        }

        int updateCount = 3;
        int validRows = 0;
        for (int i = 0; i < updateCount; i++) {
            if (ecrRows[i] >= 0) validRows++;
        }

        ensureDeltaCapacity(validRows);
        int d = deltaPointer;
        deltaRowsCount[d] = validRows;

        int idx = 0;
        for (int i = 0; i < updateCount; i++) {
            int r = ecrRows[i];
            if (r >= 0) {
                deltaRows[d][idx] = r;
                System.arraycopy(assigncost[r], 0, deltaOldRows[d][idx], 0, dim);
                deltaOldTripletCounts[d][idx] = currentT1TripletCount[r];
                idx++;
            }
        }

        if (validRows > 0) {
            System.arraycopy(u, 0, deltaOldU[d], 0, dim);
            System.arraycopy(v, 0, deltaOldV[d], 0, dim);
            System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
            System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        }
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;

        for (int i = 0; i < updateCount; i++) {
            int r = ecrRows[i];
            if (r >= 0) {
                computeRowCostFast(r, ecrTripletSets[i]);
            }
            if (updateSplits && i < 2 && ecrNodes[i] != null) {
                BitSet bs = currentSplits.get(ecrNodes[i]);
                if (bs != null) {
                    bs.clear();
                    bs.or(ecrTripletSets[i][0]);
                    bs.or(ecrTripletSets[i][1]);
                }
            }
        }

        if (validRows > 0) {
            int[] changedRowsParam = cachedKArrays[validRows];
            System.arraycopy(deltaRows[d], 0, changedRowsParam, 0, validRows);
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
            this.currentDistance = 0.5 * rawMetric;
        }

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
            ecrBBits[i] = getSplitForNode(b[i]);
        }

        ecrNextAvailIdx = 1;
        ecrUpdateCount = 0;

        Node top = cluster.get(0);
        compute3sEcrTripletsFast(template, 0, cluster);

        int validRows = 0;
        for (int i = 0; i < ecrUpdateCount; i++) {
            if (ecrRows[i] >= 0) validRows++;
        }

        ensureDeltaCapacity(validRows);
        int d = deltaPointer;
        deltaRowsCount[d] = validRows;

        int idx = 0;
        for (int i = 0; i < ecrUpdateCount; i++) {
            int r = ecrRows[i];
            if (r >= 0) {
                deltaRows[d][idx] = r;
                System.arraycopy(assigncost[r], 0, deltaOldRows[d][idx], 0, dim);
                deltaOldTripletCounts[d][idx] = currentT1TripletCount[r];
                idx++;
            }
        }

        if (validRows > 0) {
            System.arraycopy(u, 0, deltaOldU[d], 0, dim);
            System.arraycopy(v, 0, deltaOldV[d], 0, dim);
            System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
            System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        }
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;

        for (int i = 0; i < ecrUpdateCount; i++) {
            int r = ecrRows[i];
            if (r >= 0) {
                computeRowCostFast(r, ecrTripletSets[i]);
            }
            if (updateSplits && ecrNodes[i] != top && ecrNodes[i] != null) {
                BitSet bs = currentSplits.get(ecrNodes[i]);
                if (bs != null) {
                    bs.clear();
                    bs.or(ecrUnionSets[i]);
                }
            }
        }

        if (validRows > 0) {
            int[] changedRowsParam = cachedKArrays[validRows];
            System.arraycopy(deltaRows[d], 0, changedRowsParam, 0, validRows);
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
            this.currentDistance = 0.5 * rawMetric;
        }

        return this.currentDistance;
    }

    private BitSet compute3sEcrTripletsFast(SubtreeEcr3Utils.TopologyTemplate3sECR temp, int clusterIdx, List<Node> cluster) {
        Node currNode = cluster.get(clusterIdx);
        BitSet leftBits;
        if (temp.left.leafIndex != -1) {
            leftBits = ecrBBits[temp.left.leafIndex];
        } else {
            int cSize = cluster.size();
            int nextIdx = (ecrNextAvailIdx < cSize) ? ecrNextAvailIdx++ : cSize - 1;
            leftBits = compute3sEcrTripletsFast(temp.left, nextIdx, cluster);
        }

        BitSet rightBits;
        if (temp.right.leafIndex != -1) {
            rightBits = ecrBBits[temp.right.leafIndex];
        } else {
            int cSize = cluster.size();
            int nextIdx = (ecrNextAvailIdx < cSize) ? ecrNextAvailIdx++ : cSize - 1;
            rightBits = compute3sEcrTripletsFast(temp.right, nextIdx, cluster);
        }

        int slot = ecrUpdateCount++;
        ecrNodes[slot] = currNode;
        Integer r = getRowForNode(currNode);
        ecrRows[slot] = (r != null) ? r : -1;
        fillTripletSets(ecrTripletSets[slot], leftBits, rightBits);

        BitSet union = ecrUnionSets[slot];
        union.clear();
        union.or(leftBits);
        union.or(rightBits);
        return union;
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
        if (srcNode == null || destTree == null) return null;
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

    public void applySprPrune(Node pruneNode) { this.activePruneNode = pruneNode; }
    public void undoSprPrune(Node pruneNode) { this.activePruneNode = null; }

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { deltaPointer = 0; }
    @Override public double getDistance(Tree t1, Tree t2, int... indexes) { return mtMetricFull.getDistance(t1, t2, indexes); }
    @Override public String getName() { return "Accelerated " + mtMetricFull.getName(); }
    @Override public String getCommandLineName() { return mtMetricFull.getCommandLineName(); }
    @Override public void setCommandLineName(String cln) { mtMetricFull.setCommandLineName(cln); }
    @Override public void setName(String name) { mpMetricSetNameFallback(name); }
    private void mpMetricSetNameFallback(String name) { mtMetricFull.setName(name); }
    @Override public String getDescription() { return mtMetricFull.getDescription(); }
    @Override public void setDescription(String d) { mtMetricFull.setDescription(d); }
    @Override public void initData() { mtMetricFull.initData(); }
    @Override public boolean isRooted() { return false; }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return mtMetricFull.isDiffLeafSets(); }
    @Override public AlignInfo getAlignment() { return mtMetricFull.getAlignment(); }
}