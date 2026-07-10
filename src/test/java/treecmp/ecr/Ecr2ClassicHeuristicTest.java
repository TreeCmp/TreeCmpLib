package treecmp.ecr;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.ecr.Ecr2ClassicHeuristic;
import treecmp.metrics.topological.*;
import treecmp.util.TestTreeFactory;

class Ecr2ClassicHeuristicTest {

    private static final double DELTA = 0.000001;

    @Test
    void testDistanceToSelfIsZero_Unrooted() {
        // Arrange: 2-sECR wymaga min 5 liści
        Tree t = TestTreeFactory.fiveLeavesUnrootedCaterpillarTree();
        Tree tM3 = TestTreeFactory.tenLeavesUnrootedTree1(); // M3 preferuje większe drzewa

        // Assert: Używamy uniwersalnego Ecr2ClassicHeuristic z odpowiednimi metrykami
        assertEquals(0.0, new Ecr2ClassicHeuristic(new RFMetric(), false, "RF").getDistance(t, t), DELTA);
        assertEquals(0.0, new Ecr2ClassicHeuristic(new MatchingSplitMetric(), false, "MS").getDistance(t, t), DELTA);
        assertEquals(0.0, new Ecr2ClassicHeuristic(new MatchingTripletMetric(), false, "M3").getDistance(tM3, tM3), DELTA);
    }

    @Test
    void testDistanceToSelfIsZero_Rooted() {
        // Arrange
        Tree t = TestTreeFactory.fiveLeavesRootedCaterpillarTree();

        // Assert
        assertEquals(0.0, new Ecr2ClassicHeuristic(new RFClusterMetric(), true, "RFC").getDistance(t, t), DELTA);
        assertEquals(0.0, new Ecr2ClassicHeuristic(new MatchingClusterMetric(), true, "MC").getDistance(t, t), DELTA);
        assertEquals(0.0, new Ecr2ClassicHeuristic(new MatchingPairMetric(), true, "MP").getDistance(t, t), DELTA);
    }

    @Test
    void testClassicHeuristicResolvesLargerDistance_Unrooted() {
        // Arrange
        Tree t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        // Assert
        assertTrue(new Ecr2ClassicHeuristic(new RFMetric(), false, "RF").getDistance(t1, t2) > 0.0);
        assertTrue(new Ecr2ClassicHeuristic(new MatchingSplitMetric(), false, "MS").getDistance(t1, t2) > 0.0);
        assertTrue(new Ecr2ClassicHeuristic(new MatchingTripletMetric(), false, "M3").getDistance(t1, t2) > 0.0);
    }

    @Test
    void testClassicHeuristicResolvesLargerDistance_Rooted() {
        // Arrange
        Tree t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        // Assert
        assertTrue(new Ecr2ClassicHeuristic(new RFClusterMetric(), true, "RFC").getDistance(t1, t2) > 0.0);
        assertTrue(new Ecr2ClassicHeuristic(new MatchingClusterMetric(), true, "MC").getDistance(t1, t2) > 0.0);
        assertTrue(new Ecr2ClassicHeuristic(new MatchingPairMetric(), true, "MP").getDistance(t1, t2) > 0.0);
    }
}