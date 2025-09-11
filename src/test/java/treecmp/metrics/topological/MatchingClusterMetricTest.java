package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchingClusterMetricTest {

    @Test
    void getMatchingClusterDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var mcm = new MatchingClusterMetric();

        double distance = mcm.getDistance(t1, t1);

        assertEquals(0.0, distance);
    }

    @Test
    void getMatchingClusterDistance_4leafsTrees_returnsFour() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        var mcm = new MatchingClusterMetric();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(4.0, distance);
    }

    @Test
    void getMatchingClusterDistance_10leafsTrees_returnsEightyFour() {
        var t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        var mcm = new MatchingClusterMetric();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(23.0, distance);
    }
}
