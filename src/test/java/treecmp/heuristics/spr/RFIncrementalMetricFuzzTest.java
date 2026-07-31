package treecmp.heuristics.spr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import pal.tree.SimpleTree;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.topological.RFMetric;
import treecmp.metrics.topological.acc.RFIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ekstremalny Fuzz Test udowadniający w 100% poprawność akceleratora uSPR dla drzew nieukorzenionych.
 * Symuluje Walker z włączonym wejściem w "Inner Moves" (wejście do wnętrza odciętego fragmentu).
 */
public class RFIncrementalMetricFuzzTest {

    private RFIncrementalMetric incrementalMetric;
    private RFMetric classicMetric;
    private UsprUtils usprUtils;

    private static final int FUZZ_ITERATIONS = 50;
    private static final double DELTA = 0.000001;
    private int totalEvaluations = 0;

    @BeforeEach
    void setUp() {
        incrementalMetric = new RFIncrementalMetric();
        classicMetric = new RFMetric();
        usprUtils = new UsprUtils();
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

                Node[] allNodes = TreeCmpUtils.getAllNodes(baseTree);
                for (Node pruneNode : allNodes) {
                    // uSPR: Pomijamy główną trifurkację z powodu wirtualnego korzenia stopnia 3
                    if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

                    incrementalMetric.applySprPrune(pruneNode);
                    dfsUsprFuzz(pruneNode, baseTree.getRoot(), baseTree, targetTree);
                    incrementalMetric.undoSprPrune(pruneNode);

                    assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                            "Błąd wycofywania stosu (Undo Prune leak) w drzewie nr " + i);
                }
            }
            System.out.println("RF uSPR DFS Fuzz Passed! Zweryfikowano bezbłędnie " + totalEvaluations +
                    " kroków uSPR (wliczając Inner Moves) w pełnym cyklu odcięcie/wpięcie.");
        }

    private void dfsUsprFuzz(Node pruneNode, Node currentNode, Tree baseTree, Tree targetTree) {
        if (currentNode != pruneNode && currentNode != pruneNode.getParent()) {
            if (usprUtils.isValidUsprMove(pruneNode, currentNode)) {

                // Wirtualny dystans w O(1) z kompensacją kierunku bitów (normailzeSplit)
                double fastDist = incrementalMetric.evaluateSprRegraft(pruneNode, currentNode);

                // Pełna wyrocznia topologiczna
                Tree physicalTree = usprUtils.createUsprTree(baseTree, pruneNode, currentNode);
                double classicDist = classicMetric.getDistance(physicalTree, targetTree);

                assertEquals(classicDist, fastDist, DELTA,
                        "Błąd w obliczeniach komplementarnych (Bipartitions) w uSPR!");
                totalEvaluations++;
            }
        }

        if (!currentNode.isLeaf()) {
            incrementalMetric.applySprRegraftStep(pruneNode, currentNode);

            for (int i = 0; i < currentNode.getChildCount(); i++) {
                Node child = currentNode.getChild(i);

                // KLUCZOWE: W uSPR testujemy wędrowanie wewnątrz odciętego fragmentu (Inner Moves)!
                // Nie używamy "if (child == pruneNode) continue;"
                dfsUsprFuzz(pruneNode, child, baseTree, targetTree);
            }

            incrementalMetric.undoSprRegraftStep();
        }
    }
}