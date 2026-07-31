package treecmp.heuristics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.metrics.topological.MatchingClusterMetric;
import treecmp.metrics.topological.RFMetric;
import treecmp.metrics.topological.acc.MCIncrementalMetric;
import treecmp.metrics.topological.acc.RFIncrementalMetric;
import treecmp.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Regression Test: Weryfikacja stanu evaluateSingleStep w heurystykach przyrostowych")
public class IncrementalSingleStepRegressionTest {

    private void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @ParameterizedTest(name = "Rooted SPR (MC) - rozmiar drzewa: {0}")
    @ValueSource(ints = {10, 20, 30})
    @DisplayName("evaluateSingleStep dla ukorzenionego SPR (MC) nie może zwracać Infinity i musi być równe Classic 1-Step")
    void testRootedSprSingleStepNotInfiniteAndEqualsClassic(int treeSize) throws TreeCmpException {
        // 1. Przygotowanie danych
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(treeSize, 12345L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(treeSize, 67890L);
        Tree t1ForIncr = TestTreeFactory.randomRootedBinaryTree(treeSize, 12345L);

        assignNumbers(t1);
        assignNumbers(t2);
        assignNumbers(t1ForIncr);

        SprIncrementalHeuristicMetric incrHeuristic = new SprIncrementalHeuristicMetric(
                new MCIncrementalMetric(), "MC"
        );
        MatchingClusterMetric classicMetric = new MatchingClusterMetric();
        SprUtils sprUtils = new SprUtils();

        // 2. Obliczenie wartości przyrostowej (z klasy bazowej IncrementalHeuristicBaseMetric)
        double incrementalDist = incrHeuristic.evaluateSingleStep(t1ForIncr, t2);

        // 3. Asercja 1: Ochrona przed błędem "Infinity / NaN" (Field Shadowing Regression)
        assertFalse(Double.isInfinite(incrementalDist),
                "evaluateSingleStep zwróciło Infinity! Sprawdź synchronizację bestDist w klasie bazowej.");
        assertFalse(Double.isNaN(incrementalDist),
                "evaluateSingleStep zwróciło NaN!");
        assertTrue(incrementalDist >= 0.0,
                "Dystans musi być wartością nieujemną!");

        // 4. Obliczenie klasycznej wartości 1-Step (złoty standard)
        final double[] bestClassicDist = {Double.POSITIVE_INFINITY};
        sprUtils.forEachSprTree(t1, neighbor -> {
            if (neighbor instanceof SimpleTree) {
                ((SimpleTree) neighbor).createNodeList();
            }
            double d = classicMetric.getDistance(neighbor, t2);
            if (d < bestClassicDist[0]) {
                bestClassicDist[0] = d;
            }
        });

        // 5. Asercja 2: Pełna zgodność wyniku akcelerowanego z klasycznym
        assertEquals(bestClassicDist[0], incrementalDist, 1e-9,
                "Wynik evaluateSingleStep różni się od klasycznego przeszukania 1-Step!");
    }

    @ParameterizedTest(name = "Unrooted uSPR (RF) - rozmiar drzewa: {0}")
    @ValueSource(ints = {10, 20, 30})
    @DisplayName("evaluateSingleStep dla nieukorzenionego uSPR (RF) nie może zwracać Infinity i musi być równe Classic 1-Step")
    void testUnrootedUsprSingleStepNotInfiniteAndEqualsClassic(int treeSize) throws TreeCmpException {
        // 1. Przygotowanie danych
        Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
        Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 67890L);
        Tree t1ForIncr = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);

        assignNumbers(t1);
        assignNumbers(t2);
        assignNumbers(t1ForIncr);

        UsprIncrementalHeuristicMetric incrHeuristic = new UsprIncrementalHeuristicMetric(
                new RFIncrementalMetric(), "RF"
        );
        RFMetric classicMetric = new RFMetric();
        UsprUtils usprUtils = new UsprUtils();

        // 2. Obliczenie wartości przyrostowej
        double incrementalDist = incrHeuristic.evaluateSingleStep(t1ForIncr, t2);

        // 3. Asercja 1: Ochrona przed błędem "Infinity"
        assertFalse(Double.isInfinite(incrementalDist),
                "evaluateSingleStep zwróciło Infinity dla uSPR!");
        assertFalse(Double.isNaN(incrementalDist),
                "evaluateSingleStep zwróciło NaN dla uSPR!");

        // 4. Obliczenie klasycznej wartości 1-Step
        final double[] bestClassicDist = {Double.POSITIVE_INFINITY};
        usprUtils.forEachUsprTree(t1, neighbor -> {
            if (neighbor instanceof SimpleTree) {
                ((SimpleTree) neighbor).createNodeList();
            }
            double d = classicMetric.getDistance(neighbor, t2);
            if (d < bestClassicDist[0]) {
                bestClassicDist[0] = d;
            }
        });

        // 5. Asercja 2: Zgodność wyników
        assertEquals(bestClassicDist[0], incrementalDist, 1e-9,
                "Wynik evaluateSingleStep (uSPR) różni się od klasycznego!");
    }

    @Test
    @DisplayName("Szybki test regresyjny: evaluateSingleStep nie może modyfikować drzewa wejściowego")
    void testEvaluateSingleStepDoesNotMutateInputTree() {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(15, 999L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(15, 888L);
        assignNumbers(t1);
        assignNumbers(t2);

        String originalNewick = t1.toString();

        SprIncrementalHeuristicMetric incrHeuristic = new SprIncrementalHeuristicMetric(
                new MCIncrementalMetric(), "MC"
        );

        incrHeuristic.evaluateSingleStep(t1, t2);

        assertEquals(originalNewick, t1.toString(),
                "evaluateSingleStep nie powinno trwale modyfikować przekazanego drzewa wejściowego!");
    }
}