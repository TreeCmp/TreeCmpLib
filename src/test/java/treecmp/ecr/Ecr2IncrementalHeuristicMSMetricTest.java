package treecmp.ecr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristicMSMetric;
import treecmp.util.TestTreeFactory;

class Ecr2IncrementalHeuristicMSMetricTest {

    private Ecr2IncrementalHeuristicMSMetric metric;
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        // Testujemy inkrementalną heurystykę 2-sECR dla Matching Split (MS)
        metric = new Ecr2IncrementalHeuristicMSMetric();
    }

    @Test
    void testDistanceToSelfIsZero() {
        // Arrange (2-sECR wymaga co najmniej 5 liści)
        Tree t1 = TestTreeFactory.fiveLeavesUnrootedCaterpillarTree();

        // Act
        double distance = metric.getDistance(t1, t1);

        // Assert
        assertEquals(0.0, distance, DELTA,
                "Dystans MS dla identycznych drzew musi wynosić 0.0");
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
        System.out.println("Dystans 2-sECR MS Incremental Heuristic (10 liści) wyniósł: " + distance);
    }
}