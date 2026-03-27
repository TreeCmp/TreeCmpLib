package treecmp.heuristics.spr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.SprVisitor;
import treecmp.metrics.topological.BaseRFIncrementalMetric;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class SprNeighborhoodWalker {

    private SprUtils sprUtils = new SprUtils();

    public void walk(Tree baseTree, BaseRFIncrementalMetric metric, SprVisitor visitor) {
        List<Node> allNodes = getAllNodes(baseTree);

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            BitSet movingBits = metric.getCluster(pruneNode);
            Node oldParent = pruneNode.getParent();

            // 1. GLOBALNE ODCIĘCIE NNI: Ścieżka od dziadka do korzenia traci wędrujące bity
            int pruneDepth = 0;
            Node curr = oldParent.getParent();
            while (curr != null && !curr.isRoot()) {
                metric.applyNniStep(curr, movingBits, null); // Ściągamy bity z węzła
                pruneDepth++;
                curr = curr.getParent();
            }

            // 2. SPACER W DÓŁ: Szukamy punktów wpięcia w całym drzewie
            traverseRegraft(pruneNode, movingBits, baseTree.getRoot(), metric, visitor);

            // 3. COFNIĘCIE ODCIĘCIA (Backtracking stosu NNI)
            for (int i = 0; i < pruneDepth; i++) {
                metric.undoNniStep();
            }
        }
    }

    private void traverseRegraft(Node pruneNode, BitSet movingBits, Node currentNode, BaseRFIncrementalMetric metric, SprVisitor visitor) {
        // A. EWALUACJA WPIĘCIA (Zanim węzeł currentNode otrzyma bity!)
        // Omijamy stary punkt odcięcia i jego rodzica
        if (currentNode != pruneNode && currentNode != pruneNode.getParent()) {
            if (sprUtils.isValidSprMove(pruneNode, currentNode)) {

                // Używamy korekty, aby podać perfekcyjny dystans SPR bazując na stosie NNI
                double dist = metric.evaluateExactSprDistance(pruneNode, currentNode, movingBits);
                visitor.visit(dist, pruneNode, currentNode);

                // Optymalizacja: jeśli znaleźliśmy cel, możemy przerwać
                if (dist == 0) return;
            }
        }

        // B. WEJŚCIE GŁĘBIEJ W DRZEWO
        if (!currentNode.isLeaf()) {
            // Tymczasowo dodajemy wędrujące bity do currentNode (krok NNI) na czas wizytowania jego dzieci
            metric.applyNniStep(currentNode, null, movingBits);

            for (int i = 0; i < currentNode.getChildCount(); i++) {
                Node child = currentNode.getChild(i);
                if (child == pruneNode) continue; // Nie wchodzimy do odciętej gałęzi

                traverseRegraft(pruneNode, movingBits, child, metric, visitor);
            }

            // Zdejmujemy bity przy wychodzeniu z węzła (Backtracking)
            metric.undoNniStep();
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