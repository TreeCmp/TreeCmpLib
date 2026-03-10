package treecmp.heuristics.nni;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFClusterMetric;

/**
 * Klasyczna heurystyka (Steepest Descent / zachłanna) dla metryki RFCluster
 * używająca pełnego przeglądu otoczenia (R)NNI.
 */
public class NniHeuristicRFCMetric extends HeuristicBaseMetric {

    public NniHeuristicRFCMetric() {
        // super(true) oznacza, że heurystyka operuje na drzewach ukorzenionych
        super(true);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        // BARDZO WAŻNE: RFCluster (RFC) wymaga drzew ukorzenionych.
        // Dlatego używamy NniUtils z flagą false (RNNI - Rooted NNI).
        // Gdyby to była zwykła metryka RF (dla bipartycji), użylibyśmy true.
        return new NniUtils(false);
    }

    @Override
    protected Metric getMetric() {
        return new RFClusterMetric();
    }
}