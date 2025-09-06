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
        var t1 = TestTreeFactory.simpleTreeA();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> RFMetric.getRFDistance(null, t1)
        );
        assertNull(exception.getMessage());
    }

    @Test
    void getRFDistance_secondTreeAsNull_returnsException() {
        var t1 = TestTreeFactory.simpleTreeA();

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> RFMetric.getRFDistance(t1, null)
        );
        assertNull(exception.getMessage());
    }

    @Test
    void getRFDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.simpleTreeA();
        var t2 = TestTreeFactory.simpleTreeA();

        double distance = RFMetric.getRFDistance(t1, t2);

        assertEquals(0.0, distance);
    }

    @Test
    void getRFDistance_4leafsTrees_returnsTwo() {
        var t1 = TestTreeFactory.simpleTreeA();
        var t2 = TestTreeFactory.simpleTreeB();

        double distance = RFMetric.getRFDistance(t1, t2);

        assertEquals(2.0, distance);
    }
}
