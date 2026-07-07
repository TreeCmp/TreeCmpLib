package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.MatchingClusterMetric;

/**
 * Klasyczna heurystyka (Steepest Descent) dla metryki Matching Cluster (MC)
 * używająca pełnego przeglądu otoczenia 2-sECR.
 */
public class Ecr2HeuristicMCMetric extends HeuristicBaseMetric {

    public Ecr2HeuristicMCMetric() {
        super(true); // true oznacza drzewo UKORZENIONE (Rooted) w MC
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SubtreeEcr2Utils(false); // false oznacza, że pracujemy na topologii ukorzenionej
    }

    @Override
    protected Metric getMetric() {
        return new MatchingClusterMetric();
    }

    @Override
    public String getName() {
        return "2sECR_ClassicHeuristic_MC";
    }
}