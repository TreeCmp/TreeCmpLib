package treecmp.heuristics.tbr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.M3IncrementalMetric;
import treecmp.metrics.topological.acc.RFIncrementalMetric;

import java.util.ArrayList;
import java.util.List;

/**
 * Zoptymalizowany, przyrostowy Walker dla otoczenia uTBR (Unrooted TBR).
 * Obsługuje szybką 2D-DFS dla MS/MC/MP, a dla M3 i RF omija obciążającą pamięć Refleksję.
 */
public class UtbrNeighborhoodWalker {

    public interface UtbrVisitor {
        void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
    }

    private final UTbrUtils utbrUtils = new UTbrUtils();

    // Buforowane listy węzłów (Zero-Allocation per walk)
    private final List<Node> allNodesBuf = new ArrayList<>();
    private final List<Node> rerootNodesBuf = new ArrayList<>();
    private final List<Node> targetNodesBuf = new ArrayList<>();

    public void walk(Tree baseTree, IncrementalMetric metric, UtbrVisitor visitor) {
        // MS, MC, MP używają błyskawicznego 2D-DFS
        if (metric instanceof RootedTbrMetric && !(metric instanceof M3IncrementalMetric)) {
            walkFast2dDfs(baseTree, (RootedTbrMetric) metric, visitor);
            return;
        }

        // RF oraz M3 korzystają ze zoptymalizowanej, bezalokacyjnej wyroczni O(depth)
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

            metric.setPrunedState(pruneNode, wanderingSource);
            metric.setTargetRoot(pruneNode, pruneNode, wanderingSource);

            dfsReroot(pruneNode, pruneNode, wanderingSource, root, metric, visitor);

            metric.revertPrunedState(pruneNode, wanderingSource);
        }
    }

    private void dfsReroot(Node currentReroot, Node pruneNode, Node wanderingSource, Node root,
                           RootedTbrMetric metric, UtbrVisitor visitor) {

        dfsTarget(root, pruneNode, currentReroot, wanderingSource, metric, visitor);

        if (!currentReroot.isLeaf()) {
            for (int i = 0; i < currentReroot.getChildCount(); i++) {
                Node nextReroot = currentReroot.getChild(i);
                metric.moveRerootDown(currentReroot, nextReroot, pruneNode);
                dfsReroot(nextReroot, pruneNode, wanderingSource, root, metric, visitor);
                metric.moveRerootUp(currentReroot, nextReroot, pruneNode);
            }
        }
    }

    private void dfsTarget(Node currentTarget, Node pruneNode, Node currentReroot, Node wanderingSource,
                           RootedTbrMetric metric, UtbrVisitor visitor) {

        if (!(currentReroot == pruneNode && currentTarget == pruneNode.getParent())) {
            if (utbrUtils.isValidUtbrMove(pruneNode, currentReroot, currentTarget)) {
                visitor.visit(metric.getCurrentDistance(), pruneNode, currentReroot, currentTarget);
            }
        }

        if (!currentTarget.isLeaf()) {
            for (int i = 0; i < currentTarget.getChildCount(); i++) {
                Node childTarget = currentTarget.getChild(i);
                if (childTarget == pruneNode) continue;

                metric.moveTargetDown(currentTarget, childTarget, pruneNode, currentReroot, wanderingSource);
                dfsTarget(childTarget, pruneNode, currentReroot, wanderingSource, metric, visitor);
                metric.moveTargetUp(currentTarget, childTarget, pruneNode, currentReroot, wanderingSource);
            }
        }
    }

    /**
     * Szybka ścieżka dla metryk bez 2D-DFS (RF, M3) wywołująca bezpośrednio metody klas
     * zamiast korzystania z obciążającej metody Method.invoke()
     */
    private void walkFallback(Tree baseTree, IncrementalMetric metric, UtbrVisitor visitor) {
        allNodesBuf.clear();
        collectSubtreeNodes(baseTree.getRoot(), allNodesBuf);

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
                        double dist;
                        if (metric instanceof RFIncrementalMetric) {
                            dist = ((RFIncrementalMetric) metric).evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, null);
                        } else if (metric instanceof M3IncrementalMetric) {
                            dist = ((M3IncrementalMetric) metric).evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, null);
                        } else {
                            dist = metric.getCurrentDistance();
                        }
                        visitor.visit(dist, pruneNode, rerootNode, targetNode);
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