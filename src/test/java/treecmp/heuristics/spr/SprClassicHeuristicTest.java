/*
package treecmp.heuristics.spr;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.spr.SprHeuristicMetric;
import treecmp.metrics.topological.*;
import treecmp.util.TestTreeFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

*/
/**
 * Zunifikowany test dla uniwersalnej heurystyki SPR (SprHeuristicMetric).
 * Testuje klasyczne przeszukiwanie sąsiedztwa (Steepest Descent) dla wielu
 * różnych metryk topologicznych (RFC, TT, Coph, MAST, MC, MP, NS).
 *//*

class SprClassicHeuristicTest {

    private static final double DELTA = 0.000001;

    */
/**
     * Data Provider: Strumień gotowych metryk do przetestowania w środowisku SPR.
     * Dodanie nowej metryki do systemu polega na dopisaniu tutaj jednej linijki!
     *//*

    static Stream<Arguments> provideRootedMetrics() {
        return Stream.of(
                // 1. RF Cluster (RFC) - Wymaga drzew ukorzenionych
                // Zaktualizowano o flagę isRooted (true)
                Arguments.of(new SprHeuristicMetric(new RFClusterMetric(), true, "RFC")),

                // 2. Triplets (TT)
                Arguments.of(new SprHeuristicMetric(new TripletMetric(), true, "TT")),

                // 3. Cophenetic L2 (Coph)
                Arguments.of(new SprHeuristicMetric(new CopheneticL2Metric(), true, "Coph")),

                // 4. Rooted MAST
                Arguments.of(new SprHeuristicMetric(new RMASTMetric(), true, "MAST")),

                // 5. Matching Cluster O(N^3) (MC)
                Arguments.of(new SprHeuristicMetric(new MatchingClusterMetricO3(), true, "MC")),

                // 6. Matching Pair (MP)
                Arguments.of(new SprHeuristicMetric(new MatchingPairMetric(), true, "MP")),

                // 7. Nodal L2 Splitted (NS)
                Arguments.of(new SprHeuristicMetric(new NodalL2SplittedMetric(), true, "NS"))
        );
    }

    @ParameterizedTest(name = "[{index}] Test dystansu do siebie dla metryki: {0}")
    @MethodSource("provideRootedMetrics")
    void testDistanceToSelfIsZero(SprHeuristicMetric metric) {
        // Arrange: Dla SPR używamy drzew ściśle ukorzenionych (Rooted)
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(8, 42L);

        // Act
        double distance = metric.getDistance(t1, t1);

        // Assert
        assertEquals(0.0, distance, DELTA,
                "Heurystyka SPR (" + metric.getName() + ") dla identycznych drzew ukorzenionych musi natychmiast zwrócić 0.0");
    }

    @ParameterizedTest(name = "[{index}] Test dystansu dla małych drzew dla metryki: {0}")
    @MethodSource("provideRootedMetrics")
    void testDistanceBetweenDifferentSmallTrees(SprHeuristicMetric metric) {
        // Arrange: Dwa różne, małe drzewa ukorzenione
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(6, 100L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(6, 200L);

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        assertTrue(distance > 0.0,
                "Heurystyka SPR (" + metric.getName() + ") musi znaleźć różnicę i zwrócić dystans > 0 dla różnych drzew.");
    }

    @ParameterizedTest(name = "[{index}] Test stabilności przeszukiwania (10 liści): {0}")
    @MethodSource("provideTbrHeuristics")
    void testHeuristicResolvesLargerDistanceWithoutCrashing(IncrementalHeuristicBaseMetric heuristic, boolean isRooted) {
        Tree t1 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(10, 999L)
                : TestTreeFactory.randomUnrootedBinaryTree(10, 999L);
        Tree t2 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(10, 888L)
                : TestTreeFactory.randomUnrootedBinaryTree(10, 888L);
        assignNumbers(t1);
        assignNumbers(t2);

        double steps = heuristic.getDistance(new SimpleTree(t1), t2);

        assertTrue(steps > 0.0,
                "Dystans TBR (" + heuristic.getName() + ") musi być dodatni.");
    }
}*/
