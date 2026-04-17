package treecmp.metrics.topological;

import treecmp.metrics.topological.acc.BaseRFIncrementalMetric;

public class RFIncrementalMetricTest extends BaseRFIncrementalMetricTest {

    @Override
    protected BaseRFIncrementalMetric createMetricInstance() {
        // Tu dostarczamy wersję dla SPLITÓW (nieukorzenioną)
        return new RFIncrementalMetric();
    }
}