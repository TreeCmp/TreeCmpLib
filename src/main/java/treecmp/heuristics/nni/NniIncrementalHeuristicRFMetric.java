package treecmp.heuristics.nni;

import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.topological.BaseRFIncrementalMetric;
import treecmp.metrics.topological.RFIncrementalMetric;

public class NniIncrementalHeuristicRFMetric extends IncrementalHeuristicBaseMetric {

    public NniIncrementalHeuristicRFMetric() {
        super(false); // Unrooted RF
    }

    @Override
    protected BaseRFIncrementalMetric getIncrementalMetric() {
        return new RFIncrementalMetric();
    }

    @Override
    protected NniUtils getNniUtils() {
        return new NniUtils(true); // Unrooted NNI moves
    }
}