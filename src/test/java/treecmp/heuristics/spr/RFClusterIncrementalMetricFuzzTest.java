package treecmp.heuristics.spr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.acc.IncrementalSprWalker;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ekstremalny Fuzz Test udowadniający w 100% poprawność matematyczną akceleratora SPR.
 * Testuje IncrementalSprWalker z metryką RFClusterIncrementalMetric,
 * porównując wyliczany dystans przyrostowy z pełną fizyczną budową topologii przez SprUtils.
 */
public class RFClusterIncrementalMetricFuzzTest {

    private RFClusterIncrementalMetric incrementalMetric;
    private RFClusterMetric classicMetric;
    private SprUtils sprUtils;
    private IncrementalSprWalker walker;

    private static final int FUZZ_ITERATIONS = 50;
    private static final double DELTA = 0.000001;
    private int totalEvaluations = 0;

    @BeforeEach
    void setUp() {
        incrementalMetric = new RFClusterIncrementalMetric();
        classicMetric = new RFClusterMetric();
        sprUtils = new SprUtils();
        walker = new IncrementalSprWalker();
    }

    private Tree createCleanCopy(Tree original) {
        SimpleTree copy = new SimpleTree(original);
        copy.createNodeList();
        TreeUtils.computeParentPointers(copy.getRoot());
        return copy;
    }

    @Test
    void testFuzzSprDFSAcceleration() {
        Random rng = new Random(42);
        totalEvaluations = 0;

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            int numLeaves = 10 + rng.nextInt(41); // Drzewa od 10 do 50 liści
            Tree baseTree = createCleanCopy(TestTreeFactory.randomRootedBinaryTree(numLeaves, rng.nextLong()));
            Tree targetTree = createCleanCopy(TestTreeFactory.randomRootedBinaryTree(numLeaves, rng.nextLong()));

            incrementalMetric.initCalculationState(baseTree, targetTree);
            double initialDist = incrementalMetric.getCurrentDistance();

            // Uruchomienie produkcyjnego wędrowca IncrementalSprWalker
            walker.walk(baseTree, incrementalMetric, (fastDist, pruneNode, regraftNode) -> {
                // WYROCZNIA: Fizyczne zbudowanie drzewa SPR
                Tree physicalTree = sprUtils.createSprTree(baseTree, pruneNode, regraftNode);
                double classicDist = classicMetric.getDistance(physicalTree, targetTree);

                assertEquals(classicDist, fastDist, DELTA,
                        "Błąd matematyczny operacji bitowych! Dystans wirtualny różni się od fizycznego.");
                totalEvaluations++;
            });

            assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                    "Wyciek pamięci na stosie! Prune/Undo zdesynchronizowało dystans bazowy.");
        }
        System.out.println("RFC SPR DFS Fuzz Passed! Zweryfikowano bezbłędnie " + totalEvaluations +
                " kroków akceleratora O(1) w zderzeniu z pełną fizyczną budową topologii.");
    }
}