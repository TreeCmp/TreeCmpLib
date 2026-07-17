package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.topological.RFMetric;
import java.util.BitSet;

/**
 * Standardowa metryka Robinson-Foulds (Symmetric Difference of Splits).
 * Traktuje drzewa jako NIEUKORZENIONE.
 */
public class RFIncrementalMetric extends BaseRFIncrementalMetric {

    private final RFMetric classicRf = new RFMetric();
    private final UsprUtils usprUtils = new UsprUtils();
    private Tree baseTreeRef;
    private Tree targetTreeRef;

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        super.initCalculationState(baseTree, targetTree);
        this.baseTreeRef = baseTree;
        this.targetTreeRef = targetTree;
    }

    @Override
    protected BitSet normalizeSplit(BitSet rawSplit) {
        if (rawSplit.get(0)) {
            BitSet inverted = (BitSet) rawSplit.clone();
            inverted.xor(allLeavesMask);
            return inverted;
        }
        return rawSplit;
    }

    @Override
    public void applySprRegraftStep(Node pruneNode, Node currentNode) {
        // Zabezpieczenie Inner Moves przed modyfikacją bitów we własnym poddrzewie
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

        // Zabezpieczenie przed "Widmowym Korzeniem" (Trifurcation suppression) w uSPR
        boolean pruneInvolvesRoot = (pruneNode.getParent() != null && pruneNode.getParent().isRoot());
        boolean targetInvolvesRoot = (targetNode.getParent() != null && targetNode.getParent().isRoot()) || targetNode.isRoot();

        // Jeśli jakikolwiek ruch dotyka korzenia lub wnętrza odciętego fragmentu, wymuszamy wyrocznię
        if (isInnerMove || pruneInvolvesRoot || targetInvolvesRoot) {
            Tree tempTree = usprUtils.createUsprTree(this.baseTreeRef, pruneNode, targetNode);
            if (tempTree != null) {
                return classicRf.getDistance(tempTree, this.targetTreeRef);
            }
            return Double.POSITIVE_INFINITY;
        }

        // Zewnętrzne ruchy SPR z dala od korzenia -> Błyskawiczna formuła O(1)
        return super.evaluateSprRegraft(pruneNode, targetNode);
    }

    /**
     * Wędruje w górę drzewa sprawdzając, czy węzeł 'descendant' znajduje się
     * wewnątrz poddrzewa zaczynającego się od węzła 'ancestor'.
     */
    private boolean isDescendant(Node descendant, Node ancestor) {
        Node curr = descendant;
        while (curr != null) {
            if (curr == ancestor) return true;
            curr = curr.getParent();
        }
        return false;
    }
}