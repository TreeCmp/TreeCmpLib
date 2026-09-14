package treecmp.heuristics.tbr;

import org.junit.jupiter.api.BeforeEach;
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
import treecmp.util.TreeCreator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    // 1. ZAKTUALIZOWANE TESTY MATEMATYCZNE I INTEGRACYJNE
    // =========================================================================

    @Test
    void testExactSizeFor5LeavesUnrooted() {
        // Dla 5 liści otoczenie TBR jest DOKŁADNIE równe otoczeniu SPR (12 drzew).
        // W drzewie 5-liściowym każde cięcie daje poddrzewo bez krawędzi wewnętrznej,
        // więc nie da się go przekorzenić – uTBR redukuje się w 100% do uSPR.
        Tree baseTree = TreeCreator.getTreeFromString("(((1,2),3),4,5);");
        List<Tree> neighbors = collectNeighbors(utbrUtils, baseTree);

        assertEquals(12, neighbors.size(),
                "Dla 5 liści uTBR i uSPR są matematycznie tożsame i muszą wygenerować dokładnie 12 sąsiadów!");
    }

    @Test
    void testUTbrNeighborhoodSizeIsGreaterOrEqualUspr() {
        // Dla >= 6 liści uTBR zawiera właściwe przekorzenienia, więc |uTBR| >= |uSPR|
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
        // Małe drzewo 5-liściowe dla błyskawicznego wyliczenia heurystyki
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
    // 2. NOWE TESTY DLA NOWYCH METOD: isValidUtbrMove oraz createUtbrTree
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
        assertFalse(utbrUtils.isValidUtbrMove(pruneNode, rerootNode, root),
                "Ruch z targetem w korzeniu musi być odrzucony dla uTBR.");

        // 2. Target będący bezpośrednim rodzicem odcinanego węzła musi być odrzucony
        assertFalse(utbrUtils.isValidUtbrMove(pruneNode, rerootNode, pruneNode.getParent()),
                "Target będący rodzicem odcinanego węzła musi być niepoprawny.");

        // 3. Target znajdujący się wewnątrz odciętego poddrzewa musi być odrzucony (zapętlenie)
        assertFalse(utbrUtils.isValidUtbrMove(pruneNode, rerootNode, rerootNode),
                "Target wewnątrz odcinanego poddrzewa musi być odrzucony.");
    }

    @Test
    void testCreateUtbrTreeBranches() {
        // Testujemy obie ścieżki w createUtbrTree:
        // - Gałąź 1: pruneNode == rerootNode (uSPR)
        // - Gałąź 2: pruneNode != rerootNode (właściwe uTBR z przekorzenieniem)
        Tree tree = TreeCreator.getTreeFromString("((((1,2),3),4),5,6);");
        List<Node> allNodes = utbrUtils.getAllNodes(tree);

        Tree sprTreeResult = null;
        Tree tbrTreeResult = null;

        for (Node prune : allNodes) {
            if (prune.isRoot() || prune.getParent() == null) continue;

            List<Node> rerootNodes = utbrUtils.getSubtreeNodes(prune);
            for (Node reroot : rerootNodes) {
                for (Node target : allNodes) {
                    if (utbrUtils.isValidUtbrMove(prune, reroot, target)) {
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

        // Sprawdzamy gałąź uSPR (prune == reroot)
        assertNotNull(sprTreeResult, "createUtbrTree powinno poprawnie wygenerować drzewo dla pruneNode == rerootNode");
        assertEquals(tree.getExternalNodeCount(), sprTreeResult.getExternalNodeCount(),
                "Wynikowe drzewo uSPR musi mieć tę samą liczbę liści.");

        // Sprawdzamy gałąź uTBR (prune != reroot)
        assertNotNull(tbrTreeResult, "createUtbrTree powinno poprawnie wygenerować drzewo dla pruneNode != rerootNode");
        assertEquals(tree.getExternalNodeCount(), tbrTreeResult.getExternalNodeCount(),
                "Wynikowe drzewo uTBR musi mieć tę samą liczbę liści.");
    }
}