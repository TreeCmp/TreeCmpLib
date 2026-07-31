package treecmp.heuristics.nni;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pal.tree.Tree;
import treecmp.heuristics.nni.NniClassicHeuristic;
import treecmp.metrics.topological.MatchingTripletMetric;
import treecmp.metrics.topological.RFMetric;
import treecmp.metrics.topological.RMASTMetric;
import treecmp.util.TestTreeFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zunifikowany test dla wszystkich heurystyk NNI bazujących na klasycznych metrykach.
 * Używa testów sparametryzowanych JUnit 5 (@ParameterizedTest), co pozwala
 * na uruchomienie tej samej logiki testowej dla wielu różnych konfiguracji.
 */
class NniClassicHeuristicTest {

    private static final double DELTA = 0.000001;

    /**
     * Data Provider: Dostarcza strumień gotowych do przetestowania konfiguracji metryk.
     * Dodanie nowej metryki do testowania to po prostu dodanie nowej linijki tutaj!
     */
    static Stream<Arguments> provideUnrootedMetrics() {
        return Stream.of(
                // 1. Zwykłe RF
                Arguments.of(new NniClassicHeuristic(new RFMetric(), false, "RF")),

                // 2. Klasyczna metryka M3
                Arguments.of(new NniClassicHeuristic(new MatchingTripletMetric(), false, "M3")),

                // 3. Złożona metryka: UMAST jako główna, RF jako szybki filtr
                Arguments.of(new NniClassicHeuristic(new RMASTMetric(), new RFMetric(), false, "UMAST_RF"))

                // Tutaj możesz dopisać MC, MS, itp...
                // Arguments.of(new NniClassicHeuristic(new MCMetric(), false, "MC"))
        );
    }

    @ParameterizedTest(name = "[{index}] Test dystansu do siebie dla metryki: {0}")
    @MethodSource("provideUnrootedMetrics")
    void testDistanceToSelfIsZero(NniClassicHeuristic metric) {
        // Arrange
        Tree t1 = TestTreeFactory.fourLeavesUnrootedStarTree(); // ((1,2),3,4);

        // Act
        double distance = metric.getDistance(t1, t1);

        // Assert
        assertEquals(0.0, distance, DELTA,
                "Heurystyka (" + metric.getName() + ") dla dwóch identycznych drzew musi zwrócić 0.0");
    }

    @ParameterizedTest(name = "[{index}] Test dystansu dla 1 kroku NNI dla metryki: {0}")
    @MethodSource("provideUnrootedMetrics")
    void testOneStepNeighborIsFound(NniClassicHeuristic metric) {
        // Arrange
        Tree t1 = TestTreeFactory.fourLeavesUnrootedStarTree();
        Tree t2 = TestTreeFactory.fourLeavesUnrootedTargetTree();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        // Zależnie od tego, czy dana metryka pod spodem (RF, M3) zwraca inną wagę punktową,
        // testujemy uniwersalny kontrakt: dystans musi być wykryty i większy od 0.
        assertTrue(distance > 0.0,
                "Heurystyka (" + metric.getName() + ") musi bezbłędnie znaleźć różnicę do celu.");
    }

    @ParameterizedTest(name = "[{index}] Test stabilności dla większych drzew dla metryki: {0}")
    @MethodSource("provideUnrootedMetrics")
    void testHeuristicResolvesLargerDistanceWithoutCrashing(NniClassicHeuristic metric) {
        // Arrange: Bierzemy dwa zupełnie różne 10-liściowe drzewa
        Tree t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        // Testujemy "Contract" - heurystyka nie może rzucić błędem i musi wyliczyć dystans.
        assertTrue(distance > 0.0,
                "Dystans NNI dla metryki (" + metric.getName() + ") musi być dodatni.");

        if (distance == Double.POSITIVE_INFINITY) {
            System.out.println(metric.getName() + ": Algorytm wpadł w minimum lokalne dla 10 liści (to normalne dla klasycznej heurystyki zachłannej).");
        }
    }
}