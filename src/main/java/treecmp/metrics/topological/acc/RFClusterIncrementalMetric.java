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

    private final Map<Node, BitSet> cT2 = new IdentityHashMap<>();
    private final Set<Node> inSubtreeP = new HashSet<>();
    private final Set<Node> ancestorsOfP = new HashSet<>();
    private final List<Node> internalNodesOfT1 = new ArrayList<>();

    private int sharedT1_base;
    private int sharedT2_base;
    private double initialDistance;
    private double distanceBeforePrune;

    private Node currentPruneNode;
    private Node currentRerootNode;
    private Node currentTargetNode;

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
            if (scratchTarget == null || scratchTarget.size() < N) {
                scratchTarget = new BitSet(N);
                scratchReroot = new BitSet(N);
            }

            int shared = 0;
            for (Map.Entry<Node, BitSet> entry : nodeBitSets.entrySet()) {
                if (!entry.getKey().isRoot()) {
                    BitSet bs = entry.getValue();
                    if (isSharedFast(bs)) {
                        shared++;
                    }
                }
            }

            this.initialDistance = ((N - 2) + targetSplits.size() - 2.0 * shared) / 2.0;
            this.currentDistance = this.initialDistance;
            this.distanceBeforePrune = this.initialDistance;
        } else {
            this.currentDistance = 0.0;
            this.initialDistance = 0.0;
            this.distanceBeforePrune = 0.0;
        }
        doRevert();
    }

    @Override
    public void commit() {
        super.commit();
        doRevert();
        if (this.baseTreeRef != null && this.targetTreeRef != null) {
            initCalculationState(this.baseTreeRef, this.targetTreeRef);
        }
    }

    private void collectSubtreeNodes(Node n, Set<Node> set) {
        if (n == null) return;
        set.add(n);
        for (int i = 0; i < n.getChildCount(); i++) {
            collectSubtreeNodes(n.getChild(i), set);
        }
    }

    private void doRevert() {
        this.currentPruneNode = null;
        this.currentRerootNode = null;
        this.currentTargetNode = null;
        this.currentDistance = this.distanceBeforePrune;
    }

    // =========================================================================
    // KONTRAKT WĘDROWCÓW (SPR / TBR)
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
        this.currentTargetNode = null;

        this.LP = nodeBitSets.get(pruneNode);
        Node p = pruneNode.getParent();
        Node root = this.baseTreeRef.getRoot();

        if (p == root) {
            this.rootOfT2 = (p.getChild(0) == pruneNode) ? p.getChild(1) : p.getChild(0);
        } else {
            this.rootOfT2 = root;
        }

        this.bsRootT2 = (BitSet) allLeavesMask.clone();
        this.bsRootT2.andNot(LP);

        this.inSubtreeP.clear();
        collectSubtreeNodes(pruneNode, inSubtreeP);

        this.ancestorsOfP.clear();
        Node curr = (p != null) ? p.getParent() : null;
        while (curr != null && curr != root) {
            this.ancestorsOfP.add(curr);
            curr = curr.getParent();
        }

        this.internalNodesOfT1.clear();
        for (Node u : inSubtreeP) {
            if (!u.isLeaf()) {
                internalNodesOfT1.add(u);
            }
        }

        this.sharedT1_base = 0;
        for (Node u : internalNodesOfT1) {
            BitSet bs = nodeBitSets.get(u);
            if (isSharedFast(bs)) {
                this.sharedT1_base++;
            }
        }

        this.cT2.clear();
        this.sharedT2_base = 0;
        for (Map.Entry<Node, BitSet> entry : nodeBitSets.entrySet()) {
            Node v = entry.getKey();
            if (inSubtreeP.contains(v) || v == p || v == rootOfT2 || v.isLeaf()) {
                continue;
            }
            BitSet bs = entry.getValue();
            BitSet l2Bs;
            if (ancestorsOfP.contains(v)) {
                l2Bs = (BitSet) bs.clone();
                l2Bs.andNot(LP);
            } else {
                l2Bs = bs;
            }
            cT2.put(v, l2Bs);
            if (isSharedFast(l2Bs)) {
                this.sharedT2_base++;
            }
        }

        this.currentTargetNode = this.rootOfT2;
        this.currentDistance = computeExactDistance(pruneNode, pruneNode, this.rootOfT2);
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
        this.currentDistance = computeExactDistance(pruneNode, rerootNode, this.rootOfT2);
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
        this.currentPruneNode = pruneNode;
        this.currentRerootNode = rerootNode;
        this.currentTargetNode = childTarget;
        this.currentDistance = computeExactDistance(pruneNode, rerootNode, childTarget);
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
        this.currentTargetNode = parentTarget;
        this.currentDistance = computeExactDistance(pruneNode, rerootNode, parentTarget);
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
        this.currentRerootNode = childReroot;
        this.currentTargetNode = this.rootOfT2;
        this.currentDistance = computeExactDistance(pruneNode, childReroot, this.rootOfT2);
    }

    public void moveRerootDown(Node parentReroot, Node childReroot) {
        moveRerootDown(parentReroot, childReroot, this.currentPruneNode);
    }

    @Override
    public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {
        this.currentRerootNode = parentReroot;
        this.currentTargetNode = this.rootOfT2;
        this.currentDistance = computeExactDistance(pruneNode, parentReroot, this.rootOfT2);
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
    // DOKŁADNE ANALITYCZNE OBLICZENIE KLASTRÓW W CZASIE O(depth) - ZERO ALOKACJI
    // =========================================================================

    public double computeExactDistance(Node pruneNode, Node rerootNode, Node targetNode) {
        if (pruneNode == null || targetNode == null || pruneNode.isRoot() || this.baseTreeRef == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (this.currentPruneNode != pruneNode || this.LP == null) {
            setPrunedState(pruneNode, null);
        }

        Node p = pruneNode.getParent();
        if (p == null) return Double.POSITIVE_INFINITY;

        if (inSubtreeP.contains(targetNode) || targetNode == p) {
            return Double.POSITIVE_INFINITY;
        }

        Node sibling = (p.getChild(0) == pruneNode) ? p.getChild(1) : p.getChild(0);
        if (rerootNode == pruneNode && targetNode == sibling) {
            return this.initialDistance;
        }

        // 1. Składowa klastrów w przekorzenionym komponencie T1
        int sharedT1 = sharedT1_base;
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
                        sharedT1++;
                    }
                }
                BitSet cParent = nodeBitSets.get(parent);
                if (isSharedFast(cParent)) {
                    sharedT1--;
                }
                child = parent;
                parent = parent.getParent();
            }
        }

        // 2. Składowa klastrów w komponencie T2
        int sharedT2 = sharedT2_base;
        if (targetNode == rootOfT2) {
            if (isSharedFast(bsRootT2)) {
                sharedT2++;
            }
        } else {
            scratchTarget.clear();
            BitSet bsT = getCT2(targetNode);
            if (bsT != null) scratchTarget.or(bsT);
            scratchTarget.or(LP);
            if (isSharedFast(scratchTarget)) {
                sharedT2++;
            }

            Node currAnc = getParentInT2(targetNode, pruneNode);
            while (currAnc != null && currAnc != rootOfT2) {
                BitSet bsOld = cT2.get(currAnc);
                if (bsOld != null) {
                    if (isSharedFast(bsOld)) {
                        sharedT2--;
                    }
                    scratchTarget.clear();
                    scratchTarget.or(bsOld);
                    scratchTarget.or(LP);
                    if (isSharedFast(scratchTarget)) {
                        sharedT2++;
                    }
                }
                currAnc = getParentInT2(currAnc, pruneNode);
            }
        }

        int totalShared = sharedT1 + sharedT2;
        return ((N - 2) + targetSplits.size() - 2.0 * totalShared) / 2.0;
    }

    private Node getParentInT2(Node v, Node pruneNode) {
        if (v == null || v == rootOfT2) return null;
        Node p = pruneNode.getParent();
        Node parent = v.getParent();
        if (parent == p) {
            return (p != null) ? p.getParent() : null;
        }
        return parent;
    }

    private BitSet getCT2(Node v) {
        if (v == null) return null;
        if (v.isLeaf()) return nodeBitSets.get(v);
        return cT2.get(v);
    }
}