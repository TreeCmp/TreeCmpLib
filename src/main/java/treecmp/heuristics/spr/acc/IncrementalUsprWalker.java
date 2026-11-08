package treecmp.heuristics.spr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.spr.SprVisitor;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Zoptymalizowany, bezalokacyjny (Zero-Allocation) Wędrowiec uSPR dla metryk nieukorzenionych (RF, MS, M3).
 * Działa polimorficznie przez kontrakt UsprIncrementalMetric (spacer radialny po grafie nieukorzenionym).
 */
public class IncrementalUsprWalker {

    private final UsprUtils usprUtils = new UsprUtils();
    private final List< Node > reusableNodes = new ArrayList< >(512);

    public void walk(Tree baseTree, UsprIncrementalMetric metric, SprVisitor visitor) {
        reusableNodes.clear();
        collectNodes(baseTree.getRoot(), reusableNodes);
        int nodeCount = reusableNodes.size();

        for (int nIdx = 0; nIdx < nodeCount; nIdx++) {
            Node pruneNode = reusableNodes.get(nIdx);
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            metric.applySprPrune(pruneNode);
            BitSet pruneMask = metric.getPruneMask(pruneNode);
            Node startNode = pruneNode.getParent();

            if (startNode.getParent() != null) {
                exploreRadially(pruneNode, startNode.getParent(), startNode, metric, visitor, pruneMask);
            }
            for (int i = 0; i < startNode.getChildCount(); i++) {
                Node child = startNode.getChild(i);
                if (child != pruneNode) {
                    exploreRadially(pruneNode, child, startNode, metric, visitor, pruneMask);
                }
            }

            metric.undoSprPrune(pruneNode);
        }
    }

    public void walk(Tree baseTree, IncrementalMetric metric, SprVisitor visitor) {
        walk(baseTree, (UsprIncrementalMetric) metric, visitor);
    }

    private void exploreRadially(Node pruneNode, Node currentNode, Node previousNode,
                                 UsprIncrementalMetric metric, SprVisitor visitor, BitSet pruneMask) {

        boolean statePushed = metric.applyRadialStep(currentNode, previousNode, pruneMask);

        if (usprUtils.isValidUsprMove(pruneNode, currentNode)) {
            double dist = metric.evaluateCurrentRegraft(currentNode, pruneNode.getParent(), pruneMask, pruneNode);
            visitor.visit(dist, pruneNode, currentNode);
        }

        if (currentNode.getParent() != null && currentNode.getParent() != previousNode) {
            exploreRadially(pruneNode, currentNode.getParent(), currentNode, metric, visitor, pruneMask);
        }
        for (int i = 0; i < currentNode.getChildCount(); i++) {
            Node child = currentNode.getChild(i);
            if (child != previousNode && child != pruneNode) {
                exploreRadially(pruneNode, child, currentNode, metric, visitor, pruneMask);
            }
        }

        if (statePushed) {
            metric.undoRadialStep();
        }
    }

    private void collectNodes(Node node, List< Node > list) {
        if (node != null) {
            list.add(node);
            for (int i = 0; i < node.getChildCount(); i++) {
                collectNodes(node.getChild(i), list);
            }
        }
    }
}