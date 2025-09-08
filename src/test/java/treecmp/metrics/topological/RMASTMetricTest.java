package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.topological.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RMASTMetricTest {

    @Test
    void getRMASTDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var rm = new RMASTMetric();

        double distance = rm.getDistance(t1, t1);

        assertEquals(0.0, distance);
    }

    @Test
    void getRMASTDistance_4leafsTrees_returnsFour() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        var rm = new RMASTMetric();

        double distance = rm.getDistance(t1, t2);

        assertEquals(2.0, distance);
    }

    @Test
    void getRMASTDistance_10leafsTrees_returnsEightyFour() {
        var t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        var rm = new RMASTMetric();

        double distance = rm.getDistance(t1, t2);

        assertEquals(5.0, distance);
    }
}
