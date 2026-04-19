package treecmp.tbr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Tree;
import treecmp.heuristics.spr.SprHeuristicRFCMetric;
import treecmp.heuristics.tbr.TbrHeuristicRFCMetric;
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

import static org.junit.jupiter.api.Assertions.*;

class TbrHeuristicRFCMetricTest {

    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {}

    @AfterEach
    void tearDown() {}

    @Test
    void testIdenticalTreesShouldHaveZeroDistance() {
        Tree tree = TestTreeFactory.fiveLeavesRootedTree1();
        TbrHeuristicRFCMetric tbrMetric = new TbrHeuristicRFCMetric();

        assertEquals(0.0, tbrMetric.getDistance(tree, tree), DELTA, "Dystans TBR dla identycznych drzew musi wynosić 0");
    }

    @Test
    void testRootedTbrCanReverseBackboneInOneMove_SprNeedsTwo() {
        // Baza: Kręgosłup opadający w lewo (((1,2),3),4)
        Tree t1 = TestTreeFactory.sixLeavesRootedCaterpillarTree();

        // Cel: Kręgosłup opadający w prawo (1,(2,(3,4)))
        Tree t2 = TestTreeFactory.sixLeavesRootedTargetTree1();

        SprHeuristicRFCMetric sprMetric = new SprHeuristicRFCMetric();
        TbrHeuristicRFCMetric tbrMetric = new TbrHeuristicRFCMetric();

        double sprDist = sprMetric.getDistance(t1, t2);
        double tbrDist = tbrMetric.getDistance(t1, t2);

        assertTrue(sprDist > 1.0, "rSPR powinno wymagać więcej niż 1 kroku (oczekiwane 2.0)");
        assertEquals(1.0, tbrDist, DELTA, "rTBR powinno wymagać tylko 1 kroku dzięki przekorzenieniu (odwróceniu krawędzi)!");
    }

    @Test
    void testTbrDistanceIsAlwaysLessThanOrEqualSprDistance() {
        Tree t1 = TestTreeFactory.tenLeavesRootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesRootedTree2();

        SprHeuristicRFCMetric sprMetric = new SprHeuristicRFCMetric();
        TbrHeuristicRFCMetric tbrMetric = new TbrHeuristicRFCMetric();

        double sprDist = sprMetric.getDistance(t1, t2);
        double tbrDist = tbrMetric.getDistance(t1, t2);

        assertTrue(tbrDist <= sprDist, "Dystans rTBR (" + tbrDist + ") nie może być większy niż rSPR (" + sprDist + ")");
    }
}