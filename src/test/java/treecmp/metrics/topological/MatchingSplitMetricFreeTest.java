package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MatchingSplitMetricFreeTest {

    @Test
    void getMatchingSplitDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();

        var ms = new MatchingSpliMetricFree();

        double distance = ms.getDistance(t1, t1);

        assertEquals(0.0, distance);
    }

    @Test
    void getMatchingSplitDistance_4leafsTrees_returnsTwo() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        var ms = new MatchingSpliMetricFree();

        double distance = ms.getDistance(t1, t2);

        assertEquals(4.0, distance);
    }

    @Test
    void getMatchingSplitDistance_10leafsTrees_returnsSeven() {
        var t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        var ms = new MatchingSpliMetricFree();

        double distance = ms.getDistance(t1, t2);

        assertEquals(21, distance);
    }

    @Test
    void getMatchingSplitDistance_100leafsTrees_returnsSeven() {
        var t1 = TestTreeFactory.hundredLeavesBinaryUnrootedTree1();
        var t2 = TestTreeFactory.hundredLeavesBinaryUnrootedTree2();

        var ms = new MatchingSpliMetricFree();

        double distance = ms.getDistance(t1, t2);

        assertEquals(1392, distance);
    }

}
