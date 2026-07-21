package treecmp.heuristics.spr.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.SprVisitor;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.M3IncrementalMetric;
import treecmp.metrics.topological.acc.MSIncrementalMetric;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class IncrementalUsprWalker {

    private final UsprUtils usprUtils = new UsprUtils();

    public void walk(Tree baseTree, IncrementalMetric metric, SprVisitor visitor) {
        // Identyfikujemy zoptymalizowane metryki
        boolean isFastMs = metric instanceof MSIncrementalMetric;
        boolean isFastM3 = metric instanceof M3IncrementalMetric;
        boolean isFastUspr = isFastMs || isFastM3;

        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
        int numLeaves = baseTree.getExternalNodeCount();
        List<Node> allNodes = getAllNodes(baseTree);

        for (Node pruneNode : allNodes) {
            // W uSPR omijamy korzeń wirtualny
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            // Metryki M3 potrzebują wiedzieć, co zostało odcięte
            metric.applySprPrune(pruneNode);

            BitSet pruneMask = getLeafMask(pruneNode, idGroup, numLeaves);
            Node startNode = pruneNode.getParent();

            if (startNode.getParent() != null) {
                exploreRadially(pruneNode, startNode.getParent(), startNode, metric, visitor, pruneMask, isFastMs, isFastM3, isFastUspr);
            }
            for (int i = 0; i < startNode.getChildCount(); i++) {
                Node child = startNode.getChild(i);
                if (child != pruneNode) {
                    exploreRadially(pruneNode, child, startNode, metric, visitor, pruneMask, isFastMs, isFastM3, isFastUspr);
                }
            }

            metric.undoSprPrune(pruneNode);
        }
    }

    private void exploreRadially(Node pruneNode, Node currentNode, Node previousNode,
                                 IncrementalMetric metric, SprVisitor visitor, BitSet pruneMask,
                                 boolean isFastMs, boolean isFastM3, boolean isFastUspr) {

        boolean statePushed = false;

        // Błyskawiczny przesuw (NNI Step) na maskach bitowych bez dotykania drzewa!
        if (isFastUspr) {
            boolean movingUp = (currentNode == previousNode.getParent());
            Node nodeToUpdate = movingUp ? previousNode : currentNode;
            BitSet bitsOut = movingUp ? pruneMask : null;
            BitSet bitsIn = movingUp ? null : pruneMask;

            if (isFastMs) {
                statePushed = ((MSIncrementalMetric) metric).applyNniStep(nodeToUpdate, bitsOut, bitsIn);
            } else {
                statePushed = ((M3IncrementalMetric) metric).applyNniStep(nodeToUpdate, bitsOut, bitsIn);
            }
        }

        if (usprUtils.isValidUsprMove(pruneNode, currentNode)) {
            double dist;
            // Piekielnie szybki, jednowierszowy update LapSolvera
            if (isFastMs) {
                dist = ((MSIncrementalMetric) metric).getFixedDistanceForRegraft(currentNode, pruneNode.getParent(), pruneMask, pruneNode);
            } else if (isFastM3) {
                dist = ((M3IncrementalMetric) metric).getFixedDistanceForRegraft(currentNode, pruneNode.getParent(), pruneMask, pruneNode);
            } else {
                dist = metric.evaluateSprRegraft(pruneNode, currentNode);
            }
            visitor.visit(dist, pruneNode, currentNode);
        }

        if (currentNode.getParent() != null && currentNode.getParent() != previousNode) {
            exploreRadially(pruneNode, currentNode.getParent(), currentNode, metric, visitor, pruneMask, isFastMs, isFastM3, isFastUspr);
        }
        for (int i = 0; i < currentNode.getChildCount(); i++) {
            Node child = currentNode.getChild(i);
            if (child != previousNode && child != pruneNode) {
                exploreRadially(pruneNode, child, currentNode, metric, visitor, pruneMask, isFastMs, isFastM3, isFastUspr);
            }
        }

        // Natychmiastowe wycofanie zmian ze stosu
        if (statePushed) {
            if (isFastMs) {
                ((MSIncrementalMetric) metric).undoNniStep();
            } else {
                ((M3IncrementalMetric) metric).undoNniStep();
            }
        }
    }

    private BitSet getLeafMask(Node node, IdGroup idGroup, int numLeaves) {
        BitSet bs = new BitSet(numLeaves);
        populateLeafMask(node, idGroup, bs);
        return bs;
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

    private List<Node> getAllNodes(Tree tree) {
        List<Node> list = new ArrayList<>();
        collectNodes(tree.getRoot(), list);
        return list;
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