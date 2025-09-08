package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.topological.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RFClusterMetricTest {

    @Test
    void getRFCDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree1();

        double distance = RFClusterMetric.getRFClusterMetric(t1, t2);

        assertEquals(0.0, distance);
    }

    @Test
    void getRFCDistance_4leafsTrees_returnsTwo() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        double distance = RFClusterMetric.getRFClusterMetric(t1, t2);

        assertEquals(2.0, distance);
    }

    @Test
    void getRFCDistance_10leafsTrees_returnsSeven() {
        var t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        double distance = RFClusterMetric.getRFClusterMetric(t1, t2);

        assertEquals(7.0, distance);
    }

}
