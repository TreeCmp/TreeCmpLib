package treecmp.heuristics.tbr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.tbr.acc.UtbrNeighborhoodWalker;
import treecmp.metrics.topological.RFMetric;
import treecmp.metrics.topological.acc.RFIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fuzz Test weryfikujący zgodność wyceny uTBR w UtbrNeighborhoodWalker
 * z wyrocznią opartą na UTbrUtils oraz klasycznej metryce RFMetric.
 */
public class RFIncrementalUtbrFuzzTest {

    private RFIncrementalMetric incrementalMetric;
    private RFMetric classicMetric;
    private UTbrUtils utbrUtils;
    private UtbrNeighborhoodWalker walker;

    private static final int FUZZ_ITERATIONS = 20;
    private static final double DELTA = 0.000001;
    private int totalEvaluations = 0;

    @BeforeEach
    void setUp() {
        incrementalMetric = new RFIncrementalMetric();
        classicMetric = new RFMetric();
        utbrUtils = new UTbrUtils();
        walker = new UtbrNeighborhoodWalker();
    }

    private Tree createCleanCopy(Tree original) {
        SimpleTree copy = new SimpleTree(original);
        copy.createNodeList();
        TreeUtils.computeParentPointers(copy.getRoot());
        return copy;
    }

    @Test
    void testFuzzUtbrWalkerEvaluation() {
        Random rng = new Random(123);
        totalEvaluations = 0;

        for (int iter = 0; iter < FUZZ_ITERATIONS; iter++) {
            int numLeaves = 8 + rng.nextInt(15);
            Tree baseTree = createCleanCopy(TestTreeFactory.randomUnrootedBinaryTree(numLeaves, rng.nextLong()));
            Tree targetTree = createCleanCopy(TestTreeFactory.randomUnrootedBinaryTree(numLeaves, rng.nextLong()));

            incrementalMetric.initCalculationState(baseTree, targetTree);
            double initialDist = incrementalMetric.getCurrentDistance();

            walker.walk(baseTree, incrementalMetric, (walkerDist, pruneNode, rerootNode, targetNode) -> {
                Tree physicalTree = utbrUtils.createUtbrTree(baseTree, pruneNode, rerootNode, targetNode);
                if (physicalTree != null) {
                    double oracleDist = classicMetric.getDistance(physicalTree, targetTree);
                    assertEquals(oracleDist, walkerDist, DELTA,
                            String.format("Błąd wyceny uTBR w walkerze dla ruchu prune=%s, reroot=%s, target=%s",
                                    pruneNode.getNumber(), rerootNode.getNumber(), targetNode.getNumber()));
                    totalEvaluations++;
                }
            });

            assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                    "Walker uTBR zdesynchronizował stan metryki po iteracji " + iter);
        }

        System.out.println("RF uTBR Walker Fuzz Passed! Zweryfikowano bezbłędnie " + totalEvaluations +
                " ewaluacji uTBR względem fizycznej topologii.");
    }
}