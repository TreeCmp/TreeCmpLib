package treecmp.metrics.topological.acc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.topological.MatchingTripletMetric;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying the mathematical consistency of the MatchingTripletIncrementalMetric.
 * It ensures that the optimized O(N^3) hybrid approach combined with LAP warm-starts
 * produces exact distance values identical to the baseline MatchingTripletMetric,
 * and that history stacks for LIFO rollbacks (undo) function flawlessly.
 */
public class MatchingTripletIncrementalMetricTest {

    private MatchingTripletIncrementalMetric incrementalMetric;
    private MatchingTripletMetric classicMetric;
    private SprUtils sprUtils;

    private static final double DELTA = 0.000001;

    private Tree baseTree;
    private Tree targetTree;

    @BeforeEach
    void setUp() {
        incrementalMetric = new MatchingTripletIncrementalMetric();
        classicMetric = new MatchingTripletMetric();
        sprUtils = new SprUtils();

        // Standardowe drzewa 10-liściowe z fabryki TreeCmp
        baseTree = TestTreeFactory.tenLeavesUnrootedTree1();
        targetTree = TestTreeFactory.tenLeavesUnrootedTree2();
    }

    @Test
    void testInitialDistanceConsistency() {
        // Arrange & Act
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double expectedClassicDist = classicMetric.getDistance(new SimpleTree(baseTree), targetTree);

        // Assert: Inicjalny stan musi idealnie pasować do metryki bazowej
        assertEquals(expectedClassicDist, incrementalMetric.getCurrentDistance(), DELTA,
                "Initial MT distance must be perfectly identical between incremental and classic metrics!");
    }

    @Test
    void testSingleNniMoveAndUndoConsistency() {
        // Arrange
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        // Pobieramy gwarantowanie prawidłowy ruch NNI dla baseTree
        NniMove move = getDeterministicValidMoves(baseTree).get(0);

        // Obliczamy cel z użyciem oryginalnej biblioteki PAL (Baseline)
        Node sibling = getSibling(move.movingSubtree);
        Tree expectedNewTree = sprUtils.createSprTree(baseTree, sibling, move.swapPartner);
        double expectedDistAfterMove = classicMetric.getDistance(expectedNewTree, targetTree);

        // Act 1: Aplikacja ruchu
        double actualDistAfterMove = incrementalMetric.applyNni(move);

        // Assert 1: Inkrementalna delta musi się zrównać z chłodnym startem klasycznej metryki
        assertEquals(expectedDistAfterMove, actualDistAfterMove, DELTA,
                "The target-matching NNI move failed to correctly update the NCV assignments!");

        // Act 2: Wycofanie (Undo)
        incrementalMetric.undoNni(move);

        // Assert 2: Dystans musi wrócić do dokładnej wartości sprzed ruchu
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "The rollback operation (undo) failed to restore the exact original MT distance!");
    }

    @Test
    void testMultipleSequentialNniMovesMaintainStateConsistency() {
        // Arrange
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        Tree currentExpectedTree = baseTree;
        List<NniMove> moves = getDeterministicValidMoves(currentExpectedTree);
        assertTrue(moves.size() >= 3, "Test tree does not support enough valid NNI structural moves!");

        NniMove move1 = moves.get(0);
        NniMove move2 = moves.get(1);
        NniMove move3 = moves.get(2);

        // Act & Assert: Kaskadowe ruchy naprzód
        double dist1 = incrementalMetric.applyNni(move1);
        double dist2 = incrementalMetric.applyNni(move2);
        incrementalMetric.applyNni(move3);

        // Act & Assert: Kaskadowe wycofywanie LIFO
        incrementalMetric.undoNni(move3);
        assertEquals(dist2, incrementalMetric.getCurrentDistance(), DELTA,
                "Undoing move3 must return the metric state precisely to the post-move2 state!");

        incrementalMetric.undoNni(move2);
        assertEquals(dist1, incrementalMetric.getCurrentDistance(), DELTA,
                "Undoing move2 must return the metric state precisely to the post-move1 state!");

        incrementalMetric.undoNni(move1);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "After wiping the entire move history stack, the MT distance must equal the initial baseline!");
    }

    @Test
    void testComplexBranchingTrajectoryWithNestedUndos() {
        // Arrange
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        List<NniMove> startMoves = getDeterministicValidMoves(baseTree);
        NniMove pathA_step1 = startMoves.get(0);
        NniMove pathB_step1 = startMoves.get(1);

        // Act 1: Idziemy ścieżką A (Main Track)
        double distAfterPathA = incrementalMetric.applyNni(pathA_step1);

        // Wykonujemy zagnieżdżony ruch na zaktualizowanym wirtualnym drzewie z wewnątrz metryki
        Tree virtualTree1 = sprUtils.createSprTree(baseTree, getSibling(pathA_step1.movingSubtree), pathA_step1.swapPartner);
        NniMove pathA_step2 = getDeterministicValidMoves(virtualTree1).get(0);
        incrementalMetric.applyNni(pathA_step2);

        // Act 2: Cofamy się z całej ślepej uliczki A (2 kroki Undo)
        incrementalMetric.undoNni(pathA_step2);
        assertEquals(distAfterPathA, incrementalMetric.getCurrentDistance(), DELTA, "Nested undo level 1 failed!");

        incrementalMetric.undoNni(pathA_step1);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA, "Nested undo level 2 failed!");

        // Act 3: Wybieramy ścieżkę B i aplikujemy
        double distAfterPathB = incrementalMetric.applyNni(pathB_step1);

        // Obliczamy dla pewności Baseline dla Ścieżki B
        Tree expectedTreeB = sprUtils.createSprTree(baseTree, getSibling(pathB_step1.movingSubtree), pathB_step1.swapPartner);
        double expectedClassicB = classicMetric.getDistance(expectedTreeB, targetTree);

        // Assert: Sprawdzamy czy zmiana trajektorii i pamięć klastrów nie skaziły macierzy LAP
        assertEquals(expectedClassicB, distAfterPathB, DELTA,
                "Cross-contamination detected! Switching trajectories broke the NCV tracking matrix.");
    }

    // ==========================================================
    // Test Helpers (Safe Deterministic Move Generation)
    // ==========================================================

    private List<NniMove> getDeterministicValidMoves(Tree tree) {
        List<NniMove> validMoves = new ArrayList<>();
        Node[] allNodes = TreeCmpUtils.getAllNodes(tree);

        for (Node node : allNodes) {
            if (!node.isRoot() && !node.isLeaf()) {
                Node parent = node.getParent();
                if (parent != null) {
                    Node uncle = getSibling(node);
                    if (uncle != null && node.getChildCount() >= 2) {
                        Node c1 = node.getChild(0);
                        Node c2 = node.getChild(1);
                        validMoves.add(new NniMove(c1, uncle));
                        validMoves.add(new NniMove(c2, uncle));
                    }
                }
            }
        }
        return validMoves;
    }

    private Node getSibling(Node node) {
        Node parent = node.getParent();
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) != node) {
                return parent.getChild(i);
            }
        }
        return null;
    }
}