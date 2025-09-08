package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.topological.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MatchingTripletMetricTest {

    @Test
    void getMatchingTripletDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree1();

        var m3m = new MatchingTripletMetric();

        double distance = m3m.getDistance(t1, t2);

        assertEquals(0.0, distance);
    }

    @Test
    void getMatchingTripletDistance_4leafsTrees_returnsTwo() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        var m3m = new MatchingTripletMetric();

        double distance = m3m.getDistance(t1, t2);

        assertEquals(2.0, distance);
    }

    @Test
    void getMatchingTripletDistance_10leafsTrees_returnsSeven() {
        var t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        var m3m = new MatchingTripletMetric();

        double distance = m3m.getDistance(t1, t2);

        assertEquals(84.0, distance);
    }

}
