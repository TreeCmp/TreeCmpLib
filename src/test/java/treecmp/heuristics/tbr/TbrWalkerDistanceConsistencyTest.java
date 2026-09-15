package treecmp.heuristics.tbr;

import org.junit.jupiter.api.Test;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.heuristics.tbr.acc.TbrNeighborhoodWalker;
import treecmp.heuristics.tbr.acc.UtbrNeighborhoodWalker;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test Spójności Dystansu dla Wędrowców TBR i uTBR.
 * Weryfikuje, czy wyceny inkrementalne dla każdego odwiedzanego ruchu w otoczeniu TBR
 * zachowują 100% zgodności z fizyczną topologią orakularną (TbrUtils / UTbrUtils).
 */
public class TbrWalkerDistanceConsistencyTest {

    private static final double EPSILON = 1e-9;
    private static final int TREE_SIZE = 6; // N=6 generuje wystarczającą głębokość przekorzenień i wpięć

    // ==========================================
    // ROOTED WALKERS (RFC, MC, MP)
    // ==========================================

    @Test
    public void testTbrWalker_RFC_Consistency() {
        verifyRootedTbrWalkerConsistency(new RFClusterIncrementalMetric(), new RFClusterMetric());
    }

    @Test
    public void testTbrWalker_MC_Consistency() {
        verifyRootedTbrWalkerConsistency(new MCIncrementalMetric(), new MatchingClusterMetric());
    }

    @Test
    public void testTbrWalker_MP_Consistency() {
        verifyRootedTbrWalkerConsistency(new MPIncrementalMetric(), new MatchingPairMetric());
    }

    // ==========================================
    // UNROOTED WALKERS (RF, MS, M3)
    // ==========================================

    @Test
    public void testUtbrWalker_RF_Consistency() {
        verifyUnrootedUtbrWalkerConsistency(new RFIncrementalMetric(), new RFMetric());
    }

    @Test
    public void testUtbrWalker_MS_Consistency() {
        verifyUnrootedUtbrWalkerConsistency(new MSIncrementalMetric(), new MatchingSplitMetric());
    }

    @Test
    public void testUtbrWalker_M3_Consistency() {
        verifyUnrootedUtbrWalkerConsistency(new M3IncrementalMetric(), new MatchingTripletMetric());
    }

    // ==========================================
    // SILNIKI WERYFIKUJĄCE
    // ==========================================

    private void verifyRootedTbrWalkerConsistency(IncrementalMetric incMetric, Metric classicMetric) {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(TREE_SIZE, 123L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(TREE_SIZE, 456L);
        assignNumbers(t1);
        assignNumbers(t2);

        incMetric.initCalculationState(t1, t2);
        TbrUtils tbrUtils = new TbrUtils();
        TbrNeighborhoodWalker walker = new TbrNeighborhoodWalker();

        walker.walk(t1, incMetric, (actualDist, pruneNode, rerootNode, targetNode) -> {
            Tree physicalTree = tbrUtils.createTbrTree(t1, pruneNode, rerootNode, targetNode);
            if (physicalTree != null) {
                assignNumbers(physicalTree);
                double expectedDist;
                try {
                    expectedDist = classicMetric.getDistance(physicalTree, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }

                assertEquals(expectedDist, actualDist, EPSILON,
                        String.format("BŁĄD ZGODNOŚCI rTBR (%s)! Ruch P:%s, R:%s, T:%s dał błędny dystans.",
                                classicMetric.getCommandLineName(),
                                pruneNode.getNumber(), rerootNode.getNumber(), targetNode.getNumber()));
            }
        });
    }

    private void verifyUnrootedUtbrWalkerConsistency(IncrementalMetric incMetric, Metric classicMetric) {
        Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(TREE_SIZE, 123L);
        Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(TREE_SIZE, 456L);
        assignNumbers(t1);
        assignNumbers(t2);

        incMetric.initCalculationState(t1, t2);
        UTbrUtils utbrUtils = new UTbrUtils();
        UtbrNeighborhoodWalker walker = new UtbrNeighborhoodWalker();

        walker.walk(t1, incMetric, (actualDist, pruneNode, rerootNode, targetNode) -> {
            Tree physicalTree = utbrUtils.createUtbrTree(t1, pruneNode, rerootNode, targetNode);
            if (physicalTree != null) {
                assignNumbers(physicalTree);
                double expectedDist;
                try {
                    expectedDist = classicMetric.getDistance(physicalTree, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }

                assertEquals(expectedDist, actualDist, EPSILON,
                        String.format("BŁĄD ZGODNOŚCI uTBR (%s)! Ruch P:%s, R:%s, T:%s dał błędny dystans.",
                                classicMetric.getCommandLineName(),
                                pruneNode.getNumber(), rerootNode.getNumber(), targetNode.getNumber()));
            }
        });
    }

    private void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }
}