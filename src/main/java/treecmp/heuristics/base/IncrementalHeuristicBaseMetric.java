package treecmp.heuristics.base;

import pal.tree.Tree;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.nni.NniUtils;
import treecmp.metrics.BaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.BaseRFIncrementalMetric;
import java.util.ArrayList;

public abstract class IncrementalHeuristicBaseMetric extends BaseMetric implements Metric {

    protected IncrementalHeuristicBaseMetric(boolean rooted) {
        super();
        this.rooted = rooted;
    }

    protected abstract BaseRFIncrementalMetric getIncrementalMetric();
    protected abstract NniUtils getNniUtils();

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        BaseRFIncrementalMetric incMetric = getIncrementalMetric();
        NniUtils nniUtils = getNniUtils();

        // Inicjalizacja stanu metryki na podstawie drzew wejściowych
        incMetric.initCalculationState(tree1, tree2);
        double currentDist = incMetric.getCurrentDistance();

        if (currentDist == 0) return 0;

        int totalSteps = 0;
        boolean improved = true;
        Tree currentTree = tree1;

        // Pętla Hill-Climbing (Steepest Descent)
        while (improved && currentDist > 0) {
            improved = false;
            NniMove[] moves = nniUtils.generateNniMoves(currentTree);

            NniMove bestMove = null;
            double bestMoveDist = currentDist;

            for (NniMove move : moves) {
                // Wirtualna próba ruchu (O(1))
                double tempDist = incMetric.applyNni(move);

                if (tempDist < bestMoveDist) {
                    bestMoveDist = tempDist;
                    bestMove = move;
                    improved = true;
                }

                // Cofnięcie zmiany stanu metryki (O(1))
                incMetric.undoNni(move);
            }

            if (improved && bestMove != null) {
                // 1. Aktualizacja stanu metryki (ZANIM zmienimy fizycznie strukturę drzewa!)
                // Wywołanie applyNni tutaj na stałe ustawia wirtualne BitSety dla nowej bazy
                currentDist = incMetric.applyNni(bestMove);

                // 2. KLUCZOWA ZMIANA: Zatwierdzenie stanu
                // Przenosi zmiany z activeVirtualSplits do nodeBitSets i czyści stosy historii
                incMetric.commitNni();

                // 3. Fizyczna zmiana struktury drzewa PAL
                // Dopiero teraz, gdy metryka jest już w nowym stanie, bezpiecznie zmieniamy wskaźniki
                currentTree = nniUtils.applyPhysicalMove(currentTree, bestMove);

                totalSteps++;
            }
        }

        // Zwracamy liczbę kroków jako dystans heurystyczny
        return (currentDist == 0) ? (double) totalSteps : Double.POSITIVE_INFINITY;
    }

}