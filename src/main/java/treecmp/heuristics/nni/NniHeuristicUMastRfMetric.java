package treecmp.heuristics.nni;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFMetric;
import treecmp.metrics.topological.RMASTMetric;

/**
 * Heurystyka NNI dla drzew nieukorzenionych (Unrooted).
 * Wykorzystuje RF jako szybki filtr (Primary Metric)
 * oraz MAST do precyzyjnego wyboru spośród najlepszych sąsiadów (Secondary Metric).
 */
public class NniHeuristicUMastRfMetric extends HeuristicBaseMetric {

    public NniHeuristicUMastRfMetric() {
        // false oznacza operowanie na drzewach nieukorzenionych
        super(false);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        // true w konstruktorze NniUtils generuje otoczenie NNI dla drzew nieukorzenionych
        return new NniUtils(true);
    }

    @Override
    protected Metric getMetric() {
        // Metryka docelowa i tie-breaker: MAST
        return new RMASTMetric();
    }

    @Override
    protected Metric getPrimaryMetric() {
        // Metryka prowadząca (filtr): nieukorzeniony Robinson-Foulds (splity)
        return new RFMetric();
    }
}