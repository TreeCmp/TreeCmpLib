package treecmp.heuristics.spr.acc;

import treecmp.metrics.topological.acc.M3IncrementalMetric;

public class UsprHeuristicM3AcceleratedMetric extends UsprIncrementalHeuristicMetric {

    public UsprHeuristicM3AcceleratedMetric() {
        super(false, new M3IncrementalMetric());
    }
}