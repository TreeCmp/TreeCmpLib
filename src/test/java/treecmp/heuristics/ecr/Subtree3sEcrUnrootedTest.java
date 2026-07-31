package treecmp.heuristics.ecr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.io.InputSource;
import pal.tree.Node;
import pal.tree.ReadTree;
import pal.tree.Tree;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR;
import treecmp.heuristics.moves.Ecr3Move;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testy jednostkowe dla SubtreeEcr3Utils (isUnrooted = true)")
public class Subtree3sEcrUnrootedTest {

    private SubtreeEcr3Utils utils;

    @BeforeEach
    void setUp() {
        // Inicjalizacja w trybie NIEUKORZENIONYM
        utils = new SubtreeEcr3Utils(true);
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

    private List<Node> findFirstValidCluster(Tree tree, int clusterSize) {
        List<List<Node>> rootClusters = utils.getClusters(tree.getRoot(), clusterSize);
        if (!rootClusters.isEmpty()) {
            return rootClusters.get(0);
        }
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            List<List<Node>> clusters = utils.getClusters(tree.getInternalNode(i), clusterSize);
            if (!clusters.isEmpty()) {
                return clusters.get(0);
            }
        }
        throw new AssertionError("Nie odnaleziono klastra o rozmiarze " + clusterSize + " w drzewie nieukorzenionym!");
    }

    @Test
    @DisplayName("createEcr3Tree (unrooted): Każdy ze 105 szablonów musi generować poprawne drzewo bez utraty liści")
    void testCreateEcr3Tree_All105TemplatesUnrooted() {
        Tree startTree = parseNewick("(((((L1:1,L2:1):1,L3:1):1,L4:1):1,L5:1):1,L6:1);");
        Set<String> expectedLeaves = getLeafNames(startTree);

        List<Node> targetCluster = findFirstValidCluster(startTree, 4);
        List<Node> boundarySubtrees = utils.getBoundarySubtrees(targetCluster);

        assertEquals(5, boundarySubtrees.size(), "Klaster 3sECR musi mieć 5 poddrzew brzegowych");
        Node[] s = boundarySubtrees.toArray(new Node[0]);

        for (TopologyTemplate3sECR template : SubtreeEcr3Utils.getTemplates()) {
            Tree resultTree = utils.createEcr3Tree(startTree, targetCluster, s, template);

            assertNotNull(resultTree, "createEcr3Tree nie powinien zwracać null w trybie unrooted");
            assertEquals(6, resultTree.getExternalNodeCount(), "Liczba liści musi wynosić 6");
            assertEquals(expectedLeaves, getLeafNames(resultTree), "Zbiór liści po 3sECR musi być nienaruszony");
        }
    }

    @Test
    @DisplayName("generateNeighbours (unrooted): Brak pętli wskaźników rodzic-dziecko po transformacji")
    void testGenerateNeighbours_NoParentPointersCyclesUnrooted() {
        Tree tree = parseNewick("(((((1:0.1,2:0.1):0.1,3:0.1):0.1,4:0.1):0.1,5:0.1):0.1,6:0.1);");
        Tree[] neighbours = utils.generateNeighbours(tree);

        assertTrue(neighbours.length > 0, "Powinno wygenerować sąsiadów 3sECR dla drzewa nieukorzenionego");

        for (Tree n : neighbours) {
            assertNotNull(n, "Sąsiad nie może być null");
            for (int i = 0; i < n.getExternalNodeCount(); i++) {
                Node curr = n.getExternalNode(i);
                int steps = 0;
                while (curr != null && steps < 100) {
                    curr = curr.getParent();
                    steps++;
                }
                assertTrue(steps < 100, "Wskaźniki rodzic-dziecko w drzewie nieukorzenionym zawierają nieskończoną pętlę!");
            }
        }
    }

    @Test
    @DisplayName("Ecr3Move#getNniTrajectory (unrooted): Szablon o koszcie 2 NNI musi zwrócić trajektorię o długości 2")
    void testEcr3Move_TrajectorySizeForCost2Unrooted() {
        Tree startTree = parseNewick("(((((A:1,B:1):1,C:1):1,D:1):1,E:1):1,F:1);");

        List<Node> cluster = findFirstValidCluster(startTree, 4);
        Node[] s = utils.getBoundarySubtrees(cluster).toArray(new Node[0]);

        TopologyTemplate3sECR templateCost2 = SubtreeEcr3Utils.getTemplates().stream()
                .filter(t -> t.nniCost == 2)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Brak szablonu o koszcie 2 NNI w katalogu"));

        Ecr3Move move = new Ecr3Move(cluster, s, templateCost2);
        List<Tree> trajectory = move.getNniTrajectory(startTree);

        assertEquals(2, trajectory.size(),
                "Dla kosztu 2 NNI w trybie nieukorzenionym trajektoria musi mieć 2 kroki");
        assertNotNull(trajectory.get(0), "Krok pośredni #1 nie może być null");
        assertNotNull(trajectory.get(1), "Drzewo docelowe nie może być null");
    }
}