package treecmp.heuristics.tbr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.util.TestTreeFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Izolowany test diagnostyczny TBR (Classic Rooted) dla n10y200rb")
public class TbrRootedClassicDiagnosticTest {

    // Drzewa ze zbioru n10y200rb.newick: Para 6 (linie 12 i 13) oraz Pary 1 i 4
    private static final String TREE_PAIR6_T1 = "((3,(10,4)),((5,(1,9)),(7,(6,(8,2)))));";
    private static final String TREE_PAIR6_T2 = "(10,((3,(7,(6,5))),(4,(8,(1,(9,2))))));";

    private static final String TREE_PAIR1_T1 = "((((1,8),(4,(10,9))),(3,(2,7))),(6,5));";
    private static final String TREE_PAIR1_T2 = "(((5,(10,9)),(8,(2,(6,(1,7))))),(4,3));";

    private static final String TREE_PAIR4_T1 = "(((7,(8,(6,(2,4)))),(10,(3,5))),(1,9));";
    private static final String TREE_PAIR4_T2 = "((6,(((9,5),(1,4)),(7,8))),(10,(3,2)));";

    private Tree parseNewick(String nh) {
        pal.io.InputSource in = pal.io.InputSource.openString(nh);
        try {
            Tree tree = new pal.tree.ReadTree(in);
            SimpleTree st = new SimpleTree(tree);
            st.createNodeList();
            TreeUtils.computeParentPointers(st.getRoot());
            return st;
        } catch (pal.tree.TreeParseException e) {
            throw new RuntimeException(e);
        }
    }

    private Set<String> getLeafNames(Tree tree) {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            set.add(tree.getExternalNode(i).getIdentifier().getName());
        }
        return set;
    }

    @Test
    @DisplayName("Weryfikacja integralności liści dla sąsiadów wygenerowanych przez TbrUtils (Para 6)")
    void testTbrUtilsDoesNotDropLeavesOnPair6() {
        Tree t1 = parseNewick(TREE_PAIR6_T1);
        int expectedLeaves = t1.getExternalNodeCount();
        Set<String> expectedTaxa = getLeafNames(t1);

        TbrUtils utils = new TbrUtils();
        List<String> violations = new ArrayList<>();

        utils.forEachNeighbour(t1, neighbor -> {
            int actualLeaves = neighbor.getExternalNodeCount();
            if (actualLeaves != expectedLeaves) {
                violations.add(String.format("Uszkodzone drzewo! Oczekiwano %d liści, otrzymano %d. Newick: %s",
                        expectedLeaves, actualLeaves, neighbor.toString()));
            } else {
                Set<String> actualTaxa = getLeafNames(neighbor);
                if (!actualTaxa.equals(expectedTaxa)) {
                    Set<String> missing = new HashSet<>(expectedTaxa);
                    missing.removeAll(actualTaxa);
                    violations.add(String.format("Niezgodność taksonów! Brakujące: %s. Newick: %s",
                            missing, neighbor.toString()));
                }
            }
        });

        assertTrue(violations.isEmpty(),
                () -> "TbrUtils wygenerowało uszkodzone drzewa z brakującymi liśćmi:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("Test zstępowania TBR_ClassicHeuristic_RFC na Parze 6 (Wykrywanie zapętlenia/hang)")
    void testClassicTbrDescentPair6DoesNotHang() {
        Tree t1 = parseNewick(TREE_PAIR6_T1);
        Tree t2 = parseNewick(TREE_PAIR6_T2);

        TbrHeuristicMetric classicTbr = new TbrHeuristicMetric(new RFClusterMetric(), true, "RFC");

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            double finalDist = classicTbr.performLocalDescent(t1, t2);
            assertTrue(finalDist >= 0.0, "Dystans końcowy musi być nieujemny");

            Tree optimum = classicTbr.getLastOptimumTree();
            assertNotNull(optimum, "Drzewo optymalne nie może być null");
            assertEquals(10, optimum.getExternalNodeCount(),
                    "Drzewo optymalne po zstąpieniu TBR utraciło liście!");
        });
    }

    @Test
    @DisplayName("Sprawdzenie odporności na uszkodzenia dla Par 1 i 4 (Błędy z logu benchmarku)")
    void testClassicTbrIntegrityOnProblematicPairs() {
        TbrUtils utils = new TbrUtils();

        for (String newick : Arrays.asList(TREE_PAIR1_T1, TREE_PAIR4_T1)) {
            Tree tree = parseNewick(newick);
            int n = tree.getExternalNodeCount();

            utils.forEachNeighbour(tree, neighbor -> {
                assertEquals(n, neighbor.getExternalNodeCount(),
                        "Wykryto uszkodzone drzewo w otoczeniu TBR dla Newick: " + newick);
                assertNotNull(neighbor.getRoot(), "Korzeń drzewa sąsiedniego nie może być null");
                assertTrue(neighbor.getRoot().getChildCount() >= 2, "Korzeń musi mieć co najmniej 2 dzieci");
            });
        }
    }
}