package treecmp.heuristics.tbr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;
import treecmp.metrics.topological.acc.RFIncrementalMetric;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Zoptymalizowany, strukturalny Walker dla otoczenia TBR drzew ukorzenionych (rTBR).
 * Wykorzystuje dekuplowany 1D-DFS + 1D-DFS (O(N^2)) dla metryk addytywnych (RFC/RF)
 * oraz 2D-DFS dla pozostałych metryk implementujących RtbrIncrementalMetric.
 */
public class TbrNeighborhoodWalker {

    @FunctionalInterface
    public interface TbrVisitor {
        void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
    }

    private static final double EPS = 1e-9;

    private final TbrUtils tbrUtils = new TbrUtils();
    private final List<Node> allNodesBuf = new ArrayList<>(512);

    // Prealokowane bufory Zero-Allocation dla dekuplowanego 1D-DFS (RFC / RF)
    private Node[] rerootNodesBuf = new Node[512];
    private double[] rerootDeltasBuf = new double[512];
    private int rerootCount = 0;

    // Cache dla mechanizmu refleksji (fallback dla metryk nieimplementujących RtbrIncrementalMetric)
    private Method evalMethod4 = null;
    private Method evalMethod3 = null;
    private Method getClusterMethod = null;
    private boolean reflectionInitialized = false;
    private Class<?> lastMetricClass = null;

    public void walk(Tree baseTree, IncrementalMetric metric, TbrVisitor visitor) {
        if (metric instanceof RtbrIncrementalMetric) {
            walk(baseTree, (RtbrIncrementalMetric) metric, visitor);
            return;
        }
        walkFallback(baseTree, metric, visitor);
    }

    public void walk(Tree baseTree, RtbrIncrementalMetric metric, TbrVisitor visitor) {
        if (isAdditiveSeparableMetric(metric)) {
            walkFast1dDfs(baseTree, metric, visitor);
        } else {
            walkFast2dDfs(baseTree, metric, visitor);
        }
    }

    private boolean isAdditiveSeparableMetric(IncrementalMetric metric) {
        return metric instanceof RFClusterIncrementalMetric || metric instanceof RFIncrementalMetric;
    }

    /**
     * Zdekuplowany 1D-DFS + 1D-DFS o złożoności O(N^2) na pełny przegląd sąsiedztwa rTBR.
     */
    private void walkFast1dDfs(Tree baseTree, RtbrIncrementalMetric metric, TbrVisitor visitor) {
        allNodesBuf.clear();
        Node root = baseTree.getRoot();
        collectSubtreeNodes(root, allNodesBuf);

        for (int i = 0, size = allNodesBuf.size(); i < size; i++) {
            Node pruneNode = allNodesBuf.get(i);
            Node wanderingSource = pruneNode.getParent();
            if (pruneNode.isRoot() || wanderingSource == null) continue;

            // 1. Odcięcie poddrzewa T_P i ustawienie wpięcia bazowego w korzeniu T_B
            metric.setPrunedState(pruneNode, wanderingSource);
            metric.setTargetRoot(pruneNode, wanderingSource);
            double baseDist = metric.getCurrentDistance();

            // 2. Faza 1: 1D-DFS po odciętym poddrzewie T_P (zebranie delt przeukorzenienia)
            this.rerootCount = 0;
            collectRerootDeltas1D(pruneNode, pruneNode, baseDist, metric);
            sortRerootCandidates(this.rerootCount);

            // 3. Faza 2: 1D-DFS po drzewie szkieletowym T_B
            evaluateBestRerootsAtTarget(metric.getCurrentDistance(), pruneNode, root, visitor);
            explorePrunedChildren1D(root, pruneNode, wanderingSource, metric, visitor);

            // 4. Atomowe wycofanie stanu odcięcia
            metric.revertPrunedState(pruneNode, wanderingSource);
        }
    }

    private void collectRerootDeltas1D(Node currentReroot, Node pruneNode, double baseDist,
                                       RtbrIncrementalMetric metric) {
        int idx = this.rerootCount++;
        if (idx >= this.rerootNodesBuf.length) {
            growRerootBuffers();
        }
        this.rerootNodesBuf[idx] = currentReroot;
        this.rerootDeltasBuf[idx] = metric.getCurrentDistance() - baseDist;

        if (!currentReroot.isLeaf()) {
            for (int i = 0, n = currentReroot.getChildCount(); i < n; i++) {
                Node nextReroot = currentReroot.getChild(i);
                metric.moveRerootDown(currentReroot, nextReroot, pruneNode);
                collectRerootDeltas1D(nextReroot, pruneNode, baseDist, metric);
                metric.moveRerootUp(currentReroot, nextReroot, pruneNode);
            }
        }
    }

    private void sortRerootCandidates(int count) {
        for (int i = 1; i < count; i++) {
            double keyDelta = this.rerootDeltasBuf[i];
            Node keyNode = this.rerootNodesBuf[i];
            int j = i - 1;
            while (j >= 0 && this.rerootDeltasBuf[j] > keyDelta) {
                this.rerootDeltasBuf[j + 1] = this.rerootDeltasBuf[j];
                this.rerootNodesBuf[j + 1] = this.rerootNodesBuf[j];
                j--;
            }
            this.rerootDeltasBuf[j + 1] = keyDelta;
            this.rerootNodesBuf[j + 1] = keyNode;
        }
    }

    private void dfsTarget1D(Node parentTarget, Node targetNode, Node pruneNode,
                             Node wanderingSource, RtbrIncrementalMetric metric, TbrVisitor visitor) {
        metric.moveTargetDown(parentTarget, targetNode, pruneNode, wanderingSource);
        evaluateBestRerootsAtTarget(metric.getCurrentDistance(), pruneNode, targetNode, visitor);
        explorePrunedChildren1D(targetNode, pruneNode, wanderingSource, metric, visitor);
        metric.moveTargetUp(parentTarget, targetNode, pruneNode, wanderingSource);
    }

    private void explorePrunedChildren1D(Node n, Node pruneNode, Node wanderingSource,
                                         RtbrIncrementalMetric metric, TbrVisitor visitor) {
        int childCount = n.getChildCount();
        if (n == wanderingSource) {
            for (int i = 0; i < childCount; i++) {
                Node child = n.getChild(i);
                if (child != pruneNode) {
                    dfsTarget1D(n, child, pruneNode, wanderingSource, metric, visitor);
                }
            }
            return;
        }

        for (int i = 0; i < childCount; i++) {
            Node c = n.getChild(i);
            if (c == pruneNode) continue;
            if (c == wanderingSource) {
                for (int j = 0, gcCount = wanderingSource.getChildCount(); j < gcCount; j++) {
                    Node gc = wanderingSource.getChild(j);
                    if (gc != pruneNode) {
                        dfsTarget1D(n, gc, pruneNode, wanderingSource, metric, visitor);
                    }
                }
            } else {
                dfsTarget1D(n, c, pruneNode, wanderingSource, metric, visitor);
            }
        }
    }

    private void evaluateBestRerootsAtTarget(double targetBaseDist, Node pruneNode,
                                             Node targetNode, TbrVisitor visitor) {
        final int count = this.rerootCount;
        for (int k = 0; k < count; k++) {
            Node reroot = this.rerootNodesBuf[k];
            if (isValidMove(pruneNode, reroot, targetNode)) {
                double bestDelta = this.rerootDeltasBuf[k];
                visitor.visit(targetBaseDist + bestDelta, pruneNode, reroot, targetNode);

                // Zgłoszenie ewentualnych remisów (istotne dla trybu tie-breaker z primaryMetric)
                for (int m = k + 1; m < count; m++) {
                    if (this.rerootDeltasBuf[m] > bestDelta + EPS) {
                        break;
                    }
                    Node tiedReroot = this.rerootNodesBuf[m];
                    if (isValidMove(pruneNode, tiedReroot, targetNode)) {
                        visitor.visit(targetBaseDist + this.rerootDeltasBuf[m], pruneNode, tiedReroot, targetNode);
                    }
                }
                return;
            }
        }
    }

    private void walkFast2dDfs(Tree baseTree, RtbrIncrementalMetric metric, TbrVisitor visitor) {
        allNodesBuf.clear();
        Node root = baseTree.getRoot();
        collectSubtreeNodes(root, allNodesBuf);

        for (int i = 0, size = allNodesBuf.size(); i < size; i++) {
            Node pruneNode = allNodesBuf.get(i);
            Node wanderingSource = pruneNode.getParent();
            if (pruneNode.isRoot() || wanderingSource == null) continue;

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

        // B. Wewnętrzny Target DFS w głąb drzewa szkieletowego T_B (z pominięciem pruneNode i wanderingSource)
        explorePrunedChildren(root, pruneNode, currentReroot, wanderingSource, metric, visitor);

        // C. Zewnętrzny Reroot DFS w głąb odciętego poddrzewa T_P
        if (!currentReroot.isLeaf()) {
            for (int i = 0, n = currentReroot.getChildCount(); i < n; i++) {
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
        int childCount = n.getChildCount();
        if (n == wanderingSource) {
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
            if (c == wanderingSource) {
                for (int j = 0, gcCount = wanderingSource.getChildCount(); j < gcCount; j++) {
                    Node gc = wanderingSource.getChild(j);
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

    private void growRerootBuffers() {
        int newCap = this.rerootNodesBuf.length << 1;
        this.rerootNodesBuf = Arrays.copyOf(this.rerootNodesBuf, newCap);
        this.rerootDeltasBuf = Arrays.copyOf(this.rerootDeltasBuf, newCap);
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