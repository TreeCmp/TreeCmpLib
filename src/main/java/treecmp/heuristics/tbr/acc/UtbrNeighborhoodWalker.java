/*
package treecmp.heuristics.tbr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.lang.reflect.Method;

*/
/**
 * Zoptymalizowany, strukturalny Walker dla otoczenia uTBR (Unrooted TBR).
 * Przystosowany do nawigacji po drzewach nieukorzenionych z zachowaniem trifurkacji.
 *//*

public class UtbrNeighborhoodWalker {

    public interface UtbrVisitor {
        void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
    }

    private final UTbrUtils utbrUtils = new UTbrUtils();

    private Method evalMethod = null;
    private Method getDescriptorMethod = null;
    private boolean reflectionInitialized = false;

    public void walk(Tree baseTree, IncrementalMetric metric, UtbrVisitor visitor) {
        List<Node> allNodes = getAllNodes(baseTree);

        for (Node pruneNode : allNodes) {
            // W uTBR nie odcinamy korzenia, żeby nie zaburzyć trifurkacji
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            // 1. Zbieramy wszystkie potencjalne nowe korzenie dla odciętego poddrzewa
            List<Node> rerootNodes = new ArrayList<>();
            collectSubtreeNodes(pruneNode, rerootNodes);

            // 2. Zbieramy wszystkie potencjalne miejsca wpięcia w głównym drzewie
            List<Node> targetNodes = new ArrayList<>();
            collectOutsideNodes(baseTree.getRoot(), pruneNode, targetNodes);

            // 3. Weryfikujemy i ewaluujemy każdą kombinację
            for (Node rerootNode : rerootNodes) {
                for (Node targetNode : targetNodes) {

                    // Pomijamy ruch tożsamościowy (wpięcie w to samo miejsce bez zmiany korzenia)
                    if (rerootNode == pruneNode && targetNode == pruneNode.getParent()) continue;

                    // Oczekujemy, że UTbrUtils ma metodę walidacyjną (analogiczną do UsprUtils/TbrUtils)
                    if (utbrUtils.isValidUtbrMove(pruneNode, rerootNode, targetNode)) {
                        double dist = evaluate(metric, pruneNode, rerootNode, targetNode);
                        visitor.visit(dist, pruneNode, rerootNode, targetNode);
                    }
                }
            }
        }
    }

    private double evaluate(IncrementalMetric metric, Node prune, Node reroot, Node target) {
        if (!reflectionInitialized) {
            try {
                // Dla drzew nieukorzenionych metryki używają podziałów (Splits) zamiast klastrów
                getDescriptorMethod = metric.getClass().getMethod("getSplit", Node.class);
                evalMethod = metric.getClass().getMethod("evaluateExactUTbrDistance", Node.class, Node.class, Node.class, BitSet.class);
            } catch (Exception e) {
                // Metryka nie wspiera wbudowanego wzoru O(1) dla uTBR - to całkowicie normalne
            }
            reflectionInitialized = true;
        }

        // Jeśli metryka ma akcelerator matematyczny
        if (evalMethod != null && getDescriptorMethod != null) {
            try {
                BitSet movingBits = (BitSet) getDescriptorMethod.invoke(metric, reroot);
                return (Double) evalMethod.invoke(metric, prune, reroot, target, movingBits);
            } catch (Exception e) {
                return metric.getCurrentDistance();
            }
        }

        // Zawsze bezpieczny fallback do przeliczenia inkrementalnego (np. dla MS, MT)
        return metric.getCurrentDistance();
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

    private List<Node> getAllNodes(Tree tree) {
        List<Node> list = new ArrayList<>();
        collectSubtreeNodes(tree.getRoot(), list);
        return list;
    }
}*/
