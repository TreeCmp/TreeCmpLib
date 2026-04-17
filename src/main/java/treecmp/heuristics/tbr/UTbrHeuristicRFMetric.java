package treecmp.heuristics.tbr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFMetric;

/**
 * Heurystyka uTBR dla drzew nieukorzenionych z wykorzystaniem standardowej metryki RF (Splits).
 */
public class UTbrHeuristicRFMetric extends HeuristicBaseMetric {

    public UTbrHeuristicRFMetric() {
        super(false);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new UTbrUtils();
    }

    @Override
    protected Metric getMetric() {
        return new RFMetric();
    }
}