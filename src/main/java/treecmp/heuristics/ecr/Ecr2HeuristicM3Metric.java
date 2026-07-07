package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.MatchingTripletMetric;

public class Ecr2HeuristicM3Metric extends HeuristicBaseMetric {
    public Ecr2HeuristicM3Metric() {
        super(false); // Poprawka: M3 JEST NIEUKORZENIONA (Unrooted)
    }
    @Override protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SubtreeEcr2Utils(true); // true = unrooted
    }
    @Override protected Metric getMetric() { return new MatchingTripletMetric(); }
    @Override public String getName() { return "2sECR_ClassicHeuristic_M3"; }
}