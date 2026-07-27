package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchingClusterMetricFreeTest {

    @Test
    void getMatchingClusterDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesRootedTree1();
        var mcm = new MatchingClusterMetricFree();

        double distance = mcm.getDistance(t1, t1);

        assertEquals(0.0, distance);
    }

    @Test
    void getMatchingClusterDistance_4leafsTrees_returnsFour() {
        var t1 = TestTreeFactory.fourLeavesRootedTree1();
        var t2 = TestTreeFactory.fourLeavesRootedTree2();

        var mcm = new MatchingClusterMetricFree();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(4.0, distance);
    }

    @Test
    void getMatchingClusterDistance_10leafsTrees_returnsEightyFour() {
        var t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        var mcm = new MatchingClusterMetricFree();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(23.0, distance);
    }
}
