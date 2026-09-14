package treecmp.heuristics.tbr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.TreeUnrootedHolder;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.topological.RFMetric;
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class UTbrUtilsTest {

    private UTbrUtils utbrUtils;
    private UsprUtils usprUtils;

    @BeforeEach
    void setUp() {
        utbrUtils = new UTbrUtils();
        usprUtils = new UsprUtils();
    }

    private List<Tree> collectNeighbors(TreeNeighborhoodUtils utils, Tree baseTree) {
        List<Tree> neighbors = new ArrayList<>();
        utils.forEachNeighbour(baseTree, neighbors::add);
        return neighbors;
    }

    // =========================================================================
    // 1. TESTY MATEMATYCZNE I INTEGRACYJNE
    // =========================================================================

    @Test
    void testExactSizeFor5LeavesUnrooted() {
        Tree baseTree = TreeCreator.getTreeFromString("(((1,2),3),4,5);");
        List<Tree> neighbors = collectNeighbors(utbrUtils, baseTree);

        assertEquals(12, neighbors.size(),
                "Dla 5 liści uTBR i uSPR są matematycznie tożsame i muszą wygenerować dokładnie 12 sąsiadów!");
    }

    @Test
    void testUTbrNeighborhoodSizeIsGreaterOrEqualUspr() {
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),4),5,6);");

        List<Tree> utbrNeighbors = collectNeighbors(utbrUtils, baseTree);
        List<Tree> usprNeighbors = collectNeighbors(usprUtils, baseTree);

        assertFalse(utbrNeighbors.isEmpty(), "Otoczenie uTBR nie może być puste.");
        assertFalse(usprNeighbors.isEmpty(), "Otoczenie uSPR nie może być puste.");

        assertTrue(utbrNeighbors.size() >= usprNeighbors.size(),
                "Otoczenie uTBR (" + utbrNeighbors.size() + ") musi być >= uSPR (" + usprNeighbors.size() + ")");
    }

    @Test
    void testUTbrNeighborsAreAllUnique() {
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),4),5,6);");
        List<Tree> utbrNeighbors = collectNeighbors(utbrUtils, baseTree);

        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
        TreeUnrootedHolder baseTreeHolder = new TreeUnrootedHolder(baseTree, idGroup);
        Set<TreeUnrootedHolder> uniqueTrees = new HashSet<>();

        for (Tree t : utbrNeighbors) {
            TreeUnrootedHolder holder = new TreeUnrootedHolder(t, idGroup);
            assertNotEquals(baseTreeHolder, holder,
                    "Otoczenie uTBR nie może zawierać drzewa tożsamego z bazowym!");
            uniqueTrees.add(holder);
        }

        assertEquals(utbrNeighbors.size(), uniqueTrees.size(),
                "Wszystkie wygenerowane drzewa uTBR muszą być unikalne w sensie nieukorzenionym!");
    }

    @Test
    void testUTbrNeighborsPreserveLeafSet() {
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),4),5,6);");
        List<Tree> utbrNeighbors = collectNeighbors(utbrUtils, baseTree);
        int expectedLeaves = baseTree.getExternalNodeCount();

        for (Tree neighbor : utbrNeighbors) {
            assertEquals(expectedLeaves, neighbor.getExternalNodeCount(),
                    "Żaden ruch uTBR nie może modyfikować liczby liści.");
            assertNotNull(neighbor.getRoot(), "Każdy wygenerowany sąsiad musi posiadać korzeń.");
            assertNull(neighbor.getRoot().getParent(), "Korzeń nie może posiadać referencji do rodzica.");
        }
    }

    @Test
    void testAllUTbrNeighborsAreExactlyOneStepAway() throws TreeCmpException {
        Tree baseTree = TreeCreator.getTreeFromString("(((1,2),3),4,5);");
        List<Tree> utbrNeighbors = collectNeighbors(utbrUtils, baseTree);

        TbrHeuristicMetric utbrMetric = new TbrHeuristicMetric(new RFMetric(), false, "RF");

        for (Tree neighbor : utbrNeighbors) {
            double dist = utbrMetric.getDistance(baseTree, neighbor);
            assertEquals(1.0, dist, 0.000001,
                    "Każdy sąsiad wygenerowany przez UTbrUtils musi być w odległości dokładnie 1 kroku uTBR.");
        }
    }

    // =========================================================================
    // 2. TESTY WALIDACJI I CHIRURGII DRZEWA
    // =========================================================================

    @Test
    void testIsValidUtbrMoveRejections() {
        Tree tree = TreeCreator.getTreeFromString("((((1,2),3),4),5,6);");
        List<Node> allNodes = utbrUtils.getAllNodes(tree);

        Node root = tree.getRoot();
        Node pruneNode = null;
        for (Node n : allNodes) {
            if (!n.isRoot() && !n.isLeaf() && n.getChildCount() > 0) {
                pruneNode = n;
                break;
            }
        }
        assertNotNull(pruneNode, "W drzewie musi istnieć węzeł wewnętrzny inny niż korzeń.");

        Node rerootNode = pruneNode.getChild(0);

        // 1. Target będący korzeniem musi zostać odrzucony (drzewo nieukorzenione)
        assertFalse(utbrUtils.isValidUTbrMove(pruneNode, rerootNode, root),
                "Ruch z targetem w korzeniu musi być odrzucony dla uTBR.");

        // 2. Target będący bezpośrednim rodzicem odcinanego węzła musi być odrzucony
        assertFalse(utbrUtils.isValidUTbrMove(pruneNode, rerootNode, pruneNode.getParent()),
                "Target będący rodzicem odcinanego węzła musi być niepoprawny.");

        // 3. Target znajdujący się wewnątrz odciętego poddrzewa musi być odrzucony (zapętlenie)
        assertFalse(utbrUtils.isValidUTbrMove(pruneNode, rerootNode, rerootNode),
                "Target wewnątrz odcinanego poddrzewa musi być odrzucony.");
    }

    @Test
    void testCreateUtbrTreeBranches() {
        Tree tree = TreeCreator.getTreeFromString("((((1,2),3),4),5,6);");
        List<Node> allNodes = utbrUtils.getAllNodes(tree);

        Tree sprTreeResult = null;
        Tree tbrTreeResult = null;

        for (Node prune : allNodes) {
            if (prune.isRoot() || prune.getParent() == null) continue;

            List<Node> rerootNodes = utbrUtils.getSubtreeNodes(prune);
            for (Node reroot : rerootNodes) {
                for (Node target : allNodes) {
                    if (utbrUtils.isValidUTbrMove(prune, reroot, target)) {
                        if (prune == reroot && sprTreeResult == null) {
                            sprTreeResult = utbrUtils.createUtbrTree(tree, prune, reroot, target);
                        } else if (prune != reroot && tbrTreeResult == null) {
                            tbrTreeResult = utbrUtils.createUtbrTree(tree, prune, reroot, target);
                        }
                    }
                    if (sprTreeResult != null && tbrTreeResult != null) break;
                }
                if (sprTreeResult != null && tbrTreeResult != null) break;
            }
            if (sprTreeResult != null && tbrTreeResult != null) break;
        }

        assertNotNull(sprTreeResult, "createUtbrTree powinno poprawnie wygenerować drzewo dla pruneNode == rerootNode");
        assertEquals(tree.getExternalNodeCount(), sprTreeResult.getExternalNodeCount(),
                "Wynikowe drzewo uSPR musi mieć tę samą liczbę liści.");

        assertNotNull(tbrTreeResult, "createUtbrTree powinno poprawnie wygenerować drzewo dla pruneNode != rerootNode");
        assertEquals(tree.getExternalNodeCount(), tbrTreeResult.getExternalNodeCount(),
                "Wynikowe drzewo uTBR musi mieć tę samą liczbę liści.");
    }

    // =========================================================================
    // 3. NOWE TESTY: WERYFIKACJA POPRAWNOŚCI NOWYCH MECHANIZMÓW uTBR
    // =========================================================================

    @Test
    @DisplayName("uSPR musi być ścisłym podzbiorem uTBR (uSPR ⊆ uTBR) z niepustą różnicą dla N >= 6")
    void testUsprIsStrictSubsetOfUtbr() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrootedCaterpillarTree();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        Set<TreeUnrootedHolder> usprSet = new HashSet<>();
        usprUtils.forEachNeighbour(baseTree, t -> usprSet.add(new TreeUnrootedHolder(t, idGroup)));

        Set<TreeUnrootedHolder> utbrSet = new HashSet<>();
        utbrUtils.forEachNeighbour(baseTree, t -> utbrSet.add(new TreeUnrootedHolder(t, idGroup)));

        // Każde drzewo uSPR musi znajdować się w uTBR
        assertTrue(utbrSet.containsAll(usprSet),
                "Każdy sąsiad uSPR musi być obecny w otoczeniu uTBR!");

        // Dla N=6 uTBR musi zawierać właściwe ruchy przekorzenienia, których nie ma w uSPR
        Set<TreeUnrootedHolder> tbrOnly = new HashSet<>(utbrSet);
        tbrOnly.removeAll(usprSet);

        assertFalse(tbrOnly.isEmpty(),
                "Dla N=6 uTBR musi zawierać drzewa wynikające z przekorzenienia poddrzewa (uTBR \\ uSPR != ∅)");
        assertEquals(34, utbrSet.size());
        assertEquals(30, usprSet.size());
        assertEquals(4, tbrOnly.size(), "Dla 6 liści grzebienia uTBR zawiera dokładnie 4 dodatkowe topologie.");
    }

    @Test
    @DisplayName("Wszystkie drzewa wygenerowane przez uTBR muszą mieć korzeń o stopniu >= 3")
    void testAllNeighborsHaveRootDegreeAtLeastThree() {
        Tree baseTree = TestTreeFactory.eightLeavesUnrootedBalancedTree();
        List<Tree> neighbors = collectNeighbors(utbrUtils, baseTree);

        for (Tree neighbor : neighbors) {
            int rootDegree = neighbor.getRoot().getChildCount();
            assertTrue(rootDegree >= 3,
                    "Wykryto zdegenerowany korzeń stopnia " + rootDegree + " w drzewie unrooted: " + neighbor);
        }
    }

    @Test
    @DisplayName("Identyfikatory liści muszą być w 100% zachowane (brak anonimowych liści i brak utraty etykiet)")
    void testLeafTaxaIntegrityAndNoAnonymousLeaves() {
        Tree baseTree = TestTreeFactory.tenLeavesUnrootedBalancedTree();
        Set<String> expectedTaxa = new HashSet<>();
        for (int i = 0; i < baseTree.getExternalNodeCount(); i++) {
            expectedTaxa.add(baseTree.getExternalNode(i).getIdentifier().getName());
        }

        List<Tree> neighbors = collectNeighbors(utbrUtils, baseTree);

        for (Tree neighbor : neighbors) {
            Set<String> actualTaxa = new HashSet<>();
            for (int i = 0; i < neighbor.getExternalNodeCount(); i++) {
                Node leaf = neighbor.getExternalNode(i);
                assertTrue(leaf.isLeaf(), "Węzeł z listy externalNode musi być liściem.");
                assertNotNull(leaf.getIdentifier(), "Liść nie może mieć null jako Identifier.");

                String name = leaf.getIdentifier().getName();
                assertNotNull(name, "Nazwa taksonu liścia nie może być nullem.");
                assertFalse(name.trim().isEmpty(), "Liść nie może posiadać pustej/anonimowej nazwy taksonu.");
                actualTaxa.add(name);
            }
            assertEquals(expectedTaxa, actualTaxa,
                    "Zbiór nazw taksonów sąsiada uTBR musi być identyczny ze zbiorem taksonów drzewa bazowego.");
        }
    }

    @Test
    @DisplayName("Metoda tablicowa generateNeighboursOBSOLETE musi zwracać identyczny zbiór jak forEachNeighbour")
    void testGenerateNeighboursObsoleteConsistencyWithForEach() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrootedBalancedTree();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        List<Tree> streamNeighbors = collectNeighbors(utbrUtils, baseTree);
        Tree[] arrayNeighbors = utbrUtils.generateNeighboursOBSOLETE(baseTree);

        assertEquals(streamNeighbors.size(), arrayNeighbors.length,
                "Liczba sąsiadów z generateNeighboursOBSOLETE musi odpowiadać forEachNeighbour.");

        Set<TreeUnrootedHolder> streamSet = new HashSet<>();
        for (Tree t : streamNeighbors) streamSet.add(new TreeUnrootedHolder(t, idGroup));

        Set<TreeUnrootedHolder> arraySet = new HashSet<>();
        for (Tree t : arrayNeighbors) arraySet.add(new TreeUnrootedHolder(t, idGroup));

        assertEquals(streamSet, arraySet,
                "Zbiór topologii wygenerowany przez generateNeighboursOBSOLETE musi być tożsamy z forEachNeighbour.");
    }

    @Test
    @DisplayName("uTBR musi obsłużyć drzewo wejściowe posiadające sztuczny korzeń stopnia 2")
    void testHandlingInputTreeWithDegreeTwoRoot() {
        // Tworzymy drzewo ze sztucznie wprowadzonym korzeniem o 2 dzieciach
        Tree treeWithBifurcatingRoot = TreeCreator.getTreeFromString("(((1,2),(3,4)),(5,6));");
        assertEquals(2, treeWithBifurcatingRoot.getRoot().getChildCount(),
                "Warunek wstępny: drzewo wejściowe powinno mieć korzeń stopnia 2.");

        List<Tree> neighbors = collectNeighbors(utbrUtils, treeWithBifurcatingRoot);

        // Złoty wzorzec dla 6 liści zrównoważonych to dokładnie 30
        assertEquals(30, neighbors.size(),
                "Automatyczna normalizacja wejścia w uTBR powinna wygenerować dokładnie 30 sąsiadów.");

        for (Tree t : neighbors) {
            assertTrue(t.getRoot().getChildCount() >= 3,
                    "Każdy wygenerowany sąsiad musi mieć znormalizowany korzeń o stopniu >= 3.");
        }
    }
}