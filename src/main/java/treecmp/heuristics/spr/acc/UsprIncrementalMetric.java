package treecmp.heuristics.spr.acc;

import pal.tree.Node;
import treecmp.metrics.IncrementalMetric;
import java.util.BitSet;

public interface UsprIncrementalMetric extends IncrementalMetric {
    void applySprPrune(Node pruneNode);
    void undoSprPrune(Node pruneNode);

    BitSet getPruneMask(Node pruneNode);

    /** Krok radialny wzdłuż krawędzi (odpowiednik NNI na maskach) */
    boolean applyRadialStep(Node currentNode, Node previousNode, BitSet pruneMask);
    void undoRadialStep();

    /** Szybka ewaluacja dla bieżącego punktu wpięcia */
    double evaluateCurrentRegraft(Node targetNode, Node pruneParent, BitSet pruneMask, Node pruneNode);
}