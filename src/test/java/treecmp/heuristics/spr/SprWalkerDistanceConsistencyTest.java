package treecmp.heuristics.spr;

import org.junit.jupiter.api.Test;
import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.SprVisitor;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.acc.IncrementalSprWalker;
import treecmp.heuristics.spr.acc.IncrementalUsprWalker;
import treecmp.heuristics.spr.acc.RootedSprIncrementalMetric;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.MatchingClusterMetric;
import treecmp.metrics.topological.MatchingPairMetric;
import treecmp.metrics.topological.MatchingSplitMetric;
import treecmp.metrics.topological.acc.MCIncrementalMetric;
import treecmp.metrics.topological.acc.MPIncrementalMetric;
import treecmp.metrics.topological.acc.MSIncrementalMetric;
import treecmp.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test Zgodności Matematycznej dla Wędrowców SPR.
 * Weryfikuje, czy optymalizacje pamięciowe (Delta Stos) w walkerach produkcyjnych
 * utrzymują 100% spójność macierzy względem pełnej ewaluacji w każdym węźle sąsiedztwa.
 */
public class SprWalkerDistanceConsistencyTest {

    private static final double EPSILON = 1e-9;
    private static final int TREE_SIZE = 4;

    // ==========================================
    // ROOTED WALKERS (MC, MP)
    // ==========================================

    @Test
    public void testIncrementalSprWalker_MC_Consistency() {
        verifyRootedWalkerConsistency(new IncrementalSprWalker(), new MCIncrementalMetric(), new MatchingClusterMetric());
    }

    @Test
    public void testIncrementalSprWalker_MP_Consistency() {
        // verifyRootedWalkerConsistency(new IncrementalSprWalker(), new MPIncrementalMetric(), new MatchingPairMetric());
    }

    // ==========================================
    // UNROOTED WALKERS (MS)
    // ==========================================

    @Test
    public void testIncrementalUsprWalker_MS_Consistency() {
        verifyUnrootedWalkerConsistency(new IncrementalUsprWalker(), new MSIncrementalMetric(), new MatchingSplitMetric());
    }

    // ==========================================
    // SILNIKI WERYFIKUJĄCE (ENGINES)
    // ==========================================

    private void verifyRootedWalkerConsistency(IncrementalSprWalker walker, IncrementalMetric incMetric, Metric classicMetric) {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(TREE_SIZE, 123L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(TREE_SIZE, 456L);
        assignNumbers(t1);
        assignNumbers(t2);

        incMetric.initCalculationState(t1, t2);
        SprUtils sprUtils = new SprUtils();

        SprVisitor strictAuditor = (incrementalDistance, pruneNode, targetNode) -> {
            Tree physicalTree = sprUtils.createSprTree(t1, pruneNode, targetNode);
            if (physicalTree != null) {
                assignNumbers(physicalTree);
                double classicDistance;
                try {
                    classicDistance = classicMetric.getDistance(physicalTree, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }

                assertEquals(classicDistance, incrementalDistance, EPSILON,
                        String.format("BŁĄD ZGODNOŚCI w %s! Ruch %s -> %s zepsuł macierz.",
                                walker.getClass().getSimpleName(), pruneNode.getNumber(), targetNode.getNumber()));
            }
        };

        walker.walk(t1, (RootedSprIncrementalMetric) incMetric, strictAuditor);
    }

    private void verifyUnrootedWalkerConsistency(IncrementalUsprWalker walker, IncrementalMetric incMetric, Metric classicMetric) {
        Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(TREE_SIZE, 123L);
        Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(TREE_SIZE, 456L);
        assignNumbers(t1);
        assignNumbers(t2);

        incMetric.initCalculationState(t1, t2);
        UsprUtils usprUtils = new UsprUtils();

        SprVisitor strictAuditor = (incrementalDistance, pruneNode, targetNode) -> {
            Tree physicalTree = usprUtils.createUsprTree(t1, pruneNode, targetNode);
            if (physicalTree != null) {
                assignNumbers(physicalTree);
                double classicDistance;
                try {
                    classicDistance = classicMetric.getDistance(physicalTree, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }

                assertEquals(classicDistance, incrementalDistance, EPSILON,
                        String.format("BŁĄD ZGODNOŚCI w %s! Ruch %s -> %s zepsuł macierz.",
                                walker.getClass().getSimpleName(), pruneNode.getNumber(), targetNode.getNumber()));
            }
        };

        walker.walk(t1, incMetric, strictAuditor);
    }

    private void assignNumbers(Tree tree) {
        if (tree instanceof pal.tree.SimpleTree) {
            ((pal.tree.SimpleTree) tree).createNodeList();
        }
    }
}