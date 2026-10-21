package treecmp.heuristics.spr.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.spr.SprVisitor;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.BaseRFIncrementalMetric;
import treecmp.metrics.topological.acc.M3IncrementalMetric;
import treecmp.metrics.topological.acc.MSIncrementalMetric;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class IncrementalUsprWalker {

    private final UsprUtils usprUtils = new UsprUtils();
    private final List<Node> reusableNodes = new ArrayList<>(512);
    private BitSet reusablePruneMask = new BitSet(256);

    public void walk(Tree baseTree, IncrementalMetric metric, SprVisitor visitor) {
        boolean isFastMs = metric instanceof MSIncrementalMetric;
        boolean isFastM3 = metric instanceof M3IncrementalMetric;
        boolean isFastRf = metric instanceof BaseRFIncrementalMetric;
        boolean isFastUspr = isFastMs; // TYLKO MS używa applyNniStep!

        IdGroup idGroup = (!isFastMs && !isFastRf) ? TreeUtils.getLeafIdGroup(baseTree) : null;
        int numLeaves = baseTree.getExternalNodeCount();
        if (reusablePruneMask.size() < numLeaves) {
            reusablePruneMask = new BitSet(numLeaves);
        }

        reusableNodes.clear();
        collectNodes(baseTree.getRoot(), reusableNodes);
        int nodeCount = reusableNodes.size();

        for (int nIdx = 0; nIdx < nodeCount; nIdx++) {
            Node pruneNode = reusableNodes.get(nIdx);
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            // TYLKO BaseRFIncrementalMetric potrzebuje applySprPrune
            if (isFastRf) {
                ((BaseRFIncrementalMetric) metric).applySprPrune(pruneNode);
            }

            BitSet pruneMask;
            if (isFastMs) {
                pruneMask = ((MSIncrementalMetric) metric).getBaseSplitBits(pruneNode);
            } else if (isFastRf) {
                pruneMask = null;
            } else {
                reusablePruneMask.clear();
                populateLeafMask(pruneNode, idGroup, reusablePruneMask);
                pruneMask = reusablePruneMask;
            }

            Node startNode = pruneNode.getParent();

            if (startNode.getParent() != null) {
                exploreRadially(pruneNode, startNode.getParent(), startNode, metric, visitor, pruneMask, isFastMs, isFastM3, isFastRf, isFastUspr);
            }
            for (int i = 0; i < startNode.getChildCount(); i++) {
                Node child = startNode.getChild(i);
                if (child != pruneNode) {
                    exploreRadially(pruneNode, child, startNode, metric, visitor, pruneMask, isFastMs, isFastM3, isFastRf, isFastUspr);
                }
            }

            if (isFastRf) {
                ((BaseRFIncrementalMetric) metric).undoSprPrune(pruneNode);
            }
        }
    }

    private void exploreRadially(Node pruneNode, Node currentNode, Node previousNode,
                                 IncrementalMetric metric, SprVisitor visitor, BitSet pruneMask,
                                 boolean isFastMs, boolean isFastM3, boolean isFastRf, boolean isFastUspr) {

        boolean statePushed = false;

        // Błyskawiczny przesuw (NNI Step) na maskach bitowych - TYLKO DLA MS!
        if (isFastUspr) {
            boolean movingUp = (currentNode == previousNode.getParent());
            Node nodeToUpdate = movingUp ? previousNode : currentNode;
            BitSet bitsOut = movingUp ? pruneMask : null;
            BitSet bitsIn = movingUp ? null : pruneMask;
            statePushed = ((MSIncrementalMetric) metric).applyNniStep(nodeToUpdate, bitsOut, bitsIn);
        }

        if (usprUtils.isValidUsprMove(pruneNode, currentNode)) {
            double dist;
            if (isFastMs) {
                dist = ((MSIncrementalMetric) metric).getFixedDistanceForRegraft(currentNode, pruneNode.getParent(), pruneMask, pruneNode);
            } else if (isFastM3) {
                dist = ((M3IncrementalMetric) metric).evaluateSprRegraft(pruneNode, currentNode);
            } else if (isFastRf) {
                dist = ((BaseRFIncrementalMetric) metric).evaluateSprRegraft(pruneNode, currentNode);
            } else {
                dist = Double.POSITIVE_INFINITY;
            }
            visitor.visit(dist, pruneNode, currentNode);
        }

        if (currentNode.getParent() != null && currentNode.getParent() != previousNode) {
            exploreRadially(pruneNode, currentNode.getParent(), currentNode, metric, visitor, pruneMask, isFastMs, isFastM3, isFastRf, isFastUspr);
        }
        for (int i = 0; i < currentNode.getChildCount(); i++) {
            Node child = currentNode.getChild(i);
            if (child != previousNode && child != pruneNode) {
                exploreRadially(pruneNode, child, currentNode, metric, visitor, pruneMask, isFastMs, isFastM3, isFastRf, isFastUspr);
            }
        }

        if (statePushed) {
            ((MSIncrementalMetric) metric).undoNniStep();
        }
    }

    private void populateLeafMask(Node node, IdGroup idGroup, BitSet bs) {
        if (node.isLeaf()) {
            bs.set(idGroup.whichIdNumber(node.getIdentifier().getName()));
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                populateLeafMask(node.getChild(i), idGroup, bs);
            }
        }
    }

    private void collectNodes(Node node, List<Node> list) {
        if (node != null) {
            list.add(node);
            for (int i = 0; i < node.getChildCount(); i++) {
                collectNodes(node.getChild(i), list);
            }
        }
    }
}