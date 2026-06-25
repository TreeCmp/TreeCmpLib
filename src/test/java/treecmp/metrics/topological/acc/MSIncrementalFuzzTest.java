package treecmp.metrics.topological.acc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.topological.MatchingSplitMetric;
import treecmp.metrics.topological.acc.MSIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Extreme Fuzz Testing for the MSIncrementalMetric (Matching Split).
 * Validates the O(N^2) warm-start LAP optimization and unrooted split bipartition
 * evaluations against the classic O(N^3) baseline across thousands of random topologies.
 */
public class MSIncrementalFuzzTest {

    private MSIncrementalMetric incrementalMetric;
    private MatchingSplitMetric classicMetric;
    private SprUtils sprUtils;

    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        incrementalMetric = new MSIncrementalMetric();
        classicMetric = new MatchingSplitMetric(); // Using the unrooted baseline oracle
        sprUtils = new SprUtils();
    }

    @Test
    void testFuzzThousandsOfRandomUnrootedNniMoves() throws Exception {
        int numberOfRandomTrees = 50;
        Random sizeRng = new Random(42);

        int totalNniEvaluations = 0;

        for (int i = 0; i < numberOfRandomTrees; i++) {
            int numLeaves = 10 + sizeRng.nextInt(41);

            Tree baseTree = TestTreeFactory.randomRootedBinaryTree(numLeaves, sizeRng.nextLong());
            Tree targetTree = TestTreeFactory.randomRootedBinaryTree(numLeaves, sizeRng.nextLong());

            incrementalMetric.initCalculationState(baseTree, targetTree);
            double initialClassicDist = classicMetric.getDistance(baseTree, targetTree);

            assertEquals(initialClassicDist, incrementalMetric.getCurrentDistance(), DELTA,
                    "Base initialization mismatch on tree " + i);

            List<NniMove> validMoves = getAllValidNniMoves(baseTree);

            for (NniMove move : validMoves) {
                // A. Generate the physical neighbor to simulate the NNI swap structurally.
                // We prune the sibling of the moving subtree and regraft it at the swap partner.
                Node siblingOfMoving = getSibling(move.movingSubtree);
                Tree physicalNeighbor = sprUtils.createSprTree(baseTree, siblingOfMoving, move.swapPartner);

                if (physicalNeighbor == null) continue;

                // B. Calculate absolute truth using the heavy O(N^3) classic MS metric
                double expectedDist = classicMetric.getDistance(physicalNeighbor, targetTree);

                // C. Calculate the accelerated distance using the O(N^2) LAP Warm-Start
                double actualDist = incrementalMetric.applyNni(move);

                // D. HARD ASSERTION: Must match perfectly for unrooted splits
                assertEquals(expectedDist, actualDist, DELTA,
                        String.format("Mismatch on random MS tree %d (Size: %d leaves). Expected: %f, Actual: %f",
                                i, numLeaves, expectedDist, actualDist));

                // E. Rollback the virtual state
                incrementalMetric.undoNni(move);

                // F. HARD ASSERTION: Rollback must restore the precise initial split distance
                assertEquals(initialClassicDist, incrementalMetric.getCurrentDistance(), DELTA,
                        "Rollback corrupted the split history stack on random MS tree " + i);

                totalNniEvaluations++;
            }
        }

        System.out.println("MS Fuzz Test Passed! Successfully executed " + totalNniEvaluations +
                " random O(N^2) unrooted NNI evaluations against the O(N^3) MS baseline.");
    }

    /**
     * Helper method to fetch the sibling of a given node in a binary tree.
     */
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

    private List<NniMove> getAllValidNniMoves(Tree tree) {
        List<NniMove> moves = new ArrayList<>();
        int intCount = tree.getInternalNodeCount();

        for (int i = 0; i < intCount; i++) {
            Node u = tree.getInternalNode(i);
            if (u.isRoot()) continue;

            Node p = u.getParent();
            if (p == null) continue;

            Node s = null;
            for (int j = 0; j < p.getChildCount(); j++) {
                if (p.getChild(j) != u) {
                    s = p.getChild(j);
                    break;
                }
            }

            if (s == null) continue;

            if (!u.isLeaf() && u.getChildCount() >= 2) {
                Node c1 = u.getChild(0);
                Node c2 = u.getChild(1);

                moves.add(new NniMove(c1, s));
                moves.add(new NniMove(c2, s));
            }
        }
        return moves;
    }
}