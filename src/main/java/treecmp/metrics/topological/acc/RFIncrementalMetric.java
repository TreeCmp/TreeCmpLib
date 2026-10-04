package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.Tree;

import java.util.*;

public class RFIncrementalMetric extends BaseRFIncrementalMetric {

    protected Tree baseTreeRef;
    protected Tree targetTreeRef;

    private int N;
    private double initialDistance;
    private int baseSplitsCount;
    private BitSet[] baseSplitsArray = new BitSet[256];
    private final Map<Node, BitSet> baseClusterMap = new IdentityHashMap<>();

    // Pula robocza wielokrotnego użytku - zero alokacji na stercie w pętli ewaluacji
    private BitSet[] pool;
    private int poolIdx;

    private BitSet[] removedArray = new BitSet[256];
    private BitSet[] addedArray = new BitSet[256];
    private int removedCount;
    private int addedCount;

    private BitSet[] finalSplitsArray = new BitSet[512];
    private int finalCount;

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        this.baseTreeRef = baseTree;
        this.targetTreeRef = targetTree;
        super.initCalculationState(baseTree, targetTree);
        this.N = (baseTree != null) ? baseTree.getExternalNodeCount() : 0;
        this.initialDistance = this.currentDistance;

        ensureCapacity(this.N);
        refreshBaseSplits();
    }

    @Override
    public void commit() {
        super.commit();
        this.initialDistance = this.currentDistance;
        refreshBaseSplits();
    }

    private void ensureCapacity(int leafCount) {
        if (leafCount <= 0) return;
        if (pool == null || pool[0].size() < leafCount) {
            pool = new BitSet[512];
            for (int i = 0; i < pool.length; i++) {
                pool[i] = new BitSet(leafCount);
            }
        }
        int maxSplits = leafCount * 2 + 64;
        if (baseSplitsArray.length < maxSplits) {
            baseSplitsArray = new BitSet[maxSplits];
            finalSplitsArray = new BitSet[maxSplits];
            removedArray = new BitSet[maxSplits];
            addedArray = new BitSet[maxSplits];
        }
    }

    private BitSet getScratch() {
        if (poolIdx >= pool.length) {
            BitSet[] newPool = new BitSet[pool.length * 2];
            System.arraycopy(pool, 0, newPool, 0, pool.length);
            for (int i = pool.length; i < newPool.length; i++) {
                newPool[i] = new BitSet(allLeavesMask.size());
            }
            pool = newPool;
        }
        BitSet bs = pool[poolIdx++];
        bs.clear();
        return bs;
    }

    private BitSet getNormalized(BitSet rawSplit) {
        if (rawSplit == null) return null;
        if (rawSplit.get(0)) {
            BitSet inv = getScratch();
            inv.or(rawSplit);
            inv.xor(allLeavesMask);
            return inv;
        }
        return rawSplit;
    }

    private void refreshBaseSplits() {
        this.baseSplitsCount = 0;
        this.baseClusterMap.clear();

        for (Map.Entry<Node, BitSet> e : nodeBitSets.entrySet()) {
            Node n = e.getKey();
            BitSet bs = e.getValue();
            baseClusterMap.put(n, bs);
            if (isNonTrivial(bs.cardinality(), N)) {
                baseSplitsArray[baseSplitsCount++] = normalizeSplit(bs);
            }
        }
    }

    @Override
    protected boolean isNonTrivial(int card, int total) {
        return card > 1 && card < total - 1;
    }

    @Override
    protected BitSet normalizeSplit(BitSet rawSplit) {
        if (rawSplit == null) return null;
        if (rawSplit.get(0)) {
            BitSet inverted = (BitSet) rawSplit.clone();
            inverted.xor(allLeavesMask);
            return inverted;
        }
        return rawSplit;
    }

    private Node findLca(Node a, Node b) {
        if (a == null || b == null) return null;
        int dA = getDepth(a);
        int dB = getDepth(b);

        while (dA > dB && a != null) { a = a.getParent(); dA--; }
        while (dB > dA && b != null) { b = b.getParent(); dB--; }

        while (a != b && a != null && b != null) {
            a = a.getParent();
            b = b.getParent();
        }
        return a;
    }

    private int getDepth(Node n) {
        int d = 0;
        Node curr = n;
        while (curr != null) {
            d++;
            curr = curr.getParent();
        }
        return d;
    }

    // =========================================================================
    // OBSŁUGA uSPR I uTBR (BEZALOKACYJNA REKONSTRUKCJA BIPARTYCJI)
    // =========================================================================

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        return evaluateExactUTbrDistance(pruneNode, pruneNode, targetNode, null);
    }

    public double evaluateExactUTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        if (this.baseTreeRef == null || this.targetTreeRef == null || pruneNode == null || targetNode == null) {
            return this.initialDistance;
        }

        Node root = this.baseTreeRef.getRoot();
        Node pParent = pruneNode.getParent();
        if (pParent == null) return Double.POSITIVE_INFINITY;

        if (targetNode == pruneNode || targetNode == pParent) {
            return this.initialDistance;
        }

        // Zabezpieczenie przed wpięciem do odciętego komponentu
        Node temp = targetNode;
        while (temp != null) {
            if (temp == pruneNode) return Double.POSITIVE_INFINITY;
            temp = temp.getParent();
        }

        // Zabezpieczenie przed no-op (działa tylko dla węzła binarnego pParent != root)
        if (rerootNode == pruneNode && pParent != root) {
            Node sibling = (pParent.getChild(0) == pruneNode) ? pParent.getChild(1) : pParent.getChild(0);
            if (targetNode == sibling) {
                return this.initialDistance;
            }
        }

        BitSet LP = baseClusterMap.get(pruneNode);
        if (LP == null) return Double.POSITIVE_INFINITY;

        poolIdx = 0;
        removedCount = 0;
        addedCount = 0;

        // --- A. DRZEWO T2 (Używamy wyłącznie niezmienniczej bazy baseClusterMap) ---
        if (pParent != root) {
            BitSet bsParent = baseClusterMap.get(pParent);
            if (bsParent != null) {
                removedArray[removedCount++] = getNormalized(bsParent);
            }
        }

        Node lca = findLca(pParent, targetNode);

        if (pParent != lca) {
            Node curr = pParent.getParent();
            while (curr != null && curr != lca) {
                BitSet oldC = baseClusterMap.get(curr);
                if (oldC != null) {
                    removedArray[removedCount++] = getNormalized(oldC);
                    BitSet newC = getScratch();
                    newC.or(oldC);
                    newC.andNot(LP);
                    addedArray[addedCount++] = getNormalized(newC);
                }
                curr = curr.getParent();
            }
        }

        if (targetNode != lca) {
            Node currT = targetNode.getParent();
            while (currT != null && currT != lca) {
                BitSet oldC = baseClusterMap.get(currT);
                if (oldC != null) {
                    removedArray[removedCount++] = getNormalized(oldC);
                    BitSet newC = getScratch();
                    newC.or(oldC);
                    newC.or(LP);
                    addedArray[addedCount++] = getNormalized(newC);
                }
                currT = currT.getParent();
            }
        }

        if (targetNode == lca && targetNode != root) {
            BitSet oldC = baseClusterMap.get(targetNode);
            if (oldC != null) {
                removedArray[removedCount++] = getNormalized(oldC);
                BitSet newC = getScratch();
                newC.or(oldC);
                newC.andNot(LP);
                addedArray[addedCount++] = getNormalized(newC);
            }
        }

        // Węzeł wpięcia W
        BitSet targetBs = baseClusterMap.get(targetNode);
        if (targetBs != null) {
            BitSet newW = getScratch();
            newW.or(targetBs);
            newW.or(LP);
            addedArray[addedCount++] = getNormalized(newW);
        }

        // --- B. DRZEWO T1 (Przekorzenienie uTBR) ---
        if (rerootNode != pruneNode && rerootNode != null) {
            BitSet rCluster = baseClusterMap.get(rerootNode);
            if (rCluster != null) {
                BitSet newR = getScratch();
                newR.or(LP);
                newR.andNot(rCluster);
                addedArray[addedCount++] = getNormalized(newR);
            }

            Node currOnPath = rerootNode.getParent();
            while (currOnPath != null && currOnPath != pruneNode) {
                BitSet oldC = baseClusterMap.get(currOnPath);
                if (oldC != null) {
                    removedArray[removedCount++] = getNormalized(oldC);
                    BitSet newC = getScratch();
                    newC.or(LP);
                    newC.andNot(oldC);
                    addedArray[addedCount++] = getNormalized(newC);
                }
                currOnPath = currOnPath.getParent();
            }
        }

        // --- C. BEZALOKACYJNA DEDUPLIKACJA W KILKADZIESIĄT NANOSEKUND ---
        finalCount = 0;

        // 1. Przefiltrowanie bazy ze starych bipartycji
        for (int i = 0; i < baseSplitsCount; i++) {
            BitSet b = baseSplitsArray[i];
            boolean isRemoved = false;
            for (int r = 0; r < removedCount; r++) {
                if (b.equals(removedArray[r])) {
                    isRemoved = true;
                    break;
                }
            }
            if (!isRemoved) {
                finalSplitsArray[finalCount++] = b;
            }
        }

        // 2. Dołączenie elementów dodanych z pełną deduplikacją
        for (int i = 0; i < addedCount; i++) {
            BitSet a = addedArray[i];
            if (a == null) continue;
            int card = a.cardinality();
            if (!isNonTrivial(card, N)) continue;

            boolean duplicate = false;
            for (int f = 0; f < finalCount; f++) {
                if (a.equals(finalSplitsArray[f])) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                finalSplitsArray[finalCount++] = a;
            }
        }

        // 3. Zliczenie wspólnych bipartycji
        int shared = 0;
        for (int i = 0; i < finalCount; i++) {
            if (targetSplits.contains(finalSplitsArray[i])) {
                shared++;
            }
        }

        return (finalCount + targetSplits.size() - 2.0 * shared) / 2.0;
    }

    public double evaluateExactUtbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        return evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, movingBits);
    }

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        return evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, movingBits);
    }
}