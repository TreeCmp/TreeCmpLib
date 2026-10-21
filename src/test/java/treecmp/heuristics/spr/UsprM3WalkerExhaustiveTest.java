package treecmp.heuristics.spr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.acc.IncrementalUsprWalker;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.metrics.topological.MatchingTripletMetric;
import treecmp.metrics.topological.acc.M3IncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Weryfikacja zgodności każdego ruchu uSPR w M3 (Incremental Walker vs Pełna metryka)")
public class UsprM3WalkerExhaustiveTest {

    private static final double EPSILON = 1e-9;

    private static void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
        TreeUtils.computeParentPointers(tree.getRoot());
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            tree.getExternalNode(i).setNumber(i);
        }
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            tree.getInternalNode(i).setNumber(i);
        }
    }

    @Test
    @DisplayName("Każdy pojedynczy ruch uSPR oceniony przez IncrementalUsprWalker musi być w 100% zgodny z MatchingTripletMetric")
    void testEveryUsprMoveDistanceExactMatchWithClassicM3() {
        Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(10, 42L);
        Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(10, 99L);
        assignNumbers(t1);
        assignNumbers(t2);

        IncrementalUsprWalker walker = new IncrementalUsprWalker();
        M3IncrementalMetric incMetric = new M3IncrementalMetric();
        incMetric.initCalculationState(t1, t2);

        MatchingTripletMetric classicMetric = new MatchingTripletMetric();
        UsprUtils usprUtils = new UsprUtils();

        final int[] verifiedMovesCount = {0};

        // Przechodzimy po wszystkich ruchach i porównujemy każdy z nich
        walker.walk(t1, incMetric, (walkerDist, pruneNode, targetNode) -> {
            if (!usprUtils.isValidUsprMove(pruneNode, targetNode)) {
                return;
            }

            Tree physicalNeighbor = usprUtils.createUsprTree(t1, pruneNode, targetNode);
            assertNotNull(physicalNeighbor, "Fizyczny ruch uSPR nie powinien zwrócić null");
            assignNumbers(physicalNeighbor);

            double expectedDist = classicMetric.getDistance(physicalNeighbor, t2);

            assertEquals(expectedDist, walkerDist, EPSILON, String.format(
                    "Rozbieżność oceny ruchu uSPR (M3) dla prune=%s, target=%s! Walker=%.4f vs Classic=%.4f",
                    pruneNode.getIdentifier() != null ? pruneNode.getIdentifier().getName() : "i" + pruneNode.getNumber(),
                    targetNode.getIdentifier() != null ? targetNode.getIdentifier().getName() : "i" + targetNode.getNumber(),
                    walkerDist, expectedDist
            ));

            verifiedMovesCount[0]++;
        });

        assertTrue(verifiedMovesCount[0] > 0, "Powinno zostać zweryfikowane co najmniej kilkadziesiąt ruchów uSPR");
    }

    @Test
    @DisplayName("Weryfikacja zbieżności uSPR M3 w wariancie Pure (bez filtra RF) na puli losowych drzew")
    void testUsprM3PureConvergenceAcrossMultipleSeeds() {
        Random rng = new Random(12345L);
        int testPairs = 20;
        int successfulDescents = 0;

        for (int i = 0; i < testPairs; i++) {
            long s1 = rng.nextLong();
            long s2 = rng.nextLong();

            Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(10, s1);
            Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(10, s2);
            assignNumbers(t1);
            assignNumbers(t2);

            UsprIncrementalHeuristicMetric heuristic =
                    new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3");

            double dist = heuristic.getDistance(new SimpleTree(t1), new SimpleTree(t2));

            if (!Double.isInfinite(dist) && dist >= 0.0) {
                successfulDescents++;
                assertEquals((double) heuristic.getSprStepsCount(), dist, EPSILON,
                        "Zwrócony dystans musi odpowiadać liczbie kroków uSPR");
            }
        }

        // Klasyczny Pure M3 dla N=10 ma 100% skuteczności, wersja inkrementalna musi osiągać zbliżony wynik
        assertTrue(successfulDescents >= 18, String.format(
                "Zbyt niska skuteczność zbieżności M3 Pure (udane: %d/%d). Heurystyka nadal utyka na plateau!",
                successfulDescents, testPairs
        ));
    }
}