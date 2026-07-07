package treecmp.heuristics.spr.acc;

import treecmp.metrics.topological.acc.MatchingTripletIncrementalMetric;

public class UsprHeuristicM3AcceleratedMetric extends UsprIncrementalHeuristicMetric {

    public UsprHeuristicM3AcceleratedMetric() {
        super(false, new MatchingTripletIncrementalMetric());
    }
}