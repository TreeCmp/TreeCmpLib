package treecmp.heuristics.tbr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.List;

public class UtbrNeighborhoodWalker {

    @FunctionalInterface
    public interface UtbrVisitor {
        void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
    }

    private final UTbrUtils utbrUtils = new UTbrUtils();
    private final List<Node> allNodesBuf = new ArrayList<>();

    public void walk(Tree baseTree, IncrementalMetric metric, UtbrVisitor visitor) {
        if (metric instanceof RootedTbrMetric) {
            walkFast2dDfs(baseTree, (RootedTbrMetric) metric, visitor);
            return;
        }
        walkFallback(baseTree, metric, visitor);
    }

    private void walkFast2dDfs(Tree baseTree, RootedTbrMetric metric, UtbrVisitor visitor) {
        allNodesBuf.clear();
        collectSubtreeNodes(baseTree.getRoot(), allNodesBuf);
        Node root = baseTree.getRoot();

        for (int i = 0; i < allNodesBuf.size(); i++) {
            Node pruneNode = allNodesBuf.get(i);
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            Node wanderingSource = pruneNode.getParent();

            // 1. Odcięcie T1 i przygotowanie stanu bazowego T2
            metric.setPrunedState(pruneNode, wanderingSource);
            metric.setTargetRoot(pruneNode, pruneNode, wanderingSource);

            // 2. Dwuwymiarowy DFS: Reroot DFS w T1 x Target DFS w T2
            dfsReroot(pruneNode, pruneNode, wanderingSource, root, metric, visitor);

            // 3. Atomowe wycofanie zmian
            metric.revertPrunedState(pruneNode, wanderingSource);
        }
    }

    private void dfsReroot(Node currentReroot, Node pruneNode, Node wanderingSource, Node root,
                           RootedTbrMetric metric, UtbrVisitor visitor) {
        // A. Ewaluacja wpięcia w korzeniu T2
        if (isValidMove(pruneNode, currentReroot, root)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, currentReroot, root);
        }

        // B. Target DFS w głąb składowej T2 (z ominięciem pParent i pruneNode)
        Node pParent = pruneNode.getParent();
        for (int i = 0; i < root.getChildCount(); i++) {
            Node c = root.getChild(i);
            if (c == pruneNode) continue;
            if (c == pParent) {
                for (int j = 0; j < pParent.getChildCount(); j++) {
                    Node gc = pParent.getChild(j);
                    if (gc != pruneNode) {
                        dfsTarget(root, gc, pruneNode, currentReroot, wanderingSource, metric, visitor);
                    }
                }
            } else {
                dfsTarget(root, c, pruneNode, currentReroot, wanderingSource, metric, visitor);
            }
        }

        // C. Reroot DFS w głąb składowej T1
        if (!currentReroot.isLeaf()) {
            for (int i = 0; i < currentReroot.getChildCount(); i++) {
                Node nextReroot = currentReroot.getChild(i);
                metric.moveRerootDown(currentReroot, nextReroot, pruneNode);
                dfsReroot(nextReroot, pruneNode, wanderingSource, root, metric, visitor);
                metric.moveRerootUp(currentReroot, nextReroot, pruneNode);
            }
        }
    }

    private void dfsTarget(Node parentTarget, Node targetNode, Node pruneNode, Node currentReroot,
                           Node wanderingSource, RootedTbrMetric metric, UtbrVisitor visitor) {
        metric.moveTargetDown(parentTarget, targetNode, pruneNode, currentReroot, wanderingSource);

        if (isValidMove(pruneNode, currentReroot, targetNode)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, currentReroot, targetNode);
        }

        Node pParent = pruneNode.getParent();
        for (int i = 0; i < targetNode.getChildCount(); i++) {
            Node c = targetNode.getChild(i);
            if (c == pruneNode) continue;
            if (c == pParent) {
                for (int j = 0; j < pParent.getChildCount(); j++) {
                    Node gc = pParent.getChild(j);
                    if (gc != pruneNode) {
                        dfsTarget(targetNode, gc, pruneNode, currentReroot, wanderingSource, metric, visitor);
                    }
                }
            } else {
                dfsTarget(targetNode, c, pruneNode, currentReroot, wanderingSource, metric, visitor);
            }
        }

        metric.moveTargetUp(parentTarget, targetNode, pruneNode, currentReroot, wanderingSource);
    }

    private boolean isValidMove(Node pruneNode, Node rerootNode, Node targetNode) {
        if (rerootNode == pruneNode && targetNode == pruneNode.getParent()) return false;
        return utbrUtils.isValidUtbrMove(pruneNode, rerootNode, targetNode);
    }

    private void walkFallback(Tree baseTree, IncrementalMetric metric, UtbrVisitor visitor) {
        allNodesBuf.clear();
        collectSubtreeNodes(baseTree.getRoot(), allNodesBuf);
        List<Node> rerootNodesBuf = new ArrayList<>();
        List<Node> targetNodesBuf = new ArrayList<>();

        for (int i = 0; i < allNodesBuf.size(); i++) {
            Node pruneNode = allNodesBuf.get(i);
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            rerootNodesBuf.clear();
            collectSubtreeNodes(pruneNode, rerootNodesBuf);

            targetNodesBuf.clear();
            collectOutsideNodes(baseTree.getRoot(), pruneNode, targetNodesBuf);

            for (int r = 0; r < rerootNodesBuf.size(); r++) {
                Node rerootNode = rerootNodesBuf.get(r);
                for (int t = 0; t < targetNodesBuf.size(); t++) {
                    Node targetNode = targetNodesBuf.get(t);
                    if (rerootNode == pruneNode && targetNode == pruneNode.getParent()) continue;

                    if (utbrUtils.isValidUtbrMove(pruneNode, rerootNode, targetNode)) {
                        visitor.visit(metric.getCurrentDistance(), pruneNode, rerootNode, targetNode);
                    }
                }
            }
        }
    }

    private void collectSubtreeNodes(Node node, List<Node> list) {
        list.add(node);
        if (!node.isLeaf()) {
            for (int i = 0; i < node.getChildCount(); i++) {
                collectSubtreeNodes(node.getChild(i), list);
            }
        }
    }

    private void collectOutsideNodes(Node current, Node excludeSubtree, List<Node> list) {
        if (current == excludeSubtree) return;
        list.add(current);
        if (!current.isLeaf()) {
            for (int i = 0; i < current.getChildCount(); i++) {
                collectOutsideNodes(current.getChild(i), excludeSubtree, list);
            }
        }
    }
}