package treecmp.heuristics.tbr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFClusterMetric;

/**
 * Heurystyka TBR dla drzew ukorzenionych z wykorzystaniem metryki RF Cluster.
 */
public class TbrHeuristicRFCMetric extends HeuristicBaseMetric {

    public TbrHeuristicRFCMetric() {
        // Drzewa ukorzenione -> true
        super(true);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new TbrUtils();
    }

    @Override
    protected Metric getMetric() {
        return new RFClusterMetric();
    }
}