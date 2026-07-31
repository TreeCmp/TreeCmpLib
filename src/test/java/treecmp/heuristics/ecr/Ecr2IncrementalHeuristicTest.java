package treecmp.heuristics.ecr;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristic;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

class Ecr2IncrementalHeuristicTest {

    private static final double DELTA = 0.000001;

    @Test
    void testDistanceToSelfIsZero_Unrooted() {
        // Arrange: 2-sECR wymaga min 5 liści
        Tree t = TestTreeFactory.fiveLeavesUnrootedCaterpillarTree();
        Tree tM3 = TestTreeFactory.tenLeavesUnrootedTree1(); // M3 w starych testach wolało N=10

        // Assert: Dystans do samego siebie musi być 0
        assertEquals(0.0, new Ecr2IncrementalHeuristic(new RFIncrementalMetric(), "RF").getDistance(t, t), DELTA);
        assertEquals(0.0, new Ecr2IncrementalHeuristic(new MSIncrementalMetric(), "MS").getDistance(t, t), DELTA);
        assertEquals(0.0, new Ecr2IncrementalHeuristic(new M3IncrementalMetric(), "M3").getDistance(tM3, tM3), DELTA);
    }

    @Test
    void testDistanceToSelfIsZero_Rooted() {
        // Arrange: 2-sECR Rooted
        Tree t = TestTreeFactory.fiveLeavesRootedCaterpillarTree();

        // Assert
        assertEquals(0.0, new Ecr2IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC").getDistance(t, t), DELTA);
        assertEquals(0.0, new Ecr2IncrementalHeuristic(new MCIncrementalMetric(), "MC").getDistance(t, t), DELTA);
        assertEquals(0.0, new Ecr2IncrementalHeuristic(new MPIncrementalMetric(), "MP").getDistance(t, t), DELTA);
    }

    @Test
    void testIncrementalHeuristicResolvesLargerDistance_Unrooted() {
        // Arrange
        Tree t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        // Assert: Dystans ma być większy od zera dla różnych drzew
        assertTrue(new Ecr2IncrementalHeuristic(new RFIncrementalMetric(), "RF").getDistance(t1, t2) > 0.0);
        assertTrue(new Ecr2IncrementalHeuristic(new MSIncrementalMetric(), "MS").getDistance(t1, t2) > 0.0);
        assertTrue(new Ecr2IncrementalHeuristic(new M3IncrementalMetric(), "M3").getDistance(t1, t2) > 0.0);
    }

    @Test
    void testIncrementalHeuristicResolvesLargerDistance_Rooted() {
        // ROZWIĄZANIE: Podobnie jak w Ecr3, Ecr2 modyfikuje drzewa fizycznie (in-place)!
        // Przekazujemy całkowicie niezależne instancje drzew prosto z fabryki dla każdej metryki.

        assertTrue(new Ecr2IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC")
                .getDistance(TestTreeFactory.tenLeavesBinaryRootedTree1(), TestTreeFactory.tenLeavesBinaryRootedTree2()) > 0.0);

        assertTrue(new Ecr2IncrementalHeuristic(new MCIncrementalMetric(), "MC")
                .getDistance(TestTreeFactory.tenLeavesBinaryRootedTree1(), TestTreeFactory.tenLeavesBinaryRootedTree2()) > 0.0);

        assertTrue(new Ecr2IncrementalHeuristic(new MPIncrementalMetric(), "MP")
                .getDistance(TestTreeFactory.tenLeavesBinaryRootedTree1(), TestTreeFactory.tenLeavesBinaryRootedTree2()) > 0.0);
    }
}