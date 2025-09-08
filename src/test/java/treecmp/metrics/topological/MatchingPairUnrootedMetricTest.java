package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.topological.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MatchingPairUnrootedMetricTest {

    @Test
    void getMatchingPairUnrootedDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree1();

        var mpu = new MatchingPairMetric();

        double distance = mpu.getDistance(t1, t2);

        assertEquals(0.0, distance);
    }

    @Test
    void getMatchingPairUnrootedDistance_4leafsTrees_returnsTwo() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        var mpu = new MatchingTripletMetric();

        double distance = mpu.getDistance(t1, t2);

        assertEquals(2.0, distance);
    }

    @Test
    void getMatchingPairUnrootedDistance_10leafsTrees_returnsSeven() {
        var t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        var mpu = new MatchingTripletMetric();

        double distance = mpu.getDistance(t1, t2);

        assertEquals(84.0, distance);
    }

}
