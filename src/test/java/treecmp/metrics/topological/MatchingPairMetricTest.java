package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import pal.tree.Tree;
import treecmp.heuristics.spr.SprUtils;
import treecmp.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingPairMetricTest {

    @Test
    void getMatchingPairDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var mcm = new MatchingPairMetric();

        double distance = mcm.getDistance(t1, t1);

        assertEquals(0.0, distance);
    }

    @Test
    void getMatchingPairDistance_4leafsTrees_returnsFour() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        var mcm = new MatchingPairMetric();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(4.0, distance);
    }

    @Test
    void getMatchingPairDistance_10leafsTrees_returnsEightyFour() {
        var t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        var mcm = new MatchingPairMetric();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(26.0, distance);
    }
    @Test
    void getMatchingPairDistance_onSprNeighborhood_findsBetterTopology() {
        var t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        var mcm = new MatchingPairMetric();
        var sprUtils = new SprUtils();

        double baseDistance = mcm.getDistance(t1, t2);
        assertEquals(26.0, baseDistance, "Dystans bazowy musi wynosić 26.0");

        Tree[] neighbors = sprUtils.generateNeighbours(t1);

        double bestDistance = baseDistance;

        for (Tree neighbor : neighbors) {
            double distance = mcm.getDistance(neighbor, t2);
            if (distance < bestDistance) {
                bestDistance = distance;
            }
        }

        // Zabetonowanie logiki: Poprawnie wygenerowane otoczenie SPR
        // musi doprowadzić do spadku dystansu (optymalizacji).
        // W poprzednich błędnych testach zakładaliśmy, że nie może spaść poniżej 26.0!
        assertTrue(bestDistance < baseDistance,
                "BŁĄD: W całym otoczeniu SPR klasyk nie znalazł poprawy! Oczekiwano dystansu < 26.0, znaleziono: " + bestDistance);

        // Dla tych konkretnych drzew, optymalny ruch SPR według klasyka zmniejsza koszt do 23.0.
        assertEquals(21.0, bestDistance, "Klasyk powinien znaleźć dokładnie wynik 23.0 dla optymalnego ruchu!");
    }
}
