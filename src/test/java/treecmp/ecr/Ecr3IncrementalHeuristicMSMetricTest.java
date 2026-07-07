package treecmp.ecr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristicMSMetric;
import treecmp.util.TestTreeFactory;

class Ecr3IncrementalHeuristicMSMetricTest {

    private Ecr3IncrementalHeuristicMSMetric metric;
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        // Testujemy inkrementalną heurystykę 3-sECR dla Matching Split (MS)
        metric = new Ecr3IncrementalHeuristicMSMetric();
    }

    @Test
    void testDistanceToSelfIsZero() {
        // Arrange (3-sECR wymaga co najmniej 6 liści)
        Tree t1 = TestTreeFactory.sixLeavesUnrootedCaterpillarTree();

        // Act
        double distance = metric.getDistance(t1, t1);

        // Assert
        assertEquals(0.0, distance, DELTA,
                "Dystans MS dla identycznych drzew w 3-sECR musi wynosić 0.0");
    }

    @Test
    void testIncrementalHeuristicResolvesLargerDistance() {
        // Arrange
        Tree t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        assertTrue(distance > 0.0, "Dystans MS między różnymi drzewami musi być większy niż 0");
        System.out.println("Dystans 3-sECR MS Incremental Heuristic (10 liści) wyniósł: " + distance);
    }
}