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
 * Sprawdza zachowanie przeszukiwania Steepest Descent dla drzew ukorzenionych i nieukorzenionych.
 */
class TbrClassicHeuristicTest {

    private static final double DELTA = 1e-6;

    static Stream<Arguments> provideTbrMetrics() {
        return Stream.of(
                // 1. Rooted RF Cluster
                Arguments.of(new TbrHeuristicMetric(new RFClusterMetric(), true, "RFC"), true),

                // 2. Rooted Matching Cluster
                Arguments.of(new TbrHeuristicMetric(new MatchingClusterMetric(), true, "MC"), true),

                // 3. Rooted Matching Pair
                Arguments.of(new TbrHeuristicMetric(new MatchingPairMetric(), true, "MP"), true),

                // 4. Unrooted RF
                Arguments.of(new TbrHeuristicMetric(new RFMetric(), false, "RF"), false),

                // 5. Unrooted Matching Split
                Arguments.of(new TbrHeuristicMetric(new MatchingSplitMetric(), false, "MS"), false),

                // 6. Unrooted Matching Triplet
                Arguments.of(new TbrHeuristicMetric(new MatchingTripletMetric(), false, "M3"), false)
        );
    }

    @ParameterizedTest(name = "[{index}] Test dystansu do siebie: {0}")
    @MethodSource("provideTbrMetrics")
    void testDistanceToSelfIsZero(TbrHeuristicMetric metric, boolean isRooted) {
        Tree t1 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(8, 42L)
                : TestTreeFactory.randomUnrootedBinaryTree(8, 42L);

        double distance = metric.getDistance(t1, t1);

        assertEquals(0.0, distance, DELTA,
                "Heurystyka TBR (" + metric.getName() + ") dla identycznych drzew musi natychmiast zwrócić 0.0");
    }

    @ParameterizedTest(name = "[{index}] Test dystansu dla różnych drzew: {0}")
    @MethodSource("provideTbrMetrics")
    void testDistanceBetweenDifferentSmallTrees(TbrHeuristicMetric metric, boolean isRooted) {
        Tree t1 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(6, 100L)
                : TestTreeFactory.randomUnrootedBinaryTree(6, 100L);
        Tree t2 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(6, 200L)
                : TestTreeFactory.randomUnrootedBinaryTree(6, 200L);

        double distance = metric.getDistance(t1, t2);

        assertTrue(distance > 0.0,
                "Heurystyka TBR (" + metric.getName() + ") musi znaleźć różnicę i zwrócić dystans > 0 dla różnych drzew.");
    }

    @ParameterizedTest(name = "[{index}] Test stabilności przeszukiwania (8 liści): {0}")
    @MethodSource("provideTbrMetrics")
    void testHeuristicResolvesWithoutCrashing(TbrHeuristicMetric metric, boolean isRooted) {
        Tree t1 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(8, 999L)
                : TestTreeFactory.randomUnrootedBinaryTree(8, 999L);
        Tree t2 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(8, 888L)
                : TestTreeFactory.randomUnrootedBinaryTree(8, 888L);

        double distance = metric.getDistance(t1, t2);

        assertTrue(distance > 0.0,
                "Dystans TBR dla metryki (" + metric.getName() + ") musi być dodatni.");
    }
}