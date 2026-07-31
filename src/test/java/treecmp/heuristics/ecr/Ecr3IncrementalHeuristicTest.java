package treecmp.heuristics.ecr;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristic;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

class Ecr3IncrementalHeuristicTest {

    private static final double DELTA = 0.000001;

    @Test
    void testDistanceToSelfIsZero_Unrooted() {
        // Tutaj przekazywanie tej samej instancji jest bezpieczne, bo dystans to 0
        // i algorytm nie wykonuje żadnych ruchów (nie mutuje drzewa).
        Tree t = TestTreeFactory.sixLeavesUnrootedCaterpillarTree();
        Tree tM3 = TestTreeFactory.tenLeavesUnrootedTree1();

        //assertEquals(0.0, new Ecr3IncrementalHeuristic(new RFIncrementalMetric(), "RF").getDistance(t, t), DELTA);
        //assertEquals(0.0, new Ecr3IncrementalHeuristic(new MSIncrementalMetric(), "MS").getDistance(t, t), DELTA);
        assertEquals(0.0, new Ecr3IncrementalHeuristic(new M3IncrementalMetric(), "M3").getDistance(tM3, tM3), DELTA);
    }

    @Test
    void testDistanceToSelfIsZero_Rooted() {
        Tree t = TestTreeFactory.sixLeavesRootedCaterpillarTree();

        assertEquals(0.0, new Ecr3IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC").getDistance(t, t), DELTA);
        assertEquals(0.0, new Ecr3IncrementalHeuristic(new MCIncrementalMetric(), "MC").getDistance(t, t), DELTA);
        assertEquals(0.0, new Ecr3IncrementalHeuristic(new MPIncrementalMetric(), "MP").getDistance(t, t), DELTA);
    }

    @Test
    void testIncrementalHeuristicResolvesLargerDistance_Unrooted() {
        // ROZWIĄZANIE: Każda asercja dostaje własne, całkowicie niezależne instancje drzew z fabryki!
        assertTrue(new Ecr3IncrementalHeuristic(new RFIncrementalMetric(), "RF")
                .getDistance(TestTreeFactory.tenLeavesBinaryUnrootedTree1(), TestTreeFactory.tenLeavesBinaryUnrootedTree2()) > 0.0);

        assertTrue(new Ecr3IncrementalHeuristic(new MSIncrementalMetric(), "MS")
                .getDistance(TestTreeFactory.tenLeavesBinaryUnrootedTree1(), TestTreeFactory.tenLeavesBinaryUnrootedTree2()) > 0.0);

        assertTrue(new Ecr3IncrementalHeuristic(new M3IncrementalMetric(), "M3")
                .getDistance(TestTreeFactory.tenLeavesBinaryUnrootedTree1(), TestTreeFactory.tenLeavesBinaryUnrootedTree2()) > 0.0);
    }

    @Test
    void testIncrementalHeuristicResolvesLargerDistance_Rooted() {
        // ROZWIĄZANIE: Każda asercja dostaje własne, całkowicie niezależne instancje drzew z fabryki!
        // Uwaga na poprawną nazwę klasy kalkulatora RFC: RFClusterIncrementalMetric
        assertTrue(new Ecr3IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC")
                .getDistance(TestTreeFactory.tenLeavesBinaryRootedTree1(), TestTreeFactory.tenLeavesBinaryRootedTree2()) > 0.0);

        assertTrue(new Ecr3IncrementalHeuristic(new MCIncrementalMetric(), "MC")
                .getDistance(TestTreeFactory.tenLeavesBinaryRootedTree1(), TestTreeFactory.tenLeavesBinaryRootedTree2()) > 0.0);

        assertTrue(new Ecr3IncrementalHeuristic(new MPIncrementalMetric(), "MP")
                .getDistance(TestTreeFactory.tenLeavesBinaryRootedTree1(), TestTreeFactory.tenLeavesBinaryRootedTree2()) > 0.0);
    }
}