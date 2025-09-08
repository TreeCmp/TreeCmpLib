package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.metrics.topological.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QuartetMetricLongTest {

    @Test
    void getQuartetLongDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree1();

        Double distance = QuartetMetricLong.getQuartetDistance(t1, t2);

        assertEquals(0.0, distance);
    }

    @Test
    void getQuartetLongDistance_4leafsTrees_returnsOne() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        Double distance = QuartetMetricLong.getQuartetDistance(t1, t2);

        assertEquals(1.0, distance);
    }

    @Test
    void getQuartetLongDistance_10leafsTrees_returnsOneHundredAndFiftySix() {
        var t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        Double distance = QuartetMetricLong.getQuartetDistance(t1, t2);

        assertEquals(156.0, distance);
    }

}
