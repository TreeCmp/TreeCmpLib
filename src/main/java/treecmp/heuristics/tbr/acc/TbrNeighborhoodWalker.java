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
 * Zoptymalizowany, strukturalny Walker 2D-DFS dla otoczenia TBR drzew ukorzenionych (rTBR).
 * Wykorzystuje bezpośredni kontrakt RtbrIncrementalMetric (Zero-Allocation 2D-DFS)
 * z bezpiecznym fallbackiem dla pozostałych metryk.
 */
public class TbrNeighborhoodWalker {

    public interface TbrVisitor {
        void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
    }

    private final TbrUtils tbrUtils = new TbrUtils();
    private final List<Node> allNodesBuf = new ArrayList<>(512);

    // Cache dla mechanizmu refleksji (fallback dla metryk nieimplementujących RtbrIncrementalMetric)
    private Method evalMethod4 = null;
    private Method evalMethod3 = null;
    private Method getClusterMethod = null;
    private boolean reflectionInitialized = false;
    private Class<?> lastMetricClass = null;

    public void walk(Tree baseTree, IncrementalMetric metric, TbrVisitor visitor) {
        if (metric instanceof RtbrIncrementalMetric) {
            walkFast2dDfs(baseTree, (RtbrIncrementalMetric) metric, visitor);
            return;
        }
        walkFallback(baseTree, metric, visitor);
    }

    private void walkFast2dDfs(Tree baseTree, RtbrIncrementalMetric metric, TbrVisitor visitor) {
        allNodesBuf.clear();
        collectSubtreeNodes(baseTree.getRoot(), allNodesBuf);
        Node root = baseTree.getRoot();

        for (int i = 0; i < allNodesBuf.size(); i++) {
            Node pruneNode = allNodesBuf.get(i);
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            Node wanderingSource = pruneNode.getParent();

            // 1. Odcięcie poddrzewa T_P i ustawienie wpięcia bazowego w korzeniu T_B (2 poziomy stosu delta)
            metric.setPrunedState(pruneNode, wanderingSource);
            metric.setTargetRoot(pruneNode, wanderingSource);

            // 2. Dwuwymiarowy DFS: Zewnętrzny DFS (Reroot w T_P) x Wewnętrzny DFS (Target w T_B)
            dfsReroot(pruneNode, pruneNode, wanderingSource, root, metric, visitor);

            // 3. Atomowe wycofanie stanu odcięcia (zdejmuje setTargetRoot i setPrunedState ze stosu delta)
            metric.revertPrunedState(pruneNode, wanderingSource);
        }
    }

    private void dfsReroot(Node currentReroot, Node pruneNode, Node wanderingSource, Node root,
                           RtbrIncrementalMetric metric, TbrVisitor visitor) {
        // A. Ewaluacja wpięcia w korzeniu drzewa szkieletowego T_B
        if (isValidMove(pruneNode, currentReroot, root)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, currentReroot, root);
        }

        // B. Wewnętrzny Target DFS w głąb drzewa szkieletowego T_B (z pominięciem pruneNode i pParent)
        explorePrunedChildren(root, pruneNode, currentReroot, wanderingSource, metric, visitor);

        // C. Zewnętrzny Reroot DFS w głąb odciętego poddrzewa T_P
        if (!currentReroot.isLeaf()) {
            for (int i = 0; i < currentReroot.getChildCount(); i++) {
                Node nextReroot = currentReroot.getChild(i);
                metric.moveRerootDown(currentReroot, nextReroot, pruneNode);
                dfsReroot(nextReroot, pruneNode, wanderingSource, root, metric, visitor);
                metric.moveRerootUp(currentReroot, nextReroot, pruneNode);
            }
        }
    }

    private void dfsTarget(Node parentTarget, Node targetNode, Node pruneNode, Node currentReroot,
                           Node wanderingSource, RtbrIncrementalMetric metric, TbrVisitor visitor) {
        metric.moveTargetDown(parentTarget, targetNode, pruneNode, wanderingSource);

        if (isValidMove(pruneNode, currentReroot, targetNode)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, currentReroot, targetNode);
        }

        explorePrunedChildren(targetNode, pruneNode, currentReroot, wanderingSource, metric, visitor);

        metric.moveTargetUp(parentTarget, targetNode, pruneNode, wanderingSource);
    }

    private void explorePrunedChildren(Node n, Node pruneNode, Node currentReroot,
                                       Node wanderingSource, RtbrIncrementalMetric metric, TbrVisitor visitor) {
        Node pParent = pruneNode.getParent();
        int childCount = n.getChildCount();

        if (n == pParent) {
            for (int i = 0; i < childCount; i++) {
                Node child = n.getChild(i);
                if (child != pruneNode) {
                    dfsTarget(n, child, pruneNode, currentReroot, wanderingSource, metric, visitor);
                }
            }
            return;
        }

        for (int i = 0; i < childCount; i++) {
            Node c = n.getChild(i);
            if (c == pruneNode) continue;
            if (c == pParent) {
                for (int j = 0; j < pParent.getChildCount(); j++) {
                    Node gc = pParent.getChild(j);
                    if (gc != pruneNode) {
                        dfsTarget(n, gc, pruneNode, currentReroot, wanderingSource, metric, visitor);
                    }
                }
            } else {
                dfsTarget(n, c, pruneNode, currentReroot, wanderingSource, metric, visitor);
            }
        }
    }

    private boolean isValidMove(Node pruneNode, Node rerootNode, Node targetNode) {
        if (rerootNode == pruneNode && targetNode == pruneNode.getParent()) return false;
        return tbrUtils.isValidTbrMove(pruneNode, rerootNode, targetNode);
    }

    private void walkFallback(Tree baseTree, IncrementalMetric metric, TbrVisitor visitor) {
        allNodesBuf.clear();
        collectSubtreeNodes(baseTree.getRoot(), allNodesBuf);
        List<Node> rerootNodes = new ArrayList<>();
        List<Node> targetNodes = new ArrayList<>();

        for (int i = 0; i < allNodesBuf.size(); i++) {
            Node pruneNode = allNodesBuf.get(i);
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            rerootNodes.clear();
            collectSubtreeNodes(pruneNode, rerootNodes);

            targetNodes.clear();
            collectOutsideNodes(baseTree.getRoot(), pruneNode, targetNodes);

            for (int r = 0; r < rerootNodes.size(); r++) {
                Node rerootNode = rerootNodes.get(r);
                for (int t = 0; t < targetNodes.size(); t++) {
                    Node targetNode = targetNodes.get(t);
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

        if (!reflectionInitialized || metric.getClass() != lastMetricClass) {
            lastMetricClass = metric.getClass();
            getClusterMethod = null;
            evalMethod4 = null;
            evalMethod3 = null;
            try {
                getClusterMethod = lastMetricClass.getMethod("getCluster", Node.class);
            } catch (Exception ignored) {
            }
            try {
                evalMethod4 = lastMetricClass.getMethod("evaluateExactTbrDistance", Node.class, Node.class, Node.class, BitSet.class);
            } catch (Exception ignored) {
            }
            try {
                evalMethod3 = lastMetricClass.getMethod("evaluateExactTbrDistance", Node.class, Node.class, Node.class);
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
}
