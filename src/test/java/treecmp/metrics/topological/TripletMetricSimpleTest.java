package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.topological.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TripletMetricSimpleTest {

    @Test
    void getTripletSimpleDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree1();
        var tm = new TripletMetricSimple();

        double distance = tm.getDistance(t1, t2);

        assertEquals(0.0, distance);
    }

    @Test
    void getTripletSimpleDistance_4leafsTrees_returnsFour() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        var tm = new TripletMetricSimple();

        double distance = tm.getDistance(t1, t2);

        assertEquals(4.0, distance);
    }

    @Test
    void getTripletSimpleDistance_10leafsTrees_returnsEightyFour() {
        var t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        var tm = new TripletMetricSimple();

        double distance = tm.getDistance(t1, t2);

        assertEquals(84.0, distance);
    }
}
