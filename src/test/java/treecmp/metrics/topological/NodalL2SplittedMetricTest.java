package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.topological.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodalL2SplittedMetricTest {

    @Test
    void getNodalL2SplittedDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var nm = new NodalL2SplittedMetric();

        double distance = nm.getDistance(t1, t1);

        assertEquals(0.0, distance);
    }

    @Test
    void getNodalL2SplittedDistance_4leafsTrees_returnsFour() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        var nm = new NodalL2SplittedMetric();

        double distance = nm.getDistance(t1, t2);

        assertEquals(2.8284271247461903, distance, 0.00000000000001);
    }

    @Test
    void getNodalL2SplittedDistance_10leafsTrees_returnsEightyFour() {
        var t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        var nm = new NodalL2SplittedMetric();

        double distance = nm.getDistance(t1, t2);

        assertEquals(18.841443681416774, distance, 0.00000000000001);
    }
}
