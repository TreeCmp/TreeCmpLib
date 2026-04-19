package treecmp.nni;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.nni.NniHeuristicRFMetric;
import treecmp.util.TestTreeFactory;

class NniHeuristicRFMetricTest {

    private NniHeuristicRFMetric metric;
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        // Testujemy klasyczne NNI na splitach (nieukorzenione)
        metric = new NniHeuristicRFMetric();
    }

    @Test
    void testDistanceToSelfIsZero() {
        // Arrange
        Tree t1 = TestTreeFactory.fourLeavesUnrootedStarTree(); // ((1,2),3,4);

        // Act
        double distance = metric.getDistance(t1, t1);

        // Assert
        assertEquals(0.0, distance, DELTA,
                "Heurystyka dla dwóch identycznych drzew musi natychmiast zwrócić 0.0");
    }

    @Test
    void testOneStepNeighborIsFoundImmediately() {
        // Arrange
        // Drzewo startowe: ((1,2),3,4);
        Tree t1 = TestTreeFactory.fourLeavesUnrootedStarTree();

        // Drzewo oddalone o DOKŁADNIE 1 krok NNI: ((1,3),2,4);
        // (Z naszych poprzednich testów NniUtils wiemy, że to bezpośredni sąsiad)
        Tree t2 = TestTreeFactory.fourLeavesUnrootedTargetTree();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        assertEquals(1.0, distance, DELTA,
                "Heurystyka musi bezbłędnie znajdować cel oddalony o dokładnie 1 krok NNI");
    }

    @Test
    void testHeuristicResolvesLargerDistanceWithoutCrashing() {
        // Arrange: Bierzemy dwa zupełnie różne drzewa (np. 10 liści)
        Tree t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        // Nie wiemy, czy heurystyka znajdzie optymalną drogę, czy utknie.
        // Wiemy jednak, że wynik musi być DODATNI i nie może być równy 0 (bo drzewa są różne).
        // Testujemy po prostu "Contract" (kontrakt) metody.
        assertTrue(distance > 0.0, "Dystans między różnymi drzewami musi być większy niż 0");

        // Możemy też zalogować wynik, żeby zobaczyć jak algorytm sobie poradził:
        System.out.println("Dystans NNI Heuristic dla drzew 10-liściowych wyniósł: " + distance);

        if (distance == Double.POSITIVE_INFINITY) {
            System.out.println("Algorytm zgodnie z oczekiwaniami utknął w minimum lokalnym.");
        }
    }
}