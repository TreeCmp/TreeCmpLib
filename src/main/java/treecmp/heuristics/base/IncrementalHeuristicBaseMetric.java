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
    public double evaluateSingleStep(Tree tree1, Tree tree2) {
        // 1. KLUCZOWA POPRAWKA: Twarda inicjalizacja stanu bazowego dla metryki
        // To wykonuje mapowanie liści i zlicza bazowy dystans przed jakimkolwiek ruchem
        this.incMetric.initCalculationState(tree1, tree2);

        this.improved = false;

        // 2. Ustawiamy najgorszy możliwy dystans (Nieskończoność),
        // aby upewnić się, że pobierzemy najlepszego sąsiada z całego otoczenia,
        // dokładnie tak samo, jak robi to pętla 'for' w klasycznych benchmarkach.
        this.bestDist = Double.POSITIVE_INFINITY;
        this.bestMove = null;

        // 3. Wypuszczamy Walkera (który wykona ułamkowe ewaluacje O(1))
        searchNeighborhood(tree1);

        // 4. Zwracamy najniższy znaleziony dystans w otoczeniu
        return this.bestDist;
    }

    protected abstract void searchNeighborhood(Tree currentTree);

    protected abstract Tree applyPhysicalMove(Tree tree, TreeMove move);

    protected abstract double commitMoveToMetric(TreeMove move);
}