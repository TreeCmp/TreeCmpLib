package treecmp.heuristics.spr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.spr.UsprUtils; // Twoja nowa klasa narzędziowa uSPR
import treecmp.heuristics.spr.SprVisitor;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.List;

public class UsprNeighborhoodWalker {

    private UsprUtils usprUtils = new UsprUtils();

    public void walk(Tree baseTree, IncrementalMetric metric, SprVisitor visitor) {
        List<Node> allNodes = getAllNodes(baseTree);

        for (Node pruneNode : allNodes) {
            // W uSPR nie ruszamy trifurkacji w korzeniu
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            metric.applySprPrune(pruneNode);
            traverseRegraft(pruneNode, baseTree.getRoot(), metric, visitor);
            metric.undoSprPrune(pruneNode);
        }
    }

    private void traverseRegraft(Node pruneNode, Node currentNode, IncrementalMetric metric, SprVisitor visitor) {
        // A. EWALUACJA WPIĘCIA
        if (currentNode != pruneNode && currentNode != pruneNode.getParent()) {
            if (usprUtils.isValidUsprMove(pruneNode, currentNode)) {

                double dist = metric.evaluateSprRegraft(pruneNode, currentNode);
                visitor.visit(dist, pruneNode, currentNode);

                if (dist == 0) return;
            }
        }

        // B. WEJŚCIE GŁĘBIEJ W DRZEWO
        if (!currentNode.isLeaf()) {
            metric.applySprRegraftStep(pruneNode, currentNode);

            for (int i = 0; i < currentNode.getChildCount(); i++) {
                Node child = currentNode.getChild(i);

                // KLUCZOWA ZMIANA DLA USPR:
                // Usunięto "if (child == pruneNode) continue;"
                // Walker MUSI wejść do odciętego poddrzewa, aby wygenerować tzw. Inner Moves
                // i połączyć drzewa za pomocą innych punktów zaczepienia.
                traverseRegraft(pruneNode, child, metric, visitor);
            }

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