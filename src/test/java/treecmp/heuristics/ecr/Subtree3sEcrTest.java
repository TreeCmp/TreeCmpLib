package treecmp.heuristics.ecr;

import treecmp.heuristics.ecr.SubtreeEcr3Utils;
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

@DisplayName("Testy jednostkowe dla SubtreeEcr3Utils oraz Ecr3Move (3sECR)")
public class Subtree3sEcrTest {

    private SubtreeEcr3Utils utils;

    @BeforeEach
    void setUp() {
        utils = new SubtreeEcr3Utils(false);
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
     * Metoda pomocnicza: znajduje pierwszy legalny klaster o zadanym rozmiarze,
     * przeszukując węzły wewnętrzne (w tym korzeń).
     */
    private List<Node> findFirstValidCluster(Tree tree, int clusterSize) {
        // Najpierw sprawdzamy od korzenia w dół
        List<List<Node>> rootClusters = utils.getClusters(tree.getRoot(), clusterSize);
        if (!rootClusters.isEmpty()) {
            return rootClusters.get(0);
        }
        // Jeśli nie w korzeniu, szukamy w pozostałych węzłach wewnętrznych
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            List<List<Node>> clusters = utils.getClusters(tree.getInternalNode(i), clusterSize);
            if (!clusters.isEmpty()) {
                return clusters.get(0);
            }
        }
        throw new AssertionError("Nie odnaleziono klastra o rozmiarze " + clusterSize + " w podanym drzewie!");
    }

    @Test
    @DisplayName("Katalog TEMPLATES_105: Powinien zawierać dokładnie 105 unikalnych topologii binarnych")
    void testTemplatesCatalogSize() {
        List<TopologyTemplate3sECR> templates = SubtreeEcr3Utils.getTemplates();
        assertEquals(105, templates.size(),
                "Liczba binarnych rozstrzygnięć dla klastra 5-elementowego musi wynosić dokładnie 105");
    }

    @Test
    @DisplayName("createEcr3Tree: Każdy ze 105 szablonów musi generować poprawne drzewo bez utraty poddrzew brzegowych")
    void testCreateEcr3Tree_All105TemplatesValidity() {
        Tree startTree = parseNewick("(((((L1:1,L2:1):1,L3:1):1,L4:1):1,L5:1):1,L6:1);");
        Set<String> expectedLeaves = getLeafNames(startTree);

        // Bezpieczne pobranie klastra 4-węzłowego
        List<Node> targetCluster = findFirstValidCluster(startTree, 4);
        List<Node> boundarySubtrees = utils.getBoundarySubtrees(targetCluster);

        assertEquals(5, boundarySubtrees.size(), "Klaster 3sECR musi mieć dokładnie 5 poddrzew brzegowych");
        Node[] s = boundarySubtrees.toArray(new Node[0]);

        for (TopologyTemplate3sECR template : SubtreeEcr3Utils.getTemplates()) {
            Tree resultTree = utils.createEcr3Tree(startTree, targetCluster, s, template);

            assertNotNull(resultTree, "createEcr3Tree nie powinien zwracać null dla żadnego ze 105 szablonów");
            assertEquals(6, resultTree.getExternalNodeCount(), "Liczba liści musi wynosić 6");
            assertEquals(expectedLeaves, getLeafNames(resultTree), "Zbiór liści po 3sECR musi być nienaruszony");
        }
    }

    @Test
    @DisplayName("Ecr3Move#getNniTrajectory: Szablon o koszcie 2 NNI musi zwrócić trajektorię o długości 2")
    void testEcr3Move_TrajectorySizeForCost2() {
        Tree startTree = parseNewick("(((((A:1,B:1):1,C:1):1,D:1):1,E:1):1,F:1);");

        List<Node> cluster = findFirstValidCluster(startTree, 4);
        Node[] s = utils.getBoundarySubtrees(cluster).toArray(new Node[0]);

        // Znajdźmy w katalogu szablon o koszcie dokładnie 2 NNI
        TopologyTemplate3sECR templateCost2 = SubtreeEcr3Utils.getTemplates().stream()
                .filter(t -> t.nniCost == 2)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Brak szablonu o koszcie 2 NNI w katalogu"));

        Ecr3Move move = new Ecr3Move(cluster, s, templateCost2);
        List<Tree> trajectory = move.getNniTrajectory(startTree);

        assertEquals(2, trajectory.size(),
                "Dla kosztu 2 NNI trajektoria musi mieć 2 kroki (NNI_Substep_1 + Cel)");
        assertNotNull(trajectory.get(0), "Krok pośredni #1 nie może być null");
        assertNotNull(trajectory.get(1), "Drzewo docelowe nie może być null");
    }

    @Test
    @DisplayName("Ecr3Move#getNniTrajectory: Szablon o koszcie 3 NNI musi zwrócić trajektorię o długości 3")
    void testEcr3Move_TrajectorySizeForCost3() {
        Tree startTree = parseNewick("(((((A:1,B:1):1,C:1):1,D:1):1,E:1):1,F:1);");

        List<Node> cluster = findFirstValidCluster(startTree, 4);
        Node[] s = utils.getBoundarySubtrees(cluster).toArray(new Node[0]);

        // Znajdźmy szablon oddalony o maksymalny koszt w klastrze (3 NNI)
        TopologyTemplate3sECR templateCost3 = SubtreeEcr3Utils.getTemplates().stream()
                .filter(t -> t.nniCost == 3)
                .findFirst()
                .orElse(null);

        if (templateCost3 != null) {
            Ecr3Move move = new Ecr3Move(cluster, s, templateCost3);
            List<Tree> trajectory = move.getNniTrajectory(startTree);

            assertEquals(3, trajectory.size(),
                    "Dla kosztu 3 NNI trajektoria musi mieć 3 kroki (NNI_Substep_1, NNI_Substep_2 + Cel)");
        }
    }

    @Test
    @DisplayName("forEachNeighbour: Żaden z wygenerowanych sąsiadów 3sECR nie może mieć pętli rodzic-dziecko")
    void testGenerateNeighbours_NoParentPointersCycles() {
        Tree tree = parseNewick("(((((1:0.1,2:0.1):0.1,3:0.1):0.1,4:0.1):0.1,5:0.1):0.1,6:0.1);");

        // Licznik wygenerowanych drzew, modyfikowalny z wnętrza lambdy
        final int[] generatedCount = {0};

        // Ewaluacja w locie za pomocą zoptymalizowanego iteratora
        utils.forEachNeighbour(tree, n -> {
            generatedCount[0]++;
            assertNotNull(n, "Sąsiad nie może być null");

            for (int i = 0; i < n.getExternalNodeCount(); i++) {
                Node curr = n.getExternalNode(i);
                int steps = 0;
                // Zabezpieczenie przed nieskończoną pętlą
                while (curr != null && steps < 100) {
                    curr = curr.getParent();
                    steps++;
                }
                assertTrue(steps < 100, "Wskaźniki rodzic-dziecko zawierają nieskończoną pętlę!");
            }
        });

        // Upewniamy się, że heurystyka faktycznie wygenerowała sąsiadów
        assertTrue(generatedCount[0] > 0, "Powinno wygenerować sąsiadów 3sECR");
    }
}