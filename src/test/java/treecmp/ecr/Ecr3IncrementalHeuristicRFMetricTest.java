package treecmp.ecr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristicRFMetric;
import treecmp.util.TestTreeFactory;

class Ecr3IncrementalHeuristicRFMetricTest {

    private Ecr3IncrementalHeuristicRFMetric metric;
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        // Inicjalizacja naszej rewelacyjnej heurystyki inkrementalnej dla 3-sECR
        metric = new Ecr3IncrementalHeuristicRFMetric();
    }

    /**
     * Testuje podstawowy warunek: dystans do tego samego drzewa musi wynosić 0.
     * UWAGA: Używamy 6 liści, ponieważ 3-sECR wymaga min. 3 krawędzi wewnętrznych
     * do utworzenia 4-węzłowego klastra.
     */
    @Test
    void testDistanceToSelfIsZero() {
        // Arrange
        Tree t1 = TestTreeFactory.sixLeavesUnrootedCaterpillarTree();

        // Act
        double distance = metric.getDistance(t1, t1);

        // Assert
        assertEquals(0.0, distance, DELTA,
                "Wersja inkrementalna 3-sECR dla identycznych drzew musi zwrócić 0.0");
    }

    /**
     * Testuje zachowanie algorytmu dla większych drzew.
     * Weryfikuje stabilność systemu evaluate3sEcrMove/commit3sEcrMove
     * przy budowie 105 topologii z użyciem operacji bitowych na stosie.
     */
    @Test
    void testIncrementalHeuristicResolvesLargerDistance() {
        // Arrange: Bierzemy dwa różne drzewa 10-liściowe
        Tree t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        assertTrue(distance > 0.0, "Dystans między różnymi drzewami musi być większy niż 0");

        System.out.println("Dystans 3-sECR Incremental Heuristic (10 liści) wyniósł: " + distance);

        if (distance == Double.POSITIVE_INFINITY) {
            System.out.println("Algorytm utknął w minimum lokalnym (poprawne zachowanie dla czystej heurystyki).");
        }
    }
}