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

    // =========================================================
    // PODWÓJNA KSIĘGOWOŚĆ (VND vs Autonomiczna heurystyka)
    // =========================================================
    protected Tree lastOptimumTree;
    protected double accumulatedNniCost = 0.0;
    protected int accumulatedSteps = 0;
    protected List<Tree> fullOptimumTrajectory = new ArrayList<>();

    public Tree getLastOptimumTree() {
        return this.lastOptimumTree;
    }

    public double getAccumulatedNniCost() {
        return this.accumulatedNniCost; // Dla VND: ekwiwalent NNI
    }

    public int getAccumulatedSteps() {
        return this.accumulatedSteps; // Dla ECR/SPR: natywna liczba kroków
    }

    public IncrementalHeuristicBaseMetric(boolean rooted, IncrementalMetric metric) {
        this.rooted = rooted;
        this.incMetric = metric;
    }

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        double finalMetricDist = performLocalDescent(tree1, tree2);

        if (finalMetricDist == 0.0) {
            // NOWOŚĆ: Autonomiczne wywołanie zwraca natywną liczbę kroków
            return (double) this.accumulatedSteps;
        }
        return Double.POSITIVE_INFINITY;
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
        this.tiedMoves.clear();

        searchNeighborhood(tree1);

        return this.bestDist;
    }

    protected abstract void searchNeighborhood(Tree currentTree);

    protected abstract Tree applyPhysicalMove(Tree tree, TreeMove move);

    protected abstract double commitMoveToMetric(TreeMove move);

    public double performLocalDescent(Tree startTree, Tree targetTree) {
        Tree currentTree = new pal.tree.SimpleTree(startTree);
        if (currentTree instanceof pal.tree.SimpleTree) {
            ((pal.tree.SimpleTree) currentTree).createNodeList();
        }

        // NOWOŚĆ: Resetujemy waluty oraz czyścimy historię trajektorii
        this.improved = true;
        this.accumulatedNniCost = 0.0;
        this.accumulatedSteps = 0;
        this.fullOptimumTrajectory.clear();
        this.lastOptimumMove = null;
        this.lastMoveBaseTree = null;

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

                this.accumulatedSteps++;
                this.accumulatedNniCost += getMoveNniCost(this.bestMove);
                this.lastOptimumMove = this.bestMove;
                this.lastMoveBaseTree = currentTree;

                // NOWOŚĆ: Rejestrujemy podkroki NNI dla bieżącej mutacji i dodajemy do pełnej trajektorii
                try {
                    List<Tree> stepTraj = this.bestMove.getNniTrajectory(currentTree);
                    if (stepTraj != null && !stepTraj.isEmpty()) {
                        this.fullOptimumTrajectory.addAll(stepTraj);
                    }
                } catch (Exception e) {
                    // Bezpieczny fallback w razie błędu algebry
                }

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

    public List<Tree> getLastOptimumTrajectory(Tree startTree) {
        if (this.fullOptimumTrajectory != null && !this.fullOptimumTrajectory.isEmpty()) {
            return new ArrayList<>(this.fullOptimumTrajectory);
        }

        if (lastOptimumTree == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(lastOptimumTree);
    }

    /**
     * Metoda pomocnicza pobierająca ekwiwalentny koszt NNI z obiektu ruchu.
     */
    protected double getMoveNniCost(TreeMove move) {
        if (move == null) return 1.0;
        try {
            return move.getNniEquivalentCost();
        } catch (Exception e) {
            return 1.0;
        }
    }
}