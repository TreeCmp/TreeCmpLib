package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchingPairMetricTest {

    @Test
    void getMatchingPairDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var mcm = new MatchingPairMetric();

        double distance = mcm.getDistance(t1, t1);

        assertEquals(0.0, distance);
    }

    @Test
    void getMatchingPairDistance_4leafsTrees_returnsFour() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        var mcm = new MatchingPairMetric();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(4.0, distance);
    }

    @Test
    void getMatchingPairDistance_10leafsTrees_returnsEightyFour() {
        var t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        var mcm = new MatchingPairMetric();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(26.0, distance);
    }
}
