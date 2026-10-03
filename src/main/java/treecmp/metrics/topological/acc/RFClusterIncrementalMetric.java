package treecmp.metrics.topological.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.acc.RootedSprIncrementalMetric;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.acc.RootedTbrMetric;
import treecmp.metrics.topological.RFClusterMetric;

import java.util.BitSet;

public class RFClusterIncrementalMetric extends BaseRFIncrementalMetric implements
        RootedSprIncrementalMetric,
        RootedTbrMetric {

    private Tree baseTreeRef;
    private Tree targetTreeRef;
    private int N;

    private final TbrUtils tbrUtils = new TbrUtils();
    private final SprUtils sprUtils = new SprUtils();
    private final RFClusterMetric classicRfc = new RFClusterMetric();

    private Node currentPruneNode;
    private Node currentRerootNode;
    private Node currentTargetNode;

    @Override
    public boolean isRooted() {
        return true;
    }

    @Override
    protected boolean isNonTrivial(int card, int total) {
        return card > 1 && card < total;
    }

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        super.initCalculationState(baseTree, targetTree);
        this.baseTreeRef = baseTree;
        this.targetTreeRef = targetTree;
        this.currentPruneNode = null;
        this.currentRerootNode = null;
        this.currentTargetNode = null;

        if (baseTree != null && targetTree != null) {
            this.N = baseTree.getExternalNodeCount();
            try {
                this.currentDistance = classicRfc.getDistance(baseTree, targetTree);
            } catch (Exception e) {
                this.currentDistance = 0.0;
            }
        } else {
            this.currentDistance = 0.0;
        }
    }

    @Override
    public void commit() {
        super.commit();
        if (this.baseTreeRef != null && this.targetTreeRef != null) {
            try {
                this.currentDistance = classicRfc.getDistance(this.baseTreeRef, this.targetTreeRef);
            } catch (Exception e) {
                this.currentDistance = 0.0;
            }
        }
    }

    @Override
    protected BitSet normalizeSplit(BitSet rawSplit) {
        return rawSplit;
    }

    public BitSet getCluster(Node n) {
        if (n == null) return null;
        BitSet bs = nodeBitSets.get(n);
        if (bs != null) return bs;
        if (n.isLeaf()) {
            BitSet leafBs = new BitSet(N);
            IdGroup idGroup = TreeUtils.getLeafIdGroup(this.baseTreeRef);
            if (idGroup != null) {
                int id = idGroup.whichIdNumber(n.getIdentifier().getName());
                if (id >= 0) leafBs.set(id);
            }
            return leafBs;
        }
        return null;
    }

    // =========================================================================
    // KONTRAKT RootedSprIncrementalMetric
    // =========================================================================

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        this.currentPruneNode = pruneNode;
        this.currentRerootNode = pruneNode;
        this.currentTargetNode = null;
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node wanderingSource) {
        this.currentPruneNode = pruneNode;
        this.currentRerootNode = pruneNode;
        this.currentTargetNode = (baseTreeRef != null) ? baseTreeRef.getRoot() : null;
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {
        this.currentPruneNode = pruneNode;
        this.currentRerootNode = rerootNode;
        this.currentTargetNode = (baseTreeRef != null) ? baseTreeRef.getRoot() : null;
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        this.currentPruneNode = pruneNode;
        this.currentRerootNode = pruneNode;
        this.currentTargetNode = childTarget;
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        this.currentPruneNode = pruneNode;
        this.currentRerootNode = rerootNode;
        this.currentTargetNode = childTarget;
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        this.currentTargetNode = parentTarget;
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        this.currentTargetNode = parentTarget;
    }

    @Override
    public void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode) {
        this.currentRerootNode = childReroot;
    }

    @Override
    public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {
        this.currentRerootNode = parentReroot;
    }

    @Override
    public void revertPrunedState(Node pruneNode, Node wanderingSource) {
        this.currentPruneNode = null;
        this.currentRerootNode = null;
        this.currentTargetNode = null;
    }

    @Override
    public double getCurrentDistance() {
        if (currentPruneNode == null || currentTargetNode == null) {
            return this.currentDistance;
        }
        return evaluateExactTbrDistance(currentPruneNode, currentRerootNode, currentTargetNode, null);
    }

    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        return evaluateExactTbrDistance(pruneNode, pruneNode, targetNode, null);
    }

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode) {
        return evaluateExactTbrDistance(pruneNode, rerootNode, targetNode, null);
    }

    // =========================================================================
    // EWALUACJA SPR / TBR W OPARCIU O PEŁNĄ WYROCZNIĘ RFC
    // =========================================================================

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        if (this.baseTreeRef == null || this.targetTreeRef == null || pruneNode == null || targetNode == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (targetNode == pruneNode || targetNode == pruneNode.getParent()) {
            return Double.POSITIVE_INFINITY;
        }

        Tree tempTree;
        try {
            if (rerootNode == null || rerootNode == pruneNode) {
                tempTree = sprUtils.createSprTree(this.baseTreeRef, pruneNode, targetNode);
            } else {
                tempTree = tbrUtils.createTbrTree(this.baseTreeRef, pruneNode, rerootNode, targetNode);
            }
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }

        if (tempTree != null) {
            if (tempTree instanceof SimpleTree) {
                ((SimpleTree) tempTree).createNodeList();
                TreeUtils.computeParentPointers(tempTree.getRoot());
            }
            try {
                return classicRfc.getDistance(tempTree, this.targetTreeRef);
            } catch (Exception e) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return Double.POSITIVE_INFINITY;
    }
}