package treecmp.metrics.topological.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import pal.tree.SimpleTree;
import treecmp.common.AlignInfo;
import treecmp.common.LapSolver;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.heuristics.tbr.acc.RootedTbrMetric;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingSplitMetric;

import java.util.*;

public class MSIncrementalMetric implements IncrementalMetric, RootedTbrMetric {

    private Tree baseTree;
    private Tree targetTree;
    private double currentDistance;
    private IdGroup idGroup;
    private int N;

    private final MatchingSplitMetric msMetricFull = new MatchingSplitMetric();
    private final UTbrUtils utbrUtils = new UTbrUtils();

    private int dim;
    private short[][] assigncost;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private Map<Node, BitSet> baseSplits;
    private Map<Node, BitSet> currentSplits;
    private Map<Node, BitSet> targetSplits;
    private Map<Node, Integer> nodeToRow;

    private Node[] rowToNode;
    private Node[] colToNode;

    private BitSet currentPrunedLeaves;

    private final Stack<short[][]> costHistory = new Stack<>();
    private final Stack<int[]> rowsolHistory = new Stack<>();
    private final Stack<int[]> colsolHistory = new Stack<>();
    private final Stack<int[]> uHistory = new Stack<>();
    private final Stack<int[]> vHistory = new Stack<>();
    private final Stack<Double> distanceHistory = new Stack<>();
    private final Stack<Map<Node, BitSet>> splitHistory = new Stack<>();
    private final Stack<Integer> nniPushCountHistory = new Stack<>();

    // =========================================================================
    // PREALOKOWANY STOS DELTA (ZERO-ALLOCATION DLA NNI, SPR, ECR, TBR)
    // =========================================================================
    private int deltaMaxDepth = 128;
    private int deltaPointer = 0;
    private int[] deltaRowsCount;
    private int[][] deltaRows;
    private short[][][] deltaOldRows;
    private int[][] deltaOldU;
    private int[][] deltaOldV;
    private int[][] deltaOldRowsol;
    private int[][] deltaOldColsol;
    private double[] deltaOldDistance;
    private Node[][] deltaOldSplitsNode;
    private BitSet[][] deltaOldSplitsBits;

    // PREALOKOWANE BUFORY I STRUKTURY DLA ZERO-ALLOCATION uTBR
    private final Map<BitSet, Integer> splitToRow = new HashMap<>();

    private int numWords;
    private long[][] targetSplitWords;
    private long[] scratchAddWords;

    private short[][] scratchSavedOldRows;
    private int[] scratchSavedU;
    private int[] scratchSavedV;
    private int[] scratchSavedRowsol;
    private int[] scratchSavedColsol;
    private int[] scratchChangedRows;
    private int[][] cachedKArrays;

    private Node[] pathBuf;
    private BitSet[] bitSetPool;
    private int poolIdx = 0;
    private final List<BitSet> rawRemoved = new ArrayList<>(32);
    private final List<BitSet> rawAdded = new ArrayList<>(32);
    private final List<BitSet> toRemove = new ArrayList<>(16);
    private final List<BitSet> toAdd = new ArrayList<>(16);

    // Bufory dla fallbacku
    private BitSet[] tempTreeSplits;
    private BitSet[] scratchNewSplits;
    private boolean[] scratchRowUsed;
    private BitSet scratchCanonical;

    public BitSet getSplit(Node n) {
        return getSplitBits(n);
    }

    private int getNodeIndex(Node node) {
        return node.isLeaf() ? node.getNumber() : (N + node.getNumber());
    }

    private BitSet canonicalizeSplit(BitSet bs) {
        if (bs == null) return null;
        BitSet clone = (BitSet) bs.clone();
        if (clone.get(0)) {
            clone.flip(0, N);
        }
        return clone;
    }

    private void canonicalizeInto(BitSet src, BitSet dest) {
        dest.clear();
        dest.or(src);
        if (dest.get(0)) {
            dest.flip(0, N);
        }
    }

    private boolean isNonTrivialSplit(BitSet bs) {
        if (bs == null) return false;
        int card = bs.cardinality();
        return card > 1 && card < N - 1;
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

    private Node findLca(Node a, Node b) {
        if (a == null || b == null) return null;
        int dA = getNodeDepth(a);
        int dB = getNodeDepth(b);

        while (dA > dB && a != null) { a = a.getParent(); dA--; }
        while (dB > dA && b != null) { b = b.getParent(); dB--; }

        while (a != b && a != null && b != null) {
            a = a.getParent();
            b = b.getParent();
        }
        return a;
    }

    private int getNodeDepth(Node n) {
        int d = 0;
        Node curr = n;
        while (curr != null) {
            d++;
            curr = curr.getParent();
        }
        return d;
    }

    private Node findRootChildLeadingTo(Node root, Node pruneNode, Node targetNode) {
        Node curr = targetNode;
        while (curr != null && curr.getParent() != root) {
            curr = curr.getParent();
        }
        if (curr != null && curr.getParent() == root && curr != pruneNode) {
            return curr;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            Node ch = root.getChild(i);
            if (ch != pruneNode) return ch;
        }
        return null;
    }

    // =========================================================================
    // PRZYROSTOWA EWALUACJA uTBR DLA MS (ANALITYCZNA DELTA, CIEPŁY START LAP)
    // =========================================================================

    public double evaluateExactUTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        if (pruneNode == null || rerootNode == null || targetNode == null || this.targetTree == null || this.baseTree == null) {
            return Double.POSITIVE_INFINITY;
        }

        Node root = this.baseTree.getRoot();
        Node pParent = pruneNode.getParent();
        if (pParent == null || pruneNode == root || targetNode == root) return Double.POSITIVE_INFINITY;

        BitSet LP = getSplitBits(pruneNode);
        if (LP == null) return Double.POSITIVE_INFINITY;

        poolIdx = 0;
        rawRemoved.clear();
        rawAdded.clear();
        toRemove.clear();
        toAdd.clear();

        // 1. ZMIANY W KOMPONENCIE T1 (PRZEKORZENIENIE)
        if (rerootNode != pruneNode) {
            int pathLen = 0;
            Node curr = rerootNode;
            while (curr != null) {
                pathBuf[pathLen++] = curr;
                if (curr == pruneNode) break;
                curr = curr.getParent();
            }

            int m = pathLen - 1;
            for (int i = 1; i < m; i++) {
                Node v_i = pathBuf[pathLen - 1 - i];
                Node v_next = pathBuf[pathLen - 2 - i];

                BitSet oldC = getSplitBits(v_i);
                BitSet rem = getScratchBitSet();
                canonicalizeInto(oldC, rem);
                rawRemoved.add(rem);

                BitSet nextC = getSplitBits(v_next);
                BitSet add = getScratchBitSet();
                add.or(LP);
                add.andNot(nextC);
                if (add.get(0)) add.flip(0, N);
                rawAdded.add(add);
            }
        }

        // 2. ZMIANY W KOMPONENCIE T2 (ZWŁASZCZA TRIFURKACJA KORZENIA PAL)
        Node remCollapsed;
        if (pParent != root) {
            remCollapsed = pParent;
        } else {
            remCollapsed = findRootChildLeadingTo(root, pruneNode, targetNode);
        }

        // Eliminacja asymetrii: operacja regraftu na rodzeństwie trifurkacji PAL zachowuje topologię T2,
        // dzięki czemu zmiany w komponencie T2 całkowicie się znoszą.
        boolean cancelRoot = (pParent == root && (targetNode.getParent() == root || remCollapsed == targetNode));

        if (!cancelRoot) {
            if (remCollapsed != null) {
                BitSet oldCollapsed = getSplitBits(remCollapsed);
                if (oldCollapsed != null) {
                    BitSet remC = getScratchBitSet();
                    canonicalizeInto(oldCollapsed, remC);
                    rawRemoved.add(remC);
                }
            }

            BitSet targetBs = getSplitBits(targetNode);
            if (targetBs != null) {
                BitSet addW = getScratchBitSet();
                addW.or(targetBs);
                addW.or(LP);
                if (addW.get(0)) addW.flip(0, N);
                rawAdded.add(addW);
            }

            Node startNode = remCollapsed;
            Node lca = findLca(startNode, targetNode);

            if (startNode != null && startNode != lca) {
                Node curr = startNode.getParent();
                while (curr != null && curr != lca) {
                    BitSet oldC = getSplitBits(curr);
                    if (oldC != null) {
                        BitSet rem = getScratchBitSet();
                        canonicalizeInto(oldC, rem);
                        rawRemoved.add(rem);

                        BitSet add = getScratchBitSet();
                        add.or(oldC);
                        add.andNot(LP);
                        if (add.get(0)) add.flip(0, N);
                        rawAdded.add(add);
                    }
                    curr = curr.getParent();
                }
            }

            if (targetNode != lca) {
                Node currT = targetNode.getParent();
                while (currT != null && currT != lca) {
                    BitSet oldC = getSplitBits(currT);
                    if (oldC != null) {
                        BitSet rem = getScratchBitSet();
                        canonicalizeInto(oldC, rem);
                        rawRemoved.add(rem);

                        BitSet add = getScratchBitSet();
                        add.or(oldC);
                        add.or(LP);
                        if (add.get(0)) add.flip(0, N);
                        rawAdded.add(add);
                    }
                    currT = currT.getParent();
                }
            }

            if (targetNode == lca && targetNode != root) {
                BitSet oldC = getSplitBits(targetNode);
                if (oldC != null) {
                    BitSet rem = getScratchBitSet();
                    canonicalizeInto(oldC, rem);
                    rawRemoved.add(rem);

                    BitSet add = getScratchBitSet();
                    add.or(oldC);
                    add.andNot(LP);
                    if (add.get(0)) add.flip(0, N);
                    rawAdded.add(add);
                }
            }
        }

        // 3. WYODRĘBNIENIE NIETRYWIALNYCH I NIEIZOMORFICZNYCH ZMIAN
        for (int i = 0; i < rawRemoved.size(); i++) {
            BitSet rem = rawRemoved.get(i);
            if (!isNonTrivialSplit(rem)) continue;

            boolean matched = false;
            for (int j = 0; j < rawAdded.size(); j++) {
                BitSet add = rawAdded.get(j);
                if (add != null && rem.equals(add)) {
                    rawAdded.set(j, null);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                boolean alreadyIn = false;
                for (int r = 0; r < toRemove.size(); r++) {
                    if (rem.equals(toRemove.get(r))) {
                        alreadyIn = true;
                        break;
                    }
                }
                if (!alreadyIn) toRemove.add(rem);
            }
        }

        for (int j = 0; j < rawAdded.size(); j++) {
            BitSet add = rawAdded.get(j);
            if (add != null && isNonTrivialSplit(add)) {
                boolean alreadyIn = false;
                for (int r = 0; r < toAdd.size(); r++) {
                    if (add.equals(toAdd.get(r))) {
                        alreadyIn = true;
                        break;
                    }
                }
                if (!alreadyIn) toAdd.add(add);
            }
        }

        if (toRemove.isEmpty() && toAdd.isEmpty()) {
            return this.currentDistance;
        }

        // 4. BEZPIECZNA WERYFIKACJA PARZYSTOŚCI (BEZPOŚREDNI FALLBACK GDYBY ZASZŁA ANOMALIA)
        if (toRemove.size() != toAdd.size() || toRemove.isEmpty()) {
            return evaluateViaTempTree(pruneNode, rerootNode, targetNode);
        }

        int k = toRemove.size();

        // FAZA 1: Weryfikacja mapowania wierszy
        for (int i = 0; i < k; i++) {
            BitSet rem = toRemove.get(i);
            Integer r = splitToRow.get(rem);
            if (r == null) {
                return evaluateViaTempTree(pruneNode, rerootNode, targetNode);
            }
            scratchChangedRows[i] = r;
        }

        // FAZA 2: Kopia zapasowa bez alokacji na stercie
        System.arraycopy(u, 0, scratchSavedU, 0, dim);
        System.arraycopy(v, 0, scratchSavedV, 0, dim);
        System.arraycopy(rowsol, 0, scratchSavedRowsol, 0, dim);
        System.arraycopy(colsol, 0, scratchSavedColsol, 0, dim);

        // FAZA 3: Przeliczenie tylko zmienionych wierszy macierzy
        for (int i = 0; i < k; i++) {
            int r = scratchChangedRows[i];
            System.arraycopy(assigncost[r], 0, scratchSavedOldRows[i], 0, dim);

            BitSet add = toAdd.get(i);
            Arrays.fill(scratchAddWords, 0L);
            long[] words = add.toLongArray();
            System.arraycopy(words, 0, scratchAddWords, 0, words.length);

            short[] costRow = assigncost[r];
            for (int j = 0; j < dim; j++) {
                long[] tWords = targetSplitWords[j];
                int diff = 0;
                for (int w = 0; w < numWords; w++) {
                    diff += Long.bitCount(scratchAddWords[w] ^ tWords[w]);
                }
                costRow[j] = (short) Math.min(diff, N - diff);
            }
        }

        int[] changedRowsParam = (k < cachedKArrays.length) ? cachedKArrays[k] : new int[k];
        System.arraycopy(scratchChangedRows, 0, changedRowsParam, 0, k);

        // FAZA 4: Ciepły start solvera LAP
        double newDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);

        // FAZA 5: Przywrócenie stanu pierwotnego
        for (int i = 0; i < k; i++) {
            System.arraycopy(scratchSavedOldRows[i], 0, assigncost[scratchChangedRows[i]], 0, dim);
        }
        System.arraycopy(scratchSavedU, 0, u, 0, dim);
        System.arraycopy(scratchSavedV, 0, v, 0, dim);
        System.arraycopy(scratchSavedRowsol, 0, rowsol, 0, dim);
        System.arraycopy(scratchSavedColsol, 0, colsol, 0, dim);

        return newDistance;
    }

    // =========================================================================
    // BEZPIECZNY FALLBACK: UŻYWANY TYLKO W WYJĄTKOWYCH PRZYPADKACH
    // =========================================================================

    private double evaluateViaTempTree(Node pruneNode, Node rerootNode, Node targetNode) {
        Tree tree = this.baseTree;
        if (pruneNode.getParent() != null) {
            Node r = pruneNode;
            while (r.getParent() != null) r = r.getParent();
            if (this.baseTree != null && r == this.baseTree.getRoot()) {
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
        if (tempTree == null) return Double.POSITIVE_INFINITY;

        if (tempTree instanceof SimpleTree) {
            pal.tree.TreeUtils.computeParentPointers(tempTree.getRoot());
            ((SimpleTree) tempTree).createNodeList();
        }

        computeTempTreeSplits(tempTree.getRoot());

        Arrays.fill(scratchRowUsed, 0, dim, false);
        int newSplitCount = 0;

        int intCount = tempTree.getInternalNodeCount();
        for (int i = 0; i < intCount; i++) {
            Node n = tempTree.getInternalNode(i);
            if (n.isRoot()) continue;

            BitSet bs = tempTreeSplits[getNodeIndex(n)];
            scratchCanonical.clear();
            scratchCanonical.or(bs);
            if (scratchCanonical.get(0)) {
                scratchCanonical.flip(0, N);
            }

            if (!isNonTrivialSplit(scratchCanonical)) continue;

            Integer r = splitToRow.get(scratchCanonical);
            if (r != null && r < dim && !scratchRowUsed[r]) {
                scratchRowUsed[r] = true;
            } else {
                scratchNewSplits[newSplitCount].clear();
                scratchNewSplits[newSplitCount].or(scratchCanonical);
                newSplitCount++;
            }
        }

        if (newSplitCount == 0) {
            return this.currentDistance;
        }

        int k = 0;
        for (int r = 0; r < dim; r++) {
            if (!scratchRowUsed[r] && rowToNode[r] != null) {
                scratchChangedRows[k++] = r;
            }
        }

        if (k != newSplitCount) {
            try {
                return msMetricFull.getDistance(tempTree, this.targetTree);
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
            System.arraycopy(assigncost[r], 0, scratchSavedOldRows[i], 0, dim);

            BitSet add = scratchNewSplits[i];
            Arrays.fill(scratchAddWords, 0L);
            long[] words = add.toLongArray();
            System.arraycopy(words, 0, scratchAddWords, 0, words.length);

            short[] costRow = assigncost[r];
            for (int j = 0; j < dim; j++) {
                long[] tWords = targetSplitWords[j];
                int diff = 0;
                for (int w = 0; w < numWords; w++) {
                    diff += Long.bitCount(scratchAddWords[w] ^ tWords[w]);
                }
                costRow[j] = (short) Math.min(diff, N - diff);
            }
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

    private void computeTempTreeSplits(Node node) {
        int idx = getNodeIndex(node);
        BitSet bs = tempTreeSplits[idx];
        bs.clear();
        if (node.isLeaf()) {
            int id = idGroup.whichIdNumber(node.getIdentifier().getName());
            if (id >= 0) bs.set(id);
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                Node child = node.getChild(i);
                computeTempTreeSplits(child);
                bs.or(tempTreeSplits[getNodeIndex(child)]);
            }
        }
    }

    public double evaluateExactUtbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        return evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, movingBits);
    }

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        return evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, movingBits);
    }

    private void ensureDeltaCapacity() {
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
            deltaOldSplitsNode = Arrays.copyOf(deltaOldSplitsNode, newDepth);
            deltaOldSplitsBits = Arrays.copyOf(deltaOldSplitsBits, newDepth);

            for (int d = deltaMaxDepth; d < newDepth; d++) {
                deltaRows[d] = new int[dim];
                deltaOldRows[d] = new short[dim][dim];
                deltaOldU[d] = new int[dim];
                deltaOldV[d] = new int[dim];
                deltaOldRowsol[d] = new int[dim];
                deltaOldColsol[d] = new int[dim];
                deltaOldSplitsNode[d] = new Node[dim];
                deltaOldSplitsBits[d] = new BitSet[dim];
                for (int r = 0; r < dim; r++) {
                    deltaOldSplitsBits[d][r] = new BitSet(N);
                }
            }
            deltaMaxDepth = newDepth;
        }
    }

    private void pushEmptyDelta() {
        ensureDeltaCapacity();
        int d = deltaPointer;
        deltaRowsCount[d] = 0;
        System.arraycopy(u, 0, deltaOldU[d], 0, dim);
        System.arraycopy(v, 0, deltaOldV[d], 0, dim);
        System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
        System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;
    }

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        this.baseTree = baseTree;
        this.targetTree = targetTree;

        if (baseTree != null && targetTree != null) {
            clearHistory();
            this.idGroup = TreeUtils.getLeafIdGroup(baseTree);
            this.N = baseTree.getExternalNodeCount();

            int size1 = baseTree.getInternalNodeCount() - 1;
            int size2 = targetTree.getInternalNodeCount() - 1;
            this.dim = Math.max(size1, size2);

            this.assigncost = new short[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];
            this.u = new int[dim];
            this.v = new int[dim];

            this.numWords = (N + 63) >>> 6;
            this.scratchAddWords = new long[numWords];
            this.targetSplitWords = new long[dim][numWords];

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

            // Inicjalizacja prealokowanego stosu delty (Zero-Allocation)
            int depth = Math.max(128, N * 4);
            this.deltaMaxDepth = depth;
            this.deltaPointer = 0;
            this.deltaRowsCount = new int[depth];
            this.deltaRows = new int[depth][dim];
            this.deltaOldRows = new short[depth][dim][dim];
            this.deltaOldU = new int[depth][dim];
            this.deltaOldV = new int[depth][dim];
            this.deltaOldRowsol = new int[depth][dim];
            this.deltaOldColsol = new int[depth][dim];
            this.deltaOldDistance = new double[depth];
            this.deltaOldSplitsNode = new Node[depth][dim];
            this.deltaOldSplitsBits = new BitSet[depth][dim];
            for (int d = 0; d < depth; d++) {
                for (int r = 0; r < dim; r++) {
                    this.deltaOldSplitsBits[d][r] = new BitSet(N);
                }
            }

            int maxPoolSize = Math.max(128, N * 4);
            if (this.bitSetPool == null || this.bitSetPool.length < maxPoolSize || this.bitSetPool[0].size() < N) {
                this.bitSetPool = new BitSet[maxPoolSize];
                for (int i = 0; i < maxPoolSize; i++) {
                    this.bitSetPool[i] = new BitSet(N);
                }
            }

            int maxTreeNodes = N * 2 + 10;
            this.pathBuf = new Node[maxTreeNodes];

            if (this.tempTreeSplits == null || this.tempTreeSplits.length < maxTreeNodes || this.tempTreeSplits[0].size() < N) {
                this.tempTreeSplits = new BitSet[maxTreeNodes];
                for (int i = 0; i < maxTreeNodes; i++) {
                    this.tempTreeSplits[i] = new BitSet(N);
                }
            }

            if (this.scratchNewSplits == null || this.scratchNewSplits.length < dim + 5 || this.scratchNewSplits[0].size() < N) {
                this.scratchNewSplits = new BitSet[dim + 5];
                for (int i = 0; i < scratchNewSplits.length; i++) {
                    this.scratchNewSplits[i] = new BitSet(N);
                }
            }

            if (this.scratchRowUsed == null || this.scratchRowUsed.length < dim + 5) {
                this.scratchRowUsed = new boolean[dim + 5];
            }
            this.scratchCanonical = new BitSet(N);

            this.baseSplits = new IdentityHashMap<>();
            this.currentSplits = new IdentityHashMap<>();
            extractSplits(baseTree.getRoot(), idGroup, this.baseSplits, N, false);
            for (Map.Entry<Node, BitSet> e : baseSplits.entrySet()) {
                currentSplits.put(e.getKey(), (BitSet) e.getValue().clone());
            }

            this.targetSplits = new IdentityHashMap<>();
            extractSplits(targetTree.getRoot(), idGroup, this.targetSplits, N, true);

            this.rowToNode = new Node[dim];
            this.colToNode = new Node[dim];
            this.nodeToRow = new IdentityHashMap<>();

            int r = 0;
            for (int i = 0; i < baseTree.getInternalNodeCount(); i++) {
                Node n = baseTree.getInternalNode(i);
                if (n.isRoot()) continue;
                if (r < dim) {
                    rowToNode[r] = n;
                    nodeToRow.put(n, r);
                    r++;
                }
            }

            int c = 0;
            for (int j = 0; j < targetTree.getInternalNodeCount(); j++) {
                Node n = targetTree.getInternalNode(j);
                if (n.isRoot()) continue;
                if (c < dim) {
                    colToNode[c] = n;
                    c++;
                }
            }

            for (int j = 0; j < dim; j++) {
                Node n2 = colToNode[j];
                if (n2 != null) {
                    BitSet bs = targetSplits.get(n2);
                    if (bs != null) {
                        long[] words = bs.toLongArray();
                        System.arraycopy(words, 0, targetSplitWords[j], 0, words.length);
                    }
                }
            }

            buildInitialCostMatrix(N);
            this.currentDistance = LapSolver.lapShort(dim, assigncost, rowsol, colsol, u, v);

            this.splitToRow.clear();
            for (int i = 0; i < dim; i++) {
                Node n1 = rowToNode[i];
                if (n1 != null && currentSplits.containsKey(n1)) {
                    BitSet canonical = canonicalizeSplit(currentSplits.get(n1));
                    this.splitToRow.put(canonical, i);
                }
            }
        } else {
            this.currentDistance = 0;
            this.splitToRow.clear();
        }
    }

    private BitSet extractSplits(Node node, IdGroup idGroup, Map<Node, BitSet> map, int numLeaves, boolean polarize) {
        BitSet bs = new BitSet(numLeaves);
        if (node.isLeaf()) {
            int id = idGroup.whichIdNumber(node.getIdentifier().getName());
            if (id >= 0) bs.set(id);
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                bs.or(extractSplits(node.getChild(i), idGroup, map, numLeaves, polarize));
            }
        }
        BitSet splitToSave = (BitSet) bs.clone();
        if (polarize && splitToSave.get(0)) {
            splitToSave.flip(0, numLeaves);
        }
        map.put(node, splitToSave);
        return bs;
    }

    private void buildInitialCostMatrix(int numLeaves) {
        for (int i = 0; i < dim; i++) {
            Node n1 = rowToNode[i];
            BitSet canonicalSplit = new BitSet(numLeaves);
            if (n1 != null && currentSplits.containsKey(n1)) {
                canonicalSplit = (BitSet) currentSplits.get(n1).clone();
                if (canonicalSplit.get(0)) canonicalSplit.flip(0, numLeaves);
            }

            Arrays.fill(scratchAddWords, 0L);
            long[] words = canonicalSplit.toLongArray();
            System.arraycopy(words, 0, scratchAddWords, 0, words.length);

            short[] costRow = assigncost[i];
            for (int j = 0; j < dim; j++) {
                long[] tWords = targetSplitWords[j];
                int diff = 0;
                for (int w = 0; w < numWords; w++) {
                    diff += Long.bitCount(scratchAddWords[w] ^ tWords[w]);
                }
                costRow[j] = (short) Math.min(diff, numLeaves - diff);
            }
        }
    }

    private void updateRowSafelyAndSave(Map<Integer, BitSet> rowUpdates) {
        if (rowUpdates == null || rowUpdates.isEmpty()) return;

        ensureDeltaCapacity();
        int d = deltaPointer;
        int count = 0;

        for (Map.Entry<Integer, BitSet> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            deltaRows[d][count] = r;
            System.arraycopy(assigncost[r], 0, deltaOldRows[d][count], 0, dim);

            Node n = rowToNode[r];
            if (n != null && currentSplits.containsKey(n)) {
                deltaOldSplitsNode[d][count] = n;
                BitSet oldBs = currentSplits.get(n);
                deltaOldSplitsBits[d][count].clear();
                if (oldBs != null) {
                    deltaOldSplitsBits[d][count].or(oldBs);
                }
            } else {
                deltaOldSplitsNode[d][count] = null;
            }
            count++;
        }

        deltaRowsCount[d] = count;
        System.arraycopy(u, 0, deltaOldU[d], 0, dim);
        System.arraycopy(v, 0, deltaOldV[d], 0, dim);
        System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
        System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;

        int numLeaves = baseTree.getExternalNodeCount();

        for (Map.Entry<Integer, BitSet> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            BitSet newSplit = entry.getValue();

            Node n = rowToNode[r];
            if (n != null) {
                BitSet currBs = currentSplits.get(n);
                if (currBs == null) {
                    currBs = new BitSet(numLeaves);
                    currentSplits.put(n, currBs);
                }
                currBs.clear();
                currBs.or(newSplit);
            }

            canonicalizeInto(newSplit, scratchCanonical);

            Arrays.fill(scratchAddWords, 0L);
            long[] words = scratchCanonical.toLongArray();
            System.arraycopy(words, 0, scratchAddWords, 0, words.length);

            short[] costRow = assigncost[r];
            for (int j = 0; j < dim; j++) {
                long[] tWords = targetSplitWords[j];
                int diff = 0;
                for (int w = 0; w < numWords; w++) {
                    diff += Long.bitCount(scratchAddWords[w] ^ tWords[w]);
                }
                costRow[j] = (short) Math.min(diff, numLeaves - diff);
            }
        }

        if (dim > 0 && count > 0) {
            int[] changedRowsParam = (count < cachedKArrays.length) ? cachedKArrays[count] : new int[count];
            System.arraycopy(deltaRows[d], 0, changedRowsParam, 0, count);
            this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
        }
    }

    private void undoDeltaStack() {
        if (deltaPointer <= 0) return;
        deltaPointer--;
        int d = deltaPointer;
        int count = deltaRowsCount[d];

        for (int i = 0; i < count; i++) {
            int r = deltaRows[d][i];
            System.arraycopy(deltaOldRows[d][i], 0, assigncost[r], 0, dim);
            Node n = deltaOldSplitsNode[d][i];
            if (n != null) {
                BitSet bs = currentSplits.get(n);
                if (bs == null) {
                    bs = new BitSet(N);
                    currentSplits.put(n, bs);
                }
                bs.clear();
                bs.or(deltaOldSplitsBits[d][i]);
            }
        }

        System.arraycopy(deltaOldU[d], 0, u, 0, dim);
        System.arraycopy(deltaOldV[d], 0, v, 0, dim);
        System.arraycopy(deltaOldRowsol[d], 0, rowsol, 0, dim);
        System.arraycopy(deltaOldColsol[d], 0, colsol, 0, dim);

        this.currentDistance = deltaOldDistance[d];
    }

    public double getFixedDistanceForRegraft(Node targetNode, Node wanderingSource, BitSet pruneMask, Node pruneNode) {
        Node resolvedWandering = resolveWandering(wanderingSource, pruneNode);
        Integer r_w = nodeToRow.get(resolvedWandering);

        if (r_w == null) {
            return evaluateSprRegraft(pruneNode, targetNode);
        }

        BitSet origT = baseSplits.get(targetNode);
        BitSet pureT = getScratchBitSet();
        pureT.or(origT);
        pureT.andNot(pruneMask);

        BitSet combinedX = getScratchBitSet();
        combinedX.or(origT);
        combinedX.or(pruneMask);

        BitSet currentT = currentSplits.get(targetNode);
        BitSet shadowEdge;
        if (currentT == null) {
            shadowEdge = combinedX;
        } else if (currentT.equals(pureT)) {
            shadowEdge = combinedX;
        } else {
            shadowEdge = pureT;
        }

        canonicalizeInto(shadowEdge, scratchCanonical);

        System.arraycopy(assigncost[r_w], 0, scratchSavedOldRows[0], 0, dim);
        System.arraycopy(u, 0, scratchSavedU, 0, dim);
        System.arraycopy(v, 0, scratchSavedV, 0, dim);
        System.arraycopy(rowsol, 0, scratchSavedRowsol, 0, dim);
        System.arraycopy(colsol, 0, scratchSavedColsol, 0, dim);

        Arrays.fill(scratchAddWords, 0L);
        long[] words = scratchCanonical.toLongArray();
        System.arraycopy(words, 0, scratchAddWords, 0, words.length);

        short[] costRow = assigncost[r_w];
        for (int j = 0; j < dim; j++) {
            long[] tWords = targetSplitWords[j];
            int diff = 0;
            for (int w = 0; w < numWords; w++) {
                diff += Long.bitCount(scratchAddWords[w] ^ tWords[w]);
            }
            costRow[j] = (short) Math.min(diff, N - diff);
        }

        double fixedDist = LapSolver.lapShort(dim, assigncost, rowsol, colsol, u, v);

        System.arraycopy(scratchSavedOldRows[0], 0, assigncost[r_w], 0, dim);
        System.arraycopy(scratchSavedU, 0, u, 0, dim);
        System.arraycopy(scratchSavedV, 0, v, 0, dim);
        System.arraycopy(scratchSavedRowsol, 0, rowsol, 0, dim);
        System.arraycopy(scratchSavedColsol, 0, colsol, 0, dim);

        return fixedDist;
    }

    private BitSet getSplitBits(Node node) {
        if (node.isLeaf()) {
            BitSet bs = new BitSet(baseTree.getExternalNodeCount());
            int id = idGroup.whichIdNumber(node.getIdentifier().getName());
            if (id >= 0) bs.set(id);
            return bs;
        } else {
            return currentSplits.get(node);
        }
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
    // METODY KONTRAKTU RootedTbrMetric
    // =========================================================================

    private Integer findFloatingRow(Node pruneNode, Node wanderingSource) {
        if (wanderingSource == null) return null;

        if (!wanderingSource.isRoot()) {
            return nodeToRow.get(wanderingSource);
        }

        Node root = baseTree.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            Node child = root.getChild(i);
            if (child != pruneNode && !child.isLeaf()) {
                return nodeToRow.get(child);
            }
        }
        return null;
    }

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        this.currentPrunedLeaves = (BitSet) getSplitBits(pruneNode).clone();
        BitSet P = this.currentPrunedLeaves;
        Map<Integer, BitSet> updates = new HashMap<>();

        Node curr = (pruneNode.getParent() != null) ? pruneNode.getParent().getParent() : null;
        while (curr != null) {
            Integer r = nodeToRow.get(curr);
            if (r != null) {
                BitSet bs = (BitSet) currentSplits.get(curr).clone();
                bs.andNot(P);
                if (bs.cardinality() == N || bs.cardinality() == 0) bs.clear();
                updates.put(r, bs);
            }
            curr = curr.getParent();
        }

        Integer rFloating = findFloatingRow(pruneNode, wanderingSource);
        if (rFloating != null) {
            updates.put(rFloating, new BitSet(N));
        }

        updateRowSafelyAndSave(updates);
    }

    public void setTargetRoot(Node pruneNode, Node wanderingSource) {
        BitSet P = (this.currentPrunedLeaves != null) ? this.currentPrunedLeaves : getSplitBits(pruneNode);
        Map<Integer, BitSet> updates = new HashMap<>();

        Integer rFloating = findFloatingRow(pruneNode, wanderingSource);
        if (rFloating != null) {
            BitSet newSplit = new BitSet(N);
            newSplit.or(P);
            updates.put(rFloating, newSplit);
        }

        updateRowSafelyAndSave(updates);
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {
        setTargetRoot(pruneNode, wanderingSource);
    }

    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        BitSet P = (this.currentPrunedLeaves != null) ? this.currentPrunedLeaves : getSplitBits(pruneNode);
        Map<Integer, BitSet> updates = new HashMap<>();

        Integer rFloating = findFloatingRow(pruneNode, wanderingSource);
        if (rFloating != null) {
            BitSet childSplit = getSplitBits(childTarget);
            BitSet newSplit = (BitSet) childSplit.clone();
            newSplit.or(P);
            if (newSplit.cardinality() == N) newSplit.clear();
            updates.put(rFloating, newSplit);
        }

        Integer rParent = nodeToRow.get(parentTarget);
        if (rParent != null && !rParent.equals(rFloating)) {
            BitSet parentSplit = currentSplits.get(parentTarget);
            if (parentSplit != null) {
                BitSet newParent = (BitSet) parentSplit.clone();
                newParent.or(P);
                if (newParent.cardinality() == N) newParent.clear();
                updates.put(rParent, newParent);
            }
        }

        updateRowSafelyAndSave(updates);
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        moveTargetDown(parentTarget, childTarget, pruneNode, wanderingSource);
    }

    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        undoDeltaStack();
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        undoDeltaStack();
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
            BitSet childSplit = getSplitBits(childReroot);
            if (childSplit != null) {
                BitSet newParentSplit = (BitSet) currentPrunedLeaves.clone();
                newParentSplit.andNot(childSplit);
                if (newParentSplit.cardinality() == N) newParentSplit.clear();

                Map<Integer, BitSet> updates = new HashMap<>();
                updates.put(rParent, newParentSplit);

                updateRowSafelyAndSave(updates);
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
    // NNI, SPR & ECR
    // =========================================================================

    public boolean applyNniStep(Node nodeToUpdate, BitSet bitsOut, BitSet bitsIn) {
        Integer rIndex = nodeToRow.get(nodeToUpdate);
        if (rIndex == null) return false;

        BitSet newSplit = (BitSet) currentSplits.get(nodeToUpdate).clone();
        if (bitsOut != null) newSplit.andNot(bitsOut);
        if (bitsIn != null) newSplit.or(bitsIn);

        Map<Integer, BitSet> updates = new HashMap<>();
        updates.put(rIndex, newSplit);
        updateRowSafelyAndSave(updates);
        return true;
    }

    public void undoNniStep() {
        undoDeltaStack();
    }

    @Override
    public double applyNni(NniMove move) {
        Node nodeA = move.movingSubtree;
        Node nodeB = move.swapPartner;

        Node edgeNode = (nodeA.getParent() != baseTree.getRoot() && nodeA.getParent() != nodeB.getParent())
                ? nodeA.getParent() : nodeB.getParent();

        Integer rIndex = nodeToRow.get(edgeNode);
        if (rIndex != null) {
            BitSet oldSplit = currentSplits.get(edgeNode);
            BitSet newSplit = (BitSet) oldSplit.clone();
            newSplit.xor(getSplitBits(nodeA));
            newSplit.xor(getSplitBits(nodeB));

            Map<Integer, BitSet> updates = new HashMap<>();
            updates.put(rIndex, newSplit);
            updateRowSafelyAndSave(updates);
            nniPushCountHistory.push(1);
        } else {
            nniPushCountHistory.push(0);
        }
        return this.currentDistance;
    }

    @Override
    public void undoNni(NniMove move) {
        int pushes = nniPushCountHistory.isEmpty() ? 0 : nniPushCountHistory.pop();
        for (int i = 0; i < pushes; i++) {
            undoDeltaStack();
        }
    }

    @Override public void applySprPrune(Node pruneNode) { saveCurrentStateToHistory(); }
    @Override public void undoSprPrune(Node pruneNode) { undoSprRegraftStep(); }
    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) { saveCurrentStateToHistory(); }
    @Override public void undoSprRegraftStep() {
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
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        UsprUtils utils = new UsprUtils();
        Tree neighbor = utils.createUsprTree(baseTree, pruneNode, targetNode);
        try {
            if (neighbor instanceof pal.tree.SimpleTree) {
                ((pal.tree.SimpleTree) neighbor).createNodeList();
            }
            return msMetricFull.getDistance(neighbor, targetTree);
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }
    }

    @Override
    public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        double dist = commit2sEcrMove(top, m1, m2, b, template);
        undoDeltaStack();
        return dist;
    }

    @Override
    public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        Map<Integer, BitSet> updates = new HashMap<>();
        BitSet[] bBits = new BitSet[4];
        for (int i = 0; i < 4; i++) {
            bBits[i] = getSplitBits(b[i]);
        }

        BitSet newM1 = new BitSet();
        BitSet newM2 = new BitSet();

        if (template.isFork) {
            newM1.or(bBits[template.indices[0]]);
            newM1.or(bBits[template.indices[1]]);
            newM2.or(bBits[template.indices[2]]);
            newM2.or(bBits[template.indices[3]]);
        } else {
            newM2.or(bBits[template.indices[2]]);
            newM2.or(bBits[template.indices[3]]);
            newM1.or(bBits[template.indices[1]]);
            newM1.or(newM2);
        }

        Integer r1 = nodeToRow.get(m1);
        if (r1 != null) updates.put(r1, newM1);
        Integer r2 = nodeToRow.get(m2);
        if (r2 != null) updates.put(r2, newM2);

        updateRowSafelyAndSave(updates);
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
        Map<Integer, BitSet> updates = new HashMap<>();
        BitSet[] bBits = new BitSet[5];
        for (int i = 0; i < 5; i++) {
            bBits[i] = getSplitBits(b[i]);
        }

        Node[] available = cluster.toArray(new Node[0]);
        int[] idxArr = {1};

        compute3sEcrTemplateBits(template, available[0], available, idxArr, bBits, updates);

        updateRowSafelyAndSave(updates);
        return this.currentDistance;
    }

    private BitSet compute3sEcrTemplateBits(SubtreeEcr3Utils.TopologyTemplate3sECR temp, Node currentInternal, Node[] available, int[] idxArr, BitSet[] bBits, Map<Integer, BitSet> updates) {
        BitSet myBits = new BitSet();

        if (temp.left.leafIndex != -1) {
            myBits.or(bBits[temp.left.leafIndex]);
        } else {
            int nextIdx = (idxArr[0] < available.length) ? idxArr[0]++ : available.length - 1;
            Node nextInternal = available[nextIdx];
            BitSet leftBits = compute3sEcrTemplateBits(temp.left, nextInternal, available, idxArr, bBits, updates);
            myBits.or(leftBits);
        }

        if (temp.right.leafIndex != -1) {
            myBits.or(bBits[temp.right.leafIndex]);
        } else {
            int nextIdx = (idxArr[0] < available.length) ? idxArr[0]++ : available.length - 1;
            Node nextInternal = available[nextIdx];
            BitSet rightBits = compute3sEcrTemplateBits(temp.right, nextInternal, available, idxArr, bBits, updates);
            myBits.or(rightBits);
        }

        if (currentInternal != available[0]) {
            Integer r = nodeToRow.get(currentInternal);
            if (r != null) {
                updates.put(r, myBits);
            }
        }

        return myBits;
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

        IdentityHashMap<Node, BitSet> splitsCopy = new IdentityHashMap<>();
        for (Map.Entry<Node, BitSet> entry : currentSplits.entrySet()) {
            splitsCopy.put(entry.getKey(), (BitSet) entry.getValue().clone());
        }
        splitHistory.push(splitsCopy);
    }

    private void clearHistory() {
        costHistory.clear();
        rowsolHistory.clear();
        colsolHistory.clear();
        uHistory.clear();
        vHistory.clear();
        distanceHistory.clear();
        splitHistory.clear();
        deltaPointer = 0;
        nniPushCountHistory.clear();
        splitToRow.clear();
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