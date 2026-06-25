package treecmp.heuristics.spr.acc;

import treecmp.metrics.topological.acc.MCIncrementalMetric;

public class SprHeuristicMCAcceleratedMetric extends SprIncrementalHeuristicMetric {

    public SprHeuristicMCAcceleratedMetric() {
        super(true, new MCIncrementalMetric());
    }
}