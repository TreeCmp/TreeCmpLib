package treecmp.metrics;

import treecmp.metrics.util.TestTreeFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RFMetricTest {

    @Test
    void getRFDistance_bothTreeAsNull_returnsException() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> RFMetric.getRFDistance(null, null)
        );
        assertNull(exception.getMessage());
    }

    @Test
    void getRFDistance_firstTreeAsNull_returnsException() {
        var t1 = TestTreeFactory.fourLeavesTree1();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> RFMetric.getRFDistance(null, t1)
        );
        assertNull(exception.getMessage());
    }

    @Test
    void getRFDistance_secondTreeAsNull_returnsException() {
        var t1 = TestTreeFactory.fourLeavesTree1();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> RFMetric.getRFDistance(t1, null)
        );
        assertNull(exception.getMessage());
    }

    @Test
    void getRFDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree1();

        double distance = RFMetric.getRFDistance(t1, t2);

        assertEquals(0.0, distance);
    }

    @Test
    void getRFDistance_4leafsTrees_returnsTwo() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        double distance = RFMetric.getRFDistance(t1, t2);

        assertEquals(2.0, distance);
    }

    @Test
    void getRFDistance_10leafsTrees_returnsEight() {
        var t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        double distance = RFMetric.getRFDistance(t1, t2);

        assertEquals(8.0, distance);
    }
}
