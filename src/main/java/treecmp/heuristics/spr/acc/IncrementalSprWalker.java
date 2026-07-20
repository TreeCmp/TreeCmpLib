package treecmp.heuristics.spr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.SprVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Zoptymalizowany Wędrowiec SPR dedykowany dla metryk ukorzenionych (MC, MP).
 * Przeszukuje otoczenie przy użyciu struktury "Target DFS", aktualizując dokładnie
 * 2 wiersze macierzy LapSolvera podczas schodzenia w dół docelowej gałęzi.
 */
public class IncrementalSprWalker {
    private final SprUtils sprUtils = new SprUtils();

    public interface RootedMetric {
        void setPrunedState(Node pruneNode, Node wanderingSource);
        void setTargetRoot(Node pruneNode, Node wanderingSource);
        void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource);
        void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource);
        void revertPrunedState(Node pruneNode, Node wanderingSource);
        double getCurrentDistance();
    }

    public void walk(Tree baseTree, RootedMetric metric, SprVisitor visitor) {
        List<Node> allNodes = getAllNodes(baseTree);
        Node root = baseTree.getRoot();

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            // POPRAWKA: Wędrujące źródło to ZAWSZE oryginalny rodzic odcinanego węzła.
            // Nawet dla korzenia nie możemy nadpisywać wiersza "Brata", by nie niszczyć macierzy!
            Node wanderingSource = pruneNode.getParent();

            // 1. Inicjalizacja wyciętego stanu
            metric.setPrunedState(pruneNode, wanderingSource);
            metric.setTargetRoot(pruneNode, wanderingSource);

            if (sprUtils.isValidSprMove(pruneNode, root)) {
                visitor.visit(metric.getCurrentDistance(), pruneNode, root);
            }

            // 2. Eksploracja wszystkich punktów wszczepienia za pomocą DFS
            for (Node child : getPrunedChildren(root, pruneNode)) {
                dfsPrunedTree(root, child, pruneNode, wanderingSource, metric, visitor);
            }

            // 3. Cofnięcie do pełnego drzewa
            metric.revertPrunedState(pruneNode, wanderingSource);
        }
    }

    private void dfsPrunedTree(Node parentTarget, Node targetNode, Node pruneNode, Node wanderingSource, RootedMetric metric, SprVisitor visitor) {
        metric.moveTargetDown(parentTarget, targetNode, pruneNode, wanderingSource);

        if (sprUtils.isValidSprMove(pruneNode, targetNode)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, targetNode);
        }

        for (Node child : getPrunedChildren(targetNode, pruneNode)) {
            dfsPrunedTree(targetNode, child, pruneNode, wanderingSource, metric, visitor);
        }

        metric.moveTargetUp(parentTarget, targetNode, pruneNode, wanderingSource);
    }

    // Pozwala wędrowcowi bezpiecznie omijać "dziurę" po wyciętym węźle
    private List<Node> getPrunedChildren(Node n, Node pruneNode) {
        List<Node> children = new ArrayList<>();
        Node pParent = pruneNode.getParent();

        // Jeśli wędrujemy przez węzeł, który fizycznie znika (pParent)
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