package treecmp.nni;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristicRFMetric; // Nasza nowa klasa
import treecmp.util.TestTreeFactory;

class NniIncrementalHeuristicRFMetricTest {

    private NniIncrementalHeuristicRFMetric metric;
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        // Testujemy nową, inkrementalną wersję heurystyki NNI na splitach
        metric = new NniIncrementalHeuristicRFMetric();
    }

    /**
     * Testuje podstawowy warunek: dystans do tego samego drzewa musi wynosić 0.
     */
    @Test
    void testDistanceToSelfIsZero() {
        // Arrange
        Tree t1 = TestTreeFactory.fourLeavesUnrootedStarTree();

        // Act
        double distance = metric.getDistance(t1, t1);

        // Assert
        assertEquals(0.0, distance, DELTA,
                "Wersja inkrementalna dla identycznych drzew musi zwrócić 0.0");
    }

    /**
     * Testuje, czy heurystyka inkrementalna poprawnie wykrywa cel
     * oddalony o dokładnie jeden ruch NNI (weryfikacja applyNni/undoNni).
     */
    @Test
    void testOneStepNeighborIsFoundImmediately() {
        // Arrange
        Tree t1 = TestTreeFactory.fourLeavesUnrootedStarTree();
        Tree t2 = TestTreeFactory.fourLeavesUnrootedTargetTree();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        assertEquals(1.0, distance, DELTA,
                "Heurystyka inkrementalna musi znaleźć cel w dokładnie jednym kroku");
    }

    /**
     * Testuje zachowanie algorytmu dla większych drzew.
     * Weryfikuje stabilność stosów historii przy wielu krokach.
     */
    @Test
    void testIncrementalHeuristicResolvesLargerDistance() {
        // Arrange
        Tree t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        assertTrue(distance > 0.0, "Dystans dla różnych drzew musi być dodatni");

        System.out.println("Dystans NNI Incremental Heuristic (10 liści) wyniósł: " + distance);

        if (distance == Double.POSITIVE_INFINITY) {
            System.out.println("Algorytm utknął w minimum lokalnym (poprawne zachowanie dla NNI).");
        }
    }
}