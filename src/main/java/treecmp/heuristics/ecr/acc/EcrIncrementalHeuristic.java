package treecmp.heuristics.ecr.acc;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TreeMove;
import treecmp.metrics.IncrementalMetric;

import java.util.List;

public abstract class EcrIncrementalHeuristic extends IncrementalHeuristicBaseMetric {

    protected final String metricShortName;
    protected IncrementalMetric primaryMetric; // Opcjonalny filtr (np. RFCluster)

    public EcrIncrementalHeuristic(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(metric.isRooted(), metric);
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
    }

    protected abstract double evaluateMoveOnMetric(IncrementalMetric metric, TreeMove move);
    protected abstract double commitMoveOnMetric(IncrementalMetric metric, TreeMove move);

    @Override
    protected double commitMoveToMetric(TreeMove move) {
        return commitMoveOnMetric(this.incMetric, move);
    }

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        double finalMetricDist = performLocalDescent(tree1, tree2);
        if (finalMetricDist == 0.0) {
            return (double) this.accumulatedSteps;
        }
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public double performLocalDescent(Tree startTree, Tree targetTree) {
        Tree currentTree = new SimpleTree(startTree);
        if (currentTree instanceof SimpleTree) {
            ((SimpleTree) currentTree).createNodeList();
        }
        TreeUtils.computeParentPointers(currentTree.getRoot());

        this.improved = true;
        this.accumulatedNniCost = 0.0;
        this.accumulatedSteps = 0;
        this.fullOptimumTrajectory.clear();
        this.lastOptimumMove = null;
        this.lastMoveBaseTree = null;

        int maxSteps = 1000;
        IncrementalMetric activeMetric = (primaryMetric != null) ? primaryMetric : this.incMetric;

        // Inicjalizacja stanów obu metryk
        activeMetric.initCalculationState(currentTree, targetTree);
        if (primaryMetric != null) {
            this.incMetric.initCalculationState(currentTree, targetTree);
        }

        double currentDist = activeMetric.getCurrentDistance();
        if (currentDist == 0.0) {
            this.lastOptimumTree = currentTree;
            return 0.0;
        }

        while (currentDist > 0 && this.accumulatedSteps < maxSteps) {
            this.improved = false;
            this.bestDist = currentDist;
            this.bestMove = null;
            this.tiedMoves.clear();

            searchNeighborhood(currentTree);

            // BEZPIECZNIK 1: Brak ruchów lub brak poprawy w otoczeniu -> minimum lokalne
            if (this.tiedMoves.isEmpty() || this.bestDist > currentDist) {
                break;
            }

            TreeMove winningMove = null;

            // PRZYPADEK 1: Brak filtru -> wymagamy ścisłej poprawy na aktywnej metryce
            if (primaryMetric == null) {
                if (this.bestDist < currentDist - 1e-9) {
                    winningMove = tiedMoves.get(0);
                }
            }
            // PRZYPADEK 2: Jest filtr (primaryMetric) -> oceniamy remisy metryką pomocniczą
            else {
                double bestHeavyDist = Double.POSITIVE_INFINITY;
                double currentHeavyDist = this.incMetric.getCurrentDistance();
                boolean rfStrictlyImproved = (this.bestDist < currentDist - 1e-9);

                for (TreeMove tm : tiedMoves) {
                    double heavyDist = evaluateMoveOnMetric(this.incMetric, tm);

                    if (rfStrictlyImproved) {
                        if (heavyDist < bestHeavyDist) {
                            bestHeavyDist = heavyDist;
                            winningMove = tm;
                        }
                    } else {
                        if (heavyDist < currentHeavyDist - 1e-9 && heavyDist < bestHeavyDist) {
                            bestHeavyDist = heavyDist;
                            winningMove = tm;
                        }
                    }
                }
            }

            // BEZPIECZNIK 2: Brak wybranego ruchu poprawiającego -> natychmiast kończymy pętlę!
            if (winningMove == null) {
                break;
            }

            double newDist = commitMoveOnMetric(activeMetric, winningMove);
            activeMetric.commit();

            if (primaryMetric != null) {
                commitMoveOnMetric(this.incMetric, winningMove);
                this.incMetric.commit();
            }

            // BEZPIECZNIK 3: Dystans musi ściśle maleć (ochrona przed zapętleniem na plateau)
            if (newDist >= currentDist - 1e-9 && primaryMetric == null) {
                break;
            }

            this.accumulatedSteps++;
            this.accumulatedNniCost += getMoveNniCost(winningMove);
            this.lastOptimumMove = winningMove;
            this.lastMoveBaseTree = currentTree;

            try {
                List<Tree> stepTraj = winningMove.getNniTrajectory(currentTree);
                if (stepTraj != null && !stepTraj.isEmpty()) {
                    this.fullOptimumTrajectory.addAll(stepTraj);
                }
            } catch (Exception ignored) {
            }

            currentTree = applyPhysicalMove(currentTree, winningMove);
            TreeUtils.computeParentPointers(currentTree.getRoot());
            if (currentTree instanceof SimpleTree) {
                ((SimpleTree) currentTree).createNodeList();
            }

            currentDist = newDist;
            this.improved = true;
        }

        this.lastOptimumTree = currentTree;
        return currentDist;
    }
}