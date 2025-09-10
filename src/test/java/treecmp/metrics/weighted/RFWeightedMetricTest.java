package treecmp.metrics.weighted;

import org.junit.jupiter.api.Test;
import treecmp.metrics.topological.NodalL2Metric;
import treecmp.metrics.topological.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.*;

class RFWeightedMetricTest {



//    @Test
//    void getRFDistance_identicalTrees_returnsZero() {
//        var t1 = TestTreeFactory.fourLeavesWeightedTree1();
//
//        var rfw = new RFWeightMetric();
//
//        double distance = rfw.getDistance(t1,t1);
//
//        assertEquals(0.0, distance);
//    }

    @Test
    void getRFDistance_4leafsZeroTrees_returnsTwo() {
        var t1 = TestTreeFactory.fourLeavesZeroWeightedTree1();
        var t2 = TestTreeFactory.fourLeavesZeroWeightedTree2();

        var rfw = new RFWeightMetric();

        double distance = rfw.getDistance(t1,t2);

        assertEquals(0.0, distance);
    }

    @Test
    void getRFDistance_4leafsTrees_returnsTwo() {
        var t1 = TestTreeFactory.fourLeavesWeightedTree1();
        var t2 = TestTreeFactory.fourLeavesWeightedTree2();

        var rfw = new RFWeightMetric();

        double distance = rfw.getDistance(t1,t2);

        assertEquals(124.5, distance);
    }

    @Test
    void getRFDistance_10leafsTrees_returnsEight() {
        var t1 = TestTreeFactory.tenLeavesWeightedBinaryUnrootedTree1();
        var t2 = TestTreeFactory.tenLeavesWeightedBinaryUnrootedTree2();

        var rfw = new RFWeightMetric();

        double distance = rfw.getDistance(t1,t2);;

        assertEquals(417.5, distance);
    }
}
