package treecmp.metrics.topological.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.AlignInfo;
import treecmp.common.ClusterDist;
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

    // PREALOKOWANE BUFORY I STRUKTURY DLA SZYBKIEGO uTBR (Zero-Allocation)
    private final Set<BitSet> removedSplitsBuf = new HashSet<>();
    private final Set<BitSet> addedSplitsBuf = new HashSet<>();
    private final Map<BitSet, Integer> splitToRow = new HashMap<>();

    private final List<BitSet> toRemove = new ArrayList<>(16);
    private final List<BitSet> toAdd = new ArrayList<>(16);

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

    public BitSet getSplit(Node n) {
        return getSplitBits(n);
    }

    private Node findLca(Node a, Node b) {
        if (a == null || b == null) return null;
        int dA = getNodeDepth(a);
        int dB = getNodeDepth(b);

        while (dA > dB && a != null) { a = a.getParent(); dA--; }
        while (dB > dB && b != null) { b = b.getParent(); dB--; }

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

    private BitSet canonicalizeSplit(BitSet bs) {
        if (bs == null) return null;
        BitSet clone = (BitSet) bs.clone();
        if (clone.get(0)) {
            clone.flip(0, N);
        }
        return clone;
    }

    private boolean isNonTrivialSplit(BitSet bs) {
        if (bs == null) return false;
        int card = bs.cardinality();
        return card > 1 && card < N - 1;
    }

    // =========================================================================
    // PRZYROSTOWA EWALUACJA uTBR DLA MS (BEZALOKACYJNA Z BEZPIECZNYM FALLBACKIEM)
    // =========================================================================

    public double evaluateExactUTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        if (pruneNode == null || rerootNode == null || targetNode == null || this.targetTree == null || this.baseTree == null) {
            return Double.POSITIVE_INFINITY;
        }

        Node root = this.baseTree.getRoot();
        Node pParent = pruneNode.getParent();
        if (pParent == null) return Double.POSITIVE_INFINITY;

        BitSet LP = getSplitBits(pruneNode);
        if (LP == null) return Double.POSITIVE_INFINITY;

        removedSplitsBuf.clear();
        addedSplitsBuf.clear();

        // 1. Zmiany w komponencie T2
        if (pParent != root) {
            BitSet bsParent = getSplitBits(pParent);
            if (bsParent != null) removedSplitsBuf.add(canonicalizeSplit(bsParent));
        }

        Node lca = findLca(pParent, targetNode);

        if (pParent != lca) {
            Node curr = pParent.getParent();
            while (curr != null && curr != lca) {
                BitSet oldC = getSplitBits(curr);
                if (oldC != null) {
                    removedSplitsBuf.add(canonicalizeSplit(oldC));
                    BitSet newC = (BitSet) oldC.clone();
                    newC.andNot(LP);
                    addedSplitsBuf.add(canonicalizeSplit(newC));
                }
                curr = curr.getParent();
            }
        }

        if (targetNode != lca) {
            Node currT = targetNode.getParent();
            while (currT != null && currT != lca) {
                BitSet oldC = getSplitBits(currT);
                if (oldC != null) {
                    removedSplitsBuf.add(canonicalizeSplit(oldC));
                    BitSet newC = (BitSet) oldC.clone();
                    newC.or(LP);
                    addedSplitsBuf.add(canonicalizeSplit(newC));
                }
                currT = currT.getParent();
            }
        }

        if (targetNode == lca && targetNode != root) {
            BitSet oldC = getSplitBits(targetNode);
            if (oldC != null) {
                removedSplitsBuf.add(canonicalizeSplit(oldC));
                BitSet newC = (BitSet) oldC.clone();
                newC.andNot(LP);
                addedSplitsBuf.add(canonicalizeSplit(newC));
            }
        }

        BitSet targetBs = getSplitBits(targetNode);
        if (targetBs != null) {
            BitSet newW = (BitSet) targetBs.clone();
            newW.or(LP);
            addedSplitsBuf.add(canonicalizeSplit(newW));
        }

        // 2. Przekorzenienie w komponencie T1
        if (rerootNode != pruneNode) {
            BitSet rCluster = getSplitBits(rerootNode);
            if (rCluster != null) {
                BitSet newR = (BitSet) LP.clone();
                newR.andNot(rCluster);
                addedSplitsBuf.add(canonicalizeSplit(newR));
            }

            Node currOnPath = rerootNode.getParent();
            while (currOnPath != null && currOnPath != pruneNode) {
                BitSet oldC = getSplitBits(currOnPath);
                if (oldC != null) {
                    removedSplitsBuf.add(canonicalizeSplit(oldC));
                    BitSet newC = (BitSet) LP.clone();
                    newC.andNot(oldC);
                    addedSplitsBuf.add(canonicalizeSplit(newC));
                }
                currOnPath = currOnPath.getParent();
            }
        }

        // 3. Wyodrębnienie wyłącznie nietrywialnych zmian
        toRemove.clear();
        for (BitSet bs : removedSplitsBuf) {
            if (isNonTrivialSplit(bs) && !addedSplitsBuf.contains(bs)) {
                toRemove.add(bs);
            }
        }

        toAdd.clear();
        for (BitSet bs : addedSplitsBuf) {
            if (isNonTrivialSplit(bs) && !removedSplitsBuf.contains(bs)) {
                toAdd.add(bs);
            }
        }

        if (toRemove.isEmpty() && toAdd.isEmpty()) {
            return this.currentDistance;
        }

        // 4. Bezpieczna weryfikacja parzystości zmian (Fallback klasyczny dla nietypowych cięć)
        if (toRemove.size() != toAdd.size() || toRemove.isEmpty()) {
            try {
                Tree tempTree = utbrUtils.createUtbrTree(this.baseTree, pruneNode, rerootNode, targetNode);
                if (tempTree != null) {
                    if (tempTree instanceof pal.tree.SimpleTree) {
                        pal.tree.TreeUtils.computeParentPointers(tempTree.getRoot());
                        ((pal.tree.SimpleTree) tempTree).createNodeList();
                    }
                    return msMetricFull.getDistance(tempTree, this.targetTree);
                }
            } catch (Exception ignored) {}
            return Double.POSITIVE_INFINITY;
        }

        int k = toRemove.size();

        // FAZA 1: Weryfikacja mapowania PRZED modyfikacją pamięci
        for (int i = 0; i < k; i++) {
            BitSet rem = toRemove.get(i);
            Integer r = splitToRow.get(rem);
            if (r == null) {
                try {
                    Tree tempTree = utbrUtils.createUtbrTree(this.baseTree, pruneNode, rerootNode, targetNode);
                    if (tempTree != null) {
                        if (tempTree instanceof pal.tree.SimpleTree) {
                            pal.tree.TreeUtils.computeParentPointers(tempTree.getRoot());
                            ((pal.tree.SimpleTree) tempTree).createNodeList();
                        }
                        return msMetricFull.getDistance(tempTree, this.targetTree);
                    }
                } catch (Exception ignored) {}
                return Double.POSITIVE_INFINITY;
            }
            scratchChangedRows[i] = r;
        }

        // FAZA 2: Zapis stanu bez alokacji tablic na stercie
        System.arraycopy(u, 0, scratchSavedU, 0, dim);
        System.arraycopy(v, 0, scratchSavedV, 0, dim);
        System.arraycopy(rowsol, 0, scratchSavedRowsol, 0, dim);
        System.arraycopy(colsol, 0, scratchSavedColsol, 0, dim);

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

        double newDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);

        // 5. Przywrócenie stanu macierzy (brak efektów ubocznych)
        for (int i = 0; i < k; i++) {
            System.arraycopy(scratchSavedOldRows[i], 0, assigncost[scratchChangedRows[i]], 0, dim);
        }
        System.arraycopy(scratchSavedU, 0, u, 0, dim);
        System.arraycopy(scratchSavedV, 0, v, 0, dim);
        System.arraycopy(scratchSavedRowsol, 0, rowsol, 0, dim);
        System.arraycopy(scratchSavedColsol, 0, colsol, 0, dim);

        return newDistance;
    }

    public double evaluateExactUtbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        return evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, movingBits);
    }

    private static class LapStateDelta {
        final int[] rows;
        final short[][] oldRows;
        final int[] oldU, oldV, oldRowsol, oldColsol;
        final double oldDistance;
        final Map<Node, BitSet> oldSplits;

        LapStateDelta(int[] rows, short[][] oldRows, int[] oldU, int[] oldV, int[] oldRowsol, int[] oldColsol, double oldDistance, Map<Node, BitSet> oldSplits) {
            this.rows = rows;
            this.oldRows = oldRows;
            this.oldU = oldU;
            this.oldV = oldV;
            this.oldRowsol = oldRowsol;
            this.oldColsol = oldColsol;
            this.oldDistance = oldDistance;
            this.oldSplits = oldSplits;
        }
    }

    private final Stack<LapStateDelta> deltaStack = new Stack<>();

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

            this.cachedKArrays = new int[32][];
            for (int i = 0; i < 32; i++) {
                this.cachedKArrays[i] = new int[i];
            }

            this.scratchSavedOldRows = new short[dim][dim];
            this.scratchSavedU = new int[dim];
            this.scratchSavedV = new int[dim];
            this.scratchSavedRowsol = new int[dim];
            this.scratchSavedColsol = new int[dim];
            this.scratchChangedRows = new int[dim];

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

        int[] rows = new int[rowUpdates.size()];
        short[][] oldRows = new short[rows.length][dim];
        Map<Node, BitSet> oldSplits = new IdentityHashMap<>();

        int idx = 0;
        for (Map.Entry<Integer, BitSet> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            rows[idx] = r;
            oldRows[idx] = Arrays.copyOf(assigncost[r], dim);

            Node n = rowToNode[r];
            if (n != null && currentSplits.containsKey(n)) {
                oldSplits.put(n, (BitSet) currentSplits.get(n).clone());
            }
            idx++;
        }

        deltaStack.push(new LapStateDelta(rows, oldRows, Arrays.copyOf(u, dim), Arrays.copyOf(v, dim),
                Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance, oldSplits));

        int numLeaves = baseTree.getExternalNodeCount();

        for (Map.Entry<Integer, BitSet> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            BitSet newSplit = entry.getValue();

            Node n = rowToNode[r];
            if (n != null) currentSplits.put(n, newSplit);

            BitSet canonicalSplit = (BitSet) newSplit.clone();
            if (canonicalSplit.get(0)) canonicalSplit.flip(0, numLeaves);

            Arrays.fill(scratchAddWords, 0L);
            long[] words = canonicalSplit.toLongArray();
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

        if (dim > 0 && rows.length > 0) {
            this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, rows);
        }
    }

    private void undoDeltaStack() {
        if (deltaStack.isEmpty()) return;
        LapStateDelta delta = deltaStack.pop();
        for (int i = 0; i < delta.rows.length; i++) {
            System.arraycopy(delta.oldRows[i], 0, assigncost[delta.rows[i]], 0, dim);
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
        Node resolvedWandering = resolveWandering(wanderingSource, pruneNode);
        Integer r_w = nodeToRow.get(resolvedWandering);

        if (r_w == null) {
            return evaluateSprRegraft(pruneNode, targetNode);
        }

        BitSet origT = baseSplits.get(targetNode);
        BitSet pureT = (BitSet) origT.clone();
        pureT.andNot(pruneMask);

        BitSet combinedX = (BitSet) origT.clone();
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

        int numLeaves = baseTree.getExternalNodeCount();
        if (shadowEdge.get(0)) shadowEdge.flip(0, numLeaves);

        short[] oldRow = Arrays.copyOf(assigncost[r_w], dim);
        int[] oldU = Arrays.copyOf(u, dim);
        int[] oldV = Arrays.copyOf(v, dim);
        int[] oldRowsol = Arrays.copyOf(rowsol, dim);
        int[] oldColsol = Arrays.copyOf(colsol, dim);

        Arrays.fill(scratchAddWords, 0L);
        long[] words = shadowEdge.toLongArray();
        System.arraycopy(words, 0, scratchAddWords, 0, words.length);

        short[] costRow = assigncost[r_w];
        for (int j = 0; j < dim; j++) {
            long[] tWords = targetSplitWords[j];
            int diff = 0;
            for (int w = 0; w < numWords; w++) {
                diff += Long.bitCount(scratchAddWords[w] ^ tWords[w]);
            }
            costRow[j] = (short) Math.min(diff, numLeaves - diff);
        }

        double fixedDist = LapSolver.lapShort(dim, assigncost, rowsol, colsol, u, v);

        System.arraycopy(oldRow, 0, assigncost[r_w], 0, dim);
        System.arraycopy(oldU, 0, u, 0, dim);
        System.arraycopy(oldV, 0, v, 0, dim);
        System.arraycopy(oldRowsol, 0, rowsol, 0, dim);
        System.arraycopy(oldColsol, 0, colsol, 0, dim);

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
            deltaStack.push(new LapStateDelta(new int[0], new short[0][0], Arrays.copyOf(u, dim), Arrays.copyOf(v, dim),
                    Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance, new IdentityHashMap<>()));
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

        deltaStack.push(new LapStateDelta(new int[0], new short[0][0], Arrays.copyOf(u, dim), Arrays.copyOf(v, dim),
                Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance, new IdentityHashMap<>()));
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
        deltaStack.clear();
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