package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFMetric;

/**
 * Klasyczna heurystyka (Steepest Descent) dla metryki Robinson-Foulds (RF)
 * używająca pełnego przeglądu otoczenia 3-sECR (dla drzew nieukorzenionych).
 */
public class Ecr3HeuristicRFMetric extends HeuristicBaseMetric {

    public Ecr3HeuristicRFMetric() {
        // super(false) informuje klasę bazową, że operujemy na drzewach nieukorzenionych (RF)
        super(false);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        // Flaga 'true' oznacza otoczenie nieukorzenione
        return new SubtreeEcr3Utils(true);
    }

    @Override
    protected Metric getMetric() {
        // Zwracamy standardową metrykę opartą na bipartycjach (od zera)
        return new RFMetric();
    }

    @Override
    public String getName() {
        return "3sECR_ClassicHeuristic_RF";
    }
}