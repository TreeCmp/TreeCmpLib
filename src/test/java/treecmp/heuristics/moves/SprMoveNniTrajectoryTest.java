package treecmp.heuristics.moves;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.topological.RFMetric;
import treecmp.util.TestTreeFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Weryfikacja dekompozycji makroruchów SPR/uSPR do ścisłej sekwencji kroków 1-NNI (RF=2)")
public class SprMoveNniTrajectoryTest {

    private static final RFMetric RF_METRIC = new RFMetric();

    private void prepareTree(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
        TreeUtils.computeParentPointers(tree.getRoot());
    }

    private int getUnrootedRf(Tree t1, Tree t2) {
        Tree u1 = TreeCmpUtils.unrootTreeIfNeeded(t1);
        Tree u2 = TreeCmpUtils.unrootTreeIfNeeded(t2);
        return (int) Math.round(RF_METRIC.getDistance(u1, u2) * 2.0);
    }

    /**
     * Test parametryczny na drzewach N=10, 20, 30.
     * Losuje drzewo i testuje różne warianty ruchów uSPR:
     * - liść -> liść
     * - liść -> węzeł wewnętrzny
     * - węzeł wewnętrzny -> liść
     * - węzeł wewnętrzny -> węzeł wewnętrzny
     */
    @ParameterizedTest(name = "Weryfikacja trajektorii 1-NNI dla uSPR na drzewach N={0}")
    @ValueSource(ints = {10, 20, 30})
    void testUsprTrajectoryStrict1NniSteps(int n) {
        long seed = 12345L + n;
        Tree baseTree = TestTreeFactory.randomUnrootedBinaryTree(n, seed);
        prepareTree(baseTree);

        UsprUtils usprUtils = new UsprUtils();
        int testedMoves = 0;
        int maxMovesToTest = 25;

        int totalNodes = baseTree.getExternalNodeCount() + baseTree.getInternalNodeCount();

        for (int i = 0; i < totalNodes && testedMoves < maxMovesToTest; i++) {
            Node s = (i < baseTree.getExternalNodeCount())
                    ? baseTree.getExternalNode(i)
                    : baseTree.getInternalNode(i - baseTree.getExternalNodeCount());

            for (int j = 0; j < totalNodes && testedMoves < maxMovesToTest; j++) {
                Node t = (j < baseTree.getExternalNodeCount())
                        ? baseTree.getExternalNode(j)
                        : baseTree.getInternalNode(j - baseTree.getExternalNodeCount());

                if (!usprUtils.isValidUsprMove(s, t)) {
                    continue;
                }

                SprMove move = new SprMove(s, t);
                List<Tree> trajectory = move.getNniTrajectory(baseTree);

                assertNotNull(trajectory, "Trajektoria nie może być null");
                assertFalse(trajectory.isEmpty(), String.format(
                        "Dla poprawnego ruchu SPR (%s -> %s) trajektoria nie może być pusta!",
                        s.isLeaf() ? s.getIdentifier().getName() : "Int_" + s.getNumber(),
                        t.isLeaf() ? t.getIdentifier().getName() : "Int_" + t.getNumber()
                ));

                // WERYFIKACJA 1: Każde kolejne przejście w łańcuchu musi mieć RF == 2
                Tree prevTree = baseTree;
                for (int stepIdx = 0; stepIdx < trajectory.size(); stepIdx++) {
                    Tree currTree = trajectory.get(stepIdx);
                    prepareTree(currTree);

                    int rf = getUnrootedRf(prevTree, currTree);
                    assertEquals(2, rf, String.format(
                            "Błąd ciągłości NNI przy N=%d w ruchu SPR (%s -> %s)! Krok %d -> %d ma RF=%d (oczekiwano ściśle 2).",
                            n,
                            s.isLeaf() ? s.getIdentifier().getName() : "Int_" + s.getNumber(),
                            t.isLeaf() ? t.getIdentifier().getName() : "Int_" + t.getNumber(),
                            stepIdx, stepIdx + 1, rf
                    ));

                    prevTree = currTree;
                }

                // WERYFIKACJA 2: Ostatnie drzewo w trajektorii musi być izomorficzne z wynikiem fizycznego ruchu SPR
                Tree expectedFinalTree = usprUtils.createUsprTree(baseTree, s, t);
                if (expectedFinalTree != null) {
                    prepareTree(expectedFinalTree);
                    int finalRf = getUnrootedRf(prevTree, expectedFinalTree);
                    assertEquals(0, finalRf, String.format(
                            "Ostatnie drzewo w trajektorii NNI nie osiągnęło docelowej topologii SPR (RF do celu = %d)!",
                            finalRf
                    ));
                }

                testedMoves++;
            }
        }

        assertTrue(testedMoves >= 10, "Powinno zostać przetestowanych co najmniej 10 zróżnicowanych ruchów");
    }

    /**
     * Test regresyjny: Ruch SPR bezpośrednio przecinający korzeń PAL (węzeł stopnia 3).
     * Weryfikuje brak przeskoku RF > 2 w przypadku, gdy węzeł docelowy lub źródłowy
     * jest bezpośrednim dzieckiem korzenia w nieukorzenionej reprezentacji.
     */
    @Test
    @DisplayName("Regresja: Dekompozycja uSPR przy przejściu przez 3-dzielny korzeń PAL")
    void testRootCrossingUsprMoveDecomposition() {
        int n = 30;
        Tree baseTree = TestTreeFactory.randomUnrootedBinaryTree(n, 99999L);
        prepareTree(baseTree);

        Node root = baseTree.getRoot();
        assertEquals(3, root.getChildCount(), "Nieukorzenione drzewo PAL musi mieć w korzeniu 3 dzieci");

        // Wybieramy poddrzewo z pierwszej gałęzi korzenia i cel z drugiej gałęzi korzenia
        Node sourceChild = root.getChild(0);
        Node targetChild = root.getChild(1);

        Node s = sourceChild.isLeaf() ? sourceChild : sourceChild.getChild(0);
        Node t = targetChild.isLeaf() ? targetChild : targetChild.getChild(0);

        UsprUtils usprUtils = new UsprUtils();
        if (usprUtils.isValidUsprMove(s, t)) {
            SprMove move = new SprMove(s, t);
            List<Tree> trajectory = move.getNniTrajectory(baseTree);

            assertFalse(trajectory.isEmpty(), "Trajektoria przez korzeń nie może być pusta");

            Tree prev = baseTree;
            for (int i = 0; i < trajectory.size(); i++) {
                Tree next = trajectory.get(i);
                prepareTree(next);
                int rf = getUnrootedRf(prev, next);
                assertEquals(2, rf, String.format("Przeskok RF=%d != 2 przy przejściu przez korzeń PAL w kroku %d", rf, i + 1));
                prev = next;
            }
        }
    }
}