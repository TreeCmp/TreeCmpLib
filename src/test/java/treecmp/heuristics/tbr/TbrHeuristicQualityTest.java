package treecmp.heuristics.tbr;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TbrHeuristicQualityTest {

    private static final double DELTA = 1e-6;

    static Stream<Arguments> provideTbrHeuristics() {
        return Stream.of(
                Arguments.of(new TbrIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC"), true),
                Arguments.of(new TbrIncrementalHeuristic(new MCIncrementalMetric(), "MC"), true),
                Arguments.of(new TbrIncrementalHeuristic(new MPIncrementalMetric(), "MP"), true),
                Arguments.of(new UtbrIncrementalHeuristic(new RFIncrementalMetric(), "RF"), false),
                Arguments.of(new UtbrIncrementalHeuristic(new MSIncrementalMetric(), "MS"), false),
                Arguments.of(new UtbrIncrementalHeuristic(new M3IncrementalMetric(), "M3"), false)
        );
    }

    private static void assignNumbers(Tree t) {
        if (t instanceof SimpleTree) {
            ((SimpleTree) t).createNodeList();
        }
    }

    @ParameterizedTest(name = "[{index}] Test odległości do samego siebie: {0}")
    @MethodSource("provideTbrHeuristics")
    void testDistanceToSelfIsZero(IncrementalHeuristicBaseMetric heuristic, boolean isRooted) {
        Tree t1 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(8, 42L)
                : TestTreeFactory.randomUnrootedBinaryTree(8, 42L);
        assignNumbers(t1);

        double distance = heuristic.getDistance(new SimpleTree(t1), t1);

        assertEquals(0.0, distance, DELTA,
                "Heurystyka TBR (" + heuristic.getName() + ") dla identycznych drzew musi zwrócić 0 kroków!");
    }

    @ParameterizedTest(name = "[{index}] Test zbieżności na małych drzewach (6 liści): {0}")
    @MethodSource("provideTbrHeuristics")
    void testDistanceBetweenDifferentSmallTrees(IncrementalHeuristicBaseMetric heuristic, boolean isRooted) {
        Tree t1 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(6, 101L)
                : TestTreeFactory.randomUnrootedBinaryTree(6, 101L);
        Tree t2 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(6, 202L)
                : TestTreeFactory.randomUnrootedBinaryTree(6, 202L);
        assignNumbers(t1);
        assignNumbers(t2);

        double steps = heuristic.getDistance(new SimpleTree(t1), t2);

        assertTrue(steps > 0.0,
                "Heurystyka TBR (" + heuristic.getName() + ") musi znaleźć różnicę i zwrócić dystans > 0.");
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

        if (steps == Double.POSITIVE_INFINITY) {
            System.out.println(heuristic.getName() + ": Algorytm TBR wpadł w minimum lokalne dla 10 liści.");
        }
    }
}