package treecmp.heuristics.ecr.acc;

import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TreeMove;
import treecmp.metrics.IncrementalMetric;

/**
 * Wspólna klasa bazowa dla heurystyk z rodziny ECR (2-sECR, 3-sECR).
 * Zarządza pełną pętlą zstępującą (Descent Loop), obsługą remisów (Tie-Breaking)
 * za pomocą metryki filtrującej oraz podwójnym commitem stanu.
 */
public abstract class EcrIncrementalHeuristic extends IncrementalHeuristicBaseMetric {

    protected final String metricShortName;
    protected IncrementalMetric primaryMetric; // Opcjonalny filtr (np. RFCluster)

    public EcrIncrementalHeuristic(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(metric.isRooted(), metric);
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
    }

    // Metody abstrakcyjne, które specyficznie delegują ocenę i commit do właściwego operatora
    protected abstract double evaluateMoveOnMetric(IncrementalMetric metric, TreeMove move);
    protected abstract double commitMoveOnMetric(IncrementalMetric metric, TreeMove move);

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        Tree currentTree = tree1;
        this.improved = true;
        int totalSteps = 0;
        int maxSteps = 1000; // Zabezpieczenie przed nieskończoną pętlą

        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;

        // Inicjalizacja stanów
        activeMetric.initCalculationState(currentTree, tree2);
        if (primaryMetric != null) {
            this.incMetric.initCalculationState(currentTree, tree2);
        }

        double currentDist = activeMetric.getCurrentDistance();

        while (this.improved && currentDist > 0 && totalSteps < maxSteps) {
            this.improved = false;

            searchNeighborhood(currentTree);

            if (!this.tiedMoves.isEmpty() && this.bestDist <= currentDist && this.bestDist < Double.POSITIVE_INFINITY) {
                TreeMove bestMove = null;

                if (primaryMetric == null || tiedMoves.size() == 1) {
                    if (this.bestDist < currentDist) {
                        bestMove = tiedMoves.get(0);
                    }
                } else {
                    double bestHeavyDist = Double.POSITIVE_INFINITY;
                    double currentHeavyDist = this.incMetric.getCurrentDistance();
                    boolean rfStrictlyImproved = (this.bestDist < currentDist);

                    for (TreeMove tm : tiedMoves) {
                        double heavyDist = evaluateMoveOnMetric(this.incMetric, tm);

                        if (rfStrictlyImproved) {
                            // RF się poprawił -> wybieramy ruch o najmniejszym M3 (nawet jeśli M3 wzrosło!)
                            if (heavyDist < bestHeavyDist) {
                                bestHeavyDist = heavyDist;
                                bestMove = tm;
                            }
                        } else {
                            // Płaskowyż RF -> wymagamy, aby M3 ściśle się poprawiło
                            if (heavyDist < currentHeavyDist && heavyDist < bestHeavyDist) {
                                bestHeavyDist = heavyDist;
                                bestMove = tm;
                            }
                        }
                    }
                }

                if (bestMove != null) {
                    // Fizyczny commit na aktywnym filtrze
                    currentDist = commitMoveOnMetric(activeMetric, bestMove);
                    activeMetric.commit();

                    // Synchronizacja stanu w metryce ciężkiej
                    if (primaryMetric != null) {
                        commitMoveOnMetric(this.incMetric, bestMove);
                        this.incMetric.commit();
                    }

                    currentTree = applyPhysicalMove(currentTree, bestMove);
                    totalSteps++;
                    this.improved = true;
                }
            }
        }

        // Zwracamy dystans końcowy (lub liczbę kroków zgodnie z konwencją benchmarków ECR)
        return (currentDist == 0) ? (double) totalSteps : Double.POSITIVE_INFINITY;
    }
}