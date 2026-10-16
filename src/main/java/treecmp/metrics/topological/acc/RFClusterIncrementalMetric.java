package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.spr.acc.RootedSprIncrementalMetric;
import treecmp.heuristics.tbr.acc.RootedTbrMetric;

import java.util.*;

public class RFClusterIncrementalMetric extends BaseRFIncrementalMetric implements
        RootedSprIncrementalMetric,
        RootedTbrMetric {

    private Tree baseTreeRef;
    private Tree targetTreeRef;
    private int N;

    private Node rootOfT2;
    private BitSet LP;
    private BitSet bsRootT2;

    // Przechowuje zmodyfikowane klastry (bs \ LP) wyłącznie dla przodków węzła odcięcia
    private final Map<Node, BitSet> cT2 = new IdentityHashMap<>();

    // Prealokowana pula BitSet dla przodków (Zero-Allocation w setPrunedState)
    private BitSet[] ancestorPool;
    private int ancestorPoolIdx = 0;

    private int initialShared;
    private int baseSharedAfterPrune;
    private int deltaSharedT1;
    private int deltaSharedT2;

    private double initialDistance;
    private double distanceBeforePrune;

    private Node currentPruneNode;
    private Node currentRerootNode;
    private Node currentTargetNode;

    // Prealokowane stosy delt dla O(1) moveTargetUp oraz moveRerootUp
    private int[] deltaRerootStack = new int[2048];
    private int deltaRerootPtr = 0;
    private int[] deltaTargetStack = new int[2048];
    private int deltaTargetPtr = 0;

    private BitSet scratchTarget;
    private BitSet scratchReroot;

    @Override
    public boolean isRooted() {
        return true;
    }

    @Override
    protected BitSet normalizeSplit(BitSet rawSplit) {
        return rawSplit;
    }

    @Override
    protected boolean isNonTrivial(int card, int total) {
        return card > 1 && card < total;
    }

    private boolean isSharedFast(BitSet bs) {
        if (bs == null) return false;
        int card = bs.cardinality();
        if (card <= 1 || card >= N) return false;
        return targetSplits.contains(bs);
    }

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        super.initCalculationState(baseTree, targetTree);
        this.baseTreeRef = baseTree;
        this.targetTreeRef = targetTree;

        if (baseTree != null && targetTree != null) {
            this.N = baseTree.getExternalNodeCount();
            ensureBuffers(this.N);

            int shared = 0;
            for (Map.Entry<Node, BitSet> entry : nodeBitSets.entrySet()) {
                Node node = entry.getKey();
                if (!node.isRoot() && !node.isLeaf()) {
                    if (isSharedFast(entry.getValue())) {
                        shared++;
                    }
                }
            }

            this.initialShared = shared;
            this.initialDistance = ((N - 2) + targetSplits.size() - 2.0 * shared) / 2.0;
            this.currentDistance = this.initialDistance;
            this.distanceBeforePrune = this.initialDistance;
        } else {
            this.initialShared = 0;
            this.currentDistance = 0.0;
            this.initialDistance = 0.0;
            this.distanceBeforePrune = 0.0;
        }
        doRevert();
    }

    private void ensureBuffers(int leafCount) {
        if (scratchTarget == null || scratchTarget.size() < leafCount) {
            scratchTarget = new BitSet(leafCount);
            scratchReroot = new BitSet(leafCount);
            bsRootT2 = new BitSet(leafCount);
        }
        int maxAncestors = leafCount * 2 + 16;
        if (ancestorPool == null || ancestorPool.length < maxAncestors || ancestorPool[0].size() < leafCount) {
            ancestorPool = new BitSet[maxAncestors];
            for (int i = 0; i < maxAncestors; i++) {
                ancestorPool[i] = new BitSet(leafCount);
            }
        }
        if (deltaRerootStack.length < maxAncestors * 4) {
            deltaRerootStack = new int[maxAncestors * 4];
            deltaTargetStack = new int[maxAncestors * 4];
        }
    }

    private BitSet getAncestorScratch() {
        if (ancestorPoolIdx >= ancestorPool.length) {
            BitSet[] newPool = new BitSet[ancestorPool.length * 2];
            System.arraycopy(ancestorPool, 0, newPool, 0, ancestorPool.length);
            for (int i = ancestorPool.length; i < newPool.length; i++) {
                newPool[i] = new BitSet(N);
            }
            ancestorPool = newPool;
        }
        BitSet bs = ancestorPool[ancestorPoolIdx++];
        bs.clear();
        return bs;
    }

    @Override
    public void commit() {
        super.commit();
        doRevert();
        if (this.baseTreeRef != null && this.targetTreeRef != null) {
            initCalculationState(this.baseTreeRef, this.targetTreeRef);
        }
    }

    private void doRevert() {
        this.currentPruneNode = null;
        this.currentRerootNode = null;
        this.currentTargetNode = null;
        this.deltaRerootPtr = 0;
        this.deltaTargetPtr = 0;
        this.currentDistance = this.distanceBeforePrune;
    }

    private void updateDistanceFromShared() {
        int totalShared = this.baseSharedAfterPrune + this.deltaSharedT1 + this.deltaSharedT2;
        this.currentDistance = ((N - 2) + targetSplits.size() - 2.0 * totalShared) / 2.0;
    }

    // =========================================================================
    // KONTRAKT WĘDROWCÓW (SPR / TBR) - O(depth) PRUNE, O(1) KROKI DFS
    // =========================================================================

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        if (pruneNode == null || pruneNode.isRoot() || this.baseTreeRef == null) {
            doRevert();
            return;
        }

        if (this.currentPruneNode == null) {
            this.distanceBeforePrune = this.currentDistance;
        }

        this.currentPruneNode = pruneNode;
        this.currentRerootNode = pruneNode;
        this.deltaRerootPtr = 0;
        this.deltaTargetPtr = 0;

        this.LP = nodeBitSets.get(pruneNode);
        Node p = pruneNode.getParent();
        Node root = this.baseTreeRef.getRoot();

        if (p == root) {
            this.rootOfT2 = (p.getChild(0) == pruneNode) ? p.getChild(1) : p.getChild(0);
        } else {
            this.rootOfT2 = root;
        }

        this.bsRootT2.clear();
        this.bsRootT2.or(allLeavesMask);
        this.bsRootT2.andNot(LP);

        this.cT2.clear();
        this.ancestorPoolIdx = 0;

        // Obliczamy bazową liczbę wspólnych klastrów (T1 + T2 bez wpięcia) w czasie O(depth)
        int baseShared = this.initialShared;

        if (p != root) {
            // Węzeł p ulega rozwiązaniu po odcięciu pruneNode
            if (isSharedFast(nodeBitSets.get(p))) {
                baseShared--;
            }
        } else if (!rootOfT2.isLeaf()) {
            // Gdy p == root, węzeł rootOfT2 staje się korzeniem T2 (obsługiwanym przez bsRootT2)
            if (isSharedFast(nodeBitSets.get(rootOfT2))) {
                baseShared--;
            }
        }

        // Aktualizacja wyłącznie właściwych przodków p na ścieżce do korzenia
        Node curr = (p != null) ? p.getParent() : null;
        while (curr != null && curr != root) {
            BitSet origBs = nodeBitSets.get(curr);
            if (isSharedFast(origBs)) {
                baseShared--;
            }
            BitSet modBs = getAncestorScratch();
            modBs.or(origBs);
            modBs.andNot(LP);
            cT2.put(curr, modBs);
            if (isSharedFast(modBs)) {
                baseShared++;
            }
            curr = curr.getParent();
        }

        this.baseSharedAfterPrune = baseShared;
        this.deltaSharedT1 = 0;
        this.deltaSharedT2 = isSharedFast(bsRootT2) ? 1 : 0;
        this.currentTargetNode = this.rootOfT2;
        updateDistanceFromShared();
    }

    public void setPrunedState(Node pruneNode) {
        setPrunedState(pruneNode, null);
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {
        if (this.currentPruneNode != pruneNode || this.LP == null) {
            setPrunedState(pruneNode, wanderingSource);
        }
        this.currentPruneNode = pruneNode;
        this.currentRerootNode = rerootNode;
        this.currentTargetNode = this.rootOfT2;
        this.deltaTargetPtr = 0;

        if (rerootNode == pruneNode) {
            this.deltaSharedT1 = 0;
            this.deltaRerootPtr = 0;
        }
        this.deltaSharedT2 = isSharedFast(bsRootT2) ? 1 : 0;
        updateDistanceFromShared();
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node wanderingSource) {
        Node r = (this.currentRerootNode != null) ? this.currentRerootNode : pruneNode;
        setTargetRoot(pruneNode, r, wanderingSource);
    }

    public void setTargetRoot(Node pruneNode) {
        setTargetRoot(pruneNode, (Node) null);
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        this.currentTargetNode = childTarget;

        // Gdy p == root, przejście z (rozwiązanego) root na rootOfT2 ustawia stan w korzeniu T2
        if (childTarget == this.rootOfT2) {
            int newDeltaT2 = isSharedFast(bsRootT2) ? 1 : 0;
            int dT2 = newDeltaT2 - this.deltaSharedT2;
            this.deltaSharedT2 = newDeltaT2;
            deltaTargetStack[deltaTargetPtr++] = dT2;
            updateDistanceFromShared();
            return;
        }

        int dT2 = 0;

        // 1. Stary klaster parentTarget znika (zastąpiony przez C_T2(parentTarget) U LP, który już istniał)
        BitSet bsOldParent = (parentTarget == this.rootOfT2) ? this.bsRootT2 : getCT2(parentTarget);
        if (isSharedFast(bsOldParent)) {
            dT2--;
        }

        // 2. Dochodzi nowy klaster wpięcia C_T2(childTarget) U LP
        BitSet bsChild = getCT2(childTarget);
        scratchTarget.clear();
        if (bsChild != null) scratchTarget.or(bsChild);
        scratchTarget.or(LP);
        if (isSharedFast(scratchTarget)) {
            dT2++;
        }

        this.deltaSharedT2 += dT2;
        deltaTargetStack[deltaTargetPtr++] = dT2;
        updateDistanceFromShared();
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        Node r = (this.currentRerootNode != null) ? this.currentRerootNode : pruneNode;
        moveTargetDown(parentTarget, childTarget, pruneNode, r, wanderingSource);
    }

    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode) {
        moveTargetDown(parentTarget, childTarget, pruneNode, (Node) null);
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        this.deltaSharedT2 -= deltaTargetStack[--deltaTargetPtr];
        this.currentTargetNode = parentTarget;
        updateDistanceFromShared();
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        Node r = (this.currentRerootNode != null) ? this.currentRerootNode : pruneNode;
        moveTargetUp(parentTarget, childTarget, pruneNode, r, wanderingSource);
    }

    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode) {
        moveTargetUp(parentTarget, childTarget, pruneNode, (Node) null);
    }

    @Override
    public void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode) {
        int dT1 = 0;
        if (parentReroot != pruneNode && this.LP != null) {
            BitSet cParent = nodeBitSets.get(parentReroot);
            if (isSharedFast(cParent)) {
                dT1--;
            }
            BitSet cChild = nodeBitSets.get(childReroot);
            if (cChild != null) {
                scratchReroot.clear();
                scratchReroot.or(LP);
                scratchReroot.andNot(cChild);
                if (isSharedFast(scratchReroot)) {
                    dT1++;
                }
            }
        }

        this.deltaSharedT1 += dT1;
        deltaRerootStack[deltaRerootPtr++] = dT1;

        this.currentRerootNode = childReroot;
        this.currentTargetNode = this.rootOfT2;
        this.deltaSharedT2 = isSharedFast(bsRootT2) ? 1 : 0;
        updateDistanceFromShared();
    }

    public void moveRerootDown(Node parentReroot, Node childReroot) {
        moveRerootDown(parentReroot, childReroot, this.currentPruneNode);
    }

    @Override
    public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {
        this.deltaSharedT1 -= deltaRerootStack[--deltaRerootPtr];
        this.currentRerootNode = parentReroot;
        this.currentTargetNode = this.rootOfT2;
        this.deltaSharedT2 = isSharedFast(bsRootT2) ? 1 : 0;
        updateDistanceFromShared();
    }

    public void moveRerootUp(Node parentReroot, Node childReroot) {
        moveRerootUp(parentReroot, childReroot, this.currentPruneNode);
    }

    // =========================================================================
    // OBSŁUGA WSZYSTKICH PRZECIĄŻEŃ REVERT (SPR + TBR)
    // =========================================================================

    @Override
    public void revertPrunedState(Node pruneNode, Node wanderingSource) {
        doRevert();
    }

    public void revertPrunedState(Node pruneNode, Node rerootNode, Node wanderingSource) {
        doRevert();
    }

    public void revertPrunedState(Node pruneNode) {
        doRevert();
    }

    public void revertPrunedState() {
        doRevert();
    }

    @Override
    public void undoSprPrune(Node pruneNode) {
        super.undoSprPrune(pruneNode);
        doRevert();
    }

    @Override
    public double getCurrentDistance() {
        return this.currentDistance;
    }

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        if (pruneNode == null || targetNode == null || pruneNode.isRoot() || this.baseTreeRef == null) {
            return Double.POSITIVE_INFINITY;
        }
        boolean needRevert = (this.currentPruneNode != pruneNode || this.LP == null);
        if (needRevert) {
            setPrunedState(pruneNode, null);
        }
        double dist = computeExactDistance(pruneNode, pruneNode, targetNode);
        if (needRevert) {
            doRevert();
        }
        return dist;
    }

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        if (pruneNode == null || targetNode == null || pruneNode.isRoot() || this.baseTreeRef == null) {
            return Double.POSITIVE_INFINITY;
        }
        boolean needRevert = (this.currentPruneNode != pruneNode || this.LP == null);
        if (needRevert) {
            setPrunedState(pruneNode, null);
        }
        double dist = computeExactDistance(pruneNode, rerootNode, targetNode);
        if (needRevert) {
            doRevert();
        }
        return dist;
    }

    // =========================================================================
    // STATYCZNE OBLICZENIE KLASTRÓW W CZASIE O(depth) DLA WYWOŁAŃ POZA WALKEREM
    // =========================================================================

    private boolean isNodeInSubtree(Node node, Node subtreeRoot) {
        Node curr = node;
        while (curr != null) {
            if (curr == subtreeRoot) return true;
            curr = curr.getParent();
        }
        return false;
    }

    public double computeExactDistance(Node pruneNode, Node rerootNode, Node targetNode) {
        if (pruneNode == null || targetNode == null || pruneNode.isRoot() || this.baseTreeRef == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (this.currentPruneNode != pruneNode || this.LP == null) {
            setPrunedState(pruneNode, null);
        }

        Node p = pruneNode.getParent();
        if (p == null) return Double.POSITIVE_INFINITY;

        if (targetNode == p || isNodeInSubtree(targetNode, pruneNode)) {
            return Double.POSITIVE_INFINITY;
        }

        Node sibling = (p.getChild(0) == pruneNode) ? p.getChild(1) : p.getChild(0);
        if (rerootNode == pruneNode && targetNode == sibling) {
            return this.initialDistance;
        }

        int dT1 = 0;
        if (rerootNode != null && rerootNode != pruneNode) {
            Node child = rerootNode;
            Node parent = child.getParent();
            while (parent != null && parent != pruneNode) {
                BitSet cChild = nodeBitSets.get(child);
                if (cChild != null) {
                    scratchReroot.clear();
                    scratchReroot.or(LP);
                    scratchReroot.andNot(cChild);
                    if (isSharedFast(scratchReroot)) {
                        dT1++;
                    }
                }
                BitSet cParent = nodeBitSets.get(parent);
                if (isSharedFast(cParent)) {
                    dT1--;
                }
                child = parent;
                parent = parent.getParent();
            }
        }

        int dT2 = 0;
        if (targetNode == rootOfT2) {
            if (isSharedFast(bsRootT2)) {
                dT2++;
            }
        } else {
            scratchTarget.clear();
            BitSet bsT = getCT2(targetNode);
            if (bsT != null) scratchTarget.or(bsT);
            scratchTarget.or(LP);
            if (isSharedFast(scratchTarget)) {
                dT2++;
            }

            Node currAnc = getParentInT2(targetNode, pruneNode);
            while (currAnc != null && currAnc != rootOfT2) {
                BitSet bsOld = getCT2(currAnc);
                if (bsOld != null) {
                    if (isSharedFast(bsOld)) {
                        dT2--;
                    }
                    scratchTarget.clear();
                    scratchTarget.or(bsOld);
                    scratchTarget.or(LP);
                    if (isSharedFast(scratchTarget)) {
                        dT2++;
                    }
                }
                currAnc = getParentInT2(currAnc, pruneNode);
            }
        }

        int totalShared = this.baseSharedAfterPrune + dT1 + dT2;
        return ((N - 2) + targetSplits.size() - 2.0 * totalShared) / 2.0;
    }

    private Node getParentInT2(Node v, Node pruneNode) {
        if (v == null || v == rootOfT2) return null;
        Node p = pruneNode.getParent();
        Node parent = v.getParent();
        if (parent == p) {
            return p.getParent();
        }
        return parent;
    }

    private BitSet getCT2(Node v) {
        if (v == null) return null;
        BitSet mod = cT2.get(v);
        return (mod != null) ? mod : nodeBitSets.get(v);
    }
}