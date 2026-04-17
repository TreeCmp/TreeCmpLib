package treecmp.heuristics.base;

import pal.tree.Tree;
import treecmp.heuristics.moves.TreeMove;
import treecmp.metrics.BaseMetric;
import treecmp.metrics.IncrementalMetric;

public abstract class IncrementalHeuristicBaseMetric extends BaseMetric {

    protected final boolean rooted;
    protected final IncrementalMetric incMetric;

    protected double bestDist;
    protected treecmp.heuristics.moves.TreeMove bestMove;
    protected boolean improved;

    public IncrementalHeuristicBaseMetric(boolean rooted, IncrementalMetric metric) {
        this.rooted = rooted;
        this.incMetric = metric;
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