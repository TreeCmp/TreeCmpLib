package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.acc.RootedTbrMetric;
import treecmp.metrics.topological.RFClusterMetric;

import java.util.*;

/**
 * W pełni przyrostowa metryka Robinson-Foulds dla drzew UKORZENIONYCH (RFCluster).
 * Implementuje interfejs RootedTbrMetric dla IncrementalTbrWalker (2D-DFS 1-NNI).
 * Złożoność ewaluacji każdego ruchu: O(1) na maskach bitowych bez alokacji pamięci.
 */
public class RFClusterIncrementalMetric extends BaseRFIncrementalMetric
        implements RootedTbrMetric {

    private int N;
    private BitSet currentPrunedLeaves;
    private final Map<Node, BitSet> currentClusters = new IdentityHashMap<>();

    private static class Delta {
        final Node[] nodes;
        final BitSet[] oldBitSets;
        final int oldShared;
        final double oldDistance;

        Delta(Node[] nodes, BitSet[] oldBitSets, int oldShared, double oldDistance) {
            this.nodes = nodes;
            this.oldBitSets = oldBitSets;
            this.oldShared = oldShared;
            this.oldDistance = oldDistance;
        }
    }

    private final Stack<Delta> deltaStack = new Stack<>();
    private final TbrUtils tbrUtils = new TbrUtils();
    private final RFClusterMetric classicRfc = new RFClusterMetric();

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        super.initCalculationState(baseTree, targetTree);
        this.baseTreeRef = baseTree;
        this.targetTreeRef = targetTree;
        this.N = baseTree.getExternalNodeCount();

        this.deltaStack.clear();
        this.currentClusters.clear();
        this.currentPrunedLeaves = null;

        for (Map.Entry<Node, BitSet> e : nodeBitSets.entrySet()) {
            this.currentClusters.put(e.getKey(), (BitSet) e.getValue().clone());
        }
    }

    @Override
    protected BitSet normalizeSplit(BitSet rawSplit) {
        return rawSplit;
    }

    @Override
    public BitSet getCluster(Node n) {
        if (n == null) return null;
        BitSet bs = currentClusters.get(n);
        if (bs != null) return bs;
        return super.getCluster(n);
    }

    private boolean isClusterShared(BitSet bs) {
        if (bs == null) return false;
        int card = bs.cardinality();
        if (card <= 1 || card >= N) return false;
        return targetSplits.contains(bs);
    }

    private void applyUpdates(Map<Node, BitSet> updates) {
        if (updates == null || updates.isEmpty()) {
            deltaStack.push(new Delta(new Node[0], new BitSet[0], this.sharedSplitsCount, this.currentDistance));
            return;
        }

        Node[] nodes = new Node[updates.size()];
        BitSet[] oldBitSets = new BitSet[updates.size()];
        int idx = 0;
        for (Node n : updates.keySet()) {
            nodes[idx] = n;
            BitSet old = currentClusters.get(n);
            oldBitSets[idx] = (old != null) ? (BitSet) old.clone() : null;
            idx++;
        }

        deltaStack.push(new Delta(nodes, oldBitSets, this.sharedSplitsCount, this.currentDistance));

        for (Map.Entry<Node, BitSet> entry : updates.entrySet()) {
            Node n = entry.getKey();
            BitSet newBS = entry.getValue();
            BitSet oldBS = currentClusters.get(n);

            if (isClusterShared(oldBS)) {
                this.sharedSplitsCount--;
            }

            if (newBS != null && !newBS.isEmpty()) {
                currentClusters.put(n, newBS);
                if (isClusterShared(newBS)) {
                    this.sharedSplitsCount++;
                }
            } else {
                currentClusters.remove(n);
            }
        }

        updateCurrentDistance();
    }

    private void undoDelta() {
        if (deltaStack.isEmpty()) return;
        Delta delta = deltaStack.pop();

        for (int i = 0; i < delta.nodes.length; i++) {
            Node n = delta.nodes[i];
            BitSet old = delta.oldBitSets[i];
            if (old != null) {
                currentClusters.put(n, old);
            } else {
                currentClusters.remove(n);
            }
        }
        this.sharedSplitsCount = delta.oldShared;
        this.currentDistance = delta.oldDistance;
    }

    // =========================================================================
    // IMPLEMENTACJA KONTRAKTU RootedTbrMetric (2D-DFS O(1))
    // =========================================================================

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        this.currentPrunedLeaves = (BitSet) getCluster(pruneNode).clone();
        BitSet P = this.currentPrunedLeaves;
        Map<Node, BitSet> updates = new HashMap<>();

        Node curr = (wanderingSource != null) ? wanderingSource.getParent() : null;
        while (curr != null && !curr.isRoot()) {
            BitSet bs = currentClusters.get(curr);
            if (bs != null) {
                BitSet newBs = (BitSet) bs.clone();
                newBs.andNot(P);
                updates.put(curr, newBs);
            }
            curr = curr.getParent();
        }

        if (wanderingSource != null && !wanderingSource.isRoot()) {
            updates.put(wanderingSource, null);
        }

        applyUpdates(updates);
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {
        BitSet P = (this.currentPrunedLeaves != null) ? this.currentPrunedLeaves : getCluster(pruneNode);
        Map<Node, BitSet> updates = new HashMap<>();

        // Zabezpieczenie przed duplikacją klastra brata gdy wanderingSource jest korzeniem:
        if (wanderingSource != null && !wanderingSource.isRoot()) {
            BitSet restOfTree = new BitSet(N);
            restOfTree.set(0, N);
            restOfTree.andNot(P);
            updates.put(wanderingSource, restOfTree);
        }

        applyUpdates(updates);
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        BitSet P = (this.currentPrunedLeaves != null) ? this.currentPrunedLeaves : getCluster(pruneNode);
        Map<Node, BitSet> updates = new HashMap<>();

        if (wanderingSource != null) {
            BitSet childCluster = getCluster(childTarget);
            if (childCluster != null) {
                BitSet newWandering = (BitSet) childCluster.clone();
                newWandering.or(P);
                updates.put(wanderingSource, newWandering);
            }
        }

        if (parentTarget != null && !parentTarget.isRoot() && parentTarget != wanderingSource) {
            BitSet parentCluster = getCluster(parentTarget);
            if (parentCluster != null) {
                BitSet newParent = (BitSet) parentCluster.clone();
                newParent.or(P);
                updates.put(parentTarget, newParent);
            }
        }

        applyUpdates(updates);
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        undoDelta();
    }

    @Override
    public void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode) {
        Map<Node, BitSet> updates = new HashMap<>();

        if (currentPrunedLeaves != null && !parentReroot.isLeaf()) {
            BitSet childLeaves = getCluster(childReroot);
            if (childLeaves != null) {
                BitSet newParent = (BitSet) currentPrunedLeaves.clone();
                newParent.andNot(childLeaves);
                updates.put(parentReroot, newParent);

                if (!childReroot.isLeaf()) {
                    updates.put(childReroot, (BitSet) currentPrunedLeaves.clone());
                }
            }
        }

        applyUpdates(updates);
    }

    @Override
    public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {
        undoDelta();
    }

    @Override
    public void revertPrunedState(Node pruneNode, Node wanderingSource) {
        undoDelta(); // Cofa stan setTargetRoot
        undoDelta(); // Cofa stan setPrunedState
        this.currentPrunedLeaves = null;
    }

    @Override
    public void commit() {
        super.commit();
        this.deltaStack.clear();
        this.currentPrunedLeaves = null;
    }

    // =========================================================================
    // PEŁNA WYROCZNIA (Dla wywołań w testach jednostkowych i weryfikacji)
    // =========================================================================

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        if (this.baseTreeRef == null || this.targetTreeRef == null) {
            return getCurrentDistance();
        }

        try {
            Tree physicalTree = tbrUtils.createTbrTree(this.baseTreeRef, pruneNode, rerootNode, targetNode);
            if (physicalTree != null) {
                if (physicalTree instanceof SimpleTree) {
                    ((SimpleTree) physicalTree).createNodeList();
                }
                return classicRfc.getDistance(physicalTree, this.targetTreeRef);
            }
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }

        return Double.POSITIVE_INFINITY;
    }
}