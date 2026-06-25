package treecmp.metrics.topological.acc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.topological.MatchingClusterMetric;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Extreme Fuzz Testing for the MCIncrementalMetric.
 * Validates the O(N^2) warm-start LAP optimization against the classic O(N^3) baseline
 * across thousands of random topological NNI states and random tree shapes.
 */
public class MCIncrementalMetricFuzzTest {

    private MCIncrementalMetric incrementalMetric;
    private MatchingClusterMetric classicMetric;
    private SprUtils sprUtils;

    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        incrementalMetric = new MCIncrementalMetric();
        classicMetric = new MatchingClusterMetric();
        sprUtils = new SprUtils();
    }

    @Test
    void testFuzzThousandsOfRandomNniMoves() throws Exception {
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
                // A. Generate the physical neighbor.
                // NniMove is mathematically defined as "Swap movingSubtree with swapPartner".
                // Using SprUtils (which operates via Prune & Regraft), if we want to swap A and C,
                // we must prune the SIBLING of A, and regraft it at C.
                Node siblingOfMoving = getSibling(move.movingSubtree);
                Tree physicalNeighbor = sprUtils.createSprTree(baseTree, siblingOfMoving, move.swapPartner);

                if (physicalNeighbor == null) continue;

                // B. Calculate absolute truth using the heavy O(N^3) classic algorithm
                double expectedDist = classicMetric.getDistance(physicalNeighbor, targetTree);

                // C. Calculate the accelerated distance using the O(N^2) LAP Warm-Start
                double actualDist = incrementalMetric.applyNni(move);

                // D. HARD ASSERTION: Must match perfectly
                assertEquals(expectedDist, actualDist, DELTA,
                        String.format("Mismatch on random tree %d (Size: %d leaves). Expected: %f, Actual: %f",
                                i, numLeaves, expectedDist, actualDist));

                // E. Rollback the virtual state
                incrementalMetric.undoNni(move);

                // F. HARD ASSERTION: Rollback must restore the precise initial distance
                assertEquals(initialClassicDist, incrementalMetric.getCurrentDistance(), DELTA,
                        "Rollback corrupted the history stack on random tree " + i);

                totalNniEvaluations++;
            }
        }

        System.out.println("Fuzz Test Passed! Successfully executed " + totalNniEvaluations +
                " random O(N^2) NNI evaluations against the O(N^3) baseline without a single mathematical error.");
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