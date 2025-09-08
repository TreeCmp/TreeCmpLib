package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.topological.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QuartetMetricDoubleTest {

    @Test
    void getQuartetDoubleDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();

        double distance = QuartetMetricDouble.getQuartetDistance(t1, t1);

        assertEquals(0.0, distance);
    }

    @Test
    void getQuartetDoubleDistance_4leafsTrees_returnsOne() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        double distance = QuartetMetricDouble.getQuartetDistance(t1, t2);

        assertEquals(1.0, distance);
    }

    @Test
    void getQuartetDoubleDistance_10leafsTrees_returnsOneHundredAndFiftySix() {
        var t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        double distance = QuartetMetricDouble.getQuartetDistance(t1, t2);

        assertEquals(156.0, distance);
    }

}
