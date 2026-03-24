package treecmp.heuristics.base;

import pal.tree.Tree;
import treecmp.heuristics.moves.TreeMove;
import treecmp.metrics.BaseMetric;
import treecmp.metrics.topological.BaseRFIncrementalMetric;

public abstract class IncrementalHeuristicBaseMetric extends BaseMetric {

    protected boolean improved;
    protected double bestDist;
    protected TreeMove bestMove;

    protected final BaseRFIncrementalMetric incMetric;

    protected IncrementalHeuristicBaseMetric(boolean isRooted, BaseRFIncrementalMetric incMetric) {
        super();
        this.setRooted(isRooted);
        this.incMetric = incMetric;
    }

    protected void checkImprovement(double currentDist, TreeMove move) {
        if (currentDist < this.bestDist) {
            this.bestDist = currentDist;
            this.bestMove = move;
            this.improved = true;
        }
    }

    // Usunięto zbędny parametr incMetric (jest dostępny w klasach pochodnych jako this.incMetric)
    protected abstract void searchNeighborhood(Tree currentTree);

    protected abstract Tree applyPhysicalMove(Tree tree, TreeMove move);

    protected abstract double commitMoveToMetric(TreeMove move);
}