package treecmp.heuristics.spr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.SprVisitor;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.List;

public class IncrementalUsprWalker {
    private final UsprUtils usprUtils = new UsprUtils();

    public void walk(Tree baseTree, IncrementalMetric metric, SprVisitor visitor) {
        IncrementalSprWalker.RootedMetric targetDfsMetric = (IncrementalSprWalker.RootedMetric) metric;
        List<Node> allNodes = getAllNodes(baseTree);
        Node root = baseTree.getRoot();

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            Node wanderingSource = pruneNode.getParent();

            targetDfsMetric.setPrunedState(pruneNode, wanderingSource);
            targetDfsMetric.setTargetRoot(pruneNode, wanderingSource);

            if (usprUtils.isValidUsprMove(pruneNode, root)) {
                visitor.visit(targetDfsMetric.getCurrentDistance(), pruneNode, root);
            }

            for (Node child : getPrunedChildren(root, pruneNode)) {
                dfsPrunedTree(root, child, pruneNode, wanderingSource, targetDfsMetric, visitor);
            }

            targetDfsMetric.revertPrunedState(pruneNode, wanderingSource);
        }
    }

    private void dfsPrunedTree(Node parentTarget, Node targetNode, Node pruneNode, Node wanderingSource, IncrementalSprWalker.RootedMetric metric, SprVisitor visitor) {
        metric.moveTargetDown(parentTarget, targetNode, pruneNode, wanderingSource);

        if (usprUtils.isValidUsprMove(pruneNode, targetNode)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, targetNode);
        }

        for (Node child : getPrunedChildren(targetNode, pruneNode)) {
            dfsPrunedTree(targetNode, child, pruneNode, wanderingSource, metric, visitor);
        }

        metric.moveTargetUp(parentTarget, targetNode, pruneNode, wanderingSource);
    }

    private List<Node> getPrunedChildren(Node n, Node pruneNode) {
        List<Node> children = new ArrayList<>();
        Node pParent = pruneNode.getParent();

        if (n == pParent) {
            for (int i = 0; i < n.getChildCount(); i++) {
                if (n.getChild(i) != pruneNode) {
                    children.add(n.getChild(i));
                }
            }
            return children;
        }

        for (int i = 0; i < n.getChildCount(); i++) {
            Node c = n.getChild(i);
            if (c == pParent) {
                for (int j = 0; j < pParent.getChildCount(); j++) {
                    if (pParent.getChild(j) != pruneNode) {
                        children.add(pParent.getChild(j));
                    }
                }
            } else {
                children.add(c);
            }
        }
        return children;
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