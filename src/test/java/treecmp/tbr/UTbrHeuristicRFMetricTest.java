package treecmp.tbr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.spr.USprUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.heuristics.tbr.UTbrHeuristicRFMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFMetric;
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

import static org.junit.jupiter.api.Assertions.*;

class UTbrHeuristicRFMetricTest {

    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {}

    @AfterEach
    void tearDown() {}

    @Test
    void testGetMetricTwoMarsupialsTreesWithSPR_1_distance() throws TreeCmpException {
        Tree baseTree[] = TreeCreator.getTwoMarsupialsSPR_1_distance_trees();
        UTbrHeuristicRFMetric utbr = new UTbrHeuristicRFMetric();

        Double distance = utbr.getDistance(baseTree[0], baseTree[1]);
        assertEquals(1.0, distance, DELTA, "uTBR powinno wynieść dokładnie 1.0 dla drzew oddalonych o uSPR=1");
    }

    @Test
    void testUtbrNeighborhoodContainsUsprNeighborhood_Marsupials() throws TreeCmpException {
        // Prawdziwy test inwariantności: Otoczenie uTBR jest NADZBIOREM otoczenia uSPR.
        // Dlatego w pojedynczym kroku TBR zawsze znajdzie ruch dający RF mniejsze lub równe najlepszemu z SPR.
        Tree[] baseTree = TreeCreator.getTwoMarsupialsSPR_4_distance_trees();
        Tree t1 = baseTree[0];
        Tree t2 = baseTree[1];

        USprUtils usprUtils = new USprUtils();
        UTbrUtils utbrUtils = new UTbrUtils();

        Tree[] sprNeighbors = usprUtils.generateNeighbours(t1);
        Tree[] tbrNeighbors = utbrUtils.generateNeighbours(t1);

        Metric rf = new RFMetric();

        double bestSprDist = Double.POSITIVE_INFINITY;
        for (Tree t : sprNeighbors) {
            bestSprDist = Math.min(bestSprDist, rf.getDistance(t, t2));
        }

        double bestTbrDist = Double.POSITIVE_INFINITY;
        for (Tree t : tbrNeighbors) {
            bestTbrDist = Math.min(bestTbrDist, rf.getDistance(t, t2));
        }

        assertTrue(bestTbrDist <= bestSprDist,
                "Najlepszy 1-krokowy skrót uTBR (" + bestTbrDist + ") musi być <= najlepszemu skrótowi uSPR (" + bestSprDist + ")");
    }

    @Test
    void testUnrootedTbrCanResolveComplexMoves() {
        // Drzewo 1: 6 liści, zrównoważone (((1,2),(3,4)),5,6)
        Tree t1 = TestTreeFactory.sixLeavesUnrootedBalancedTree();

        // Drzewo 2: W uTBR potrafimy odciąć i odwrócić wnętrze! (((1,(3,4)),2),5,6)
        Tree t2 = TestTreeFactory.sixLeavesUnrootedTargetTree();

        UTbrHeuristicRFMetric utbr = new UTbrHeuristicRFMetric();
        double dist = utbr.getDistance(t1, t2);

        assertTrue(dist <= 1.0, "Ten ruch powinien zamknąć się w 1 operacji uTBR");
    }
}