package treecmp.metrics.topological.acc;

import treecmp.metrics.IncrementalMetric;

public class RFClusterIncrementalMetricTest extends BaseRFIncrementalMetricTest {

    @Override
    protected IncrementalMetric createMetricInstance() {
        // Tu dostarczamy wersję dla KLASTRÓW (ukorzenioną)
        return new RFClusterIncrementalMetric();
    }
}