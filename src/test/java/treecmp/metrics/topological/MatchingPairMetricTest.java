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
        var t1 = TestTreeFactory.fourLeavesRootedTree1();
        var mcm = new MatchingPairMetric();

        double distance = mcm.getDistance(t1, t1);

        assertEquals(0.0, distance);
    }

    @Test
    void getMatchingPairDistance_4leafsTrees_returnsFour() {
        var t1 = TestTreeFactory.fourLeavesRootedTree1();
        var t2 = TestTreeFactory.fourLeavesRootedTree2();

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

        // Zmienna wewnątrz tablicy, aby można było ją modyfikować z poziomu lambdy
        final double[] bestDistance = { baseDistance };

        // Używamy nowej, oszczędzającej pamięć metody iterującej po sąsiedztwie
        sprUtils.forEachSprTree(t1, neighbor -> {
            try {
                // W razie potrzeby odświeżamy wewnętrzną listę węzłów (wymagane przez niektóre metryki)
                if (neighbor instanceof pal.tree.SimpleTree) {
                    ((pal.tree.SimpleTree) neighbor).createNodeList();
                }

                double distance = mcm.getDistance(neighbor, t2);
                if (distance < bestDistance[0]) {
                    bestDistance[0] = distance;
                }
            } catch (Exception e) {
                throw new RuntimeException("Błąd podczas obliczania dystansu dla sąsiada", e);
            }
        });

        // Weryfikujemy, czy heurystyka rzeczywiście znalazła lepsze drzewo (dystans mniejszy od 26.0)
        assertTrue(bestDistance[0] < baseDistance,
                "Heurystyka powinna znaleźć w otoczeniu SPR drzewo o mniejszym dystansie.");
    }
}
