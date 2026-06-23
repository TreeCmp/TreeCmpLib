package treecmp.heuristics.spr.acc;

import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;

public class UsprHeuristicRFCAcceleratedMetric extends UsprIncrementalHeuristicMetric {

    public UsprHeuristicRFCAcceleratedMetric() {
        super(false, new RFClusterIncrementalMetric());
    }
}