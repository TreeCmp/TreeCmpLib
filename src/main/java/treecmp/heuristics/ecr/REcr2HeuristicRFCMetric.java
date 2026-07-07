package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFClusterMetric;

/**
 * Klasyczna heurystyka (Steepest Descent / zachłanna) dla metryki RFCluster (RFC)
 * używająca pełnego przeglądu otoczenia ukorzenionego 2-sECR.
 */
public class REcr2HeuristicRFCMetric extends HeuristicBaseMetric {

    public REcr2HeuristicRFCMetric() {
        // super(true) oznacza, że operujemy na drzewach ukorzenionych
        super(true);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        // Flaga 'false' oznacza otoczenie ukorzenione (rooted)
        return new SubtreeEcr2Utils(false);
    }

    @Override
    protected Metric getMetric() {
        // Zwracamy metrykę klastrową, która wymaga ukorzenienia
        return new RFClusterMetric();
    }
}