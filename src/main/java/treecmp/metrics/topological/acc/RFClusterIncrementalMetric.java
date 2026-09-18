package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.acc.RootedTbrMetric;
import treecmp.metrics.topological.RFClusterMetric;

import java.util.*;

public class RFClusterIncrementalMetric extends BaseRFIncrementalMetric
        implements RootedTbrMetric {

    private int N;
    private final Set<BitSet> initialClustersSet = new HashSet<>();
    private final Set<BitSet> finalClusters = new HashSet<>();
    private final Set<BitSet> removedClusters = new HashSet<>();
    private final Set<BitSet> addedClusters = new HashSet<>();
    private final BitSet leavesT2 = new BitSet();

    private final TbrUtils tbrUtils = new TbrUtils();
    private final RFClusterMetric classicRfc = new RFClusterMetric();

    private Node currentPruneNode;
    private Node currentRerootNode;
    private Node currentTargetNode;

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        super.initCalculationState(baseTree, targetTree);
        this.N = baseTree.getExternalNodeCount();
        refreshInitialClustersSet();
    }

    @Override
    public void commit() {
        super.commit();
        refreshInitialClustersSet();
    }

    private void refreshInitialClustersSet() {
        this.initialClustersSet.clear();
        for (Map.Entry<Node, BitSet> e : nodeBitSets.entrySet()) {
            BitSet bs = (BitSet) e.getValue().clone();
            if (isNonTrivial(bs.cardinality(), N)) {
                this.initialClustersSet.add(bs);
            }
        }
    }

    @Override
    protected BitSet normalizeSplit(BitSet rawSplit) {
        return rawSplit;
    }

    private boolean isClusterShared(BitSet bs) {
        if (bs == null) return false;
        int card = bs.cardinality();
        if (card <= 1 || card >= N) return false;
        return targetSplits.contains(bs);
    }

    private Node findLca(Node a, Node b) {
        if (a == null || b == null) return null;
        int dA = getDepth(a);
        int dB = getDepth(b);

        while (dA > dB && a != null) { a = a.getParent(); dA--; }
        while (dB > dA && b != null) { b = b.getParent(); dB--; }

        while (a != b && a != null && b != null) {
            a = a.getParent();
            b = b.getParent();
        }
        return a;
    }

    private int getDepth(Node n) {
        int d = 0;
        Node curr = n;
        while (curr != null) {
            d++;
            curr = curr.getParent();
        }
        return d;
    }

    // =========================================================================
    // KONTRAKT RootedTbrMetric
    // =========================================================================

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        this.currentPruneNode = pruneNode;
        this.currentRerootNode = pruneNode;
        this.currentTargetNode = (baseTreeRef != null) ? baseTreeRef.getRoot() : null;
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {
        this.currentPruneNode = pruneNode;
        this.currentRerootNode = rerootNode;
        this.currentTargetNode = (baseTreeRef != null) ? baseTreeRef.getRoot() : null;
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        this.currentPruneNode = pruneNode;
        this.currentRerootNode = rerootNode;
        this.currentTargetNode = childTarget;
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        this.currentTargetNode = parentTarget;
    }

    @Override
    public void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode) {
        this.currentRerootNode = childReroot;
    }

    @Override
    public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {
        this.currentRerootNode = parentReroot;
    }

    @Override
    public void revertPrunedState(Node pruneNode, Node wanderingSource) {
        this.currentPruneNode = null;
        this.currentRerootNode = null;
        this.currentTargetNode = null;
    }

    @Override
    public double getCurrentDistance() {
        if (currentPruneNode == null || currentRerootNode == null || currentTargetNode == null) {
            return this.currentDistance;
        }
        return evaluateExactTbrDistance(currentPruneNode, currentRerootNode, currentTargetNode, null);
    }

    // =========================================================================
    // DOKŁADNA BITOWA DELTA KLASTROW W CZASIE O(depth) BEZ ALOKACJI PAMIĘCI
    // =========================================================================

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        if (this.baseTreeRef == null || this.targetTreeRef == null) {
            return this.currentDistance;
        }

        Node root = this.baseTreeRef.getRoot();
        Node pParent = pruneNode.getParent();
        if (pParent == null) return Double.POSITIVE_INFINITY;

        BitSet LP = getCluster(pruneNode);
        if (LP == null) return Double.POSITIVE_INFINITY;

        removedClusters.clear();
        addedClusters.clear();

        // --- A. DRZEWO T2 ---
        if (pParent != root) {
            BitSet bsParent = getCluster(pParent);
            if (bsParent != null) removedClusters.add(bsParent);
        }

        Node lca = findLca(pParent, targetNode);

        if (pParent != lca) {
            Node curr = pParent.getParent();
            while (curr != null && curr != lca) {
                BitSet oldC = getCluster(curr);
                if (oldC != null) {
                    removedClusters.add(oldC);
                    BitSet newC = (BitSet) oldC.clone();
                    newC.andNot(LP);
                    if (isNonTrivial(newC.cardinality(), N)) {
                        addedClusters.add(newC);
                    }
                }
                curr = curr.getParent();
            }
        }

        if (targetNode != lca) {
            Node currT = targetNode.getParent();
            while (currT != null && currT != lca) {
                BitSet oldC = getCluster(currT);
                if (oldC != null) {
                    removedClusters.add(oldC);
                    BitSet newC = (BitSet) oldC.clone();
                    newC.or(LP);
                    if (isNonTrivial(newC.cardinality(), N)) {
                        addedClusters.add(newC);
                    }
                }
                currT = currT.getParent();
            }
        }

        if (targetNode == lca) {
            BitSet oldC = getCluster(targetNode);
            if (oldC != null) {
                removedClusters.add(oldC);
                BitSet newC = (BitSet) oldC.clone();
                newC.andNot(LP);
                if (isNonTrivial(newC.cardinality(), N)) {
                    addedClusters.add(newC);
                }
            }
        }

        // Węzeł wpięcia W w T2
        if (targetNode == root) {
            leavesT2.clear();
            leavesT2.set(0, N);
            leavesT2.andNot(LP);
            if (isNonTrivial(leavesT2.cardinality(), N)) {
                addedClusters.add((BitSet) leavesT2.clone());
            }
        } else {
            BitSet targetBs = getCluster(targetNode);
            if (targetBs != null) {
                BitSet newW = (BitSet) targetBs.clone();
                newW.or(LP);
                if (isNonTrivial(newW.cardinality(), N)) {
                    addedClusters.add(newW);
                }
            }
        }

        // --- B. DRZEWO T1 (Przekorzenienie na krawędź powyżej R) ---
        if (rerootNode != pruneNode) {
            Node childOnPath = rerootNode;
            Node currOnPath = rerootNode.getParent();

            while (currOnPath != null && currOnPath != pruneNode) {
                BitSet oldC = getCluster(currOnPath);
                if (oldC != null) {
                    removedClusters.add(oldC);
                }

                BitSet childC = getCluster(childOnPath);
                if (childC != null) {
                    BitSet newC = (BitSet) LP.clone();
                    newC.andNot(childC);
                    if (isNonTrivial(newC.cardinality(), N)) {
                        addedClusters.add(newC);
                    }
                }

                childOnPath = currOnPath;
                currOnPath = currOnPath.getParent();
            }
        }

        // --- C. BEZPOŚREDNIA REKONSTRUKCJA ZBIORU KLASTRÓW ---
        finalClusters.clear();
        finalClusters.addAll(initialClustersSet);
        finalClusters.removeAll(removedClusters);
        finalClusters.addAll(addedClusters);

        int shared = 0;
        for (BitSet bs : finalClusters) {
            if (isClusterShared(bs)) {
                shared++;
            }
        }

        return (finalClusters.size() + targetSplits.size() - 2.0 * shared) / 2.0;
    }
}