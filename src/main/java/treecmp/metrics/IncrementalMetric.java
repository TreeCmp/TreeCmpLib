package treecmp.metrics;

import treecmp.heuristics.moves.NniMove;
import pal.tree.Tree;

public interface IncrementalMetric { // ewentualnie extends BaseMetric

    void initCalculationState(Tree baseTree, Tree targetTree);

    double applyNni(NniMove move);

    void undoNni(NniMove move);

    double getCurrentDistance();
}