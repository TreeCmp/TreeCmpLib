package treecmp.heuristics.spr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.SprVisitor;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.List;

public class ClassicSprWalker {

    private SprUtils sprUtils = new SprUtils();

    // Parametrem jest teraz czysty interfejs
    public void walk(Tree baseTree, IncrementalMetric metric, SprVisitor visitor) {
        List<Node> allNodes = getAllNodes(baseTree);

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            // 1. GLOBALNE ODCIĘCIE SPR (Metryka sama zarządza swoim stanem)
            metric.applySprPrune(pruneNode);

            // 2. SPACER W DÓŁ: Szukamy punktów wpięcia w całym drzewie
            traverseRegraft(pruneNode, baseTree.getRoot(), metric, visitor);

            // 3. COFNIĘCIE ODCIĘCIA
            metric.undoSprPrune(pruneNode);
        }
    }

    private void traverseRegraft(Node pruneNode, Node currentNode, IncrementalMetric metric, SprVisitor visitor) {
        // A. EWALUACJA WPIĘCIA
        if (currentNode != pruneNode && currentNode != pruneNode.getParent()) {
            if (sprUtils.isValidSprMove(pruneNode, currentNode)) {

                // Prosimy metrykę o wynik
                double dist = metric.evaluateSprRegraft(pruneNode, currentNode);
                visitor.visit(dist, pruneNode, currentNode);

                // Optymalizacja: jeśli znaleźliśmy cel, możemy przerwać
                if (dist == 0) return;
            }
        }

        // B. WEJŚCIE GŁĘBIEJ W DRZEWO
        if (!currentNode.isLeaf()) {
            // Metryka przygotowuje węzeł do wejścia w dół
            metric.applySprRegraftStep(pruneNode, currentNode);

            for (int i = 0; i < currentNode.getChildCount(); i++) {
                Node child = currentNode.getChild(i);
                if (child == pruneNode) continue;

                traverseRegraft(pruneNode, child, metric, visitor);
            }

            // Metryka cofa krok
            metric.undoSprRegraftStep();
        }
    }

    private List<Node> getAllNodes(Tree tree) {
        List<Node> list = new ArrayList<>();
        collectNodes(tree.getRoot(), list);
        return list;
    }

    private void collectNodes(Node node, List<Node> list) {
        list.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNodes(node.getChild(i), list);
        }
    }
}