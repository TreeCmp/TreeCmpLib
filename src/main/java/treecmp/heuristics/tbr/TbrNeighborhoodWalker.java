//package treecmp.heuristics.tbr;
//
//import pal.tree.Node;
//import pal.tree.Tree;
//import treecmp.heuristics.moves.NniMove;
//import treecmp.heuristics.moves.VirtualNniMove;
//import treecmp.metrics.IncrementalMetric;
//import treecmp.metrics.topological.BaseRFIncrementalMetric;
//
//import java.util.ArrayList;
//import java.util.BitSet;
//import java.util.List;
//
//public class TbrNeighborhoodWalker {
//
//    public interface TbrVisitor {
//        void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
//    }
//
//    private TbrUtils tbrUtils = new TbrUtils();
//
//    public void walk(Tree baseTree, IncrementalMetric metric, TbrVisitor visitor) {
//        List<Node> allNodes = getAllNodes(baseTree);
//
//        for (Node pruneNode : allNodes) {
//            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;
//
//            // Wirtualną bramą (Gateway) łączącą poddrzewo z resztą drzewa jest na początku brat pruneNode
//            Node startGateway = getSibling(pruneNode);
//            if (startGateway == null) continue;
//
//            // Wymiar 2: Rerooting DFS - Rozpoczynamy przekorzenianie odciętej gałęzi
//            traverseReroot(pruneNode, pruneNode, startGateway, pruneNode.getParent(), metric, visitor);
//        }
//    }
//
//    private void traverseReroot(Node pruneNode, Node currentReroot, Node mainTreeGateway, Node cameFrom,
//                                IncrementalMetric metric, TbrVisitor visitor) {
//
//        // Wymiar 3: Dla konkretnego ułożenia korzenia, odpalamy spacer po głównym drzewie
//        traverseRegraft(pruneNode, currentReroot, mainTreeGateway, currentReroot, metric, visitor);
//
//        // Zmiana korzenia (Schodzimy NNI w dół odciętego poddrzewa)
//        if (!currentReroot.isLeaf()) {
//            for (int i = 0; i < currentReroot.getChildCount(); i++) {
//                Node child = currentReroot.getChild(i);
//                if (child == cameFrom || child == mainTreeGateway) continue;
//
//                Node otherChild = (i == 0) ? currentReroot.getChild(1) : currentReroot.getChild(0);
//                if (otherChild != null) {
//                    // Krok rNNI: Wirtualny korzeń (currentReroot) traci gałąź (otherChild), a zyskuje wejście do głównego drzewa
//                    NniMove rerootMove = new VirtualNniMove(otherChild, mainTreeGateway, currentReroot);
//
//                    metric.applyNni(rerootMove); // Odkładamy na stos
//
//                    // Rekurencja z nowym korzeniem
//                    traverseReroot(pruneNode, child, mainTreeGateway, currentReroot, metric, visitor);
//
//                    metric.undoNni(rerootMove); // Ściągamy ze stosu
//                }
//            }
//        }
//    }
//
//    private boolean traverseRegraft(Node pruneNode, Node rerootNode, Node currentTarget, Node cameFrom,
//                                    IncrementalMetric metric, TbrVisitor visitor) {
//
//        // 1. RUCH W GÓRĘ GŁÓWNEGO DRZEWA (rNNI UP)
//        Node p = currentTarget.getParent();
//        if (p != null && p != cameFrom && p != pruneNode) {
//            Node aunt = getSibling(p);
//            if (aunt != null && aunt != pruneNode) {
//                // Krok rNNI: Węzeł 'p' wymienia odcięte poddrzewo (rerootNode) na rodzeństwo (aunt)
//                NniMove upMove = new VirtualNniMove(rerootNode, aunt, p);
//
//                metric.applyNni(upMove);
//
//                if (tbrUtils.isValidTbrMove(pruneNode, rerootNode, p)) {
//                    double exactDist = evaluate(metric, pruneNode, rerootNode, p);
//                    visitor.visit(exactDist, pruneNode, rerootNode, p);
//                    if (exactDist == 0) { metric.undoNni(upMove); return false; }
//                }
//
//                if (!traverseRegraft(pruneNode, rerootNode, p, currentTarget, metric, visitor)) {
//                    metric.undoNni(upMove); return false;
//                }
//                metric.undoNni(upMove);
//            }
//        }
//
//        // 2. RUCH W DÓŁ GŁÓWNEGO DRZEWA (rNNI DOWN)
//        if (!currentTarget.isLeaf()) {
//            for (int i = 0; i < currentTarget.getChildCount(); i++) {
//                Node child = currentTarget.getChild(i);
//                if (child == cameFrom || child == pruneNode) continue;
//
//                Node otherChild = (i == 0) ? currentTarget.getChild(1) : currentTarget.getChild(0);
//                if (otherChild != null) {
//                    // Krok rNNI: Węzeł 'currentTarget' wymienia 'otherChild' na wędrujące poddrzewo ('rerootNode')
//                    NniMove downMove = new VirtualNniMove(otherChild, rerootNode, currentTarget);
//
//                    metric.applyNni(downMove);
//
//                    if (tbrUtils.isValidTbrMove(pruneNode, rerootNode, child)) {
//                        double exactDist = evaluate(metric, pruneNode, rerootNode, child);
//                        visitor.visit(exactDist, pruneNode, rerootNode, child);
//                        if (exactDist == 0) { metric.undoNni(downMove); return false; }
//                    }
//
//                    if (!traverseRegraft(pruneNode, rerootNode, child, currentTarget, metric, visitor)) {
//                        metric.undoNni(downMove); return false;
//                    }
//                    metric.undoNni(downMove);
//                }
//            }
//        }
//        return true;
//    }
//
//    // Ekstrakcja dokładnego dystansu, jeśli metryka to RF
//    private double evaluate(IncrementalMetric metric, Node prune, Node reroot, Node target) {
//        if (metric instanceof BaseRFIncrementalMetric) {
//            BaseRFIncrementalMetric rfMetric = (BaseRFIncrementalMetric) metric;
//            BitSet movingBits = rfMetric.getCluster(reroot);
//            return rfMetric.evaluateExactTbrDistance(prune, reroot, target, movingBits);
//        }
//        // Dla innych metryk (np. MP/MC), zwracamy to co jest na stosie (wymaga osobnej obsługi widm)
//        return metric.getCurrentDistance();
//    }
//
//    private Node getSibling(Node node) {
//        Node parent = node.getParent();
//        if (parent == null || parent.getChildCount() < 2) return null;
//        return (parent.getChild(0) == node) ? parent.getChild(1) : parent.getChild(0);
//    }
//
//    private List<Node> getAllNodes(Tree tree) {
//        List<Node> list = new ArrayList<>();
//        collectNodes(tree.getRoot(), list);
//        return list;
//    }
//
//    private void collectNodes(Node node, List<Node> list) {
//        list.add(node);
//        for (int i = 0; i < node.getChildCount(); i++) {
//            collectNodes(node.getChild(i), list);
//        }
//    }
//}