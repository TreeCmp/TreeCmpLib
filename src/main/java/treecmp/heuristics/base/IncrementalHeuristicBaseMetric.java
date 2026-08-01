package treecmp.heuristics.base;

import pal.tree.Tree;
import treecmp.heuristics.moves.TreeMove;
import treecmp.metrics.BaseMetric;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class IncrementalHeuristicBaseMetric extends BaseMetric {

    protected final boolean rooted;
    protected final IncrementalMetric incMetric;

    protected double bestDist;
    protected TreeMove bestMove;
    protected boolean improved;
    protected TreeMove lastOptimumMove;
    protected Tree lastMoveBaseTree;
    protected final List<TreeMove> tiedMoves = new ArrayList<>();

    public IncrementalHeuristicBaseMetric(boolean rooted, IncrementalMetric metric) {
        this.rooted = rooted;
        this.incMetric = metric;
    }

    protected void checkImprovementWithTies(double currentDist, TreeMove move) {
        if (currentDist < this.bestDist) {
            this.bestDist = currentDist;
            this.bestMove = move;
            this.tiedMoves.clear();
            this.tiedMoves.add(move);
        } else if (currentDist == this.bestDist && currentDist != Double.POSITIVE_INFINITY) {
            this.tiedMoves.add(move);
        }
    }

    public double evaluateSingleStep(Tree tree1, Tree tree2) {
        this.incMetric.initCalculationState(tree1, tree2);
        this.improved = false;
        this.bestDist = Double.POSITIVE_INFINITY;
        this.bestMove = null;
        this.tiedMoves.clear(); // Czyścimy remisy przed startem

        searchNeighborhood(tree1);

        return this.bestDist;
    }

    protected abstract void searchNeighborhood(Tree currentTree);

    protected abstract Tree applyPhysicalMove(Tree tree, TreeMove move);

    protected abstract double commitMoveToMetric(TreeMove move);

    // =========================================================
    // ROZSZERZENIE DLA VND (Variable Neighborhood Descent)
    // =========================================================
    protected Tree lastOptimumTree;

    public Tree getLastOptimumTree() {
        return this.lastOptimumTree;
    }

    protected double accumulatedNniCost = 0.0;

    public double getAccumulatedNniCost() {
        return this.accumulatedNniCost;
    }
    /**
     * W odróżnieniu od klasycznego getDistance (które czasem zwraca liczbe krokow lub Infinity),
     * ta metoda rygorystycznie zwraca najlepszy fizyczny dystans i zapisuje wynikowe drzewo.
     */
    public double performLocalDescent(Tree startTree, Tree targetTree) {
        Tree currentTree = new pal.tree.SimpleTree(startTree);
        if (currentTree instanceof pal.tree.SimpleTree) {
            ((pal.tree.SimpleTree) currentTree).createNodeList();
        }

        this.improved = true;
        this.incMetric.initCalculationState(currentTree, targetTree);
        double currentDist = this.incMetric.getCurrentDistance();

        while (this.improved && currentDist > 0) {
            this.improved = false;
            this.bestDist = currentDist;
            this.bestMove = null;

            searchNeighborhood(currentTree);

            if (this.improved && this.bestMove != null) {
                currentDist = commitMoveToMetric(this.bestMove);
                this.incMetric.commit();
                currentTree = applyPhysicalMove(currentTree, this.bestMove);
            }
        }

        this.lastOptimumTree = currentTree;
        return currentDist;
    }

    public double evaluateInitialDistance(Tree startTree, Tree targetTree) {
        this.incMetric.initCalculationState(startTree, targetTree);
        return this.incMetric.getCurrentDistance();
    }

    /**
     * Zwraca trajektorię drzew pośrednich NNI dla wygrywającego ruchu.
     * Jeśli ruch nie został zarejestrowany, zwraca listę z samym drzewem docelowym.
     */
    public List<Tree> getLastOptimumTrajectory(Tree startTree) {
        if (lastOptimumTree == null) {
            return Collections.emptyList();
        }

        if (lastOptimumMove != null && lastMoveBaseTree != null) {
            try {
                List<Tree> traj = lastOptimumMove.getNniTrajectory(lastMoveBaseTree);
                if (traj != null && !traj.isEmpty()) {
                    return traj;
                }
            } catch (Exception e) {
            }
        }

        return Collections.singletonList(lastOptimumTree);
    }
}