package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.topological.RFMetric;

import java.util.BitSet;

/**
 * Zoptymalizowana, przyrostowa metryka Robinson-Foulds dla drzew NIEUKORZENIONYCH.
 * Kanonicznie polaryzuje splity względem liścia 0 i współpracuje z UtbrNeighborhoodWalker.
 */
public class RFIncrementalMetric extends BaseRFIncrementalMetric {

    private final RFMetric classicRf = new RFMetric();
    private final UsprUtils usprUtils = new UsprUtils();
    private final UTbrUtils utbrUtils = new UTbrUtils();

    private Tree baseTreeRef;
    private Tree targetTreeRef;

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        super.initCalculationState(baseTree, targetTree);
        this.baseTreeRef = baseTree;
        this.targetTreeRef = targetTree;
    }

    /**
     * Kanoniczna normalizacja splitu dla drzew nieukorzenionych:
     * Split {A, L \ A} reprezentujemy tak, aby liść 0 ZAWSZE miał bit 0 (poza maską).
     * Bezpieczne klonowanie zapobiega współdzieleniu referencji w mapach.
     */
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

    /**
     * Zwraca kanoniczny split powiązany z danym węzłem w drzewie nieukorzenionym.
     * Wymagany przez UtbrNeighborhoodWalker.
     */
    public BitSet getSplit(Node node) {
        return getCluster(node);
    }

    // =========================================================================
    // AKCELERATOR uTBR DLA DRZEW NIEUKORZENIONYCH
    // =========================================================================

    public double evaluateExactUTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        if (this.baseTreeRef == null || this.targetTreeRef == null) {
            return getCurrentDistance();
        }

        try {
            Tree physicalTree = utbrUtils.createUtbrTree(this.baseTreeRef, pruneNode, rerootNode, targetNode);
            if (physicalTree != null) {
                if (physicalTree instanceof SimpleTree) {
                    ((SimpleTree) physicalTree).createNodeList();
                }
                return classicRf.getDistance(physicalTree, this.targetTreeRef);
            }
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }

        return Double.POSITIVE_INFINITY;
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
            activeSplitHistory.push(getCluster(currentNode));
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