package treecmp.heuristics.spr.acc;

import treecmp.metrics.topological.RFClusterIncrementalMetric;

public class SprHeuristicRFCAcceleratedMetric extends SprIncrementalHeuristicMetric {

    public SprHeuristicRFCAcceleratedMetric() {
        // Konfigurujemy klasę wyżej: to jest metryka dla drzew ukorzenionych (true)
        // używająca kalkulatora RFClusterIncrementalMetric.
        super(true, new RFClusterIncrementalMetric());
    }
}