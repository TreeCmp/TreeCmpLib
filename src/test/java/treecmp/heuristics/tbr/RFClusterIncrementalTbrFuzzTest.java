package treecmp.heuristics.tbr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.tbr.acc.TbrNeighborhoodWalker;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fuzz Test weryfikujący poprawność ewaluacji TbrNeighborhoodWalker
 * względem fizycznej budowy topologii przez TbrUtils i metryki bazowej RFClusterMetric.
 */
public class RFClusterIncrementalTbrFuzzTest {

    private RFClusterIncrementalMetric incrementalMetric;
    private RFClusterMetric classicMetric;
    private TbrUtils tbrUtils;
    private TbrNeighborhoodWalker walker;

    private static final int FUZZ_ITERATIONS = 20;
    private static final double DELTA = 0.000001;
    private int totalEvaluations = 0;

    @BeforeEach
    void setUp() {
        incrementalMetric = new RFClusterIncrementalMetric();
        classicMetric = new RFClusterMetric();
        tbrUtils = new TbrUtils();
        walker = new TbrNeighborhoodWalker();
    }

    private Tree createCleanCopy(Tree original) {
        SimpleTree copy = new SimpleTree(original);
        copy.createNodeList();
        TreeUtils.computeParentPointers(copy.getRoot());
        return copy;
    }

    @Test
    void testFuzzTbrWalkerEvaluation() {
        Random rng = new Random(42);
        totalEvaluations = 0;

        for (int iter = 0; iter < FUZZ_ITERATIONS; iter++) {
            int numLeaves = 8 + rng.nextInt(15); // Drzewa od 8 do 22 liści
            Tree baseTree = createCleanCopy(TestTreeFactory.randomRootedBinaryTree(numLeaves, rng.nextLong()));
            Tree targetTree = createCleanCopy(TestTreeFactory.randomRootedBinaryTree(numLeaves, rng.nextLong()));

            incrementalMetric.initCalculationState(baseTree, targetTree);
            double initialDist = incrementalMetric.getCurrentDistance();

            walker.walk(baseTree, incrementalMetric, (walkerDist, pruneNode, rerootNode, targetNode) -> {
                // Wyrocznia: fizyczna konstrukcja drzewa TBR
                Tree physicalTree = tbrUtils.createTbrTree(baseTree, pruneNode, rerootNode, targetNode);
                if (physicalTree != null) {
                    double oracleDist = classicMetric.getDistance(physicalTree, targetTree);
                    assertEquals(oracleDist, walkerDist, DELTA,
                            String.format("Błąd wyceny TBR w walkerze dla ruchu prune=%s, reroot=%s, target=%s",
                                    pruneNode.getNumber(), rerootNode.getNumber(), targetNode.getNumber()));
                    totalEvaluations++;
                }
            });

            // Weryfikacja braku wycieku stanu w trakcie całego przejścia Walkera
            assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                    "Walker zdesynchronizował stan wewnętrzny metryki inkrementalnej po iteracji " + iter);
        }

        System.out.println("RFC rTBR Walker Fuzz Passed! Zweryfikowano bezbłędnie " + totalEvaluations +
                " ewaluacji rTBR względem fizycznej topologii.");
    }
}