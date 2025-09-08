package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.topological.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.*;

class TripletMetricTest {

    @Test
    void getTripletDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var tm = new TripletMetric();

        double distance = tm.getDistance(t1, t1);

        assertEquals(0.0, distance);
    }

    @Test
    void getTripletDistance_4leafsTrees_returnsFour() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        var tm = new TripletMetric();

        double distance = tm.getDistance(t1, t2);

        assertEquals(4.0, distance);
    }

    @Test
    void getTripletDistance_10leafsTrees_returnsEightyFour() {
        var t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        var tm = new TripletMetric();

        double distance = tm.getDistance(t1, t2);

        assertEquals(84.0, distance);
    }
}
