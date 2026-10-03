package treecmp.metrics.topological.acc;

public class RFClusterIncrementalMetricTest extends BaseRFIncrementalMetricTest {

    @Override
    protected BaseRFIncrementalMetric createMetricInstance() {
        return new RFClusterIncrementalMetric();
    }
}