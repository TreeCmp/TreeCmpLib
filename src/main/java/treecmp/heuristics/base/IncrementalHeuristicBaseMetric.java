package treecmp.heuristics.base;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeNeighborhoodUtils;
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
        return this.accumulatedNniCost;
    }

    public int getAccumulatedSteps() {
        return this.accumulatedSteps;
    }

    public IncrementalHeuristicBaseMetric(boolean rooted, IncrementalMetric metric) {
        this.rooted = rooted;
        this.incMetric = metric;
    }

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        double finalMetricDist = performLocalDescent(tree1, tree2);

        if (finalMetricDist == 0.0) {
            return (double) this.accumulatedSteps;
        }
        return Double.POSITIVE_INFINITY;
    }

    protected void checkImprovementWithTies(double currentDist, TreeMove move) {
        if (currentDist < this.bestDist) {
            this.bestDist = currentDist;
            this.bestMove = move;
            this.improved = true;
            this.tiedMoves.clear();
            this.tiedMoves.add(move);
        } else if (currentDist == this.bestDist && currentDist != Double.POSITIVE_INFINITY) {
            this.tiedMoves.add(move);
        }
    }

    public double evaluateSingleStep(Tree tree1, Tree tree2) {
        Tree safeTree1 = ensureIndexedSimpleTree(tree1);
        this.incMetric.initCalculationState(safeTree1, tree2);
        this.improved = false;
        this.bestDist = Double.POSITIVE_INFINITY;
        this.bestMove = null;
        this.tiedMoves.clear();

        searchNeighborhood(safeTree1);

        return this.bestDist;
    }

    protected abstract void searchNeighborhood(Tree currentTree);

    protected abstract Tree applyPhysicalMove(Tree tree, TreeMove move);

    protected abstract double commitMoveToMetric(TreeMove move);

    public double performLocalDescent(Tree startTree, Tree targetTree) {
        Tree currentTree = ensureIndexedSimpleTree(startTree);

        this.accumulatedNniCost = 0.0;
        this.accumulatedSteps = 0;
        this.fullOptimumTrajectory.clear();
        this.lastOptimumMove = null;
        this.lastMoveBaseTree = null;

        this.incMetric.initCalculationState(currentTree, targetTree);
        double currentDist = this.incMetric.getCurrentDistance();

        if (currentDist == 0.0) {
            this.lastOptimumTree = currentTree;
            return 0.0;
        }

        this.improved = true;
        while (this.improved && currentDist > 0) {
            this.improved = false;
            this.bestDist = currentDist;
            this.bestMove = null;
            this.tiedMoves.clear();

            searchNeighborhood(currentTree);

            if (this.improved && this.bestMove != null) {
                // 1. Sprawdzamy czy fizyczna modyfikacja powiodła się i zmieniła drzewo
                Tree nextTree = applyPhysicalMove(currentTree, this.bestMove);
                if (nextTree == null || nextTree == currentTree) {
                    break;
                }
                nextTree = ensureIndexedSimpleTree(nextTree);

                // 2. Dekompozycja ruchu makro na ciąg 1-NNI do certyfikacji
                List<Tree> stepTraj = null;
                try {
                    stepTraj = this.bestMove.getNniTrajectory(currentTree);
                } catch (Exception e) {
                    stepTraj = null;
                }

                if (stepTraj != null && !stepTraj.isEmpty()) {
                    for (Tree intermediateTree : stepTraj) {
                        this.fullOptimumTrajectory.add(ensureIndexedSimpleTree(intermediateTree));
                    }
                    this.accumulatedNniCost += stepTraj.size();
                } else {
                    this.fullOptimumTrajectory.add(nextTree);
                    this.accumulatedNniCost += getMoveNniCost(this.bestMove);
                }

                this.accumulatedSteps++;
                this.lastOptimumMove = this.bestMove;
                this.lastMoveBaseTree = currentTree;
                currentTree = nextTree;

                // 3. Pełna synchronizacja stanu metryki z fizycznym drzewem
                try {
                    commitMoveToMetric(this.bestMove);
                    this.incMetric.commit();
                } catch (Exception ignored) {
                }

                this.incMetric.initCalculationState(currentTree, targetTree);
                double newDist = this.incMetric.getCurrentDistance();

                // 4. BEZPIECZNIK ANTY-ZAPĘTLENIOWY: Dystans musi ściśle maleć!
                if (newDist >= currentDist) {
                    break;
                }

                currentDist = newDist;
                this.improved = true;
            }
        }

        this.lastOptimumTree = currentTree;
        return currentDist;
    }

    public double evaluateInitialDistance(Tree startTree, Tree targetTree) {
        Tree safeStart = ensureIndexedSimpleTree(startTree);
        this.incMetric.initCalculationState(safeStart, targetTree);
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

    protected Tree ensureIndexedSimpleTree(Tree tree) {
        if (tree == null) return null;
        SimpleTree st = (tree instanceof SimpleTree) ? (SimpleTree) tree : new SimpleTree(tree);
        st.createNodeList();
        TreeUtils.computeParentPointers(st.getRoot());
        return st;
    }

    protected double getMoveNniCost(TreeMove move) {
        if (move == null) return 1.0;
        try {
            return move.getNniEquivalentCost();
        } catch (Exception e) {
            return 1.0;
        }
    }
}