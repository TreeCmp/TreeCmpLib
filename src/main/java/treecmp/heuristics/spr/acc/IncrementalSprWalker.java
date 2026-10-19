package treecmp.heuristics.spr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.SprVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Zoptymalizowany, bezalokacyjny (Zero-Allocation) Wędrowiec SPR dedykowany dla metryk ukorzenionych (RFC, MC, MP).
 * Przeszukuje otoczenie przy użyciu struktury "Target DFS", aktualizując dokładnie
 * 2 wiersze macierzy LapSolvera podczas schodzenia w dół docelowej gałęzi.
 */
public class IncrementalSprWalker {
    private final SprUtils sprUtils = new SprUtils();
    private final List<Node> reusableNodes = new ArrayList<>(512);

    public void walk(Tree baseTree, RootedSprIncrementalMetric metric, SprVisitor visitor) {
        reusableNodes.clear();
        collectNodes(baseTree.getRoot(), reusableNodes);
        Node root = baseTree.getRoot();
        int nodeCount = reusableNodes.size();

        for (int idx = 0; idx < nodeCount; idx++) {
            Node pruneNode = reusableNodes.get(idx);
            Node pParent = pruneNode.getParent();
            if (pruneNode.isRoot() || pParent == null) continue;

            // Wędrujące źródło to oryginalny rodzic odcinanego węzła
            Node wanderingSource = pParent;

            // 1. Inicjalizacja wyciętego stanu
            metric.setPrunedState(pruneNode, wanderingSource);
            metric.setTargetRoot(pruneNode, wanderingSource);

            if (sprUtils.isValidSprMove(pruneNode, root)) {
                visitor.visit(metric.getCurrentDistance(), pruneNode, root);
            }

            // 2. Bezalokacyjna eksploracja wszystkich punktów wszczepienia za pomocą DFS
            explorePrunedChildren(root, pruneNode, pParent, wanderingSource, metric, visitor);

            // 3. Cofnięcie do pełnego drzewa
            metric.revertPrunedState(pruneNode, wanderingSource);
        }
    }

    private void dfsPrunedTree(Node parentTarget, Node targetNode, Node pruneNode, Node pParent,
                               Node wanderingSource, RootedSprIncrementalMetric metric, SprVisitor visitor) {
        metric.moveTargetDown(parentTarget, targetNode, pruneNode, wanderingSource);

        if (sprUtils.isValidSprMove(pruneNode, targetNode)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, targetNode);
        }

        explorePrunedChildren(targetNode, pruneNode, pParent, wanderingSource, metric, visitor);

        metric.moveTargetUp(parentTarget, targetNode, pruneNode, wanderingSource);
    }

    /**
     * Bezalokacyjne przejście po dzieciach węzła z pominięciem wyciętego poddrzewa (pruneNode)
     * oraz zapadniętego węzła rodzica (pParent).
     */
    private void explorePrunedChildren(Node n, Node pruneNode, Node pParent,
                                       Node wanderingSource, RootedSprIncrementalMetric metric, SprVisitor visitor) {
        int childCount = n.getChildCount();

        if (n == pParent) {
            for (int i = 0; i < childCount; i++) {
                Node child = n.getChild(i);
                if (child != pruneNode) {
                    dfsPrunedTree(n, child, pruneNode, pParent, wanderingSource, metric, visitor);
                }
            }
            return;
        }

        for (int i = 0; i < childCount; i++) {
            Node c = n.getChild(i);
            if (c == pParent) {
                int pChildCount = pParent.getChildCount();
                for (int j = 0; j < pChildCount; j++) {
                    Node grandChild = pParent.getChild(j);
                    if (grandChild != pruneNode) {
                        dfsPrunedTree(n, grandChild, pruneNode, pParent, wanderingSource, metric, visitor);
                    }
                }
            } else {
                dfsPrunedTree(n, c, pruneNode, pParent, wanderingSource, metric, visitor);
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