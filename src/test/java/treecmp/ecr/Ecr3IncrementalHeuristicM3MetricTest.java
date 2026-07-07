package treecmp.ecr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristicM3Metric;
import treecmp.util.TestTreeFactory;

class Ecr3IncrementalHeuristicM3MetricTest {

    private Ecr3IncrementalHeuristicM3Metric metric;
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        // Inicjalizujemy inkrementalną heurystykę 3-sECR dla Matching Triplet (M3 - Unrooted)
        metric = new Ecr3IncrementalHeuristicM3Metric();
    }

    @Test
    void testDistanceToSelfIsZero() {
        // Arrange: M3 działa na drzewach NIEUKORZENIONYCH.
        Tree t1 = TestTreeFactory.tenLeavesUnrootedTree1();

        // Act
        double distance = metric.getDistance(t1, t1);

        // Assert
        assertEquals(0.0, distance, DELTA,
                "Dystans M3 dla identycznych drzew nieukorzenionych w otoczeniu 3-sECR musi wynosić 0.0");
    }

    @Test
    void testIncrementalHeuristicResolvesLargerDistance() {
        // Arrange: Bierzemy dwa różne drzewa nieukorzenione
        Tree t1 = TestTreeFactory.tenLeavesUnrootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesUnrootedTree2();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        assertTrue(distance > 0.0, "Dystans M3 między różnymi drzewami nieukorzenionymi musi być większy niż 0");
        System.out.println("Dystans 3-sECR M3 Incremental Heuristic (10 liści) wyniósł: " + distance);
    }
}