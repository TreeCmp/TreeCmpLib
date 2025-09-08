package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.topological.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CopheneticL2MetricTest {

    @Test
    void getCopheneticL2Distance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var mcm = new CopheneticL2Metric();

        double distance = mcm.getDistance(t1, t1);

        assertEquals(0.0, distance);
    }

    @Test
    void getCopheneticL2Distance_4leafsTrees_returnsFour() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        var mcm = new CopheneticL2Metric();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(2.0, distance);
    }

    @Test
    void getCopheneticL2Distance_10leafsTrees_returnsEightyFour() {
        var t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        var mcm = new CopheneticL2Metric();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(13.2664991614216, distance,0.00000000000001);
    }
}
