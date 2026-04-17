package treecmp.metrics.topological;

import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;

public class RFClusterIncrementalMetricTest extends BaseRFIncrementalMetricTest {

    @Override
    protected IncrementalMetric createMetricInstance() {
        // Tu dostarczamy wersję dla KLASTRÓW (ukorzenioną)
        return new RFClusterIncrementalMetric();
    }
}