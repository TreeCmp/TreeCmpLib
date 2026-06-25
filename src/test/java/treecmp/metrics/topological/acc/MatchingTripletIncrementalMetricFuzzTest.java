package treecmp.metrics.topological.acc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.Tree;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.topological.MatchingTripletMetric;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fuzz / Chaos testing for the MatchingTripletIncrementalMetric.
 * Fires hundreds of strict NNI structural perturbations
 * and unpredictable rollbacks (undo) to ensure the incremental O(N^3) optimization
 * NEVER drifts from the pure baseline MatchingTripletMetric.
 */
public class MatchingTripletIncrementalMetricFuzzTest {

    private MatchingTripletIncrementalMetric incrementalMetric;
    private MatchingTripletMetric classicMetric;
    private SprUtils sprUtils;

    private static final double DELTA = 0.000001;
    private static final int FUZZ_ITERATIONS = 300;

    @BeforeEach
    void setUp() {
        incrementalMetric = new MatchingTripletIncrementalMetric();
        classicMetric = new MatchingTripletMetric();
        sprUtils = new SprUtils();
    }

    @Test
    void testFuzzRandomTrajectoriesAndRollbacks() {
        Tree baseTree = TestTreeFactory.tenLeavesRootedTree1();
        Tree targetTree = TestTreeFactory.tenLeavesRootedTree2();

        incrementalMetric.initCalculationState(baseTree, targetTree);
        Tree currentTestTree = baseTree;

        // Stałe ziarno dla pełnej powtarzalności testu rozmytego
        Random random = new Random(42);
        Stack<Tree> treeHistory = new Stack<>();

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            // 1. Zbieramy wszystkie matematycznie w 100% poprawne ruchy NNI z aktualnej topologii
            List<NniMove> validMoves = new ArrayList<>();
            Node[] allNodes = TreeCmpUtils.getAllNodes(currentTestTree);

            for (Node node : allNodes) {
                if (!node.isRoot() && !node.isLeaf()) {
                    Node parent = node.getParent();
                    if (parent != null) {
                        Node uncle = getSibling(node);
                        if (uncle != null && node.getChildCount() >= 2) {
                            Node c1 = node.getChild(0);
                            Node c2 = node.getChild(1);
                            // Czysty ruch NNI: Zamiana dziecka na wujka
                            validMoves.add(new NniMove(c1, uncle));
                            validMoves.add(new NniMove(c2, uncle));
                        }
                    }
                }
            }

            // Fallback na wypadek ekstremalnej degeneracji drzewa (np. brak węzłów wewn.)
            if (validMoves.isEmpty()) break;

            // 2. Losujemy całkowicie bezpieczny ruch z puli dozwolonych
            NniMove move = validMoves.get(random.nextInt(validMoves.size()));
            Node movingSubtree = move.movingSubtree;
            Node swapPartner = move.swapPartner;
            Node sibling = getSibling(movingSubtree);

            // Zapisujemy stan przed ruchem (na wypadek operacji LIFO Undo)
            treeHistory.push(currentTestTree);

            // 3. Budujemy "Oczekiwaną" topologię w środowisku PAL i liczymy baseline w O(N^3)
            Tree expectedNewTree = sprUtils.createSprTree(currentTestTree, sibling, swapPartner);
            double expectedDistance = classicMetric.getDistance(expectedNewTree, targetTree);

            // 4. Aplikujemy ten sam ruch w naszej hybrydowej i szybkiej metryce
            double actualDistance = incrementalMetric.applyNni(move);

            // 5. EGZEKUCJA ASERCJI: Odległość (i parowanie Trójek) musi być perfekcyjnie spójna
            assertEquals(expectedDistance, actualDistance, DELTA,
                    "Fuzz mismatch at iteration " + i + "! Incremental MT logic drifted from baseline.");

            currentTestTree = expectedNewTree;

            // 6. Chaos-Rollback: W 30% przypadków algorytm wykonuje krok w tył (Undo)
            if (random.nextDouble() < 0.3) {
                incrementalMetric.undoNni(move);
                currentTestTree = treeHistory.pop(); // Cofamy zewnętrzne drzewo testowe

                // Weryfikujemy, czy wycofanie (undoNni) idealnie przywróciło historię dystansu i macierz NCV
                double expectedUndoDist = classicMetric.getDistance(currentTestTree, targetTree);
                assertEquals(expectedUndoDist, incrementalMetric.getCurrentDistance(), DELTA,
                        "Undo mismatch at iteration " + i + "! History stack corruption detected in MT.");
            }
        }
    }

    // ==========================================================
    // Metody Pomocnicze dla testu
    // ==========================================================

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