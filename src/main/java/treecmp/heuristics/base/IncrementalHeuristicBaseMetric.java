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

    // Służy wyłącznie do testów wydajnościowych (Single-Step Benchmark)
    public double evaluateSingleStep(pal.tree.Tree tree1, pal.tree.Tree tree2) {
        this.incMetric.initCalculationState(tree1, tree2);
        // Przeszukanie całego otoczenia bez robienia fizycznego kroku (applyPhysicalMove)
        searchNeighborhood(tree1);
        return this.bestDist;
    }

    protected abstract void searchNeighborhood(Tree currentTree);

    protected abstract Tree applyPhysicalMove(Tree tree, TreeMove move);

    protected abstract double commitMoveToMetric(TreeMove move);
}