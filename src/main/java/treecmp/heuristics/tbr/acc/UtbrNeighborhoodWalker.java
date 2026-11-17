package treecmp.heuristics.tbr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.RFIncrementalMetric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UtbrNeighborhoodWalker {

    @FunctionalInterface
    public interface UtbrVisitor {
        void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
    }

    private static final double EPS = 1e-9;

    private final UTbrUtils utbrUtils = new UTbrUtils();
    private final List<Node> allNodesBuf = new ArrayList<>(512);

    // Prealokowane bufory Zero-Allocation dla dekuplowanego 1D-DFS (RF)
    private Node[] rerootNodesBuf = new Node[512];
    private double[] rerootDeltasBuf = new double[512];
    private int rerootCount = 0;

    public void walk(Tree baseTree, IncrementalMetric metric, UtbrVisitor visitor) {
        if (metric instanceof UtbrIncrementalMetric) {
            UtbrIncrementalMetric utbrMetric = (UtbrIncrementalMetric) metric;
            if (isAdditiveSeparableMetric(metric)) {
                walkFast1dDfs(baseTree, utbrMetric, visitor);
            } else {
                walkFast2dDfs(baseTree, utbrMetric, visitor);
            }
            return;
        }
        walkFallback(baseTree, metric, visitor);
    }

    private boolean isAdditiveSeparableMetric(IncrementalMetric metric) {
        return metric instanceof RFIncrementalMetric;
    }

    /**
     * Zdekuplowany 1D-DFS + 1D-DFS o złożoności O(N^2) na pełny przegląd sąsiedztwa uTBR.
     * Wykorzystuje ścisłą rozłączność zbiorów splitów w odciętym poddrzewie T_P oraz szkielecie T_B.
     */
    private void walkFast1dDfs(Tree baseTree, UtbrIncrementalMetric metric, UtbrVisitor visitor) {
        allNodesBuf.clear();
        Node root = baseTree.getRoot();
        collectSubtreeNodes(root, allNodesBuf);

        for (int i = 0, size = allNodesBuf.size(); i < size; i++) {
            Node pruneNode = allNodesBuf.get(i);
            Node wanderingSource = pruneNode.getParent();
            if (pruneNode.isRoot() || wanderingSource == null) continue;

            // 1. Odcięcie T_P i ustawienie stanu bazowego (reroot = pruneNode, target = root)
            metric.setPrunedState(pruneNode, wanderingSource);
            metric.setTargetRoot(pruneNode, pruneNode, wanderingSource);
            double baseDist = metric.getCurrentDistance();

            // 2. Faza 1: 1D-DFS po odciętym poddrzewie T_P (zebranie delt przeukorzenienia)
            this.rerootCount = 0;
            collectRerootDeltas1D(pruneNode, pruneNode, baseDist, metric);
            sortRerootCandidates(this.rerootCount);

            // 3. Faza 2: 1D-DFS po drzewie szkieletowym T_B (reroot w metryce pozostaje w pruneNode)
            evaluateBestRerootsAtTarget(metric.getCurrentDistance(), pruneNode, root, visitor);
            explorePrunedChildren1D(root, pruneNode, wanderingSource, metric, visitor);

            // 4. Atomowe wycofanie stanu odcięcia
            metric.revertPrunedState(pruneNode, wanderingSource);
        }
    }

    private void collectRerootDeltas1D(Node currentReroot, Node pruneNode, double baseDist,
                                       UtbrIncrementalMetric metric) {
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
                             Node wanderingSource, UtbrIncrementalMetric metric, UtbrVisitor visitor) {
        metric.moveTargetDown(parentTarget, targetNode, pruneNode, pruneNode, wanderingSource);
        evaluateBestRerootsAtTarget(metric.getCurrentDistance(), pruneNode, targetNode, visitor);
        explorePrunedChildren1D(targetNode, pruneNode, wanderingSource, metric, visitor);
        metric.moveTargetUp(parentTarget, targetNode, pruneNode, pruneNode, wanderingSource);
    }

    private void explorePrunedChildren1D(Node n, Node pruneNode, Node wanderingSource,
                                         UtbrIncrementalMetric metric, UtbrVisitor visitor) {
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
                                             Node targetNode, UtbrVisitor visitor) {
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

    private void walkFast2dDfs(Tree baseTree, UtbrIncrementalMetric metric, UtbrVisitor visitor) {
        allNodesBuf.clear();
        Node root = baseTree.getRoot();
        collectSubtreeNodes(root, allNodesBuf);

        for (int i = 0, size = allNodesBuf.size(); i < size; i++) {
            Node pruneNode = allNodesBuf.get(i);
            Node wanderingSource = pruneNode.getParent();
            if (pruneNode.isRoot() || wanderingSource == null) continue;

            // 1. Odcięcie T1 i przygotowanie stanu bazowego T2
            metric.setPrunedState(pruneNode, wanderingSource);
            metric.setTargetRoot(pruneNode, pruneNode, wanderingSource);

            // 2. Dwuwymiarowy DFS: Reroot DFS w T1 x Target DFS w T2
            dfsReroot(pruneNode, pruneNode, wanderingSource, root, metric, visitor);

            // 3. Atomowe wycofanie zmian
            metric.revertPrunedState(pruneNode, wanderingSource);
        }
    }

    private void dfsReroot(Node currentReroot, Node pruneNode, Node wanderingSource, Node root,
                           UtbrIncrementalMetric metric, UtbrVisitor visitor) {
        // A. Ewaluacja wpięcia w korzeniu T2
        if (isValidMove(pruneNode, currentReroot, root)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, currentReroot, root);
        }

        // B. Target DFS w głąb składowej T2 (z ominięciem wanderingSource i pruneNode)
        explorePrunedChildren2D(root, pruneNode, currentReroot, wanderingSource, metric, visitor);

        // C. Reroot DFS w głąb składowej T1
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
                           Node wanderingSource, UtbrIncrementalMetric metric, UtbrVisitor visitor) {
        metric.moveTargetDown(parentTarget, targetNode, pruneNode, currentReroot, wanderingSource);

        if (isValidMove(pruneNode, currentReroot, targetNode)) {
            visitor.visit(metric.getCurrentDistance(), pruneNode, currentReroot, targetNode);
        }

        explorePrunedChildren2D(targetNode, pruneNode, currentReroot, wanderingSource, metric, visitor);

        metric.moveTargetUp(parentTarget, targetNode, pruneNode, currentReroot, wanderingSource);
    }

    private void explorePrunedChildren2D(Node n, Node pruneNode, Node currentReroot,
                                         Node wanderingSource, UtbrIncrementalMetric metric, UtbrVisitor visitor) {
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
        return utbrUtils.isValidUtbrMove(pruneNode, rerootNode, targetNode);
    }

    private void growRerootBuffers() {
        int newCap = this.rerootNodesBuf.length << 1;
        this.rerootNodesBuf = Arrays.copyOf(this.rerootNodesBuf, newCap);
        this.rerootDeltasBuf = Arrays.copyOf(this.rerootDeltasBuf, newCap);
    }

    private void walkFallback(Tree baseTree, IncrementalMetric metric, UtbrVisitor visitor) {
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

                    if (utbrUtils.isValidUtbrMove(pruneNode, rerootNode, targetNode)) {
                        visitor.visit(metric.getCurrentDistance(), pruneNode, rerootNode, targetNode);
                    }
                }
            }
        }
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