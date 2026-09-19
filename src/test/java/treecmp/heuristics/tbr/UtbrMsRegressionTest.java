package treecmp.heuristics.tbr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.io.InputSource;
import pal.tree.ReadTree;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeParseException;
import pal.tree.TreeUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrNeighborhoodWalker;
import treecmp.metrics.topological.MatchingSplitMetric;
import treecmp.metrics.topological.acc.MSIncrementalMetric;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testy regresyjne uTBR i MSIncrementalMetric (eliminacja rozjazdu 4.0 vs 8.0)")
public class UtbrMsRegressionTest {

    private final MatchingSplitMetric classicOracle = new MatchingSplitMetric();
    private final UTbrUtils utbrUtils = new UTbrUtils();

    private Tree parseUnrooted(String nh) {
        try {
            InputSource in = InputSource.openString(nh);
            Tree t = new ReadTree(in);
            SimpleTree st = new SimpleTree(t);
            st.createNodeList();
            TreeUtils.computeParentPointers(st.getRoot());
            return st;
        } catch (TreeParseException e) {
            throw new RuntimeException("Błąd parsowania Newick", e);
        }
    }

    @Test
    @DisplayName("1. Weryfikacja dokładności wyceny UtbrNeighborhoodWalker dla MSIncrementalMetric")
    void testUtbrNeighborhoodWalkerEvaluationParityWithClassicMS() {
        // Drzewa testowe N=10 (nieukorzenione, trifurkacja w korzeniu)
        String t1Str = "(1,2,((3,4),((5,6),(7,(8,(9,10))))));";
        String t2Str = "((1,3),(2,5),((4,6),((7,9),(8,10))));";

        Tree baseTree = parseUnrooted(t1Str);
        Tree targetTree = parseUnrooted(t2Str);

        MSIncrementalMetric msInc = new MSIncrementalMetric();
        msInc.initCalculationState(baseTree, targetTree);

        UtbrNeighborhoodWalker walker = new UtbrNeighborhoodWalker();
        AtomicInteger evaluatedMoves = new AtomicInteger(0);

        walker.walk(baseTree, msInc, (reportedDist, pruneNode, rerootNode, targetNode) -> {
            evaluatedMoves.incrementAndGet();

            // Budujemy fizyczne drzewo sąsiednie
            Tree physicalTree = utbrUtils.createUtbrTree(baseTree, pruneNode, rerootNode, targetNode);
            assertNotNull(physicalTree, "Fizyczne drzewo nie powinno być null dla poprawnego ruchu uTBR");

            if (physicalTree instanceof SimpleTree) {
                ((SimpleTree) physicalTree).createNodeList();
                TreeUtils.computeParentPointers(physicalTree.getRoot());
            }

            // Obliczamy odległość wyrocznią klasyczną od zera
            double expectedClassicDist = classicOracle.getDistance(physicalTree, targetTree);

            // Asercja: dystans zgłoszony przez walker musi być w 100% zgodny z wyrocznią
            assertEquals(expectedClassicDist, reportedDist, 1e-6,
                    String.format("Błąd wyceny w walkerze uTBR dla MS! Move: prune=%s, reroot=%s, target=%s. Reported=%.4f vs Classic=%.4f",
                            pruneNode.getNumber(), rerootNode.getNumber(), targetNode.getNumber(), reportedDist, expectedClassicDist));
        });

        assertTrue(evaluatedMoves.get() > 0, "Walker powinien sprawdzić co najmniej jeden poprawny ruch uTBR");
    }

    @Test
    @DisplayName("2. Test zapobiegania wyciekowi stanu w performLocalDescent przy odrzuceniu ruchu")
    void testNoStateLeakOnRejectedMoveInUtbrHeuristic() {
        String t1Str = "(1,2,((3,4),(5,(6,(7,(8,(9,10)))))));";
        String t2Str = "((1,10),(2,9),((3,8),((4,7),(5,6))));";

        Tree startTree = parseUnrooted(t1Str);
        Tree targetTree = parseUnrooted(t2Str);

        MSIncrementalMetric msInc = new MSIncrementalMetric();
        UtbrIncrementalHeuristic heuristic = new UtbrIncrementalHeuristic(msInc, "MS");

        double descentResult = heuristic.performLocalDescent(startTree, targetTree);
        Tree lastOptTree = heuristic.getLastOptimumTree();

        assertNotNull(lastOptTree, "lastOptimumTree nie może być null");

        // Obliczamy rzeczywistą odległość na drzewie zapisanym jako optimum
        double actualDistOnOptimum = classicOracle.getDistance(lastOptTree, targetTree);

        // Zapobiega sytuacji, w której zwrócono dystans stary (np. 4.0), a w lastOptimumTree siedzi nextTree (np. 8.0)
        assertEquals(descentResult, actualDistOnOptimum, 1e-6,
                String.format("Wyciek stanu w UtbrIncrementalHeuristic! Zwrócono dystans=%.4f, ale lastOptimumTree ma dystans=%.4f",
                        descentResult, actualDistOnOptimum));
    }

    @Test
    @DisplayName("3. Test zstępowania dla N=20: brak asercji MISMATCH dla uTBR + MS")
    void testUtbrMsDescentParityN20() {
        // Reprezentatywna para drzew N=20 nieukorzenionych
        String n20_T1 = "(1,2,(3,(4,(5,(6,(7,(8,(9,(10,(11,(12,(13,(14,(15,(16,(17,(18,(19,20))))))))))))))))));";

        String n20_T2 = "((1,20),(2,19),((3,18),((4,17),((5,16),((6,15),((7,14),((8,13),((9,12),(10,11)))))))));";

        Tree t1 = parseUnrooted(n20_T1);
        Tree t2 = parseUnrooted(n20_T2);

        MSIncrementalMetric msInc = new MSIncrementalMetric();
        UtbrIncrementalHeuristic heuristic = new UtbrIncrementalHeuristic(msInc, "MS");

        double reportedDist = heuristic.performLocalDescent(t1, t2);
        Tree finalOptimum = heuristic.getLastOptimumTree();

        assertNotNull(finalOptimum);
        assertEquals(20, finalOptimum.getExternalNodeCount(), "Drzewo optymalne nie może utracić liści");

        double classicDist = classicOracle.getDistance(finalOptimum, t2);

        // Ścisła weryfikacja asercji [uTBR DESCENT MISMATCH]
        assertEquals(classicDist, reportedDist, 1e-6,
                String.format("[uTBR DESCENT MISMATCH] Reported=%.4f vs ActualClassic=%.4f", reportedDist, classicDist));
    }
}