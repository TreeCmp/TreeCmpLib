package treecmp.heuristics.ecr.acc;

import pal.tree.Tree;
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
        Tree currentTree = new pal.tree.SimpleTree(startTree);
        if (currentTree instanceof pal.tree.SimpleTree) {
            ((pal.tree.SimpleTree) currentTree).createNodeList();
        }

        this.improved = true;
        this.accumulatedNniCost = 0.0;
        this.accumulatedSteps = 0;
        this.fullOptimumTrajectory.clear(); // NOWOŚĆ: Czyszczenie trajektorii na starcie
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

        while (this.improved && currentDist > 0 && this.accumulatedSteps < maxSteps) {
            this.improved = false;
            this.bestDist = currentDist;
            this.bestMove = null;

            searchNeighborhood(currentTree);

            if (!this.tiedMoves.isEmpty() && this.bestDist <= currentDist && this.bestDist < Double.POSITIVE_INFINITY) {
                TreeMove winningMove = null;

                // PRZYPADEK 1: Brak filtru -> wymagamy ścisłej poprawy na jednej metryce
                if (primaryMetric == null) {
                    if (this.bestDist < currentDist) {
                        winningMove = tiedMoves.get(0);
                    }
                }
                // PRZYPADEK 2: Jest filtr (primaryMetric) -> oceniamy WSZYSTKIE remisy (nawet gdy jest tylko 1!)
                else {
                    double bestHeavyDist = Double.POSITIVE_INFINITY;
                    double currentHeavyDist = this.incMetric.getCurrentDistance();
                    boolean rfStrictlyImproved = (this.bestDist < currentDist);

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

                if (winningMove != null) {
                    currentDist = commitMoveOnMetric(activeMetric, winningMove);
                    activeMetric.commit();

                    if (primaryMetric != null) {
                        commitMoveOnMetric(this.incMetric, winningMove);
                        this.incMetric.commit();
                    }

                    this.accumulatedSteps++;
                    this.accumulatedNniCost += getMoveNniCost(winningMove);
                    this.lastOptimumMove = winningMove;
                    this.lastMoveBaseTree = currentTree;

                    // NOWOŚĆ: Rejestracja wszystkich podkroków NNI dla bieżącego ruchu ECR
                    try {
                        List<Tree> stepTraj = winningMove.getNniTrajectory(currentTree);
                        if (stepTraj != null && !stepTraj.isEmpty()) {
                            this.fullOptimumTrajectory.addAll(stepTraj);
                        }
                    } catch (Exception e) {
                        // Bezpieczny fallback
                    }

                    currentTree = applyPhysicalMove(currentTree, winningMove);
                    this.improved = true;
                }
            }
        }

        this.lastOptimumTree = currentTree;
        return currentDist;
    }
}