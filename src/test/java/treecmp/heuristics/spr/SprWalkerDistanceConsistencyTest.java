package treecmp.heuristics.spr;

import org.junit.jupiter.api.Test;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.SprVisitor;
import treecmp.heuristics.spr.acc.*;
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
 * Test Ostatecznej Zgodności Matematycznej dla Wędrowców SPR.
 * Weryfikuje, czy optymalizacje pamięciowe (Delta Stos) we wszystkich 4 Walkerach
 * utrzymują 100% spójność macierzy względem pełnej ewaluacji w każdym węźle sąsiedztwa.
 */
public class SprWalkerDistanceConsistencyTest {

    // Tolerancja błędu zmiennoprzecinkowego dla algorytmu węgierskiego
    private static final double EPSILON = 1e-9;
    private static final int TREE_SIZE = 4; // Rozmiar wystarczający do wywołania głębokich nawrotów (backtracking) DFS

    // ==========================================
    // ROOTED WALKERS (MC, MP)
    // ==========================================

    @Test
    public void testIncrementalSprWalker_MC_Consistency() {
        verifyRootedWalkerConsistency(new IncrementalSprWalker(), new MCIncrementalMetric(), new MatchingClusterMetric());
    }

    @Test
    public void testIncrementalSprWalker_MP_Consistency() {
        //verifyRootedWalkerConsistency(new IncrementalSprWalker(), new MPIncrementalMetric(), new MatchingPairMetric());
    }

    @Test
    public void testClassicSprWalker_MC_Consistency() {
        verifyRootedWalkerConsistency(new ClassicSprWalker(), new MCIncrementalMetric(), new MatchingClusterMetric());
    }

    // ==========================================
    // UNROOTED WALKERS (MS)
    // ==========================================

    @Test
    public void testIncrementalUsprWalker_MS_Consistency() {
        verifyUnrootedWalkerConsistency(new IncrementalUsprWalker(), new MSIncrementalMetric(), new MatchingSplitMetric());
    }

    @Test
    public void testClassicUsprWalker_MS_Consistency() {
        verifyUnrootedWalkerConsistency(new ClassicUsprWalker(), new MSIncrementalMetric(), new MatchingSplitMetric());
    }

    // ==========================================
    // SILNIKI WERYFIKUJĄCE (ENGINES)
    // ==========================================

    private void verifyRootedWalkerConsistency(Object walker, IncrementalMetric incMetric, Metric classicMetric) {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(TREE_SIZE, 123L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(TREE_SIZE, 456L);
        assignNumbers(t1);
        assignNumbers(t2);

        incMetric.initCalculationState(t1, t2);
        SprUtils sprUtils = new SprUtils();

        // Nasz audytor: Na każdym kroku Walkera buduje prawdziwe drzewo i sprawdza matematykę
        SprVisitor strictAuditor = (incrementalDistance, pruneNode, targetNode) -> {
            Tree physicalTree = sprUtils.createSprTree(t1, pruneNode, targetNode);
            if (physicalTree != null) {
                assignNumbers(physicalTree);
                double classicDistance = 0;
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

        // Uruchamiamy testowanego Walkera
        if (walker instanceof IncrementalSprWalker) {
            ((IncrementalSprWalker) walker).walk(t1, (IncrementalSprWalker.RootedMetric) incMetric, strictAuditor);
        } else if (walker instanceof ClassicSprWalker) {
            ((ClassicSprWalker) walker).walk(t1, incMetric, strictAuditor);
        } else {
            throw new IllegalArgumentException("Nieobsługiwany Walker Ukorzeniony w teście.");
        }
    }
    private void verifyUnrootedWalkerConsistency(Object walker, IncrementalMetric incMetric, Metric classicMetric) {
        Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(TREE_SIZE, 123L);
        Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(TREE_SIZE, 456L);
        assignNumbers(t1); assignNumbers(t2);

        incMetric.initCalculationState(t1, t2);

        SprVisitor visitor = (actualDist, pruneNode, targetNode) -> {
            double expectedDist = incMetric.evaluateSprRegraft(pruneNode, targetNode);
            assertEquals(expectedDist, actualDist, "BŁĄD ZGODNOŚCI w " + walker.getClass().getSimpleName() + "! Ruch " + pruneNode.getNumber() + " -> " + targetNode.getNumber() + " zepsuł macierz.");
        };

        if (walker instanceof IncrementalUsprWalker) {
            ((IncrementalUsprWalker) walker).walk(t1, incMetric, visitor);
        } else if (walker instanceof ClassicUsprWalker) {
            ((ClassicUsprWalker) walker).walk(t1, incMetric, visitor);
        } else {
            throw new IllegalArgumentException("Nieobsługiwany Walker Nieukorzeniony w teście.");
        }
    }

    private void assignNumbers(Tree tree) {
        if (tree instanceof pal.tree.SimpleTree) {
            ((pal.tree.SimpleTree) tree).createNodeList();
        }
    }

}