package treecmp.spr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import pal.tree.SimpleTree;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ekstremalny Fuzz Test udowadniający w 100% poprawność matematyczną akceleratora SPR.
 * Symuluje rekurencyjnego Walkera DFS, porównując O(1) maski bitowe z fizyczną topologią O(N).
 */
public class RFClusterIncrementalMetricFuzzTest {

    private RFClusterIncrementalMetric incrementalMetric;
    private RFClusterMetric classicMetric;
    private SprUtils sprUtils;

    private static final int FUZZ_ITERATIONS = 50;
    private static final double DELTA = 0.000001;
    private int totalEvaluations = 0;

    @BeforeEach
    void setUp() {
        incrementalMetric = new RFClusterIncrementalMetric();
        classicMetric = new RFClusterMetric();
        sprUtils = new SprUtils();
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

            Node[] allNodes = TreeCmpUtils.getAllNodes(baseTree);

            for (Node pruneNode : allNodes) {
                // SPR: Nie odcinamy korzenia ani gałęzi do niego prowadzącej
                if (pruneNode.isRoot() || pruneNode == baseTree.getRoot()) continue;

                // 1. ODCINAMY PODDRZEWO (WIRTUALNIE)
                incrementalMetric.applySprPrune(pruneNode);

                // 2. ODPALAMY REKURENCYJNEGO WALKERA (Symulacja DFS)
                dfsSprFuzz(pruneNode, baseTree.getRoot(), baseTree, targetTree);

                // 3. COFAMY ODCIĘCIE
                incrementalMetric.undoSprPrune(pruneNode);

                assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                        "Wyciek pamięci na stosie! Prune/Undo zdesynchronizowało dystans bazowy.");
            }
        }
        System.out.println("RFC SPR DFS Fuzz Passed! Zweryfikowano bezbłędnie " + totalEvaluations +
                " kroków akceleratora O(1) w zderzeniu z pełną fizyczną budową topologii.");
    }

    private void dfsSprFuzz(Node pruneNode, Node currentNode, Tree baseTree, Tree targetTree) {
        // A. Wycena aktualnego miejsca wpięcia (jeśli ruch jest dozwolony)
        if (currentNode != pruneNode && currentNode != pruneNode.getParent()) {
            if (sprUtils.isValidSprMove(pruneNode, currentNode)) {

                // AKCELERATOR O(1)
                double fastDist = incrementalMetric.evaluateSprRegraft(pruneNode, currentNode);

                // WYROCZNIA: Fizyczne zbudowanie drzewa SPR
                Tree physicalTree = sprUtils.createSprTree(baseTree, pruneNode, currentNode);
                double classicDist = classicMetric.getDistance(physicalTree, targetTree);

                assertEquals(classicDist, fastDist, DELTA,
                        "Błąd matematyczny operacji bitowych! Dystans wirtualny różni się od fizycznego.");
                totalEvaluations++;
            }
        }

        // B. Zejście w głąb drzewa (DFS) za pomocą stosów historii
        if (!currentNode.isLeaf()) {
            incrementalMetric.applySprRegraftStep(pruneNode, currentNode);

            for (int i = 0; i < currentNode.getChildCount(); i++) {
                Node child = currentNode.getChild(i);
                if (child == pruneNode) continue; // SPR: Nie wchodzimy do własnego odciętego poddrzewa
                dfsSprFuzz(pruneNode, child, baseTree, targetTree);
            }

            incrementalMetric.undoSprRegraftStep(); // Zdjęcie masek bitowych ze stosu przy wyjściu
        }
    }
}