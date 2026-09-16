package treecmp.heuristics.tbr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.TbrUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Zoptymalizowany Wędrowiec TBR (2D-DFS 1-NNI).
 * Gwarantuje symetrię stosu delty:
 * 1. setPrunedState (bisekcja)
 * 2. setTargetRoot (inicjalizacja wpięcia w korzeniu T2)
 * 3. 2D-DFS (Reroot DFS w T1 x Target DFS w T2)
 * 4. revertPrunedState (atomowe wycofanie setTargetRoot i setPrunedState)
 */
public class IncrementalTbrWalker {

    private final TbrUtils tbrUtils = new TbrUtils();

    @FunctionalInterface
    public interface TbrVisitor {
        void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
    }

    public void walk(Tree baseTree, RootedTbrMetric metric, TbrVisitor visitor) {
        List<Node> allNodes = getAllNodes(baseTree);
        Node root = baseTree.getRoot();

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            Node wanderingSource = pruneNode.getParent();

            // 1. Bisekcja: odcięcie poddrzewa T1
            metric.setPrunedState(pruneNode, wanderingSource);

            // 2. Inicjalizacja wpięcia w korzeniu T2 (dokładnie raz!)
            metric.setTargetRoot(pruneNode, pruneNode, wanderingSource);

            // 3. Dwuwymiarowy DFS
            dfsReroot(pruneNode, pruneNode, wanderingSource, root, metric, visitor);

            // 4. Przywrócenie stanu (zdejmuje setTargetRoot oraz setPrunedState)
            metric.revertPrunedState(pruneNode, wanderingSource);
        }
    }

    private void dfsReroot(Node currentReroot, Node pruneNode, Node wanderingSource, Node root, RootedTbrMetric metric, TbrVisitor visitor) {
        // A. Ewaluacja wpięcia w korzeniu T2 dla ustalonego currentReroot
        if (isValidMove(pruneNode, currentReroot, root)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, currentReroot, root);
        }

        // B. Target DFS w głąb drzewa T2
        for (Node child : getPrunedChildren(root, pruneNode)) {
            dfsTarget(root, child, pruneNode, currentReroot, wanderingSource, metric, visitor);
        }

        // C. Reroot DFS w głąb T1 krokami 1-NNI
        if (!currentReroot.isLeaf()) {
            for (int i = 0; i < currentReroot.getChildCount(); i++) {
                Node nextReroot = currentReroot.getChild(i);

                metric.moveRerootDown(currentReroot, nextReroot, pruneNode);
                dfsReroot(nextReroot, pruneNode, wanderingSource, root, metric, visitor);
                metric.moveRerootUp(currentReroot, nextReroot, pruneNode);
            }
        }
    }

    private void dfsTarget(Node parentTarget, Node targetNode, Node pruneNode, Node rerootNode, Node wanderingSource, RootedTbrMetric metric, TbrVisitor visitor) {
        metric.moveTargetDown(parentTarget, targetNode, pruneNode, rerootNode, wanderingSource);

        if (isValidMove(pruneNode, rerootNode, targetNode)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, rerootNode, targetNode);
        }

        for (Node child : getPrunedChildren(targetNode, pruneNode)) {
            dfsTarget(targetNode, child, pruneNode, rerootNode, wanderingSource, metric, visitor);
        }

        metric.moveTargetUp(parentTarget, targetNode, pruneNode, rerootNode, wanderingSource);
    }

    private boolean isValidMove(Node pruneNode, Node rerootNode, Node targetNode) {
        if (rerootNode == pruneNode && targetNode == pruneNode.getParent()) return false;
        return tbrUtils.isValidTbrMove(pruneNode, rerootNode, targetNode);
    }

    private List<Node> getPrunedChildren(Node n, Node pruneNode) {
        List<Node> children = new ArrayList<>();
        Node pParent = pruneNode.getParent();

        if (n == pParent) {
            for (int i = 0; i < n.getChildCount(); i++) {
                if (n.getChild(i) != pruneNode) children.add(n.getChild(i));
            }
            return children;
        }

        for (int i = 0; i < n.getChildCount(); i++) {
            Node c = n.getChild(i);
            if (c == pParent) {
                for (int j = 0; j < pParent.getChildCount(); j++) {
                    if (pParent.getChild(j) != pruneNode) children.add(pParent.getChild(j));
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
            for (int i = 0; i < node.getChildCount(); i++) collectNodes(node.getChild(i), list);
        }
    }
}