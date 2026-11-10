package treecmp.metrics.topological.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.AlignInfo;
import treecmp.common.LapSolver;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalMetric;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalMetric;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.nni.acc.NniIncrementalMetric;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.acc.SprIncrementalMetric;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.acc.RtbrIncrementalMetric;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingClusterMetric;

import java.util.*;

public class MCIncrementalMetric implements IncrementalMetric,
        NniIncrementalMetric,
        SprIncrementalMetric,
        RtbrIncrementalMetric,
        Ecr2IncrementalMetric,
        Ecr3IncrementalMetric {

    private Tree baseTree;
    private Tree targetTree;
    private Tree lastTargetTree;
    private double currentDistance;
    private boolean enableTriangleLowerBoundPruning = true;
    private boolean useTreeDpForRowCosts = false;
    private IdGroup idGroup;
    private int N;

    private final MatchingClusterMetric mcMetricFull = new MatchingClusterMetric();
    private final TbrUtils tbrUtils = new TbrUtils();
    private final SprUtils sprUtils = new SprUtils();

    private int dim;
    private int allocatedDim = -1;
    private int allocatedN = -1;
    private short[][] assigncost;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private Map<Node, BitSet> baseClusters;
    private Map<Node, BitSet> currentClusters;
    private Map<Node, BitSet> targetClusters;
    private Map<Node, Integer> nodeToRow;

    private Node[] rowToNode;
    private Node[] colToNode;

    // Prealokowane reprezentacje słów 64-bitowych dla błyskawicznego XOR bez alokacji
    private int numWords;
    private long[][] targetClusterWords;
    private short[] targetNullRowCosts;
    private long[] scratchWords;

    // Struktury do szybkiego przejścia O(N) po T2 (Tree DP indeksowane pozycją post-order)
    private int postOrderT2Count;
    private int[] t2PostOrderChild0;
    private int[] t2PostOrderChild1;
    private int[] t2PostOrderFirstChild;
    private int[] t2PostOrderChildCount;
    private int[] t2PostOrderChildrenFlat;
    private int[] t2PostOrderLeafId;
    private int[] t2PostOrderCol;
    private int[] t2CountA;

    private final Stack<short[][]> costHistory = new Stack<>();
    private final Stack<int[]> rowsolHistory = new Stack<>();
    private final Stack<int[]> colsolHistory = new Stack<>();
    private final Stack<int[]> uHistory = new Stack<>();
    private final Stack<int[]> vHistory = new Stack<>();
    private final Stack<Double> distanceHistory = new Stack<>();
    private final Stack<Map<Node, BitSet>> clusterHistory = new Stack<>();

    private BitSet currentPrunedLeaves;
    private BitSet fullTreeLeaves;

    // =========================================================================
    // PREALOKOWANY STOS DELTA O ZREDUKOWANEJ PAMIĘCI O(N^2) (2D-DFS TBR, NNI, ECR)
    // =========================================================================
    private int deltaMaxDepth = 0;
    private int deltaPointer = 0;
    private int[] deltaRowsCount;
    private int[][] deltaRows;
    private short[][][] deltaOldRows;
    private int[][] deltaOldU;
    private int[][] deltaOldV;
    private int[][] deltaOldRowsol;
    private int[][] deltaOldColsol;
    private double[] deltaOldDistance;
    private Node[][] deltaOldClustNode;
    private BitSet[][] deltaOldClustBits;

    private int[][] cachedKArrays;
    private BitSet tbrScratch1;
    private BitSet tbrScratch2;
    private int[] scratchPruneRows;
    private BitSet[] scratchPruneBitSets;

    // Prealokowane bufory dla bezalokacyjnego 3s-ECR
    private BitSet[] bitSetPool;
    private int poolIdx = 0;
    private final BitSet[] ecr3BBits = new BitSet[5];
    private int ecr3NextClusterIdx = 1;
    private int ecr3UpdateCount = 0;

    // Prealokowane bufory dla ciepłego startu LAP (SPR / TBR fallback & getFixedDistanceForRegraft)
    private final Map<BitSet, Integer> clusterToRow = new HashMap<>();
    private boolean clusterToRowInitialized = false;
    private short[][] scratchSavedOldRows;
    private int[] scratchSavedU;
    private int[] scratchSavedV;
    private int[] scratchSavedRowsol;
    private int[] scratchSavedColsol;
    private int[] scratchChangedRows;
    private BitSet[] tempTreeClusters;
    private BitSet[] scratchNewClusters;
    private boolean[] scratchRowUsed;

    public void setEnableTriangleLowerBoundPruning(boolean enable) {
        this.enableTriangleLowerBoundPruning = enable;
    }

    public void setUseTreeDpForRowCosts(boolean useTreeDp) {
        this.useTreeDpForRowCosts = useTreeDp;
    }

    public BitSet getSplit(Node n) {
        return getCluster(n);
    }

    private int getNodeIndex(Node node) {
        return node.isLeaf() ? node.getNumber() : (N + node.getNumber());
    }

    private BitSet getScratchBitSet() {
        if (poolIdx >= bitSetPool.length) {
            int newCap = bitSetPool.length * 2;
            BitSet[] newPool = new BitSet[newCap];
            System.arraycopy(bitSetPool, 0, newPool, 0, bitSetPool.length);
            for (int i = bitSetPool.length; i < newCap; i++) {
                newPool[i] = new BitSet(N);
            }
            bitSetPool = newPool;
        }
        BitSet bs = bitSetPool[poolIdx++];
        bs.clear();
        return bs;
    }

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        this.baseTree = baseTree;
        this.targetTree = targetTree;

        if (baseTree != null && targetTree != null) {
            clearHistory();
            this.N = baseTree.getExternalNodeCount();

            int size1 = baseTree.getInternalNodeCount() - 1;
            int size2 = targetTree.getInternalNodeCount() - 1;
            this.dim = Math.max(size1, size2);
            this.numWords = (N + 63) >>> 6;

            boolean sameTarget = (targetTree == this.lastTargetTree)
                    && (this.dim == this.allocatedDim)
                    && (this.N == this.allocatedN)
                    && (this.idGroup != null);
            if (!sameTarget) {
                this.idGroup = TreeUtils.getLeafIdGroup(baseTree);
            }

            boolean needRealloc = (this.dim != this.allocatedDim)
                    || (this.N != this.allocatedN)
                    || (this.assigncost == null);

            if (needRealloc) {
                this.assigncost = new short[dim][dim];
                this.rowsol = new int[dim];
                this.colsol = new int[dim];
                this.u = new int[dim];
                this.v = new int[dim];

                this.scratchWords = new long[numWords];
                this.targetClusterWords = new long[dim][numWords];
                this.targetNullRowCosts = new short[dim];

                int maxK = Math.max(64, dim + 1);
                this.cachedKArrays = new int[maxK][];
                for (int i = 0; i < maxK; i++) {
                    this.cachedKArrays[i] = new int[i];
                }

                this.scratchSavedOldRows = new short[dim][dim];
                this.scratchSavedU = new int[dim];
                this.scratchSavedV = new int[dim];
                this.scratchSavedRowsol = new int[dim];
                this.scratchSavedColsol = new int[dim];
                this.scratchChangedRows = new int[dim];

                int depth = Math.max(128, N * 4);
                this.deltaMaxDepth = depth;
                this.deltaRowsCount = new int[depth];
                this.deltaRows = new int[depth][dim];
                this.deltaOldRows = new short[depth][][];
                this.deltaOldU = new int[depth][dim];
                this.deltaOldV = new int[depth][dim];
                this.deltaOldRowsol = new int[depth][dim];
                this.deltaOldColsol = new int[depth][dim];
                this.deltaOldDistance = new double[depth];
                this.deltaOldClustNode = new Node[depth][];
                this.deltaOldClustBits = new BitSet[depth][];

                int baseRowSlots = Math.max(4, dim);
                for (int d = 0; d < depth; d++) {
                    int rowSlots = (d < 2) ? baseRowSlots : 4;
                    this.deltaOldRows[d] = new short[rowSlots][dim];
                    this.deltaOldClustNode[d] = new Node[rowSlots];
                    this.deltaOldClustBits[d] = new BitSet[rowSlots];
                    for (int r = 0; r < rowSlots; r++) {
                        this.deltaOldClustBits[d][r] = new BitSet(N);
                    }
                }

                int maxPoolSize = Math.max(128, N * 4);
                this.bitSetPool = new BitSet[maxPoolSize];
                for (int i = 0; i < maxPoolSize; i++) {
                    this.bitSetPool[i] = new BitSet(N);
                }

                int maxTreeNodes = N * 2 + 16;
                this.tempTreeClusters = new BitSet[maxTreeNodes];
                for (int i = 0; i < maxTreeNodes; i++) {
                    this.tempTreeClusters[i] = new BitSet(N);
                }

                this.scratchNewClusters = new BitSet[dim + 5];
                for (int i = 0; i < scratchNewClusters.length; i++) {
                    this.scratchNewClusters[i] = new BitSet(N);
                }
                this.scratchRowUsed = new boolean[dim + 5];

                this.tbrScratch1 = new BitSet(N);
                this.tbrScratch2 = new BitSet(N);
                this.currentPrunedLeaves = new BitSet(N);
                this.fullTreeLeaves = new BitSet(N);
                this.fullTreeLeaves.set(0, N);

                this.scratchPruneRows = new int[dim];
                this.scratchPruneBitSets = new BitSet[dim];
                for (int i = 0; i < dim; i++) {
                    this.scratchPruneBitSets[i] = new BitSet(N);
                }

                int expectedMapSize = (N * 2 + 5) * 4 / 3;
                this.baseClusters = new IdentityHashMap<>(expectedMapSize);
                this.currentClusters = new IdentityHashMap<>(expectedMapSize);
                this.targetClusters = new IdentityHashMap<>(expectedMapSize);
                this.nodeToRow = new IdentityHashMap<>(expectedMapSize);
                this.rowToNode = new Node[dim];
                this.colToNode = new Node[dim];

                this.allocatedDim = this.dim;
                this.allocatedN = this.N;
                sameTarget = false;
            } else {
                for (int i = 0; i < dim; i++) {
                    Arrays.fill(this.assigncost[i], (short) 0);
                }
                Arrays.fill(this.rowsol, 0);
                Arrays.fill(this.colsol, 0);
                Arrays.fill(this.u, 0);
                Arrays.fill(this.v, 0);
                Arrays.fill(this.rowToNode, null);
                this.baseClusters.clear();
                this.currentClusters.clear();
                this.nodeToRow.clear();
            }

            this.deltaPointer = 0;
            this.poolIdx = 0;
            this.clusterToRowInitialized = false;

            extractClusters(baseTree.getRoot(), idGroup, this.baseClusters);
            for (Map.Entry<Node, BitSet> e : this.baseClusters.entrySet()) {
                this.currentClusters.put(e.getKey(), (BitSet) e.getValue().clone());
            }

            int r = 0;
            for (int i = 0; i < baseTree.getInternalNodeCount(); i++) {
                Node n = baseTree.getInternalNode(i);
                if (!n.isRoot() && r < dim) {
                    rowToNode[r] = n;
                    nodeToRow.put(n, r);
                    r++;
                }
            }

            if (!sameTarget) {
                this.targetClusters.clear();
                Arrays.fill(this.colToNode, null);
                Arrays.fill(this.targetNullRowCosts, (short) 0);
                for (int j = 0; j < dim; j++) {
                    Arrays.fill(this.targetClusterWords[j], 0L);
                }

                extractClusters(targetTree.getRoot(), idGroup, this.targetClusters);

                Map<Node, Integer> targetNodeToCol = new IdentityHashMap<>(targetTree.getInternalNodeCount() * 2);
                int c = 0;
                for (int j = 0; j < targetTree.getInternalNodeCount(); j++) {
                    Node n = targetTree.getInternalNode(j);
                    if (!n.isRoot() && c < dim) {
                        colToNode[c] = n;
                        targetNodeToCol.put(n, c);
                        c++;
                    }
                }

                for (int j = 0; j < dim; j++) {
                    Node n2 = colToNode[j];
                    if (n2 != null) {
                        BitSet bs = targetClusters.get(n2);
                        if (bs != null) {
                            fillWordsFromBitSet(bs, targetClusterWords[j]);
                            targetNullRowCosts[j] = (short) bs.cardinality();
                        }
                    }
                }

                Node[] postOrderT2 = TreeCmpUtils.getNodesInPostOrder(targetTree);
                this.postOrderT2Count = postOrderT2.length;
                if (this.t2PostOrderChild0 == null || this.t2PostOrderChild0.length < postOrderT2Count) {
                    this.t2PostOrderChild0 = new int[postOrderT2Count];
                    this.t2PostOrderChild1 = new int[postOrderT2Count];
                    this.t2PostOrderFirstChild = new int[postOrderT2Count];
                    this.t2PostOrderChildCount = new int[postOrderT2Count];
                    this.t2PostOrderChildrenFlat = new int[postOrderT2Count];
                    this.t2PostOrderLeafId = new int[postOrderT2Count];
                    this.t2PostOrderCol = new int[postOrderT2Count];
                    this.t2CountA = new int[postOrderT2Count];
                }

                Map<Node, Integer> nodeToPostOrder = new IdentityHashMap<>(postOrderT2Count * 2);
                for (int i = 0; i < postOrderT2Count; i++) {
                    nodeToPostOrder.put(postOrderT2[i], i);
                }

                int flatChildPtr = 0;
                for (int i = 0; i < postOrderT2Count; i++) {
                    Node n = postOrderT2[i];
                    if (n.isLeaf()) {
                        t2PostOrderLeafId[i] = idGroup.whichIdNumber(n.getIdentifier().getName());
                        t2PostOrderCol[i] = -1;
                        t2PostOrderChild0[i] = -1;
                        t2PostOrderChild1[i] = -1;
                        t2PostOrderFirstChild[i] = -1;
                        t2PostOrderChildCount[i] = 0;
                    } else {
                        t2PostOrderLeafId[i] = -1;
                        Integer colIdx = targetNodeToCol.get(n);
                        t2PostOrderCol[i] = (colIdx != null) ? colIdx : -1;
                        int chCount = n.getChildCount();
                        t2PostOrderChildCount[i] = chCount;
                        t2PostOrderChild0[i] = (chCount > 0) ? nodeToPostOrder.get(n.getChild(0)) : -1;
                        t2PostOrderChild1[i] = (chCount > 1) ? nodeToPostOrder.get(n.getChild(1)) : -1;
                        t2PostOrderFirstChild[i] = flatChildPtr;
                        for (int k = 0; k < chCount; k++) {
                            t2PostOrderChildrenFlat[flatChildPtr++] = nodeToPostOrder.get(n.getChild(k));
                        }
                    }
                }

                this.lastTargetTree = targetTree;
            }

            buildInitialCostMatrix();
            this.currentDistance = LapSolver.lapShort(dim, assigncost, rowsol, colsol, u, v);
        } else {
            this.currentDistance = 0;
        }
    }

    private void ensureClusterToRowInitialized() {
        if (!clusterToRowInitialized) {
            clusterToRow.clear();
            for (int i = 0; i < dim; i++) {
                Node n1 = rowToNode[i];
                if (n1 != null) {
                    BitSet bs = currentClusters.get(n1);
                    if (bs != null && !bs.isEmpty() && bs.cardinality() < N) {
                        clusterToRow.put((BitSet) bs.clone(), i);
                    }
                }
            }
            clusterToRowInitialized = true;
        }
    }

    private BitSet extractClusters(Node node, IdGroup idGroup, Map<Node, BitSet> map) {
        BitSet bs = new BitSet(N);
        if (node.isLeaf()) {
            int id = idGroup.whichIdNumber(node.getIdentifier().getName());
            if (id >= 0) bs.set(id);
            map.put(node, bs);
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                bs.or(extractClusters(node.getChild(i), idGroup, map));
            }
            map.put(node, bs);
        }
        return bs;
    }

    private void fillWordsFromBitSet(BitSet bs, long[] dest) {
        Arrays.fill(dest, 0L);
        if (bs != null && !bs.isEmpty()) {
            for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
                dest[i >>> 6] |= (1L << i);
            }
        }
    }

    private void computeRowCostsFast(int r, BitSet cluster) {
        short[] costRow = assigncost[r];
        if (cluster == null || cluster.isEmpty()) {
            System.arraycopy(targetNullRowCosts, 0, costRow, 0, dim);
            return;
        }

        if (useTreeDpForRowCosts || numWords > 8) {
            computeRowCostsTreeDp(r, cluster);
            return;
        }

        fillWordsFromBitSet(cluster, scratchWords);

        if (numWords == 1) {
            long w0 = scratchWords[0];
            short card = (short) Long.bitCount(w0);
            for (int j = 0; j < dim; j++) {
                if (colToNode[j] != null) {
                    costRow[j] = (short) Long.bitCount(w0 ^ targetClusterWords[j][0]);
                } else {
                    costRow[j] = card;
                }
            }
            return;
        }

        int card = 0;
        for (int w = 0; w < numWords; w++) {
            card += Long.bitCount(scratchWords[w]);
        }
        short shortCard = (short) card;
        for (int j = 0; j < dim; j++) {
            if (colToNode[j] != null) {
                long[] tWords = targetClusterWords[j];
                int diff = 0;
                for (int w = 0; w < numWords; w++) {
                    diff += Long.bitCount(scratchWords[w] ^ tWords[w]);
                }
                costRow[j] = (short) diff;
            } else {
                costRow[j] = shortCard;
            }
        }
    }

    /**
     * Wyliczanie wiersza macierzy kosztów w czasie O(N) za pomocą przejścia post-order Tree DP po T2.
     */
    private void computeRowCostsTreeDp(int r, BitSet cluster) {
        int card = cluster.cardinality();
        short shortCard = (short) card;
        short[] costRow = assigncost[r];

        for (int i = 0; i < postOrderT2Count; i++) {
            int leafId = t2PostOrderLeafId[i];
            if (leafId >= 0) {
                t2CountA[i] = cluster.get(leafId) ? 1 : 0;
            } else {
                int chCount = t2PostOrderChildCount[i];
                int sumA;
                if (chCount == 2) {
                    sumA = t2CountA[t2PostOrderChild0[i]] + t2CountA[t2PostOrderChild1[i]];
                } else {
                    sumA = 0;
                    int first = t2PostOrderFirstChild[i];
                    for (int k = 0; k < chCount; k++) {
                        sumA += t2CountA[t2PostOrderChildrenFlat[first + k]];
                    }
                }
                t2CountA[i] = sumA;

                int col = t2PostOrderCol[i];
                if (col >= 0) {
                    costRow[col] = (short) (card + targetNullRowCosts[col] - (sumA << 1));
                }
            }
        }

        for (int j = 0; j < dim; j++) {
            if (colToNode[j] == null) {
                costRow[j] = shortCard;
            }
        }
    }

    private void buildInitialCostMatrix() {
        for (int i = 0; i < dim; i++) {
            Node n1 = rowToNode[i];
            if (n1 != null) {
                computeRowCostsFast(i, currentClusters.get(n1));
            } else {
                System.arraycopy(targetNullRowCosts, 0, assigncost[i], 0, dim);
            }
        }
    }

    public BitSet getCluster(Node n) {
        BitSet bs = currentClusters.get(n);
        if (bs != null) return bs;
        if (n.isLeaf()) {
            BitSet leafBs = new BitSet(N);
            int id = idGroup.whichIdNumber(n.getIdentifier().getName());
            if (id >= 0) leafBs.set(id);
            currentClusters.put(n, leafBs);
            return leafBs;
        }
        return null;
    }

    public BitSet getBaseCluster(Node n) {
        BitSet bs = baseClusters.get(n);
        if (bs != null) return bs;
        return getCluster(n);
    }

    // =========================================================================
    // CIEPŁY START LAP DLA DRZEW TYMCZASOWYCH (SPR / TBR)
    // =========================================================================

    private void computeTempTreeClusters(Node node) {
        int idx = getNodeIndex(node);
        BitSet bs = tempTreeClusters[idx];
        bs.clear();
        if (node.isLeaf()) {
            int id = idGroup.whichIdNumber(node.getIdentifier().getName());
            if (id >= 0) bs.set(id);
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                Node child = node.getChild(i);
                computeTempTreeClusters(child);
                bs.or(tempTreeClusters[getNodeIndex(child)]);
            }
        }
    }

    private double evaluateTempTreeDistance(Tree tempTree) {
        if (tempTree == null) return Double.POSITIVE_INFINITY;
        if (tempTree instanceof SimpleTree) {
            TreeUtils.computeParentPointers(tempTree.getRoot());
            ((SimpleTree) tempTree).createNodeList();
        }

        if (deltaPointer != 0) {
            try {
                return mcMetricFull.getDistance(tempTree, this.targetTree);
            } catch (Exception e) {
                return Double.POSITIVE_INFINITY;
            }
        }

        ensureClusterToRowInitialized();
        computeTempTreeClusters(tempTree.getRoot());

        Arrays.fill(scratchRowUsed, 0, dim, false);
        int newClusterCount = 0;

        int intCount = tempTree.getInternalNodeCount();
        for (int i = 0; i < intCount; i++) {
            Node n = tempTree.getInternalNode(i);
            if (n.isRoot()) continue;

            BitSet bs = tempTreeClusters[getNodeIndex(n)];
            if (bs.isEmpty() || bs.cardinality() >= N) continue;

            Integer r = clusterToRow.get(bs);
            if (r != null && r < dim && !scratchRowUsed[r]) {
                scratchRowUsed[r] = true;
            } else {
                scratchNewClusters[newClusterCount].clear();
                scratchNewClusters[newClusterCount].or(bs);
                newClusterCount++;
            }
        }

        if (newClusterCount == 0) {
            return this.currentDistance;
        }

        int k = 0;
        for (int r = 0; r < dim; r++) {
            if (!scratchRowUsed[r] && rowToNode[r] != null) {
                scratchChangedRows[k++] = r;
            }
        }

        if (k != newClusterCount) {
            try {
                return mcMetricFull.getDistance(tempTree, this.targetTree);
            } catch (Exception e) {
                return Double.POSITIVE_INFINITY;
            }
        }

        System.arraycopy(u, 0, scratchSavedU, 0, dim);
        System.arraycopy(v, 0, scratchSavedV, 0, dim);
        System.arraycopy(rowsol, 0, scratchSavedRowsol, 0, dim);
        System.arraycopy(colsol, 0, scratchSavedColsol, 0, dim);

        for (int i = 0; i < k; i++) {
            int r = scratchChangedRows[i];
            System.arraycopy(assigncost[r], 0, scratchSavedOldRows[i], 0, dim);
            computeRowCostsFast(r, scratchNewClusters[i]);
        }

        int[] changedRowsParam = (k < cachedKArrays.length) ? cachedKArrays[k] : new int[k];
        System.arraycopy(scratchChangedRows, 0, changedRowsParam, 0, k);

        double newDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);

        for (int i = 0; i < k; i++) {
            System.arraycopy(scratchSavedOldRows[i], 0, assigncost[scratchChangedRows[i]], 0, dim);
        }
        System.arraycopy(scratchSavedU, 0, u, 0, dim);
        System.arraycopy(scratchSavedV, 0, v, 0, dim);
        System.arraycopy(scratchSavedRowsol, 0, rowsol, 0, dim);
        System.arraycopy(scratchSavedColsol, 0, colsol, 0, dim);

        return newDistance;
    }

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        if (pruneNode == null || rerootNode == null || targetNode == null || this.targetTree == null || this.baseTree == null) {
            return Double.POSITIVE_INFINITY;
        }
        try {
            Tree tempTree = tbrUtils.createTbrTree(this.baseTree, pruneNode, rerootNode, targetNode);
            return evaluateTempTreeDistance(tempTree);
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }
    }

    // =========================================================================
    // ZARZĄDZANIE STOSAMI DELTA
    // =========================================================================

    private void ensureDeltaCapacity(int requiredRows) {
        if (deltaPointer >= deltaMaxDepth) {
            int newDepth = deltaMaxDepth * 2;
            deltaRowsCount = Arrays.copyOf(deltaRowsCount, newDepth);
            deltaRows = Arrays.copyOf(deltaRows, newDepth);
            deltaOldRows = Arrays.copyOf(deltaOldRows, newDepth);
            deltaOldU = Arrays.copyOf(deltaOldU, newDepth);
            deltaOldV = Arrays.copyOf(deltaOldV, newDepth);
            deltaOldRowsol = Arrays.copyOf(deltaOldRowsol, newDepth);
            deltaOldColsol = Arrays.copyOf(deltaOldColsol, newDepth);
            deltaOldDistance = Arrays.copyOf(deltaOldDistance, newDepth);
            deltaOldClustNode = Arrays.copyOf(deltaOldClustNode, newDepth);
            deltaOldClustBits = Arrays.copyOf(deltaOldClustBits, newDepth);

            for (int d = deltaMaxDepth; d < newDepth; d++) {
                deltaRows[d] = new int[dim];
                deltaOldRows[d] = new short[4][dim];
                deltaOldU[d] = new int[dim];
                deltaOldV[d] = new int[dim];
                deltaOldRowsol[d] = new int[dim];
                deltaOldColsol[d] = new int[dim];
                deltaOldClustNode[d] = new Node[4];
                deltaOldClustBits[d] = new BitSet[4];
                for (int r = 0; r < 4; r++) {
                    deltaOldClustBits[d][r] = new BitSet(N);
                }
            }
            deltaMaxDepth = newDepth;
        }

        if (deltaOldRows[deltaPointer].length < requiredRows) {
            int newSlots = Math.max(requiredRows, dim);
            deltaOldRows[deltaPointer] = new short[newSlots][dim];
            deltaOldClustNode[deltaPointer] = new Node[newSlots];
            BitSet[] oldBits = deltaOldClustBits[deltaPointer];
            BitSet[] newBits = new BitSet[newSlots];
            System.arraycopy(oldBits, 0, newBits, 0, oldBits.length);
            for (int r = oldBits.length; r < newSlots; r++) {
                newBits[r] = new BitSet(N);
            }
            deltaOldClustBits[deltaPointer] = newBits;
        }
    }

    private void pushEmptyDelta() {
        ensureDeltaCapacity(0);
        int d = deltaPointer;
        deltaRowsCount[d] = 0;
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;
    }

    private void updateSingleRowAndSave(int r, BitSet newCluster) {
        ensureDeltaCapacity(1);
        int d = deltaPointer;
        deltaRows[d][0] = r;
        System.arraycopy(assigncost[r], 0, deltaOldRows[d][0], 0, dim);

        Node n = rowToNode[r];
        if (n != null) {
            deltaOldClustNode[d][0] = n;
            BitSet oldBs = currentClusters.get(n);
            deltaOldClustBits[d][0].clear();
            if (oldBs != null) deltaOldClustBits[d][0].or(oldBs);

            if (oldBs == null) {
                oldBs = new BitSet(N);
                currentClusters.put(n, oldBs);
            }
            oldBs.clear();
            oldBs.or(newCluster);
        } else {
            deltaOldClustNode[d][0] = null;
        }

        deltaRowsCount[d] = 1;
        System.arraycopy(u, 0, deltaOldU[d], 0, dim);
        System.arraycopy(v, 0, deltaOldV[d], 0, dim);
        System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
        System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;

        computeRowCostsFast(r, newCluster);

        int[] changedRowsParam = cachedKArrays[1];
        changedRowsParam[0] = r;
        this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
    }

    private void updateTwoRowsAndSave(int r1, BitSet c1, int r2, BitSet c2) {
        ensureDeltaCapacity(2);
        int d = deltaPointer;
        deltaRows[d][0] = r1;
        deltaRows[d][1] = r2;
        System.arraycopy(assigncost[r1], 0, deltaOldRows[d][0], 0, dim);
        System.arraycopy(assigncost[r2], 0, deltaOldRows[d][1], 0, dim);

        Node n1 = rowToNode[r1];
        if (n1 != null) {
            deltaOldClustNode[d][0] = n1;
            BitSet b1 = currentClusters.get(n1);
            deltaOldClustBits[d][0].clear();
            if (b1 != null) deltaOldClustBits[d][0].or(b1);
            if (b1 == null) {
                b1 = new BitSet(N);
                currentClusters.put(n1, b1);
            }
            b1.clear();
            b1.or(c1);
        } else {
            deltaOldClustNode[d][0] = null;
        }

        Node n2 = rowToNode[r2];
        if (n2 != null) {
            deltaOldClustNode[d][1] = n2;
            BitSet b2 = currentClusters.get(n2);
            deltaOldClustBits[d][1].clear();
            if (b2 != null) deltaOldClustBits[d][1].or(b2);
            if (b2 == null) {
                b2 = new BitSet(N);
                currentClusters.put(n2, b2);
            }
            b2.clear();
            b2.or(c2);
        } else {
            deltaOldClustNode[d][1] = null;
        }

        deltaRowsCount[d] = 2;
        System.arraycopy(u, 0, deltaOldU[d], 0, dim);
        System.arraycopy(v, 0, deltaOldV[d], 0, dim);
        System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
        System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;

        computeRowCostsFast(r1, c1);
        computeRowCostsFast(r2, c2);

        int[] changedRowsParam = cachedKArrays[2];
        changedRowsParam[0] = r1;
        changedRowsParam[1] = r2;
        this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
    }

    private void updateMultipleRowsAndSave(int count, int[] rows, BitSet[] clusters) {
        if (count <= 0) {
            pushEmptyDelta();
            return;
        }
        ensureDeltaCapacity(count);
        int d = deltaPointer;

        for (int i = 0; i < count; i++) {
            int r = rows[i];
            deltaRows[d][i] = r;
            System.arraycopy(assigncost[r], 0, deltaOldRows[d][i], 0, dim);

            Node n = rowToNode[r];
            if (n != null) {
                deltaOldClustNode[d][i] = n;
                BitSet oldBs = currentClusters.get(n);
                deltaOldClustBits[d][i].clear();
                if (oldBs != null) deltaOldClustBits[d][i].or(oldBs);
            } else {
                deltaOldClustNode[d][i] = null;
            }
        }

        deltaRowsCount[d] = count;
        System.arraycopy(u, 0, deltaOldU[d], 0, dim);
        System.arraycopy(v, 0, deltaOldV[d], 0, dim);
        System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
        System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;

        for (int i = 0; i < count; i++) {
            int r = rows[i];
            BitSet newClust = clusters[i];
            Node n = rowToNode[r];
            if (n != null) {
                BitSet currBs = currentClusters.get(n);
                if (currBs == null) {
                    currBs = new BitSet(N);
                    currentClusters.put(n, currBs);
                }
                currBs.clear();
                currBs.or(newClust);
            }
            computeRowCostsFast(r, newClust);
        }

        int[] changedRowsParam = (count < cachedKArrays.length) ? cachedKArrays[count] : new int[count];
        System.arraycopy(rows, 0, changedRowsParam, 0, count);
        this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
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
                Node n = deltaOldClustNode[d][i];
                if (n != null) {
                    BitSet bs = currentClusters.get(n);
                    if (bs == null) {
                        bs = new BitSet(N);
                        currentClusters.put(n, bs);
                    }
                    bs.clear();
                    bs.or(deltaOldClustBits[d][i]);
                }
            }
            System.arraycopy(deltaOldU[d], 0, u, 0, dim);
            System.arraycopy(deltaOldV[d], 0, v, 0, dim);
            System.arraycopy(deltaOldRowsol[d], 0, rowsol, 0, dim);
            System.arraycopy(deltaOldColsol[d], 0, colsol, 0, dim);
        }
        this.currentDistance = deltaOldDistance[d];
    }

    private Node resolveWandering(Node wanderingSource, Node pruneNode) {
        if (wanderingSource != null && wanderingSource.isRoot()) {
            Node p = pruneNode.getParent();
            for (int i = 0; i < p.getChildCount(); i++) {
                if (p.getChild(i) != pruneNode) return p.getChild(i);
            }
        }
        return wanderingSource;
    }

    // =========================================================================
    // ZERO-ALLOCATION 2D-DFS TBR (RootedTbrMetric)
    // =========================================================================

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        this.currentPrunedLeaves.clear();
        this.currentPrunedLeaves.or(getCluster(pruneNode));
        BitSet P = this.currentPrunedLeaves;

        int count = 0;
        Node curr = pruneNode.getParent().getParent();
        while (curr != null) {
            Integer r = nodeToRow.get(curr);
            if (r != null) {
                scratchPruneRows[count] = r;
                BitSet bs = scratchPruneBitSets[count];
                bs.clear();
                bs.or(currentClusters.get(curr));
                bs.andNot(P);
                if (bs.cardinality() == N) bs.clear();
                count++;
            }
            curr = curr.getParent();
        }

        Integer r_p = nodeToRow.get(resolveWandering(wanderingSource, pruneNode));
        if (r_p != null) {
            scratchPruneRows[count] = r_p;
            scratchPruneBitSets[count].clear();
            count++;
        }
        updateMultipleRowsAndSave(count, scratchPruneRows, scratchPruneBitSets);
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node wanderingSource) {
        BitSet P = this.currentPrunedLeaves;
        Integer r_p = nodeToRow.get(resolveWandering(wanderingSource, pruneNode));
        if (r_p != null) {
            tbrScratch1.clear();
            tbrScratch1.or(fullTreeLeaves);
            tbrScratch1.andNot(P);
            if (tbrScratch1.cardinality() == N) tbrScratch1.clear();
            updateSingleRowAndSave(r_p, tbrScratch1);
        } else {
            pushEmptyDelta();
        }
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        BitSet P = this.currentPrunedLeaves;
        Node resolvedWandering = resolveWandering(wanderingSource, pruneNode);

        // Gdy odcinamy bezpośrednio spod korzenia (wanderingSource.isRoot()),
        // wpięcie nad drugim dzieckiem korzenia (childTarget == resolvedWandering)
        // odpowiada wpięciu w korzeniu drzewa szkieletowego T_B, które zostało już
        // skonfigurowane przez setTargetRoot (węzeł resolvedWandering zachowuje klaster L \ P).
        if (childTarget == resolvedWandering) {
            pushEmptyDelta();
            return;
        }

        Integer r_p = nodeToRow.get(resolvedWandering);

        if (r_p != null) {
            BitSet childCluster = getCluster(childTarget);
            tbrScratch1.clear();
            if (childCluster != null) tbrScratch1.or(childCluster);
            tbrScratch1.or(P);
            if (tbrScratch1.cardinality() == N) tbrScratch1.clear();
        }

        Integer r_parent = nodeToRow.get(parentTarget);
        boolean updateParent = (r_parent != null && parentTarget != wanderingSource && parentTarget != resolvedWandering && !r_parent.equals(r_p));
        if (updateParent) {
            BitSet parentCluster = currentClusters.get(parentTarget);
            tbrScratch2.clear();
            if (parentCluster != null) tbrScratch2.or(parentCluster);
            tbrScratch2.or(P);
            if (tbrScratch2.cardinality() == N) tbrScratch2.clear();
        }

        if (r_p != null && updateParent) {
            updateTwoRowsAndSave(r_p, tbrScratch1, r_parent, tbrScratch2);
        } else if (r_p != null) {
            updateSingleRowAndSave(r_p, tbrScratch1);
        } else if (updateParent) {
            updateSingleRowAndSave(r_parent, tbrScratch2);
        } else {
            pushEmptyDelta();
        }
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        undoDeltaStack();
    }

    // Przeciążenia wsteczne zabezpieczające wywołania z parametrem rerootNode
    public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {
        setTargetRoot(pruneNode, wanderingSource);
    }

    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        moveTargetDown(parentTarget, childTarget, pruneNode, wanderingSource);
    }

    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        moveTargetUp(parentTarget, childTarget, pruneNode, wanderingSource);
    }

    @Override
    public void revertPrunedState(Node pruneNode, Node wanderingSource) {
        undoDeltaStack();
        undoDeltaStack();
    }

    @Override
    public void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode) {
        if (parentReroot == pruneNode) {
            pushEmptyDelta();
            return;
        }

        Integer rParent = nodeToRow.get(parentReroot);
        if (rParent != null && currentPrunedLeaves != null) {
            BitSet childCluster = getCluster(childReroot);
            if (childCluster != null) {
                tbrScratch1.clear();
                tbrScratch1.or(currentPrunedLeaves);
                tbrScratch1.andNot(childCluster);
                if (tbrScratch1.cardinality() == N) tbrScratch1.clear();

                updateSingleRowAndSave(rParent, tbrScratch1);
                return;
            }
        }

        pushEmptyDelta();
    }

    @Override
    public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {
        undoDeltaStack();
    }

    // =========================================================================
    // O(1) OGRANICZENIE Z NIERÓWNOŚCI TRÓJKĄTA ORAZ NNI, SPR & ECR
    // =========================================================================

    /**
     * Wylicza w czasie O(1) dokładne ograniczenie górne na zmianę odległości MC
     * po ruchu rNNI (|B| + |C| dla standardowego rNNI).
     */
    public double getNniMaxDelta(NniMove move) {
        if (move == null || move.movingSubtree == null || move.swapPartner == null) {
            return Double.POSITIVE_INFINITY;
        }
        Node nodeA = move.movingSubtree;
        Node nodeB = move.swapPartner;
        Node pA = nodeA.getParent();
        Node pB = nodeB.getParent();
        if (pA == null || pB == null || pA == pB) {
            return 0.0;
        }

        BitSet clusterA = getCluster(nodeA);
        BitSet clusterB = getCluster(nodeB);
        if (clusterA == null || clusterB == null) {
            return Double.POSITIVE_INFINITY;
        }

        int delta = clusterA.cardinality() + clusterB.cardinality();
        if (pA.getParent() == pB || pB.getParent() == pA) {
            return delta;
        }
        return 2.0 * delta;
    }

    /**
     * Ewaluacja rNNI z jawnym odcięciem Branch-and-Bound w czasie O(1).
     */
    public double applyNni(NniMove move, double bestThreshold) {
        if (enableTriangleLowerBoundPruning) {
            double maxDelta = getNniMaxDelta(move);
            if (this.currentDistance - maxDelta >= bestThreshold) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return applyNni(move);
    }

    @Override
    public double applyNni(NniMove move) {
        Node nodeA = move.movingSubtree;
        Node nodeB = move.swapPartner;

        Node pA = nodeA.getParent();
        Node pB = nodeB.getParent();

        BitSet clusterA = getCluster(nodeA);
        BitSet clusterB = getCluster(nodeB);

        if (pA.getParent() == pB) {
            if (!applyNniStep(pA, clusterA, clusterB)) {
                pushEmptyDelta();
            }
        } else if (pB.getParent() == pA) {
            if (!applyNniStep(pB, clusterB, clusterA)) {
                pushEmptyDelta();
            }
        } else {
            Integer rA = nodeToRow.get(pA);
            Integer rB = nodeToRow.get(pB);

            if (rA != null && rB != null && !rA.equals(rB)) {
                tbrScratch1.clear();
                tbrScratch1.or(currentClusters.get(pA));
                if (clusterA != null) tbrScratch1.andNot(clusterA);
                if (clusterB != null) tbrScratch1.or(clusterB);
                if (tbrScratch1.cardinality() == N) tbrScratch1.clear();

                tbrScratch2.clear();
                tbrScratch2.or(currentClusters.get(pB));
                if (clusterB != null) tbrScratch2.andNot(clusterB);
                if (clusterA != null) tbrScratch2.or(clusterA);
                if (tbrScratch2.cardinality() == N) tbrScratch2.clear();

                updateTwoRowsAndSave(rA, tbrScratch1, rB, tbrScratch2);
            } else if (rA != null) {
                applyNniStep(pA, clusterA, clusterB);
            } else if (rB != null) {
                applyNniStep(pB, clusterB, clusterA);
            } else {
                pushEmptyDelta();
            }
        }
        return this.currentDistance;
    }

    @Override
    public void undoNni(NniMove move) {
        undoDeltaStack();
    }

    public boolean applyNniStep(Node nodeToUpdate, BitSet bitsOut, BitSet bitsIn) {
        Integer rIndex = nodeToRow.get(nodeToUpdate);
        if (rIndex == null) return false;

        tbrScratch1.clear();
        tbrScratch1.or(currentClusters.get(nodeToUpdate));
        if (bitsOut != null) tbrScratch1.andNot(bitsOut);
        if (bitsIn != null) tbrScratch1.or(bitsIn);

        if (tbrScratch1.cardinality() == N) tbrScratch1.clear();

        updateSingleRowAndSave(rIndex, tbrScratch1);
        return true;
    }

    public void undoNniStep() {
        undoDeltaStack();
    }

    @Override
    public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        double dist = commit2sEcrMove(top, m1, m2, b, template);
        undoDeltaStack();
        return dist;
    }

    @Override
    public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        tbrScratch1.clear();
        tbrScratch2.clear();

        if (template.isFork) {
            tbrScratch1.or(getCluster(b[template.indices[0]]));
            tbrScratch1.or(getCluster(b[template.indices[1]]));
            tbrScratch2.or(getCluster(b[template.indices[2]]));
            tbrScratch2.or(getCluster(b[template.indices[3]]));
        } else {
            tbrScratch2.or(getCluster(b[template.indices[2]]));
            tbrScratch2.or(getCluster(b[template.indices[3]]));
            tbrScratch1.or(getCluster(b[template.indices[1]]));
            tbrScratch1.or(tbrScratch2);
        }

        if (tbrScratch1.cardinality() == N) tbrScratch1.clear();
        if (tbrScratch2.cardinality() == N) tbrScratch2.clear();

        Integer r1 = nodeToRow.get(m1);
        Integer r2 = nodeToRow.get(m2);

        if (r1 != null && r2 != null) {
            updateTwoRowsAndSave(r1, tbrScratch1, r2, tbrScratch2);
        } else if (r1 != null) {
            updateSingleRowAndSave(r1, tbrScratch1);
        } else if (r2 != null) {
            updateSingleRowAndSave(r2, tbrScratch2);
        } else {
            pushEmptyDelta();
        }
        return this.currentDistance;
    }

    @Override
    public double evaluate3sEcrMove(List<Node> cluster, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR template) {
        double dist = commit3sEcrMove(cluster, b, template);
        undoDeltaStack();
        return dist;
    }

    @Override
    public double commit3sEcrMove(List<Node> cluster, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR template) {
        poolIdx = 0;
        for (int i = 0; i < 5; i++) {
            ecr3BBits[i] = getCluster(b[i]);
        }

        ecr3NextClusterIdx = 1;
        ecr3UpdateCount = 0;

        Node top = cluster.get(0);
        compute3sEcrTemplateBitsFast(template, top, cluster, top);

        updateMultipleRowsAndSave(ecr3UpdateCount, scratchPruneRows, scratchPruneBitSets);
        return this.currentDistance;
    }

    private BitSet compute3sEcrTemplateBitsFast(SubtreeEcr3Utils.TopologyTemplate3sECR temp, Node currentInternal, List<Node> cluster, Node topNode) {
        BitSet myBits = getScratchBitSet();

        if (temp.left.leafIndex != -1) {
            myBits.or(ecr3BBits[temp.left.leafIndex]);
        } else {
            int clusterSize = cluster.size();
            int nextIdx = (ecr3NextClusterIdx < clusterSize) ? ecr3NextClusterIdx++ : clusterSize - 1;
            Node nextInternal = cluster.get(nextIdx);
            BitSet leftBits = compute3sEcrTemplateBitsFast(temp.left, nextInternal, cluster, topNode);
            myBits.or(leftBits);
        }

        if (temp.right.leafIndex != -1) {
            myBits.or(ecr3BBits[temp.right.leafIndex]);
        } else {
            int clusterSize = cluster.size();
            int nextIdx = (ecr3NextClusterIdx < clusterSize) ? ecr3NextClusterIdx++ : clusterSize - 1;
            Node nextInternal = cluster.get(nextIdx);
            BitSet rightBits = compute3sEcrTemplateBitsFast(temp.right, nextInternal, cluster, topNode);
            myBits.or(rightBits);
        }

        if (currentInternal != topNode) {
            Integer r = nodeToRow.get(currentInternal);
            if (r != null) {
                int slot = ecr3UpdateCount;
                for (int i = 0; i < ecr3UpdateCount; i++) {
                    if (scratchPruneRows[i] == r) {
                        slot = i;
                        break;
                    }
                }
                scratchPruneRows[slot] = r;
                BitSet dest = scratchPruneBitSets[slot];
                dest.clear();
                if (myBits.cardinality() != N) {
                    dest.or(myBits);
                }
                if (slot == ecr3UpdateCount) {
                    ecr3UpdateCount++;
                }
            }
        }

        return myBits;
    }

    private void clearHistory() {
        costHistory.clear();
        rowsolHistory.clear();
        colsolHistory.clear();
        uHistory.clear();
        vHistory.clear();
        distanceHistory.clear();
        clusterHistory.clear();
        deltaPointer = 0;
        poolIdx = 0;
        clusterToRow.clear();
        clusterToRowInitialized = false;
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

        IdentityHashMap<Node, BitSet> clustersCopy = new IdentityHashMap<>();
        for (Map.Entry<Node, BitSet> entry : currentClusters.entrySet()) {
            clustersCopy.put(entry.getKey(), (BitSet) entry.getValue().clone());
        }
        clusterHistory.push(clustersCopy);
    }

    @Override
    public double getCurrentDistance() {
        return this.currentDistance;
    }

    @Override
    public void commit() {
        clearHistory();
        if (this.baseClusters != null && this.currentClusters != null) {
            for (Map.Entry<Node, BitSet> entry : this.currentClusters.entrySet()) {
                BitSet baseBs = this.baseClusters.get(entry.getKey());
                if (baseBs != null) {
                    baseBs.clear();
                    baseBs.or(entry.getValue());
                } else {
                    this.baseClusters.put(entry.getKey(), (BitSet) entry.getValue().clone());
                }
            }
        }
    }

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

    public void printDiagnosticDump(Tree physicalTree) {
        System.err.println("\n================================================================================");
        System.err.println("            SZCZEGÓŁOWA ANALIZA KLASTRÓW I MACIERZY KOSZTÓW (MC)");
        System.err.println("================================================================================");
        System.err.printf("Wymiar macierzy: %dx%d | Zgłoszony dystans LAP: %.2f%n", dim, dim, currentDistance);

        Map<Node, BitSet> physClusters = new IdentityHashMap<>();
        extractClusters(physicalTree.getRoot(), idGroup, physClusters);

        Set<String> physClusterStrings = new HashSet<>();
        for (Map.Entry<Node, BitSet> e : physClusters.entrySet()) {
            if (!e.getKey().isRoot()) {
                physClusterStrings.add(bitSetToLeafNames(e.getValue()));
            }
        }

        System.err.println("\n--- [A] Klastry w FIZYCZNYM drzewie (rzeczywista topologia) ---");
        for (String pcs : physClusterStrings) {
            System.err.println("   (fizyczny) " + pcs);
        }

        System.err.println("\n--- [B] Klastry w STANIE INKREMENTALNYM (rowToNode) vs Dopasowanie w T2 ---");
        Set<String> incrClusterStrings = new HashSet<>();
        double sumCosts = 0;

        for (int i = 0; i < dim; i++) {
            Node n = rowToNode[i];
            BitSet bs = (n != null) ? currentClusters.get(n) : null;
            String bsStr = (bs != null) ? bitSetToLeafNames(bs) : "NULL/EMPTY";
            incrClusterStrings.add(bsStr);

            boolean inPhys = physClusterStrings.contains(bsStr);
            String status = inPhys ? "[OK]" : "[BŁĄD: PHANTOM/STALE]";

            int matchedCol = (rowsol != null && i < rowsol.length) ? rowsol[i] : -1;
            int cost = (matchedCol >= 0 && matchedCol < dim) ? assigncost[i][matchedCol] : -1;
            if (cost >= 0) sumCosts += cost;

            Node colNode = (matchedCol >= 0 && matchedCol < dim) ? colToNode[matchedCol] : null;
            BitSet colBs = (colNode != null) ? targetClusters.get(colNode) : null;
            String colBsStr = (colBs != null) ? bitSetToLeafNames(colBs) : "PADDING";

            System.err.printf("   Wiersz %d | Węzeł: %-6s | Klaster T1: %-26s | %-20s | Cel T2 (kol. %d): %-20s -> Koszt: %d%n",
                    i, (n != null ? (n.isLeaf() ? n.getIdentifier().getName() : "int#" + n.getNumber()) : "null"),
                    bsStr, status, matchedCol, colBsStr, cost);
        }

        System.err.printf("\nSuma cząstkowych wag dopasowania: %.2f%n", sumCosts);

        System.err.println("\n--- [C] Klastry FIZYCZNE, których BRAKUJE w strukturach inkrementalnych ---");
        for (String pcs : physClusterStrings) {
            if (!incrClusterStrings.contains(pcs)) {
                System.err.println("   [BRAKUJĄCY KLASTER] " + pcs);
            }
        }
        System.err.println("================================================================================\n");
    }

    private String bitSetToLeafNames(BitSet bs) {
        if (bs == null || bs.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            if (!first) sb.append(", ");
            if (idGroup != null && i < idGroup.getIdCount()) {
                sb.append(idGroup.getIdentifier(i).getName());
            } else {
                sb.append(i);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}