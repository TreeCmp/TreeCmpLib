package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFMetric;

/**
 * Klasyczna heurystyka (Steepest Descent / zachłanna) dla metryki Robinson-Foulds (RF)
 * używająca pełnego przeglądu otoczenia 2-sECR (dla drzew nieukorzenionych).
 */
public class Ecr2HeuristicRFMetric extends HeuristicBaseMetric {

    public Ecr2HeuristicRFMetric() {
        // super(false) informuje klasę bazową, że operujemy na drzewach nieukorzenionych
        super(false);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        // Flaga 'true' oznacza otoczenie nieukorzenione
        return new SubtreeEcr2Utils(true);
    }

    @Override
    protected Metric getMetric() {
        // Zwracamy standardową metrykę opartą na bipartycjach (splitach)
        return new RFMetric();
    }
}