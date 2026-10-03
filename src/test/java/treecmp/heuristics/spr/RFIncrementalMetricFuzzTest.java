package treecmp.heuristics.spr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.acc.IncrementalUsprWalker;
import treecmp.metrics.topological.RFMetric;
import treecmp.metrics.topological.acc.RFIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ekstremalny Fuzz Test udowadniający w 100% poprawność akceleratora uSPR dla drzew nieukorzenionych.
 * Wykorzystuje produkcyjny IncrementalUsprWalker, porównując wirtualny dystans z fizyczną wyrocznią RFMetric.
 */
public class RFIncrementalMetricFuzzTest {

    private RFIncrementalMetric incrementalMetric;
    private RFMetric classicMetric;
    private UsprUtils usprUtils;
    private IncrementalUsprWalker walker;

    private static final int FUZZ_ITERATIONS = 50;
    private static final double DELTA = 0.000001;
    private int totalEvaluations = 0;

    @BeforeEach
    void setUp() {
        incrementalMetric = new RFIncrementalMetric();
        classicMetric = new RFMetric();
        usprUtils = new UsprUtils();
        walker = new IncrementalUsprWalker();
    }

    private Tree createCleanCopy(Tree original) {
        SimpleTree copy = new SimpleTree(original);
        copy.createNodeList();
        TreeUtils.computeParentPointers(copy.getRoot());
        return copy;
    }

    @Test
    void testFuzzUsprDFSAcceleration() {
        Random rng = new Random(123);
        totalEvaluations = 0;

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            int numLeaves = 10 + rng.nextInt(41);

            // RF korzysta z drzew unrooted
            Tree baseTree = createCleanCopy(TestTreeFactory.randomUnrootedBinaryTree(numLeaves, rng.nextLong()));
            Tree targetTree = createCleanCopy(TestTreeFactory.randomUnrootedBinaryTree(numLeaves, rng.nextLong()));

            incrementalMetric.initCalculationState(baseTree, targetTree);
            double initialDist = incrementalMetric.getCurrentDistance();

            // Uruchomienie produkcyjnego wędrowca uSPR
            walker.walk(baseTree, incrementalMetric, (fastDist, pruneNode, regraftNode) -> {
                // Pełna wyrocznia topologiczna
                Tree physicalTree = usprUtils.createUsprTree(baseTree, pruneNode, regraftNode);
                double classicDist = classicMetric.getDistance(physicalTree, targetTree);

                assertEquals(classicDist, fastDist, DELTA,
                        "Błąd w obliczeniach komplementarnych (Bipartitions) w uSPR!");
                totalEvaluations++;
            });

            assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                    "Błąd wycofywania stosu (Undo Prune leak) w drzewie nr " + i);
        }
        System.out.println("RF uSPR DFS Fuzz Passed! Zweryfikowano bezbłędnie " + totalEvaluations +
                " kroków uSPR w pełnym cyklu odcięcie/wpięcie.");
    }
}