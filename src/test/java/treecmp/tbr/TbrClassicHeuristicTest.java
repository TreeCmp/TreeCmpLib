package treecmp.heuristics.tbr;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pal.tree.Tree;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.metrics.topological.RFMetric;
import treecmp.util.TestTreeFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zunifikowany test dla heurystyk TBR i uTBR (Tree Bisection and Reconnection).
 * Weryfikuje poprawne wstrzykiwanie zależności i obsługę obu wariantów drzew.
 */
class TbrClassicHeuristicTest {

    private static final double DELTA = 0.000001;

    // --- DATA PROVIDERS ---

    static Stream<Arguments> provideRootedMetrics() {
        return Stream.of(
                // TBR dla drzew ukorzenionych (np. RFClusterMetric)
                Arguments.of(new TbrClassicHeuristic(new RFClusterMetric(), true, "RFC"))
        );
    }

    static Stream<Arguments> provideUnrootedMetrics() {
        return Stream.of(
                // uTBR dla drzew nieukorzenionych (np. RFMetric)
                Arguments.of(new TbrClassicHeuristic(new RFMetric(), false, "RF"))
        );
    }

    // --- TESTY DLA DRZEW UKORZENIONYCH (TBR) ---

    @ParameterizedTest(name = "[{index}] Rooted TBR - Dystans do siebie (0.0): {0}")
    @MethodSource("provideRootedMetrics")
    void testRootedDistanceToSelfIsZero(TbrClassicHeuristic metric) {
        Tree t = TestTreeFactory.randomRootedBinaryTree(8, 42L);
        assertEquals(0.0, metric.getDistance(t, t), DELTA,
                "Heurystyka TBR (" + metric.getName() + ") musi zwracać 0.0 dla tych samych drzew.");
    }

    @ParameterizedTest(name = "[{index}] Rooted TBR - Różne drzewa (>0.0): {0}")
    @MethodSource("provideRootedMetrics")
    void testRootedDistanceBetweenDifferentTrees(TbrClassicHeuristic metric) {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(10, 100L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(10, 200L);

        double distance = metric.getDistance(t1, t2);
        assertTrue(distance > 0.0,
                "Heurystyka TBR (" + metric.getName() + ") musi znaleźć różnicę i zwrócić > 0.");
    }

    // --- TESTY DLA DRZEW NIEUKORZENIONYCH (uTBR) ---

    @ParameterizedTest(name = "[{index}] Unrooted uTBR - Dystans do siebie (0.0): {0}")
    @MethodSource("provideUnrootedMetrics")
    void testUnrootedDistanceToSelfIsZero(TbrClassicHeuristic metric) {
        Tree t = TestTreeFactory.randomUnrootedBinaryTree(8, 42L);
        assertEquals(0.0, metric.getDistance(t, t), DELTA,
                "Heurystyka uTBR (" + metric.getName() + ") musi zwracać 0.0 dla tych samych drzew.");
    }

    @ParameterizedTest(name = "[{index}] Unrooted uTBR - Różne drzewa (>0.0): {0}")
    @MethodSource("provideUnrootedMetrics")
    void testUnrootedDistanceBetweenDifferentTrees(TbrClassicHeuristic metric) {
        Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(10, 100L);
        Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(10, 200L);

        double distance = metric.getDistance(t1, t2);
        assertTrue(distance > 0.0,
                "Heurystyka uTBR (" + metric.getName() + ") musi znaleźć różnicę i zwrócić > 0.");
    }
}