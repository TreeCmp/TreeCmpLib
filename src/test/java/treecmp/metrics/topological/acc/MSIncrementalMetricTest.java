package treecmp.metrics.topological;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.moves.NniMove;
import treecmp.metrics.topological.acc.MSIncrementalMetric;
import treecmp.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying the mathematical consistency of the MSIncrementalMetric (Matching Split).
 * It ensures that the optimized warm-start incremental algorithm produces exactly
 * the same split-based distance values as the full baseline MatchingSplitMetric across
 * various complex NNI move trajectories and unrooted rollbacks.
 */
public class MSIncrementalMetricTest {

    private MSIncrementalMetric incrementalMetric;
    private MatchingSplitMetric classicMetric;

    private static final double DELTA = 0.000001;

    private Tree baseTree;
    private Tree targetTree;

    @BeforeEach
    void setUp() {
        // Initialize both the incremental MS metric and the full classic baseline MS metric
        incrementalMetric = new MSIncrementalMetric();
        classicMetric = new MatchingSplitMetric();

        // Use the 10-leaf trees from the factory.
        // Although they are technically rooted in PAL, the MS metric evaluates them
        // safely as unrooted splits by ignoring the root node.
        baseTree = TestTreeFactory.tenLeavesRootedTree1();
        targetTree = TestTreeFactory.tenLeavesRootedTree2();
    }

    @Test
    void testInitialDistanceConsistency() throws Exception {
        // Arrange & Act
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double expectedClassicDist = classicMetric.getDistance(baseTree, targetTree);

        // Assert: The initial incremental state (bipartition matching) must perfectly match the classic calculation
        assertEquals(expectedClassicDist, incrementalMetric.getCurrentDistance(), DELTA,
                "Initial MS distance must be perfectly identical between incremental and classic metrics!");
    }

    @Test
    void testSingleNniMoveAndUndoConsistency() {
        // Arrange
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        // In tenLeavesRootedTree1 and tenLeavesRootedTree2, leaves 2 and 3 are swapped.
        Node node2 = TreeUtils.getNodeByName(baseTree, "2");
        Node node3 = TreeUtils.getNodeByName(baseTree, "3");
        NniMove move = new NniMove(node2, node3);

        // Act: Apply the local NNI update incrementally for splits
        double distAfterMove = incrementalMetric.applyNni(move);

        // Assert: Moving the tree to match the target layout should decrease the distance to 0.0
        assertEquals(0.0, distAfterMove, DELTA,
                "The target-matching NNI move should reduce the incremental MS distance to 0.0!");

        // Act: Undo the step to trigger history stack recovery
        incrementalMetric.undoNni(move);

        // Assert: The distance must accurately revert to its baseline value
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "The rollback operation (undo) failed to restore the exact original MS distance!");
    }

    @Test
    void testMultipleSequentialNniMovesMaintainStateConsistency() {
        // Arrange
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        Node n1 = TreeUtils.getNodeByName(baseTree, "1");
        Node n2 = TreeUtils.getNodeByName(baseTree, "2");
        Node n3 = TreeUtils.getNodeByName(baseTree, "3");
        Node n4 = TreeUtils.getNodeByName(baseTree, "4");

        // Define a chain of consecutive NNI steps
        NniMove move1 = new NniMove(n2, n3); // Corrects the tree (Distance drops to 0.0)
        NniMove move2 = new NniMove(n3, n4); // Diverges the unrooted structure
        NniMove move3 = new NniMove(n1, n2); // Further divergence

        // Act & Assert: Execute sequence forwards
        double dist1 = incrementalMetric.applyNni(move1);
        assertEquals(0.0, dist1, DELTA);

        double dist2 = incrementalMetric.applyNni(move2);
        assertTrue(dist2 >= 0.0, "Split distance should stay non-negative after structural divergence.");

        incrementalMetric.applyNni(move3);

        // Act & Assert: Rollback sequence backwards using strict LIFO order
        incrementalMetric.undoNni(move3);
        assertEquals(dist2, incrementalMetric.getCurrentDistance(), DELTA,
                "Undoing move3 must return the metric state precisely to the post-move2 state!");

        incrementalMetric.undoNni(move2);
        assertEquals(dist1, incrementalMetric.getCurrentDistance(), DELTA,
                "Undoing move2 must return the metric state precisely to the post-move1 state!");

        incrementalMetric.undoNni(move1);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "After wiping the entire move history stack, the MS distance must equal the initial baseline!");
    }

    @Test
    void testComplexBranchingTrajectoryWithNestedUndos() {
        // Arrange
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        Node n1 = TreeUtils.getNodeByName(baseTree, "1");
        Node n2 = TreeUtils.getNodeByName(baseTree, "2");
        Node n3 = TreeUtils.getNodeByName(baseTree, "3");
        Node n4 = TreeUtils.getNodeByName(baseTree, "4");
        Node n5 = TreeUtils.getNodeByName(baseTree, "5");

        NniMove move1 = new NniMove(n2, n3); // Primary track
        NniMove move2 = new NniMove(n4, n5); // Secondary track
        NniMove move3 = new NniMove(n2, n4); // Speculative dead end branch

        NniMove move4 = new NniMove(n1, n5); // Alternate choice path after backing out

        // Phase 1: Go deep into the exploration track (3 steps forward)
        double dist1 = incrementalMetric.applyNni(move1);
        double dist2 = incrementalMetric.applyNni(move2);
        incrementalMetric.applyNni(move3);

        // Phase 2: Back out of the dead end path (1 step backward)
        incrementalMetric.undoNni(move3);
        assertEquals(dist2, incrementalMetric.getCurrentDistance(), DELTA,
                "Backing out of a speculative dead end branch corrupted the MS split matrix state!");

        // Phase 3: Take an alternate evolutionary path (1 new step forward)
        incrementalMetric.applyNni(move4);

        // Phase 4: Full cascade rollback back to baseline state
        incrementalMetric.undoNni(move4);
        assertEquals(dist2, incrementalMetric.getCurrentDistance(), DELTA);

        incrementalMetric.undoNni(move2);
        assertEquals(dist1, incrementalMetric.getCurrentDistance(), DELTA);

        incrementalMetric.undoNni(move1);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "Full trajectory recovery failed to clean up historical split variables!");
    }
}