package treecmp.heuristics.nni;

// Zauważ, że używamy już naszych nowych, uporządkowanych pakietów!
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFMetric; // Klasyczna metryka na splitach

/**
 * Klasyczna heurystyka (Steepest Descent / zachłanna) dla metryki Robinson-Foulds (RF)
 * używająca pełnego przeglądu otoczenia NNI (dla drzew nieukorzenionych).
 */
public class NniHeuristicRFMetric extends HeuristicBaseMetric {

    public NniHeuristicRFMetric() {
        // super(false) informuje klasę bazową, że operujemy na drzewach nieukorzenionych
        super(false);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        // Flaga 'true' oznacza klasyczne NNI (nieukorzenione)
        return new NniUtils(true);
    }

    @Override
    protected Metric getMetric() {
        // Zwracamy standardową metrykę opartą na bipartycjach (splitach)
        return new RFMetric();
    }
}