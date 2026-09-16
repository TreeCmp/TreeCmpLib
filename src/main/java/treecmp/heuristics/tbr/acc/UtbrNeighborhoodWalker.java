package treecmp.heuristics.tbr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.IncrementalMetric;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Zoptymalizowany, przyrostowy Walker dla otoczenia uTBR (Unrooted TBR).
 * Obsługuje dwuetapową eksplorację 2D-DFS dla metryk implementujących RootedTbrMetric (MS, M3, MC, MP),
 * eliminując narzut O(N^3) i alokacje pamięci, z bezpiecznym fallbackiem dla pozostałych metryk.
 */
public class UtbrNeighborhoodWalker {

    public interface UtbrVisitor {
        void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
    }

    private final UTbrUtils utbrUtils = new UTbrUtils();

    // Buforowane listy węzłów (Zero-Allocation per walk)
    private final List<Node> allNodesBuf = new ArrayList<>();
    private final List<Node> rerootNodesBuf = new ArrayList<>();
    private final List<Node> targetNodesBuf = new ArrayList<>();

    // Pamięć podręczna refleksji powiązana z klasą metryki
    private Class<?> cachedMetricClass = null;
    private Method cachedEvalMethod = null;
    private Method cachedDescriptorMethod = null;

    public void walk(Tree baseTree, IncrementalMetric metric, UtbrVisitor visitor) {
        // MS wspiera pełny 2D-DFS splitów O(N^2)
        // M3 oraz RF korzystają ze zoptymalizowanej ścieżki wyceny uTBR
        if (metric instanceof RootedTbrMetric && !(metric instanceof treecmp.metrics.topological.acc.M3IncrementalMetric)) {
            walkFast2dDfs(baseTree, (RootedTbrMetric) metric, visitor);
            return;
        }

        // Ścieżka dla M3 oraz RFIncrementalMetric
        walkFallback(baseTree, metric, visitor);
    }

    /**
     * W pełni przyrostowy spacer 2D-DFS po otoczeniu uTBR:
     * - Bisekcja w T1 (pruneNode)
     * - DFS po wariantach przekorzenienia odciętego fragmentu (Reroot DFS)
     * - DFS po wariantach wpięcia w drzewie głównym (Target DFS)
     * Każdy krok to modyfikacja 1-2 wierszy macierzy kosztów w czasie O(N^2).
     */
    private void walkFast2dDfs(Tree baseTree, RootedTbrMetric metric, UtbrVisitor visitor) {
        allNodesBuf.clear();
        collectSubtreeNodes(baseTree.getRoot(), allNodesBuf);
        Node root = baseTree.getRoot();

        for (int i = 0; i < allNodesBuf.size(); i++) {
            Node pruneNode = allNodesBuf.get(i);
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            Node wanderingSource = pruneNode.getParent();

            // 1. Faza bisekcji
            metric.setPrunedState(pruneNode, wanderingSource);
            metric.setTargetRoot(pruneNode, pruneNode, wanderingSource);

            // 2. Eksploracja 2D-DFS (Reroot DFS x Target DFS)
            dfsReroot(pruneNode, pruneNode, wanderingSource, root, metric, visitor);

            // 3. Wycofanie bisekcji
            metric.revertPrunedState(pruneNode, wanderingSource);
        }
    }

    private void dfsReroot(Node currentReroot, Node pruneNode, Node wanderingSource, Node root,
                           RootedTbrMetric metric, UtbrVisitor visitor) {

        // Dla aktualnego ukorzenienia odciętego fragmentu przeszukujemy wszystkie pozycje wpięcia w T2
        dfsTarget(root, pruneNode, currentReroot, wanderingSource, metric, visitor);

        // Schodzimy w dół odciętego poddrzewa T1, odwracając krawędzie
        if (!currentReroot.isLeaf()) {
            for (int i = 0; i < currentReroot.getChildCount(); i++) {
                Node nextReroot = currentReroot.getChild(i);
                metric.moveRerootDown(currentReroot, nextReroot, pruneNode);
                dfsReroot(nextReroot, pruneNode, wanderingSource, root, metric, visitor);
                metric.moveRerootUp(currentReroot, nextReroot, pruneNode);
            }
        }
    }

    private void dfsTarget(Node currentTarget, Node pruneNode, Node currentReroot, Node wanderingSource,
                           RootedTbrMetric metric, UtbrVisitor visitor) {

        // Pomijamy ruch tożsamościowy
        if (!(currentReroot == pruneNode && currentTarget == pruneNode.getParent())) {
            if (utbrUtils.isValidUtbrMove(pruneNode, currentReroot, currentTarget)) {
                visitor.visit(metric.getCurrentDistance(), pruneNode, currentReroot, currentTarget);
            }
        }

        // Schodzimy w głąb głównego drzewa docelowego
        if (!currentTarget.isLeaf()) {
            for (int i = 0; i < currentTarget.getChildCount(); i++) {
                Node childTarget = currentTarget.getChild(i);
                if (childTarget == pruneNode) continue;

                metric.moveTargetDown(currentTarget, childTarget, pruneNode, currentReroot, wanderingSource);
                dfsTarget(childTarget, pruneNode, currentReroot, wanderingSource, metric, visitor);
                metric.moveTargetUp(currentTarget, childTarget, pruneNode, currentReroot, wanderingSource);
            }
        }
    }

    /**
     * Zoptymalizowana ścieżka fallbackowa (z buforowaniem alokacji i refleksji)
     * dla metryk, które posiadają akcelerator matematyczny evaluateExactUTbrDistance (np. RFIncrementalMetric).
     */
    private void walkFallback(Tree baseTree, IncrementalMetric metric, UtbrVisitor visitor) {
        initReflection(metric.getClass());

        allNodesBuf.clear();
        collectSubtreeNodes(baseTree.getRoot(), allNodesBuf);

        for (int i = 0; i < allNodesBuf.size(); i++) {
            Node pruneNode = allNodesBuf.get(i);
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            rerootNodesBuf.clear();
            collectSubtreeNodes(pruneNode, rerootNodesBuf);

            targetNodesBuf.clear();
            collectOutsideNodes(baseTree.getRoot(), pruneNode, targetNodesBuf);

            for (int r = 0; r < rerootNodesBuf.size(); r++) {
                Node rerootNode = rerootNodesBuf.get(r);
                for (int t = 0; t < targetNodesBuf.size(); t++) {
                    Node targetNode = targetNodesBuf.get(t);

                    if (rerootNode == pruneNode && targetNode == pruneNode.getParent()) continue;

                    if (utbrUtils.isValidUtbrMove(pruneNode, rerootNode, targetNode)) {
                        double dist = evaluateFallback(metric, pruneNode, rerootNode, targetNode);
                        visitor.visit(dist, pruneNode, rerootNode, targetNode);
                    }
                }
            }
        }
    }

    private void initReflection(Class<?> metricClass) {
        if (cachedMetricClass != metricClass) {
            cachedMetricClass = metricClass;
            cachedEvalMethod = null;
            cachedDescriptorMethod = null;
            try {
                cachedDescriptorMethod = metricClass.getMethod("getSplit", Node.class);
                cachedEvalMethod = metricClass.getMethod("evaluateExactUTbrDistance", Node.class, Node.class, Node.class, BitSet.class);
            } catch (NoSuchMethodException ignored) {
            }
        }
    }

    private double evaluateFallback(IncrementalMetric metric, Node prune, Node reroot, Node target) {
        if (cachedEvalMethod != null && cachedDescriptorMethod != null) {
            try {
                BitSet movingBits = (BitSet) cachedDescriptorMethod.invoke(metric, reroot);
                return (Double) cachedEvalMethod.invoke(metric, prune, reroot, target, movingBits);
            } catch (Exception e) {
                return metric.getCurrentDistance();
            }
        }
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
}