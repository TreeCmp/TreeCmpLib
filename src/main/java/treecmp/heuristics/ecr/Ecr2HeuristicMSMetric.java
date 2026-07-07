package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.MatchingSplitMetric;

/**
 * Klasyczna heurystyka (Steepest Descent) dla metryki Matching Split (MS)
 * używająca pełnego przeglądu otoczenia 2-sECR.
 */
public class Ecr2HeuristicMSMetric extends HeuristicBaseMetric {

    public Ecr2HeuristicMSMetric() {
        // false oznacza, że operujemy na drzewach nieukorzenionych (zgodnie z MS)
        super(false);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SubtreeEcr2Utils(true); // true = unrooted topology
    }

    @Override
    protected Metric getMetric() {
        return new MatchingSplitMetric();
    }

    @Override
    public String getName() {
        return "2sECR_ClassicHeuristic_MS";
    }
}