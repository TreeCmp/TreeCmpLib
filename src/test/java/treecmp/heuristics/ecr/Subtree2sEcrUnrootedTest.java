package treecmp.heuristics.ecr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.io.InputSource;
import pal.tree.Node;
import pal.tree.ReadTree;
import pal.tree.Tree;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR;
import treecmp.heuristics.moves.Ecr2Move;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testy jednostkowe dla SubtreeEcr2Utils (isUnrooted = true)")
public class Subtree2sEcrUnrootedTest {

    private SubtreeEcr2Utils utils;

    @BeforeEach
    void setUp() {
        // Inicjalizacja w trybie NIEUKORZENIONYM
        utils = new SubtreeEcr2Utils(true);
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

    private static class Cluster2s {
        Node top, m1, m2;
        Node[] s = new Node[4];
    }

    private Cluster2s findFirstValid2sCluster(Tree tree) {
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
        throw new AssertionError("Nie odnaleziono poprawnego klastra 2sECR w drzewie nieukorzenionym!");
    }

    @Test
    @DisplayName("createEcrTree (unrooted): Wszystkie 15 szablonów musi zachować komplet liści")
    void testCreateEcrTree_AllTemplatesUnrooted() {
        Tree startTree = parseNewick("((((A:1,B:1):1,C:1):1,D:1):1,E:1);");
        Set<String> expectedLeaves = getLeafNames(startTree);

        Cluster2s cl = findFirstValid2sCluster(startTree);
        List<TopologyTemplate2sECR> templates = SubtreeEcr2Utils.getTemplates();

        for (TopologyTemplate2sECR template : templates) {
            Tree resultTree = SubtreeEcr2Utils.createEcrTree(
                    startTree, cl.top, cl.m1, cl.m2, cl.s, template, false
            );

            assertNotNull(resultTree, "createEcrTree nie powinien zwracać null dla szablonu w trybie unrooted");
            assertEquals(startTree.getExternalNodeCount(), resultTree.getExternalNodeCount(),
                    "Liczba liści musi pozostać niezmieniona (unrooted)");
            assertEquals(expectedLeaves, getLeafNames(resultTree),
                    "Zbiór nazw liści musi być identyczny po przebudowie klastra");
        }
    }

    @Test
    @DisplayName("forEachNeighbour (unrooted): Sąsiedztwa nieukorzenione muszą mieć poprawną strukturę")
    void testForEachNeighbour_UnrootedIntegrity() {
        Tree tree = parseNewick("(((A:1,B:1):1,(C:1,D:1):1):1,E:1);");

        // Zbieramy wygenerowane sąsiedztwa za pomocą nowego interfejsu
        List<Tree> neighbours = new ArrayList<>();
        utils.forEachNeighbour(tree, neighbours::add);

        assertTrue(neighbours.size() > 0, "Powinno wygenerować sąsiedztwa 2sECR dla drzewa nieukorzenionego");

        for (Tree n : neighbours) {
            assertNotNull(n, "Żaden z wygenerowanych sąsiadów nie może być null");
            assertEquals(5, n.getExternalNodeCount(), "Sąsiad musi mieć dokładnie 5 liści");
            assertNotNull(n.getRoot(), "Struktura drzewa musi posiadać poprawny węzeł główny");
        }
    }

    @Test
    @DisplayName("Ecr2Move#getNniTrajectory (unrooted): Ruch o koszcie 2 NNI musi generować trajektorię o długości 2")
    void testEcr2Move_TrajectorySizeUnrooted() {
        Tree startTree = parseNewick("((((1:1,2:1):1,3:1):1,4:1):1,5:1);");
        Cluster2s cl = findFirstValid2sCluster(startTree);

        TopologyTemplate2sECR template2Nni = new TopologyTemplate2sECR(false, new int[]{2, 3, 0, 1});
        Ecr2Move move = new Ecr2Move(cl.top, cl.m1, cl.m2, cl.s, template2Nni);

        List<Tree> trajectory = move.getNniTrajectory(startTree);
        assertEquals(2, trajectory.size(),
                "Trajektoria dla kosztu 2 NNI w trybie nieukorzenionym musi zawierać 2 drzewa");
        assertNotNull(trajectory.get(0), "Krok pośredni #1 nie może być null");
        assertNotNull(trajectory.get(1), "Drzewo docelowe nie może być null");
    }
}