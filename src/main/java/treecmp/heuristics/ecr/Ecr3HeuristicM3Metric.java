package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.MatchingTripletMetric;

public class Ecr3HeuristicM3Metric extends HeuristicBaseMetric {
    public Ecr3HeuristicM3Metric() {
        super(false); // Poprawka: M3 JEST NIEUKORZENIONA (Unrooted)
    }
    @Override protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SubtreeEcr3Utils(true); // true = unrooted
    }
    @Override protected Metric getMetric() { return new MatchingTripletMetric(); }
    @Override public String getName() { return "3sECR_ClassicHeuristic_M3"; }
}