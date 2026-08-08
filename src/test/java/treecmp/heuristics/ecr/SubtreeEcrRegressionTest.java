package treecmp.heuristics.ecr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.io.InputSource;
import pal.tree.Node;
import pal.tree.ReadTree;
import pal.tree.Tree;
import treecmp.heuristics.moves.Ecr2Move;
import treecmp.heuristics.moves.Ecr3Move;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.metrics.topological.RFMetric;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testy regresyjne dla naprawionych błędów 2sECR i 3sECR")
public class SubtreeEcrRegressionTest {

    private static final double DELTA = 0.000001;

    private Tree parseNewick(String newick) {
        try (InputSource is = InputSource.openString(newick)) {
            return new ReadTree(is);
        } catch (IOException | pal.tree.TreeParseException e) {
            throw new RuntimeException("Błąd parsowania drzewa Newick: " + newick, e);
        }
    }

    @Test
    @DisplayName("REGRESJA #1 (3sECR unrooted): forEachNeighbour musi wykrywać klastry poniżej korzenia (size=4)")
    void testUnrooted3sEcr_GeneratesNeighboursFromNonRootClusters() {
        // Arrange: Prawidłowe drzewo o 9 liściach. Gałąź (E,...) ma teraz dokładnie
        // 4 węzły wewnętrzne w łańcuchu, tworząc klaster o 5 poddrzewach brzegowych.
        Tree tree = parseNewick("(((A:1,B:1):1,(C:1,D:1):1):1,(E:1,(F:1,(G:1,(H:1,I:1):1):1):1):1);");
        SubtreeEcr3Utils utils = new SubtreeEcr3Utils(true);

        // Licznik wygenerowanych sąsiadów
        final int[] generatedCount = {0};

        // Act & Assert (w locie)
        utils.forEachNeighbour(tree, n -> {
            generatedCount[0]++;
            assertEquals(9, n.getExternalNodeCount(), "Sąsiad musi zachować 9 liści");
        });

        // Assert (końcowy)
        assertTrue(generatedCount[0] > 0,
                "Nieukorzenione 3-sECR musi generować sąsiedztwa z węzłów wewnętrznych poniżej korzenia!");
    }

    @Test
    @DisplayName("REGRESJA #2 (3sECR): applyPhysicalMove (in-place) musi być 100% izomorficzne z createEcr3Tree dla 105 szablonów")
    void testApplyPhysicalMove_MatchesCreateEcr3Tree_All105Templates() {
        Tree baseTree = parseNewick("(((((L1:1,L2:1):1,L3:1):1,L4:1):1,L5:1):1,L6:1);");
        SubtreeEcr3Utils utils = new SubtreeEcr3Utils(false);
        RFClusterMetric rfcMetric = new RFClusterMetric();

        List<Node> cluster = utils.getClusters(baseTree.getRoot(), 4).get(0);
        List<Node> boundary = utils.getBoundarySubtrees(cluster);
        assertEquals(5, boundary.size(), "Klaster 3sECR musi mieć 5 poddrzew brzegowych");

        for (SubtreeEcr3Utils.TopologyTemplate3sECR template : SubtreeEcr3Utils.getTemplates()) {
            Tree clonedTree = utils.createEcr3Tree(baseTree, cluster, boundary.toArray(new Node[0]), template);
            assertNotNull(clonedTree, "createEcr3Tree nie może zwracać null");

            Tree mutatedTree = baseTree.getCopy();
            List<Node> mutCluster = utils.getClusters(mutatedTree.getRoot(), 4).get(0);
            List<Node> mutBoundary = utils.getBoundarySubtrees(mutCluster);
            Ecr3Move move = new Ecr3Move(mutCluster, mutBoundary.toArray(new Node[0]), template);

            utils.applyPhysicalMove(mutatedTree, move);

            double distance = rfcMetric.getDistance(clonedTree, mutatedTree);
            assertEquals(0.0, distance, DELTA,
                    "applyPhysicalMove musi tworzyć dokładnie taką samą topologię jak createEcr3Tree!");
        }
    }

    @Test
    @DisplayName("REGRESJA #3 (2sECR rooted): applyPhysicalMove musi być 100% izomorficzne z createEcrTree dla 15 szablonów")
    void testApplyPhysicalMove_MatchesCreateEcrTree_All15Templates() {
        Tree baseTree = parseNewick("((((A:1,B:1):1,C:1):1,D:1):1,E:1);");
        SubtreeEcr2Utils utils = new SubtreeEcr2Utils(false);
        RFClusterMetric rfcMetric = new RFClusterMetric();

        Node top = baseTree.getInternalNode(baseTree.getInternalNodeCount() - 1);
        Node m1 = top.getChild(0);
        Node m2 = m1.getChild(0);
        Node[] s = new Node[]{ top.getChild(1), m1.getChild(1), m2.getChild(0), m2.getChild(1) };

        for (SubtreeEcr2Utils.TopologyTemplate2sECR template : SubtreeEcr2Utils.getTemplates()) {
            Tree clonedTree = SubtreeEcr2Utils.createEcrTree(baseTree, top, m1, m2, s, template, false);
            assertNotNull(clonedTree, "createEcrTree nie może zwracać null");

            Tree mutatedTree = baseTree.getCopy();
            Node mTop = mutatedTree.getInternalNode(mutatedTree.getInternalNodeCount() - 1);
            Node mM1 = mTop.getChild(0);
            Node mM2 = mM1.getChild(0);
            Node[] mS = new Node[]{ mTop.getChild(1), mM1.getChild(1), mM2.getChild(0), mM2.getChild(1) };
            Ecr2Move move = new Ecr2Move(mTop, mM1, mM2, mS, template);

            utils.applyPhysicalMove(mutatedTree, move);

            double distance = rfcMetric.getDistance(clonedTree, mutatedTree);
            assertEquals(0.0, distance, DELTA,
                    "applyPhysicalMove w 2sECR musi być 100% izomorficzne z createEcrTree!");
        }
    }

    @Test
    @DisplayName("REGRESJA #4 (3sECR unrooted): Brak pętli we wskaźnikach rodzic-dziecko po applyPhysicalMove")
    void testUnrooted3sEcr_NoParentPointerCycles_AfterPhysicalMove() {
        Tree startTree = parseNewick("(((((1:0.1,2:0.1):0.1,3:0.1):0.1,4:0.1):0.1,5:0.1):0.1,6:0.1);");
        SubtreeEcr3Utils utils = new SubtreeEcr3Utils(true);

        // Skoro każdy węzeł ma w PAL 2 dzieci w dół, klaster 3sECR musi mieć size = 4
        List<Node> cluster = utils.getClusters(startTree.getRoot(), 4).get(0);
        List<Node> boundary = utils.getBoundarySubtrees(cluster);
        assertEquals(5, boundary.size(), "Klaster musi mieć dokładnie 5 poddrzew brzegowych");

        for (SubtreeEcr3Utils.TopologyTemplate3sECR template : SubtreeEcr3Utils.getTemplates()) {
            Tree mutatedTree = startTree.getCopy();
            List<Node> mutCluster = utils.getClusters(mutatedTree.getRoot(), 4).get(0);
            List<Node> mutBoundary = utils.getBoundarySubtrees(mutCluster);
            Ecr3Move move = new Ecr3Move(mutCluster, mutBoundary.toArray(new Node[0]), template);

            utils.applyPhysicalMove(mutatedTree, move);

            for (int i = 0; i < mutatedTree.getExternalNodeCount(); i++) {
                Node curr = mutatedTree.getExternalNode(i);
                int steps = 0;
                while (curr != null && steps < 100) {
                    curr = curr.getParent();
                    steps++;
                }
                assertTrue(steps < 100, "Wskaźniki rodzic-dziecko zapętliły się po wywołaniu applyPhysicalMove!");
            }
        }
    }
}