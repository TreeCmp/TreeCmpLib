package treecmp.metrics.topological;

public class RFClusterIncrementalMetricTest extends BaseRFIncrementalMetricTest {

    @Override
    protected BaseRFIncrementalMetric createMetricInstance() {
        // Tu dostarczamy wersję dla KLASTRÓW (ukorzenioną)
        return new RFClusterIncrementalMetric();
    }
}