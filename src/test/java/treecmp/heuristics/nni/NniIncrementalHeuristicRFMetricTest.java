package treecmp.heuristics.nni;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.metrics.topological.acc.RFIncrementalMetric;
import treecmp.util.TestTreeFactory;

class NniIncrementalHeuristicRFMetricTest {

    // Zmieniamy typ na naszą nową, uniwersalną heurystykę
    private NniIncrementalHeuristic metric;
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        // WZORZEC KOMPOZYCJI W TESTACH:
        // Wstrzykujemy kalkulator RFIncrementalMetric do uniwersalnego silnika NNI
        metric = new NniIncrementalHeuristic(new RFIncrementalMetric(), "RF");
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

        System.out.println("Dystans NNI-RF: " + distance);

        if (distance == Double.POSITIVE_INFINITY) {
            System.out.println("Algorytm utknął w minimum lokalnym (poprawne zachowanie dla heurystyki zachłannej NNI).");
        }
    }
}