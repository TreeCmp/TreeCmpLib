package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.topological.RFMetric;

import java.util.*;

public class RFIncrementalMetric extends BaseRFIncrementalMetric {

    private final RFMetric classicRf = new RFMetric();
    private final UsprUtils usprUtils = new UsprUtils();
    private final UTbrUtils utbrUtils = new UTbrUtils();

    private int N;
    private final Set<BitSet> initialSplitsSet = new HashSet<>();
    private final Set<BitSet> finalSplits = new HashSet<>();
    private final Set<BitSet> removedSplits = new HashSet<>();
    private final Set<BitSet> addedSplits = new HashSet<>();

    private Tree baseTreeRef;
    private Tree targetTreeRef;

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        super.initCalculationState(baseTree, targetTree);
        this.baseTreeRef = baseTree;
        this.targetTreeRef = targetTree;
        this.N = baseTree.getExternalNodeCount();

        refreshInitialSplitsSet();
    }

    @Override
    public void commit() {
        super.commit();
        refreshInitialSplitsSet();
    }

    private void refreshInitialSplitsSet() {
        this.initialSplitsSet.clear();
        for (Map.Entry<Node, BitSet> e : nodeBitSets.entrySet()) {
            BitSet bs = (BitSet) e.getValue().clone();
            if (isNonTrivial(bs.cardinality(), N)) {
                this.initialSplitsSet.add(normalizeSplit(bs));
            }
        }
    }

    @Override
    protected boolean isNonTrivial(int card, int total) {
        return card > 1 && card < total - 1;
    }

    @Override
    protected BitSet normalizeSplit(BitSet rawSplit) {
        if (rawSplit == null) return null;
        if (rawSplit.get(0)) {
            BitSet inverted = (BitSet) rawSplit.clone();
            inverted.xor(allLeavesMask);
            return inverted;
        }
        return rawSplit;
    }

    public BitSet getSplit(Node node) {
        return getCluster(node);
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
    // AKCELERATOR BEZALOKACYJNY uTBR (O(depth))
    // =========================================================================

    public double evaluateExactUTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        if (this.baseTreeRef == null || this.targetTreeRef == null) {
            return this.currentDistance;
        }

        Node root = this.baseTreeRef.getRoot();
        Node pParent = pruneNode.getParent();
        if (pParent == null) return Double.POSITIVE_INFINITY;

        BitSet LP = getCluster(pruneNode);
        if (LP == null) return Double.POSITIVE_INFINITY;

        removedSplits.clear();
        addedSplits.clear();

        // --- A. DRZEWO T2 ---
        if (pParent != root) {
            BitSet bsParent = getCluster(pParent);
            if (bsParent != null) removedSplits.add(normalizeSplit(bsParent));
        }

        Node lca = findLca(pParent, targetNode);

        if (pParent != lca) {
            Node curr = pParent.getParent();
            while (curr != null && curr != lca) {
                BitSet oldC = getCluster(curr);
                if (oldC != null) {
                    removedSplits.add(normalizeSplit(oldC));
                    BitSet newC = (BitSet) oldC.clone();
                    newC.andNot(LP);
                    addedSplits.add(normalizeSplit(newC));
                }
                curr = curr.getParent();
            }
        }

        if (targetNode != lca) {
            Node currT = targetNode.getParent();
            while (currT != null && currT != lca) {
                BitSet oldC = getCluster(currT);
                if (oldC != null) {
                    removedSplits.add(normalizeSplit(oldC));
                    BitSet newC = (BitSet) oldC.clone();
                    newC.or(LP);
                    addedSplits.add(normalizeSplit(newC));
                }
                currT = currT.getParent();
            }
        }

        if (targetNode == lca && targetNode != root) {
            BitSet oldC = getCluster(targetNode);
            if (oldC != null) {
                removedSplits.add(normalizeSplit(oldC));
                BitSet newC = (BitSet) oldC.clone();
                newC.andNot(LP);
                addedSplits.add(normalizeSplit(newC));
            }
        }

        // Węzeł wpięcia W
        BitSet targetBs = getCluster(targetNode);
        if (targetBs != null) {
            BitSet newW = (BitSet) targetBs.clone();
            newW.or(LP);
            addedSplits.add(normalizeSplit(newW));
        }

        // --- B. DRZEWO T1 (Przekorzenienie) ---
        if (rerootNode != pruneNode) {
            BitSet rCluster = getCluster(rerootNode);
            if (rCluster != null) {
                BitSet newR = (BitSet) LP.clone();
                newR.andNot(rCluster);
                addedSplits.add(normalizeSplit(newR));
            }

            Node currOnPath = rerootNode.getParent();
            while (currOnPath != null && currOnPath != pruneNode) {
                BitSet oldC = getCluster(currOnPath);
                if (oldC != null) {
                    removedSplits.add(normalizeSplit(oldC));
                    BitSet newC = (BitSet) LP.clone();
                    newC.andNot(oldC);
                    addedSplits.add(normalizeSplit(newC));
                }
                currOnPath = currOnPath.getParent();
            }
        }

        // --- C. BEZPOŚREDNIA REKONSTRUKCJA ---
        finalSplits.clear();
        finalSplits.addAll(initialSplitsSet);
        finalSplits.removeAll(removedSplits);

        for (BitSet bs : addedSplits) {
            if (bs != null && isNonTrivial(bs.cardinality(), N)) {
                finalSplits.add(bs);
            }
        }

        int shared = 0;
        for (BitSet bs : finalSplits) {
            if (targetSplits.contains(bs)) {
                shared++;
            }
        }

        return (finalSplits.size() + targetSplits.size() - 2.0 * shared) / 2.0;
    }

    public double evaluateExactUtbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        return evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, movingBits);
    }

    @Override
    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        return evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, movingBits);
    }

    // =========================================================================
    // OBSŁUGA uSPR
    // =========================================================================

    @Override
    public void applySprRegraftStep(Node pruneNode, Node currentNode) {
        if (isDescendant(currentNode, pruneNode)) {
            sharedSplitsHistory.push(sharedSplitsCount);
            movingNodeHistory.push(currentNode);
            activeSplitHistory.push(activeVirtualSplits.get(currentNode));
        } else {
            super.applySprRegraftStep(pruneNode, currentNode);
        }
    }

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        boolean isInnerMove = isDescendant(targetNode, pruneNode);
        boolean pruneInvolvesRoot = (pruneNode.getParent() != null && pruneNode.getParent().isRoot());
        boolean targetInvolvesRoot = (targetNode.getParent() != null && targetNode.getParent().isRoot()) || targetNode.isRoot();

        if (isInnerMove || pruneInvolvesRoot || targetInvolvesRoot) {
            Tree tempTree = usprUtils.createUsprTree(this.baseTreeRef, pruneNode, targetNode);
            if (tempTree != null) {
                if (tempTree instanceof SimpleTree) {
                    ((SimpleTree) tempTree).createNodeList();
                }
                try {
                    return classicRf.getDistance(tempTree, this.targetTreeRef);
                } catch (Exception e) {
                    return Double.POSITIVE_INFINITY;
                }
            }
            return Double.POSITIVE_INFINITY;
        }

        return super.evaluateSprRegraft(pruneNode, targetNode);
    }

    private boolean isDescendant(Node descendant, Node ancestor) {
        Node curr = descendant;
        while (curr != null) {
            if (curr == ancestor) return true;
            curr = curr.getParent();
        }
        return false;
    }
}