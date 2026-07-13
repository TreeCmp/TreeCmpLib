package treecmp.tbr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.tbr.TbrClassicHeuristic;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.MatchingTripletMetric;
import treecmp.util.TreeCreator;

import static org.junit.jupiter.api.Assertions.*;

class UtbrHeuristicMatchingTripletMetricTest {

    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void testGetMetricTwoMarsupialsTreesWithSPR_1_distance() throws TreeCmpException {
        Tree baseTree[] = TreeCreator.getTwoMarsupialsSPR_1_distance_trees();

        // Zunifikowana klasa kompozytowa uTBR + Matching Triplet
        Metric mtUtbr = new TbrClassicHeuristic(new MatchingTripletMetric(), false, "MT");
        Double distance = mtUtbr.getDistance(baseTree[0], baseTree[1]);

        // Jeśli uSPR = 1, to uTBR musi to rozwiązać w dokładnie 1 kroku
        assertEquals(1.0, distance, DELTA, "Dla drzew odległych o 1 uSPR, dystans uTBR musi wynosić dokładnie 1");
    }

    @Test
    void testGetMetricTwoMarsupialsTreesWithSPR_2_distance() throws TreeCmpException {
        Tree baseTree[] = TreeCreator.getTwoMarsupialsSPR_2_distance_trees();
        Metric mtUtbr = new TbrClassicHeuristic(new MatchingTripletMetric(), false, "MT");
        Double distance = mtUtbr.getDistance(baseTree[0], baseTree[1]);

        // Odległość uTBR nigdy nie może przekroczyć odległości uSPR
        assertTrue(distance >= 1.0 && distance <= 2.0, "Dystans uTBR musi być w granicach [1, 2]");
    }

    @Test
    void testGetMetricTwoMarsupialsTreesWithSPR_3_distance() throws TreeCmpException {
        Tree baseTree[] = TreeCreator.getTwoMarsupialsSPR_3_distance_trees();
        Metric mtUtbr = new TbrClassicHeuristic(new MatchingTripletMetric(), false, "MT");
        Double distance = mtUtbr.getDistance(baseTree[0], baseTree[1]);

        assertTrue(distance >= 1.0 && distance <= 3.0, "Dystans uTBR musi być <= 3");
    }

    @Test
    void testGetMetricTwoMarsupialsTreesWithSPR_4_distance() throws TreeCmpException {
        Tree baseTree[] = TreeCreator.getTwoMarsupialsSPR_4_distance_trees();
        Metric mtUtbr = new TbrClassicHeuristic(new MatchingTripletMetric(), false, "MT");
        Double distance = mtUtbr.getDistance(baseTree[0], baseTree[1]);

        assertTrue(distance >= 1.0 && distance <= 4.0, "Dystans uTBR musi być <= 4");
    }

    @Test
    void testGetMetricTwoMarsupialsTreesWithSPR_4_distance_withoutLabels() throws TreeCmpException {
        Tree baseTree[] = TreeCreator.getTwoMarsupialsSPR_4_distance_trees_withoutLabels();
        Metric mtUtbr = new TbrClassicHeuristic(new MatchingTripletMetric(), false, "MT");
        Double distance = mtUtbr.getDistance(baseTree[0], baseTree[1]);

        assertTrue(distance >= 1.0 && distance <= 4.0);
    }

    @Test
    void testUtbrDistanceIsAlwaysLessThanOrEqualUsprDistance_MT() throws TreeCmpException {
        // Twardy matematyczny test porównawczy: 1 krok uTBR vs 1 krok uSPR
        Tree baseTree[] = TreeCreator.getTwoMarsupialsSPR_4_distance_trees();
        Tree t1 = baseTree[0];
        Tree t2 = baseTree[1];

        UsprUtils usprUtils = new UsprUtils();
        UTbrUtils utbrUtils = new UTbrUtils();

        Tree[] sprNeighbors = usprUtils.generateNeighbours(t1);
        Tree[] tbrNeighbors = utbrUtils.generateNeighbours(t1);

        Metric mt = new MatchingTripletMetric();

        double bestSprDist = Double.POSITIVE_INFINITY;
        for (Tree t : sprNeighbors) {
            bestSprDist = Math.min(bestSprDist, mt.getDistance(t, t2));
        }

        double bestTbrDist = Double.POSITIVE_INFINITY;
        for (Tree t : tbrNeighbors) {
            bestTbrDist = Math.min(bestTbrDist, mt.getDistance(t, t2));
        }

        assertTrue(bestTbrDist <= bestSprDist,
                "Najlepszy 1-krokowy skrót uTBR (" + bestTbrDist + ") nie może być gorszy niż skrót uSPR (" + bestSprDist + ")!");
    }
}