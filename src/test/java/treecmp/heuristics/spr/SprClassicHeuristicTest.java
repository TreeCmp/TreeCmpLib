package treecmp.heuristics.spr;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.spr.SprHeuristicMetric;
import treecmp.metrics.topological.*;
import treecmp.util.TestTreeFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zunifikowany test dla uniwersalnej heurystyki SPR (SprHeuristicMetric).
 * Testuje klasyczne przeszukiwanie sąsiedztwa (Steepest Descent) dla wielu
 * różnych metryk topologicznych (RFC, TT, Coph, MAST, MC, MP, NS).
 */
class SprClassicHeuristicTest {

    private static final double DELTA = 0.000001;

    /**
     * Data Provider: Strumień gotowych metryk do przetestowania w środowisku SPR.
     */
    static Stream<Arguments> provideRootedMetrics() {
        return Stream.of(
                Arguments.of(new SprHeuristicMetric(new RFClusterMetric(), true, "RFC")),
                Arguments.of(new SprHeuristicMetric(new TripletMetric(), true, "TT")),
                Arguments.of(new SprHeuristicMetric(new CopheneticL2Metric(), true, "Coph")),
                Arguments.of(new SprHeuristicMetric(new RMASTMetric(), true, "MAST")),
                Arguments.of(new SprHeuristicMetric(new MatchingClusterMetricO3(), true, "MC")),
                Arguments.of(new SprHeuristicMetric(new MatchingPairMetric(), true, "MP")),
                Arguments.of(new SprHeuristicMetric(new NodalL2SplittedMetric(), true, "NS"))
        );
    }

    @ParameterizedTest(name = "[{index}] Test dystansu do siebie dla metryki: {0}")
    @MethodSource("provideRootedMetrics")
    void testDistanceToSelfIsZero(SprHeuristicMetric metric) {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(8, 42L);

        double distance = metric.getDistance(t1, t1);

        assertEquals(0.0, distance, DELTA,
                "Heurystyka SPR (" + metric.getName() + ") dla identycznych drzew ukorzenionych musi natychmiast zwrócić 0.0");
    }

    @ParameterizedTest(name = "[{index}] Test dystansu dla małych drzew dla metryki: {0}")
    @MethodSource("provideRootedMetrics")
    void testDistanceBetweenDifferentSmallTrees(SprHeuristicMetric metric) {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(6, 100L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(6, 200L);

        double distance = metric.getDistance(t1, t2);

        assertTrue(distance > 0.0,
                "Heurystyka SPR (" + metric.getName() + ") musi znaleźć różnicę i zwrócić dystans > 0 dla różnych drzew.");
    }

    private static void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @ParameterizedTest(name = "[{index}] Test stabilności przeszukiwania (10 liści): {0}")
    @MethodSource("provideRootedMetrics")
    void testHeuristicResolvesLargerDistanceWithoutCrashing(SprHeuristicMetric metric) {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(10, 999L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(10, 888L);
        assignNumbers(t1);
        assignNumbers(t2);

        double steps = metric.getDistance(new SimpleTree(t1), t2);

        assertTrue(steps > 0.0,
                "Dystans SPR (" + metric.getName() + ") musi być dodatni.");
    }
}