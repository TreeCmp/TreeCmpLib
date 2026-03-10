package treecmp.metrics.topological;

public class RFIncrementalMetricTest extends BaseRFIncrementalMetricTest {

    @Override
    protected BaseRFIncrementalMetric createMetricInstance() {
        // Tu dostarczamy wersję dla SPLITÓW (nieukorzenioną)
        return new RFIncrementalMetric();
    }
}