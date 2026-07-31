package treecmp.heuristics.ecr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.io.InputSource;
import pal.tree.Node;
import pal.tree.ReadTree;
import pal.tree.Tree;
import treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR;
import treecmp.heuristics.moves.Ecr2Move;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testy jednostkowe dla SubtreeEcr2Utils oraz Ecr2Move (2sECR)")
public class Subtree2sEcrTest {

    private SubtreeEcr2Utils utils;

    @BeforeEach
    void setUp() {
        utils = new SubtreeEcr2Utils(false);
    }

    private Tree parseNewick(String newick) {
        try (InputSource is = InputSource.openString(newick)) {
            return new ReadTree(is);
        } catch (IOException | pal.tree.TreeParseException e) {
            throw new RuntimeException("Błąd parsowania drzewa Newick: " + newick, e);
        }
    }

    private Set<String> getLeafNames(Tree tree) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            names.add(tree.getExternalNode(i).getIdentifier().getName());
        }
        return names;
    }

    /**
     * Kontener na odnaleziony legalny klaster 4-elementowy (2sECR).
     */
    private static class Cluster2s {
        Node top, m1, m2;
        Node[] s = new Node[4];
    }

    /**
     * Metoda pomocnicza: przeszukuje węzły wewnętrzne od góry do dołu
     * i znajduje pierwszy legalny łańcuch 3 węzłów wewnętrznych (top -> m1 -> m2).
     */
    private Cluster2s findFirstValid2sCluster(Tree tree) {
        // Idziemy od końca tablicy (w PAL ostatnie indeksy to korzeń i wyższe węzły)
        for (int i = tree.getInternalNodeCount() - 1; i >= 0; i--) {
            Node top = tree.getInternalNode(i);
            for (int c1 = 0; c1 < top.getChildCount(); c1++) {
                Node m1 = top.getChild(c1);
                if (!m1.isLeaf()) {
                    for (int c2 = 0; c2 < m1.getChildCount(); c2++) {
                        Node m2 = m1.getChild(c2);
                        if (!m2.isLeaf()) {
                            Cluster2s cl = new Cluster2s();
                            cl.top = top;
                            cl.m1 = m1;
                            cl.m2 = m2;
                            // Zbieramy 4 poddrzewa brzegowe s[0..3]:
                            cl.s[0] = top.getChild(1 - c1);
                            cl.s[1] = m1.getChild(1 - c2);
                            cl.s[2] = m2.getChild(0);
                            cl.s[3] = m2.getChild(1);
                            return cl;
                        }
                    }
                }
            }
        }
        throw new AssertionError("Nie odnaleziono poprawnego klastra 2sECR (łańcucha 3 węzłów wewnętrznych)!");
    }

    @Test
    @DisplayName("Katalog TEMPLATES: Powinien zawierać dokładnie 15 unikalnych topologii binarnych")
    void testTemplatesCatalogSize() {
        List<TopologyTemplate2sECR> templates = SubtreeEcr2Utils.getTemplates();
        assertEquals(15, templates.size(),
                "Liczba binarnych rozstrzygnięć dla klastra 4-elementowego musi wynosić dokładnie 15");
    }

    @Test
    @DisplayName("createEcrTree: Powinien wygenerować poprawne drzewa dla wszystkich 15 szablonów bez utraty liści")
    void testCreateEcrTree_AllTemplatesLeafPreservation() {
        // Drzewo startowe z głębokim łańczuchem wewnętrznym
        Tree startTree = parseNewick("((((A:1,B:1):1,C:1):1,D:1):1,E:1);");
        Set<String> expectedLeaves = getLeafNames(startTree);

        Cluster2s cl = findFirstValid2sCluster(startTree);

        List<TopologyTemplate2sECR> templates = SubtreeEcr2Utils.getTemplates();
        assertEquals(15, templates.size(), "Katalog 2sECR musi zawierać dokładnie 15 szablonów");

        for (TopologyTemplate2sECR template : templates) {
            Tree resultTree = SubtreeEcr2Utils.createEcrTree(
                    startTree, cl.top, cl.m1, cl.m2, cl.s, template, false
            );

            assertNotNull(resultTree, "createEcrTree nie powinien zwracać null dla legalnego szablonu");
            assertEquals(startTree.getExternalNodeCount(), resultTree.getExternalNodeCount(),
                    "Liczba liści po transformacji 2sECR musi pozostać niezmieniona");
            assertEquals(expectedLeaves, getLeafNames(resultTree),
                    "Zbiór nazw liści musi być identyczny po przebudowie klastra");
        }
    }

    @Test
    @DisplayName("Ecr2Move#getNniTrajectory: Ruch o koszcie 2 NNI musi generować dokładnie 2 drzewa (Substep_1 i cel)")
    void testEcr2Move_TrajectorySizeFor2NniCost() {
        Tree startTree = parseNewick("((((1:1,2:1):1,3:1):1,4:1):1,5:1);");

        Cluster2s cl = findFirstValid2sCluster(startTree);

        // Wybieramy szablon zmieniający topologię o koszt 2 NNI (np. permutacja [2, 3, 0, 1])
        TopologyTemplate2sECR template2Nni = new TopologyTemplate2sECR(false, new int[]{2, 3, 0, 1});
        Ecr2Move move = new Ecr2Move(cl.top, cl.m1, cl.m2, cl.s, template2Nni);

        assertEquals(2, move.getNniEquivalentCost(), "Koszt NNI dla tej permutacji powinien wynosić 2");

        List<Tree> trajectory = move.getNniTrajectory(startTree);
        assertEquals(2, trajectory.size(),
                "Trajektoria dla ruchu o koszcie 2 NNI musi zawierać 1 krok pośredni + 1 drzewo docelowe");

        assertNotNull(trajectory.get(0), "Drzewo dla NNI_Substep_1 nie może być null");
        assertNotNull(trajectory.get(1), "Drzewo docelowe nie może być null");
    }

    @Test
    @DisplayName("generateNeighbours: Wygenerowane sąsiedztwa 2sECR muszą zachowywać integralność struktury")
    void testGenerateNeighbours_StructuralIntegrity() {
        Tree tree = parseNewick("(((A:1,B:1):1,(C:1,D:1):1):1,E:1);");
        Tree[] neighbours = utils.generateNeighbours(tree);

        assertTrue(neighbours.length > 0, "Powinno wygenerować co najmniej jedno sąsiedztwo 2sECR");

        for (Tree n : neighbours) {
            assertNotNull(n, "Żadne z sąsiednich drzew nie może być null");
            assertEquals(5, n.getExternalNodeCount(), "Sąsiad musi mieć dokładnie 5 liści");
            assertNotNull(n.getRoot(), "Korzeń sąsiada nie może być null");
        }
    }
}