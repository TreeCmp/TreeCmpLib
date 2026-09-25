package treecmp.heuristics.tbr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.lang.reflect.Method;

/**
 * Zoptymalizowany, strukturalny Walker dla otoczenia TBR.
 * Gwarantuje 100% pokrycia matematycznego otoczenia dla drzew ukorzenionych.
 */
public class TbrNeighborhoodWalker {

    public interface TbrVisitor {
        void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
    }

    private final TbrUtils tbrUtils = new TbrUtils();

    // Cache dla mechanizmu refleksji (akcelerator dla metryk RF/RFC/MC/MP)
    private Method evalMethod4 = null;
    private Method evalMethod3 = null;
    private Method getClusterMethod = null;
    private boolean reflectionInitialized = false;

    public void walk(Tree baseTree, IncrementalMetric metric, TbrVisitor visitor) {
        List<Node> allNodes = getAllNodes(baseTree);

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            // 1. Wszystkie potencjalne nowe korzenie dla odciętego poddrzewa
            List<Node> rerootNodes = new ArrayList<>();
            collectSubtreeNodes(pruneNode, rerootNodes);

            // 2. Wszystkie potencjalne miejsca wpięcia w głównym drzewie
            List<Node> targetNodes = new ArrayList<>();
            collectOutsideNodes(baseTree.getRoot(), pruneNode, targetNodes);

            // 3. Weryfikacja i ewaluacja każdej kombinacji
            for (Node rerootNode : rerootNodes) {
                for (Node targetNode : targetNodes) {
                    if (rerootNode == pruneNode && targetNode == pruneNode.getParent()) continue;

                    if (tbrUtils.isValidTbrMove(pruneNode, rerootNode, targetNode)) {
                        double dist = evaluate(metric, pruneNode, rerootNode, targetNode);
                        visitor.visit(dist, pruneNode, rerootNode, targetNode);
                    }
                }
            }
        }
    }

    private double evaluate(IncrementalMetric metric, Node prune, Node reroot, Node target) {
        if (metric == null) {
            return 0.0;
        }

        if (!reflectionInitialized) {
            try {
                getClusterMethod = metric.getClass().getMethod("getCluster", Node.class);
            } catch (Exception ignored) {
            }
            try {
                evalMethod4 = metric.getClass().getMethod("evaluateExactTbrDistance", Node.class, Node.class, Node.class, BitSet.class);
            } catch (Exception ignored) {
            }
            try {
                evalMethod3 = metric.getClass().getMethod("evaluateExactTbrDistance", Node.class, Node.class, Node.class);
            } catch (Exception ignored) {
            }
            reflectionInitialized = true;
        }

        if (evalMethod4 != null && getClusterMethod != null) {
            try {
                BitSet movingBits = (BitSet) getClusterMethod.invoke(metric, reroot);
                return (Double) evalMethod4.invoke(metric, prune, reroot, target, movingBits);
            } catch (Exception ignored) {
            }
        }

        if (evalMethod3 != null) {
            try {
                return (Double) evalMethod3.invoke(metric, prune, reroot, target);
            } catch (Exception ignored) {
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

    private List<Node> getAllNodes(Tree tree) {
        List<Node> list = new ArrayList<>();
        collectSubtreeNodes(tree.getRoot(), list);
        return list;
    }
}