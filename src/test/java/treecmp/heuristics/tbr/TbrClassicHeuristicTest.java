package treecmp.heuristics.tbr;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pal.tree.Tree;
import treecmp.metrics.topological.*;
import treecmp.util.TestTreeFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zunifikowany test dla klasycznej heurystyki TBR (TbrHeuristicMetric).
 * Odpowiednik SprClassicHeuristicTest dla otoczenia TBR.
 */
class TbrClassicHeuristicTest {

    private static final double DELTA = 0.000001;

    static Stream<Arguments> provideTbrHeuristics() {
        return Stream.of(
                Arguments.of(new TbrHeuristicMetric(new RFClusterMetric(), true, "RFC")),
                Arguments.of(new TbrHeuristicMetric(new MatchingClusterMetricO3(), true, "MC")),
                Arguments.of(new TbrHeuristicMetric(new MatchingPairMetric(), true, "MP")),
                Arguments.of(new TbrHeuristicMetric(new RFMetric(), false, "RF")),
                Arguments.of(new TbrHeuristicMetric(new MatchingSplitMetric(), false, "MS")),
                Arguments.of(new TbrHeuristicMetric(new MatchingTripletMetric(), false, "M3"))
        );
    }

    @ParameterizedTest(name = "[{index}] Test dystansu do siebie dla metryki: {0}")
    @MethodSource("provideTbrHeuristics")
    void testDistanceToSelfIsZero(TbrHeuristicMetric metric) {
        Tree t1 = metric.isRooted()
                ? TestTreeFactory.randomRootedBinaryTree(8, 42L)
                : TestTreeFactory.randomUnrootedBinaryTree(8, 42L);

        double distance = metric.getDistance(t1, t1);

        assertEquals(0.0, distance, DELTA,
                "Heurystyka TBR (" + metric.getName() + ") dla identycznych drzew musi natychmiast zwrócić 0.0");
    }

    @ParameterizedTest(name = "[{index}] Test dystansu dla małych drzew dla metryki: {0}")
    @MethodSource("provideTbrHeuristics")
    void testDistanceBetweenDifferentSmallTrees(TbrHeuristicMetric metric) {
        Tree t1 = metric.isRooted()
                ? TestTreeFactory.randomRootedBinaryTree(6, 100L)
                : TestTreeFactory.randomUnrootedBinaryTree(6, 100L);
        Tree t2 = metric.isRooted()
                ? TestTreeFactory.randomRootedBinaryTree(6, 200L)
                : TestTreeFactory.randomUnrootedBinaryTree(6, 200L);

        double distance = metric.getDistance(t1, t2);

        assertTrue(distance > 0.0,
                "Heurystyka TBR (" + metric.getName() + ") musi znaleźć różnicę i zwrócić dystans > 0 dla różnych drzew.");
    }

    @ParameterizedTest(name = "[{index}] Test stabilności (10 liści) dla metryki: {0}")
    @MethodSource("provideTbrHeuristics")
    void testHeuristicResolvesLargerDistanceWithoutCrashing(TbrHeuristicMetric metric) {
        Tree t1 = metric.isRooted()
                ? TestTreeFactory.randomRootedBinaryTree(10, 999L)
                : TestTreeFactory.randomUnrootedBinaryTree(10, 999L);
        Tree t2 = metric.isRooted()
                ? TestTreeFactory.randomRootedBinaryTree(10, 888L)
                : TestTreeFactory.randomUnrootedBinaryTree(10, 888L);

        double distance = metric.getDistance(t1, t2);

        assertTrue(distance > 0.0,
                "Dystans TBR dla metryki (" + metric.getName() + ") musi być dodatni.");

        if (distance == Double.POSITIVE_INFINITY) {
            System.out.println(metric.getName() + ": Algorytm TBR wpadł w minimum lokalne dla 10 liści.");
        }
    }
}