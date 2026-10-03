package treecmp.heuristics.spr.acc;

import pal.tree.Node;
import treecmp.metrics.IncrementalMetric;
import java.util.BitSet;

public interface RadialUsprIncrementalMetric extends IncrementalMetric {
    void applySprPrune(Node pruneNode);
    void undoSprPrune(Node pruneNode);

    /** Krok radialny po krawędzi (odpowiednik NNI na maskach) */
    boolean applyRadialStep(Node currentNode, Node previousNode, BitSet pruneMask);
    void undoRadialStep();

    /** Szybka ewaluacja dla bieżącego punktu wpięcia */
    double evaluateCurrentRegraft(Node targetNode, Node pruneParent, BitSet pruneMask, Node pruneNode);
}