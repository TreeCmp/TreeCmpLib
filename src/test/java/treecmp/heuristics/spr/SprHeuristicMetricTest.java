package treecmp.heuristics.spr;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pal.tree.Tree;
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

import treecmp.heuristics.spr.SprHeuristicMetric;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;

import java.util.stream.Stream;

class SprHeuristicMetricTest {

    private static final double DELTA = 0.000001;

    // Ten test uruchomi się wielokrotnie dla każdej pary z metody dostarczającej dane
    @ParameterizedTest(name = "{2}")
    @MethodSource("provideTreePairsForTesting")
    void testIncrementalMetricMatchesClassicMetric(Tree tree1, Tree tree2, String testName) {

        // WZORZEC KOMPOZYCJI W AKCJI: Testujemy ujednolicony silnik SPR wstrzykując mu RFC
        // Zaktualizowano konstruktor o flagę isRooted (true)
        SprHeuristicMetric classicMetric = new SprHeuristicMetric(new RFClusterMetric(), true, "RFC");
        SprIncrementalHeuristicMetric incrementalMetric = new SprIncrementalHeuristicMetric(new RFClusterIncrementalMetric(), "RFC");

        double expectedDistance = classicMetric.getDistance(tree1, tree2);
        double actualDistance = incrementalMetric.getDistance(tree1, tree2);

        assertEquals(expectedDistance, actualDistance, DELTA,
                "Wynik zoptymalizowanej heurystyki SPR musi być identyczny z klasyczną.");
    }
    @Test
    void testDistanceShouldBeOneIfTargetIsExactlyOneSprMoveAway() {
        Tree baseTree = TestTreeFactory.fourLeavesBalancedTree1();
        Tree targetTree = TestTreeFactory.fourLeavesCaterpillarTree1();

        SprIncrementalHeuristicMetric metric = new SprIncrementalHeuristicMetric(new RFClusterIncrementalMetric(), "RFC");
        double dist = metric.getDistance(baseTree, targetTree);

        // Skok SPR z drzewa zbalansowanego do gąsienicy (4 liście, ukorzenione)
        // odpowiada dokładnie 1 elementarnej rotacji NNI, więc ekwiwalent NNI wynosi 1.0
        assertEquals(1.0, dist, 0.0001,
                "Jeśli cel jest oddalony o 1 elementarny ruch SPR (1 NNI), ekwiwalent NNI musi wynosić 1.0");
    }

    @Test
    void testDistanceForIdenticalTreesShouldBeZero() {
        // Drzewo zaimplementowane ze stringa (jak w oryginalnym teście)
        Tree tree = TreeCreator.getTreeFromString("(((1,2),3),(4,5));");
        SprIncrementalHeuristicMetric metric = new SprIncrementalHeuristicMetric(new RFClusterIncrementalMetric(), "RFC");
        double dist = metric.getDistance(tree, tree);

        assertEquals(0.0, dist, DELTA,
                "Dystans dla dwóch identycznych drzew musi wynosić 0.0");
    }

    // --- GENERATOR DANYCH (Data Provider) ---
    static Stream<Arguments> provideTreePairsForTesting() {
        return Stream.of(
                // --- ROZMIAR 5 ---
                Arguments.of(TestTreeFactory.fiveLeavesRootedCaterpillarTree(), TestTreeFactory.fiveLeavesRootedTarget1(), "5 liści: Proste przesunięcie (d=1)"),
                Arguments.of(TestTreeFactory.fiveLeavesRootedCaterpillarTree(), TestTreeFactory.fiveLeavesRootedTree1(), "5 liści: Przesunięcie klastra (d=1)"),
                Arguments.of(TestTreeFactory.fiveLeavesRootedCaterpillarTree(), TestTreeFactory.fiveLeavesRootedTarget2(), "5 liści: Zamiana 2 i 3 (d=1)"),
                Arguments.of(TestTreeFactory.fiveLeavesRootedCaterpillarTree(), TestTreeFactory.fiveLeavesRootedTarget3(), "5 liści: Silna reorganizacja (d=2)"),

                // --- ROZMIAR 6 ---
                Arguments.of(TestTreeFactory.sixLeavesRootedTree1(), TestTreeFactory.sixLeavesRootedTarget1(), "6 liści: Przesunięcie wiśni (5,6) (d=1)"),
                Arguments.of(TestTreeFactory.sixLeavesRootedBalancedTree(), TestTreeFactory.sixLeavesRootedTarget2(), "6 liści: Pułapka kwartetu (d=2)"),
                Arguments.of(TestTreeFactory.sixLeavesRootedTree1(), TestTreeFactory.sixLeavesRootedTarget3(), "6 liści: Zamiana miejscami poddrzew (d=2)"),
                Arguments.of(TestTreeFactory.sixLeavesRootedBalancedTree(), TestTreeFactory.sixLeavesRootedTarget4(), "6 liści: Maksymalna odległość (d=3)"),

                // --- ROZMIARY 10+ ---
                Arguments.of(TestTreeFactory.tenLeavesRootedTree1(), TestTreeFactory.tenLeavesRootedTree2(), "Drzewa średnie (10 liści)"),
                Arguments.of(TestTreeFactory.tenLeavesRootedTree3(), TestTreeFactory.tenLeavesRootedTarget3(), "Drzewa średnie wężowe (10 liści)"),
                Arguments.of(TestTreeFactory.twelveLeavesRootedTree1(), TestTreeFactory.twelveLeavesRootedTarget1(), "Drzewa duże (12 liści)"),
                Arguments.of(TestTreeFactory.fifteenLeavesRootedTree1(), TestTreeFactory.fifteenLeavesRootedTarget1(), "Drzewa duże (15 liści)"),
                Arguments.of(TestTreeFactory.twentyLeavesRootedTree1(), TestTreeFactory.twentyLeavesRootedTarget1(), "Drzewa bardzo duże (20 liści)")
        );
    }
}