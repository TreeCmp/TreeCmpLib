package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.Tree;

import java.util.*;

public class RFIncrementalMetric extends BaseRFIncrementalMetric {

    protected Tree baseTreeRef;
    protected Tree targetTreeRef;

    private int N;
    private final List<BitSet> baseSplitsList = new ArrayList<>();
    private final Map<Node, Integer> nodeToSplitIndex = new IdentityHashMap<>();
    private final Map<Node, BitSet> baseClusterMap = new IdentityHashMap<>();

    // Pula robocza wielokrotnego użytku - zero alokacji w pętli ewaluacji
    private BitSet[] pool;
    private int poolIdx;

    private BitSet[] evalSplits;

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        this.baseTreeRef = baseTree;
        this.targetTreeRef = targetTree;
        super.initCalculationState(baseTree, targetTree);
        this.N = (baseTree != null) ? baseTree.getExternalNodeCount() : 0;

        ensurePool(this.N);
        refreshBaseState();
    }

    @Override
    public void commit() {
        super.commit();
        refreshBaseState();
    }

    private void ensurePool(int leafCount) {
        if (leafCount <= 0) return;
        if (pool == null || pool[0].size() < leafCount) {
            pool = new BitSet[512];
            for (int i = 0; i < pool.length; i++) {
                pool[i] = new BitSet(leafCount);
            }
        }
        if (evalSplits == null || evalSplits.length < leafCount * 2) {
            evalSplits = new BitSet[leafCount * 2];
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

    private void refreshBaseState() {
        this.baseSplitsList.clear();
        this.nodeToSplitIndex.clear();
        this.baseClusterMap.clear();

        for (Map.Entry<Node, BitSet> e : nodeBitSets.entrySet()) {
            Node n = e.getKey();
            BitSet bs = e.getValue();
            baseClusterMap.put(n, bs);
            if (isNonTrivial(bs.cardinality(), N)) {
                BitSet norm = normalizeSplit(bs);
                int idx = baseSplitsList.size();
                baseSplitsList.add(norm);
                nodeToSplitIndex.put(n, idx);
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

    private BitSet normalizeInPlace(BitSet bs) {
        if (bs != null && bs.get(0)) {
            bs.xor(allLeavesMask);
        }
        return bs;
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
            return this.currentDistance;
        }

        Node root = this.baseTreeRef.getRoot();
        Node pParent = pruneNode.getParent();
        if (pParent == null) return Double.POSITIVE_INFINITY;

        if (targetNode == pruneNode || targetNode == pParent) {
            return this.currentDistance;
        }

        Node temp = targetNode;
        while (temp != null) {
            if (temp == pruneNode) return Double.POSITIVE_INFINITY;
            temp = temp.getParent();
        }

        Node sibling = (pParent.getChild(0) == pruneNode) ? pParent.getChild(1) : pParent.getChild(0);
        if (rerootNode == pruneNode && targetNode == sibling) {
            return this.currentDistance;
        }

        BitSet LP = baseClusterMap.get(pruneNode);
        if (LP == null) return Double.POSITIVE_INFINITY;

        poolIdx = 0;
        int totalBaseSplits = baseSplitsList.size();

        // 0. Kopiujemy stan początkowy do ewaluacji (referencje)
        for (int i = 0; i < totalBaseSplits; i++) {
            evalSplits[i] = baseSplitsList.get(i);
        }

        // 1. pParent zapada się - jego bipartycja przestaje istnieć
        Integer pParentIdx = nodeToSplitIndex.get(pParent);
        if (pParentIdx != null) {
            evalSplits[pParentIdx] = null;
        }

        // 2. Ścieżka od pParent w górę do LCA (traci LP)
        Node lca = findLca(pParent, targetNode);
        if (pParent != lca) {
            Node curr = pParent.getParent();
            while (curr != null && curr != lca) {
                Integer idx = nodeToSplitIndex.get(curr);
                BitSet orig = baseClusterMap.get(curr);
                if (idx != null && orig != null) {
                    BitSet mod = getScratch();
                    mod.or(orig);
                    mod.andNot(LP);
                    normalizeInPlace(mod);
                    evalSplits[idx] = isNonTrivial(mod.cardinality(), N) ? mod : null;
                }
                curr = curr.getParent();
            }
        }

        // 3. Ścieżka od targetNode w górę do LCA (zyskuje LP)
        if (targetNode != lca) {
            Node currT = targetNode.getParent();
            while (currT != null && currT != lca) {
                Integer idx = nodeToSplitIndex.get(currT);
                BitSet orig = baseClusterMap.get(currT);
                if (idx != null && orig != null) {
                    BitSet mod = getScratch();
                    mod.or(orig);
                    mod.or(LP);
                    normalizeInPlace(mod);
                    evalSplits[idx] = isNonTrivial(mod.cardinality(), N) ? mod : null;
                }
                currT = currT.getParent();
            }
        }

        // 4. Gdy targetNode == lca
        if (targetNode == lca && targetNode != root) {
            Integer idx = nodeToSplitIndex.get(targetNode);
            BitSet orig = baseClusterMap.get(targetNode);
            if (idx != null && orig != null) {
                BitSet mod = getScratch();
                mod.or(orig);
                mod.andNot(LP);
                normalizeInPlace(mod);
                evalSplits[idx] = isNonTrivial(mod.cardinality(), N) ? mod : null;
            }
        }

        // 5. Przekorzenienie T1 w uTBR (Generuje nową krawędź R oraz modyfikuje ścieżkę do P)
        BitSet newR = null;
        if (rerootNode != pruneNode && rerootNode != null) {
            BitSet rOrig = baseClusterMap.get(rerootNode);
            if (rOrig != null) {
                newR = getScratch();
                newR.or(LP);
                newR.andNot(rOrig);
                normalizeInPlace(newR);
                if (!isNonTrivial(newR.cardinality(), N)) {
                    newR = null;
                }
            }

            Node currOnPath = rerootNode.getParent();
            while (currOnPath != null && currOnPath != pruneNode) {
                Integer idx = nodeToSplitIndex.get(currOnPath);
                BitSet orig = baseClusterMap.get(currOnPath);
                if (idx != null && orig != null) {
                    BitSet mod = getScratch();
                    mod.or(LP);
                    mod.andNot(orig);
                    normalizeInPlace(mod);
                    evalSplits[idx] = isNonTrivial(mod.cardinality(), N) ? mod : null;
                }
                currOnPath = currOnPath.getParent();
            }
        }

        // 6. Nowa bipartycja wpięcia W (w uSPR i uTBR)
        BitSet targetOrig = baseClusterMap.get(targetNode);
        BitSet newW = null;
        if (targetOrig != null) {
            newW = getScratch();
            newW.or(targetOrig);
            newW.or(LP);
            normalizeInPlace(newW);
            if (!isNonTrivial(newW.cardinality(), N)) {
                newW = null;
            }
        }

        // 7. Zliczanie unikalnych bipartycji z jednoczesną deduplikacją zapadniętych krawędzi (np. przy korzeniu)
        int shared = 0;
        int activeCount = 0;

        for (int i = 0; i < totalBaseSplits; i++) {
            BitSet bs = evalSplits[i];
            if (bs == null) continue;

            boolean isDup = false;
            for (int j = 0; j < i; j++) {
                if (bs.equals(evalSplits[j])) {
                    isDup = true;
                    break;
                }
            }
            if (isDup) continue;

            activeCount++;
            if (targetSplits.contains(bs)) {
                shared++;
            }
        }

        // 8. Weryfikacja i uwzględnienie nowej krawędzi W (z T2)
        if (newW != null) {
            boolean isDup = false;
            for (int i = 0; i < totalBaseSplits; i++) {
                if (newW.equals(evalSplits[i])) {
                    isDup = true;
                    break;
                }
            }
            if (!isDup) {
                activeCount++;
                if (targetSplits.contains(newW)) {
                    shared++;
                }
            }
        }

        // 9. Weryfikacja i uwzględnienie nowej krawędzi R (z T1 w uTBR)
        if (newR != null) {
            boolean isDup = false;
            for (int i = 0; i < totalBaseSplits; i++) {
                if (newR.equals(evalSplits[i])) {
                    isDup = true;
                    break;
                }
            }
            if (!isDup && newW != null && newR.equals(newW)) {
                isDup = true;
            }
            if (!isDup) {
                activeCount++;
                if (targetSplits.contains(newR)) {
                    shared++;
                }
            }
        }

        return (activeCount + targetSplits.size() - 2.0 * shared) / 2.0;
    }

    public double evaluateExactUtbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        return evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, movingBits);
    }

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        return evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, movingBits);
    }
}