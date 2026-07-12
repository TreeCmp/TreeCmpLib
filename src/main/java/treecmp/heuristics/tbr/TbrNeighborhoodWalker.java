package treecmp.heuristics.tbr;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.moves.VirtualNniMove;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.List;

/**
 * Zoptymalizowany Walker dla otoczenia TBR.
 * Obsługuje w pełni węzły o stopniu > 2 (np. korzeń w drzewach nieukorzenionych uTBR).
 */
public class TbrNeighborhoodWalker {

    public interface TbrVisitor {
        void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
    }

    private TbrUtils tbrUtils = new TbrUtils();

    public void walk(Tree baseTree, IncrementalMetric metric, TbrVisitor visitor) {
        List<Node> allNodes = getAllNodes(baseTree);

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            // Dla korzenia o stopniu 3 (nieukorzenione), po odcięciu jednej gałęzi
            // pozostają DWA rodzeństwa. Musimy wejść w obie ścieżki!
            List<Node> startGateways = getSiblings(pruneNode);
            for (Node startGateway : startGateways) {
                traverseReroot(pruneNode, pruneNode, startGateway, pruneNode.getParent(), metric, visitor);
            }
        }
    }

    private void traverseReroot(Node pruneNode, Node currentReroot, Node mainTreeGateway, Node cameFrom,
                                IncrementalMetric metric, TbrVisitor visitor) {

        traverseRegraft(pruneNode, currentReroot, mainTreeGateway, currentReroot, metric, visitor);

        if (!currentReroot.isLeaf()) {
            for (int i = 0; i < currentReroot.getChildCount(); i++) {
                Node child = currentReroot.getChild(i);
                if (child == cameFrom || child == mainTreeGateway) continue;

                // Pobieramy wszystkie pozostałe gałęzie (obsługa stopnia > 2)
                List<Node> otherChildren = getOtherChildren(currentReroot, child, cameFrom, mainTreeGateway);

                for (Node otherChild : otherChildren) {
                    NniMove rerootMove = new VirtualNniMove(otherChild, mainTreeGateway, currentReroot);

                    metric.applyNni(rerootMove);
                    traverseReroot(pruneNode, child, mainTreeGateway, currentReroot, metric, visitor);
                    metric.undoNni(rerootMove);
                }
            }
        }
    }

    private boolean traverseRegraft(Node pruneNode, Node rerootNode, Node currentTarget, Node cameFrom,
                                    IncrementalMetric metric, TbrVisitor visitor) {

        // 1. RUCH W GÓRĘ GŁÓWNEGO DRZEWA (UP)
        Node p = currentTarget.getParent();
        if (p != null && p != cameFrom && p != pruneNode) {
            // Węzeł 'p' może mieć wiele rodzeństwa (np. przy korzeniu)
            List<Node> aunts = getSiblings(p);
            for (Node aunt : aunts) {
                if (aunt != pruneNode) {
                    NniMove upMove = new VirtualNniMove(rerootNode, aunt, p);

                    metric.applyNni(upMove);

                    if (tbrUtils.isValidTbrMove(pruneNode, rerootNode, p)) {
                        double exactDist = evaluate(metric);
                        visitor.visit(exactDist, pruneNode, rerootNode, p);
                        if (exactDist == 0) { metric.undoNni(upMove); return false; }
                    }

                    if (!traverseRegraft(pruneNode, rerootNode, p, currentTarget, metric, visitor)) {
                        metric.undoNni(upMove); return false;
                    }
                    metric.undoNni(upMove);
                }
            }
        }

        // 2. RUCH W DÓŁ GŁÓWNEGO DRZEWA (DOWN)
        if (!currentTarget.isLeaf()) {
            for (int i = 0; i < currentTarget.getChildCount(); i++) {
                Node child = currentTarget.getChild(i);
                if (child == cameFrom || child == pruneNode) continue;

                List<Node> otherChildren = getOtherChildren(currentTarget, child, cameFrom, pruneNode);
                for (Node otherChild : otherChildren) {
                    NniMove downMove = new VirtualNniMove(otherChild, rerootNode, currentTarget);

                    metric.applyNni(downMove);

                    if (tbrUtils.isValidTbrMove(pruneNode, rerootNode, child)) {
                        double exactDist = evaluate(metric);
                        visitor.visit(exactDist, pruneNode, rerootNode, child);
                        if (exactDist == 0) { metric.undoNni(downMove); return false; }
                    }

                    if (!traverseRegraft(pruneNode, rerootNode, child, currentTarget, metric, visitor)) {
                        metric.undoNni(downMove); return false;
                    }
                    metric.undoNni(downMove);
                }
            }
        }
        return true;
    }

    // Bezpośrednie wywołanie dystansu (metryka inkrementalna zawsze zna swój stan po wykonanych ruchach NNI)
    private double evaluate(IncrementalMetric metric) {
        return metric.getCurrentDistance();
    }

    // Zwraca listę wszystkich rodzeństw danego węzła
    private List<Node> getSiblings(Node node) {
        List<Node> siblings = new ArrayList<>();
        Node parent = node.getParent();
        if (parent != null) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                if (parent.getChild(i) != node) {
                    siblings.add(parent.getChild(i));
                }
            }
        }
        return siblings;
    }

    // Bezpiecznie filtruje dzieci, omijając te zablokowane
    private List<Node> getOtherChildren(Node parent, Node exclude1, Node exclude2, Node exclude3) {
        List<Node> others = new ArrayList<>();
        for (int i = 0; i < parent.getChildCount(); i++) {
            Node child = parent.getChild(i);
            if (child != exclude1 && child != exclude2 && child != exclude3) {
                others.add(child);
            }
        }
        return others;
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