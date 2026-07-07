package treecmp.ecr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristicMPMetric;
import treecmp.util.TestTreeFactory;

class Ecr3IncrementalHeuristicMPMetricTest {

    private Ecr3IncrementalHeuristicMPMetric metric;
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        // Testujemy inkrementalną heurystykę 3-sECR dla Matching Pair (MP - Rooted)
        metric = new Ecr3IncrementalHeuristicMPMetric();
    }

    @Test
    void testDistanceToSelfIsZero() {
        // Arrange: 3-sECR wymaga klastra 4 węzłów, więc bezpiecznie bierzemy 6-liściowego caterpillara ukorzenionego
        Tree t1 = TestTreeFactory.sixLeavesRootedCaterpillarTree();

        // Act
        double distance = metric.getDistance(t1, t1);

        // Assert
        assertEquals(0.0, distance, DELTA,
                "Dystans MP dla identycznych drzew ukorzenionych w 3-sECR musi wynosić 0.0");
    }

    @Test
    void testIncrementalHeuristicResolvesLargerDistance() {
        // Arrange: Bierzemy dwa różne, większe drzewa ukorzenione
        Tree t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        assertTrue(distance > 0.0, "Dystans MP między różnymi drzewami ukorzenionymi musi być większy niż 0");
        System.out.println("Dystans 3-sECR MP Incremental Heuristic (10 liści) wyniósł: " + distance);
    }
}