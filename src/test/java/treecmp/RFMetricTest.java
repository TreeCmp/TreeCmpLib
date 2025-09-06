package treecmp;

import treecmp.metrics.RFMetric;
import treecmp.util.TestTreeFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RFMetricTest {

    @Test
    void testRFDistanceSimpleTrees() {
        var t1 = TestTreeFactory.simpleTreeA();
        var t2 = TestTreeFactory.simpleTreeB();

        double distance = RFMetric.getRFDistance(t1, t2);

        assertEquals(2.0, distance, 0.0001);
    }
}
