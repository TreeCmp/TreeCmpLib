package treecmp.heuristics.tbr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.TbrUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Bezalokacyjny Wędrowiec TBR (2D-DFS 1-NNI).
 */
public class IncrementalTbrWalker {

    private final TbrUtils tbrUtils = new TbrUtils();
    private final List<Node> allNodesBuf = new ArrayList<>();

    @FunctionalInterface
    public interface TbrVisitor {
        void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
    }

    public void walk(Tree baseTree, RootedTbrMetric metric, TbrVisitor visitor) {
        allNodesBuf.clear();
        collectNodes(baseTree.getRoot(), allNodesBuf);
        Node root = baseTree.getRoot();

        for (int i = 0; i < allNodesBuf.size(); i++) {
            Node pruneNode = allNodesBuf.get(i);
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            Node wanderingSource = pruneNode.getParent();

            // 1. Bisekcja: odcięcie poddrzewa T1
            metric.setPrunedState(pruneNode, wanderingSource);

            // 2. Inicjalizacja wpięcia w korzeniu T2 (dokładnie raz!)
            metric.setTargetRoot(pruneNode, pruneNode, wanderingSource);

            // 3. Dwuwymiarowy DFS bez alokacji list pomocniczych
            dfsReroot(pruneNode, pruneNode, wanderingSource, root, metric, visitor);

            // 4. Przywrócenie stanu
            metric.revertPrunedState(pruneNode, wanderingSource);
        }
    }

    private void dfsReroot(Node currentReroot, Node pruneNode, Node wanderingSource, Node root,
                           RootedTbrMetric metric, TbrVisitor visitor) {
        // A. Ewaluacja wpięcia w korzeniu T2 dla ustalonego currentReroot
        if (isValidMove(pruneNode, currentReroot, root)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, currentReroot, root);
        }

        // B. Target DFS w głąb drzewa T2 (bezpośrednia iteracja bez alokacji ArrayList)
        Node pParent = pruneNode.getParent();
        if (root == pParent) {
            for (int i = 0; i < root.getChildCount(); i++) {
                Node c = root.getChild(i);
                if (c != pruneNode) {
                    dfsTarget(root, c, pruneNode, currentReroot, wanderingSource, metric, visitor);
                }
            }
        } else {
            for (int i = 0; i < root.getChildCount(); i++) {
                Node c = root.getChild(i);
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

    private void dfsTarget(Node parentTarget, Node targetNode, Node pruneNode, Node rerootNode,
                           Node wanderingSource, RootedTbrMetric metric, TbrVisitor visitor) {
        metric.moveTargetDown(parentTarget, targetNode, pruneNode, rerootNode, wanderingSource);

        if (isValidMove(pruneNode, rerootNode, targetNode)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, rerootNode, targetNode);
        }

        Node pParent = pruneNode.getParent();
        if (targetNode == pParent) {
            for (int i = 0; i < targetNode.getChildCount(); i++) {
                Node c = targetNode.getChild(i);
                if (c != pruneNode) {
                    dfsTarget(targetNode, c, pruneNode, rerootNode, wanderingSource, metric, visitor);
                }
            }
        } else {
            for (int i = 0; i < targetNode.getChildCount(); i++) {
                Node c = targetNode.getChild(i);
                if (c == pParent) {
                    for (int j = 0; j < pParent.getChildCount(); j++) {
                        Node gc = pParent.getChild(j);
                        if (gc != pruneNode) {
                            dfsTarget(targetNode, gc, pruneNode, rerootNode, wanderingSource, metric, visitor);
                        }
                    }
                } else {
                    dfsTarget(targetNode, c, pruneNode, rerootNode, wanderingSource, metric, visitor);
                }
            }
        }

        metric.moveTargetUp(parentTarget, targetNode, pruneNode, rerootNode, wanderingSource);
    }

    private boolean isValidMove(Node pruneNode, Node rerootNode, Node targetNode) {
        if (rerootNode == pruneNode && targetNode == pruneNode.getParent()) return false;
        return tbrUtils.isValidTbrMove(pruneNode, rerootNode, targetNode);
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