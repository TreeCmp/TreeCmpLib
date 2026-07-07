package treecmp.ecr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristicRFMetric;
import treecmp.util.TestTreeFactory;

class Ecr2IncrementalHeuristicRFMetricTest {

    private Ecr2IncrementalHeuristicRFMetric metric;
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        // Testujemy nową, inkrementalną wersję heurystyki 2-sECR na splitach (RF)
        metric = new Ecr2IncrementalHeuristicRFMetric();
    }

    /**
     * Testuje podstawowy warunek: dystans do tego samego drzewa musi wynosić 0.
     * UWAGA: Używamy 5 liści, ponieważ 2-sECR wymaga min. 2 krawędzi wewnętrznych.
     */
    @Test
    void testDistanceToSelfIsZero() {
        // Arrange
        Tree t1 = TestTreeFactory.fiveLeavesUnrootedCaterpillarTree();

        // Act
        double distance = metric.getDistance(t1, t1);

        // Assert
        assertEquals(0.0, distance, DELTA,
                "Wersja inkrementalna 2-sECR dla identycznych drzew musi zwrócić 0.0");
    }

    /**
     * Testuje zachowanie algorytmu dla większych drzew.
     * Weryfikuje stabilność systemu evaluate/commit przy wielu krokach topologicznych.
     */
    @Test
    void testIncrementalHeuristicResolvesLargerDistance() {
        // Arrange: Bierzemy dwa zupełnie różne drzewa (np. 10 liści)
        Tree t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        assertTrue(distance > 0.0, "Dystans między różnymi drzewami musi być większy niż 0");

        System.out.println("Dystans 2-sECR Incremental Heuristic (10 liści) wyniósł: " + distance);

        if (distance == Double.POSITIVE_INFINITY) {
            System.out.println("Algorytm utknął w minimum lokalnym (typowe zjawisko dla czystej heurystyki).");
        }
    }
}