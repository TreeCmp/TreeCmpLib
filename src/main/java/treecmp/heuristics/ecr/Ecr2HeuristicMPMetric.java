package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.MatchingPairMetric;

/**
 * Klasyczna heurystyka (Steepest Descent) dla metryki Matching Pair (MP)
 * używająca pełnego przeglądu otoczenia 2-sECR.
 */
public class Ecr2HeuristicMPMetric extends HeuristicBaseMetric {

    public Ecr2HeuristicMPMetric() {
        super(true); // true oznacza drzewo UKORZENIONE (Rooted) w MP
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SubtreeEcr2Utils(false); // false = topologia ukorzeniona
    }

    @Override
    protected Metric getMetric() {
        return new MatchingPairMetric();
    }

    @Override
    public String getName() {
        return "2sECR_ClassicHeuristic_MP";
    }
}