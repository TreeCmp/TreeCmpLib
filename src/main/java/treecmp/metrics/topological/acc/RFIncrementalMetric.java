package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.acc.RootedTbrMetric;

import java.util.*;

public class RFIncrementalMetric extends BaseRFIncrementalMetric implements RootedTbrMetric {

    protected Tree baseTreeRef;
    protected Tree targetTreeRef;

    private int N;
    private double initialDistance;
    private int initialSharedSplits;
    private int sharedSplits;
    private int baseSplitsCount;
    private int activeSplitsCount;
    private BitSet[] baseSplitsArray = new BitSet[256];
    private final Map<Node, BitSet> baseClusterMap = new IdentityHashMap<>();

    private BitSet currentPrunedLeaves;
    private int[] deltaSharedStack = new int[2048];
    private int[] deltaCountStack = new int[2048];
    private int deltaSharedPtr = 0;

    // Bezalokacyjne bufory pomocnicze dla 2D-DFS
    private BitSet scratchBitSet1;
    private BitSet scratchBitSet2;
    private BitSet scratchNorm;

    // Pula robocza dla evaluateExactUTbrDistance
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

        this.sharedSplits = 0;
        for (int i = 0; i < baseSplitsCount; i++) {
            if (targetSplits.contains(baseSplitsArray[i])) {
                this.sharedSplits++;
            }
        }
        this.initialSharedSplits = this.sharedSplits;
        this.activeSplitsCount = this.baseSplitsCount;
        this.deltaSharedPtr = 0;
    }

    @Override
    public void commit() {
        super.commit();
        this.initialDistance = this.currentDistance;
        refreshBaseSplits();
        this.sharedSplits = 0;
        for (int i = 0; i < baseSplitsCount; i++) {
            if (targetSplits.contains(baseSplitsArray[i])) {
                this.sharedSplits++;
            }
        }
        this.initialSharedSplits = this.sharedSplits;
        this.activeSplitsCount = this.baseSplitsCount;
        this.deltaSharedPtr = 0;
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

    private void ensureCapacity(int leafCount) {
        if (leafCount <= 0) return;
        if (scratchBitSet1 == null || scratchBitSet1.size() < leafCount) {
            scratchBitSet1 = new BitSet(leafCount);
            scratchBitSet2 = new BitSet(leafCount);
            scratchNorm = new BitSet(leafCount);
        }
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
        if (deltaSharedStack.length < leafCount * 16) {
            deltaSharedStack = new int[leafCount * 16];
            deltaCountStack = new int[leafCount * 16];
        }
    }

    private void refreshBaseSplits() {
        this.baseSplitsCount = 0;
        this.baseClusterMap.clear();

        for (Map.Entry<Node, BitSet> e : nodeBitSets.entrySet()) {
            Node n = e.getKey();
            BitSet bs = e.getValue();
            baseClusterMap.put(n, bs);
            if (!n.isRoot() && isNonTrivial(bs.cardinality(), N)) {
                baseSplitsArray[baseSplitsCount++] = normalizeSplit(bs);
            }
        }
    }

    private void normalizeInto(BitSet src, BitSet dest) {
        dest.clear();
        dest.or(src);
        if (dest.get(0)) {
            dest.xor(allLeavesMask);
        }
    }

    private boolean isAncestorOf(Node ancestor, Node descendant) {
        Node curr = descendant.getParent();
        while (curr != null) {
            if (curr == ancestor) return true;
            curr = curr.getParent();
        }
        return false;
    }

    // =========================================================================
    // IMPLEMENTACJA KONTRAKTU RootedTbrMetric (O(1) CZASU, ZERO ALOKACJI)
    // =========================================================================

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        this.currentPrunedLeaves = baseClusterMap.get(pruneNode);
        this.sharedSplits = this.initialSharedSplits;
        this.activeSplitsCount = this.baseSplitsCount;
        this.deltaSharedPtr = 0;

        if (wanderingSource == null || wanderingSource.isRoot() || currentPrunedLeaves == null) {
            this.currentDistance = this.initialDistance;
            return;
        }

        // 1. Usunięcie bipartycji rozwiązanego węzła pParent (wanderingSource)
        BitSet bsParent = baseClusterMap.get(wanderingSource);
        if (bsParent != null && isNonTrivial(bsParent.cardinality(), N)) {
            this.activeSplitsCount--;
            normalizeInto(bsParent, scratchNorm);
            if (targetSplits.contains(scratchNorm)) {
                this.sharedSplits--;
            }
        }

        // 2. Aktualizacja właściwych przodków pParent aż do korzenia
        Node curr = wanderingSource.getParent();
        while (curr != null && !curr.isRoot()) {
            BitSet oldC = baseClusterMap.get(curr);
            if (oldC != null) {
                if (isNonTrivial(oldC.cardinality(), N)) {
                    this.activeSplitsCount--;
                    normalizeInto(oldC, scratchNorm);
                    if (targetSplits.contains(scratchNorm)) {
                        this.sharedSplits--;
                    }
                }
                scratchBitSet1.clear();
                scratchBitSet1.or(oldC);
                scratchBitSet1.andNot(currentPrunedLeaves);
                if (isNonTrivial(scratchBitSet1.cardinality(), N)) {
                    this.activeSplitsCount++;
                    normalizeInto(scratchBitSet1, scratchNorm);
                    if (targetSplits.contains(scratchNorm)) {
                        this.sharedSplits++;
                    }
                }
            }
            curr = curr.getParent();
        }

        this.currentDistance = (this.activeSplitsCount + this.targetSplits.size() - 2.0 * this.sharedSplits) / 2.0;
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {
        deltaSharedStack[deltaSharedPtr] = 0;
        deltaCountStack[deltaSharedPtr] = 0;
        deltaSharedPtr++;
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        if (currentPrunedLeaves == null) {
            deltaSharedStack[deltaSharedPtr] = 0;
            deltaCountStack[deltaSharedPtr] = 0;
            deltaSharedPtr++;
            return;
        }

        boolean pruneAtRoot = (wanderingSource != null && wanderingSource.isRoot());
        if (pruneAtRoot && parentTarget.isRoot()) {
            deltaSharedStack[deltaSharedPtr] = 0;
            deltaCountStack[deltaSharedPtr] = 0;
            deltaSharedPtr++;
            return;
        }

        int dShared = 0;
        int dCount = 0;

        // 1. Zdjęcie starej bipartycji parentTarget (o ile parentTarget != root)
        if (!parentTarget.isRoot()) {
            BitSet cParent = baseClusterMap.get(parentTarget);
            if (cParent != null) {
                scratchBitSet1.clear();
                scratchBitSet1.or(cParent);
                if (!pruneAtRoot && isAncestorOf(parentTarget, pruneNode)) {
                    scratchBitSet1.andNot(currentPrunedLeaves);
                }
                if (isNonTrivial(scratchBitSet1.cardinality(), N)) {
                    dCount--;
                    normalizeInto(scratchBitSet1, scratchNorm);
                    if (targetSplits.contains(scratchNorm)) dShared--;
                }
            }
        }

        // 2. Dodanie nowej bipartycji punktu wpięcia: C_T2(childTarget) U LP
        BitSet cChild = baseClusterMap.get(childTarget);
        if (cChild != null) {
            scratchBitSet2.clear();
            scratchBitSet2.or(cChild);
            if (!pruneAtRoot && isAncestorOf(childTarget, pruneNode)) {
                scratchBitSet2.andNot(currentPrunedLeaves);
            }
            scratchBitSet2.or(currentPrunedLeaves);
            if (isNonTrivial(scratchBitSet2.cardinality(), N)) {
                dCount++;
                normalizeInto(scratchBitSet2, scratchNorm);
                if (targetSplits.contains(scratchNorm)) dShared++;
            }
        }

        this.sharedSplits += dShared;
        this.activeSplitsCount += dCount;
        this.currentDistance = (this.activeSplitsCount + this.targetSplits.size() - 2.0 * this.sharedSplits) / 2.0;

        deltaSharedStack[deltaSharedPtr] = dShared;
        deltaCountStack[deltaSharedPtr] = dCount;
        deltaSharedPtr++;
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        deltaSharedPtr--;
        this.sharedSplits -= deltaSharedStack[deltaSharedPtr];
        this.activeSplitsCount -= deltaCountStack[deltaSharedPtr];
        this.currentDistance = (this.activeSplitsCount + this.targetSplits.size() - 2.0 * this.sharedSplits) / 2.0;
    }

    @Override
    public void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode) {
        if (parentReroot == pruneNode || currentPrunedLeaves == null) {
            deltaSharedStack[deltaSharedPtr] = 0;
            deltaCountStack[deltaSharedPtr] = 0;
            deltaSharedPtr++;
            return;
        }

        int dShared = 0;
        int dCount = 0;

        // 1. Zdjęcie starej bipartycji parentReroot w T1
        BitSet sOldRaw = baseClusterMap.get(parentReroot);
        if (sOldRaw != null && isNonTrivial(sOldRaw.cardinality(), N)) {
            dCount--;
            normalizeInto(sOldRaw, scratchNorm);
            if (targetSplits.contains(scratchNorm)) dShared--;
        }

        // 2. Dodanie odwróconej bipartycji LP \ C(childReroot) w T1
        BitSet cChild = baseClusterMap.get(childReroot);
        if (cChild != null) {
            scratchBitSet1.clear();
            scratchBitSet1.or(currentPrunedLeaves);
            scratchBitSet1.andNot(cChild);
            if (isNonTrivial(scratchBitSet1.cardinality(), N)) {
                dCount++;
                normalizeInto(scratchBitSet1, scratchNorm);
                if (targetSplits.contains(scratchNorm)) dShared++;
            }
        }

        this.sharedSplits += dShared;
        this.activeSplitsCount += dCount;
        this.currentDistance = (this.activeSplitsCount + this.targetSplits.size() - 2.0 * this.sharedSplits) / 2.0;

        deltaSharedStack[deltaSharedPtr] = dShared;
        deltaCountStack[deltaSharedPtr] = dCount;
        deltaSharedPtr++;
    }

    @Override
    public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {
        deltaSharedPtr--;
        this.sharedSplits -= deltaSharedStack[deltaSharedPtr];
        this.activeSplitsCount -= deltaCountStack[deltaSharedPtr];
        this.currentDistance = (this.activeSplitsCount + this.targetSplits.size() - 2.0 * this.sharedSplits) / 2.0;
    }

    @Override
    public void revertPrunedState(Node pruneNode, Node wanderingSource) {
        this.currentDistance = this.initialDistance;
        this.sharedSplits = this.initialSharedSplits;
        this.activeSplitsCount = this.baseSplitsCount;
        this.currentPrunedLeaves = null;
        this.deltaSharedPtr = 0;
    }

    // =========================================================================
    // EWALUACJA STATYCZNA DLA WYWOŁAŃ POZA WALKEREM
    // =========================================================================

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

        Node temp = targetNode;
        while (temp != null) {
            if (temp == pruneNode) return Double.POSITIVE_INFINITY;
            temp = temp.getParent();
        }

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

        BitSet targetBs = baseClusterMap.get(targetNode);
        if (targetBs != null) {
            BitSet newW = getScratch();
            newW.or(targetBs);
            newW.or(LP);
            addedArray[addedCount++] = getNormalized(newW);
        }

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

        finalCount = 0;
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