package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.topological.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchingClusterMetricO3LongTest {

    @Test
    void getMatchingClusterDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree1();
        var mcm = new MatchingClusterMetricO3Long();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(0.0, distance);
    }

    @Test
    void getMatchingClusterDistance_4leafsTrees_returnsFour() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        var mcm = new MatchingClusterMetricO3Long();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(4.0, distance);
    }

    @Test
    void getMatchingClusterDistance_10leafsTrees_returnsEightyFour() {
        var t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        var mcm = new MatchingClusterMetricO3Long();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(23.0, distance);
    }
}
