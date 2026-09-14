package treecmp.heuristics.tbr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.util.TreeCreator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TbrUtilsTest {

    private TbrUtils tbrUtils;
    private SprUtils sprUtils;

    @BeforeEach
    void setUp() {
        tbrUtils = new TbrUtils();
        sprUtils = new SprUtils();
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
    void testExactSizeFor4LeavesRooted() {
        // Dla n=4 rTBR redukuje się do rSPR (odcięte poddrzewa nie mają krawędzi wewnętrznych do przekorzenienia).
        // Zgodnie ze wzorem Allena-Steela rozmiar zależy od głębokości węzłów (kary gamma):
        // 1. Drzewo grzebieniowe (caterpillar): gamma = 1 -> rozmiar = 12 - 2 = 10
        Tree caterpillarTree = TreeCreator.getTreeFromString("(((1,2),3),4);");
        List<Tree> caterpillarNeighbors = collectNeighbors(tbrUtils, caterpillarTree);

        assertEquals(10, caterpillarNeighbors.size(),
                "Dla drzewa grzebieniowego 4-liściowego otoczenie rTBR musi wynosić dokładnie 10 drzew.");

        // 2. Drzewo zbalansowane: gamma = 0 -> rozmiar = 12 - 0 = 12
        Tree balancedTree = TreeCreator.getTreeFromString("((1,2),(3,4));");
        List<Tree> balancedNeighbors = collectNeighbors(tbrUtils, balancedTree);

        assertEquals(12, balancedNeighbors.size(),
                "Dla drzewa zbalansowanego 4-liściowego otoczenie rTBR musi wynosić dokładnie 12 drzew.");
    }

    @Test
    void testTbrNeighborhoodSizeIsGreaterOrEqualSpr() {
        // Otoczenie rTBR musi zawierać w sobie rSPR, a dla drzew >= 6 liści zawiera dodatkowe ruchy
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),4),(5,6));");

        List<Tree> tbrNeighbors = collectNeighbors(tbrUtils, baseTree);
        List<Tree> sprNeighbors = collectNeighbors(sprUtils, baseTree);

        assertFalse(tbrNeighbors.isEmpty(), "Otoczenie TBR nie powinno być puste dla drzewa 6-liściowego.");
        assertFalse(sprNeighbors.isEmpty(), "Otoczenie SPR nie powinno być puste dla drzewa 6-liściowego.");

        assertTrue(tbrNeighbors.size() >= sprNeighbors.size(),
                "Otoczenie rTBR (" + tbrNeighbors.size() + ") musi być >= rSPR (" + sprNeighbors.size() + ")");
    }

    @Test
    void testTbrNeighborsAreAllUnique() {
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),4),(5,6));");
        List<Tree> tbrNeighbors = collectNeighbors(tbrUtils, baseTree);

        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
        TreeRootedHolder baseTreeHolder = new TreeRootedHolder(baseTree, idGroup);
        Set<TreeRootedHolder> uniqueTrees = new HashSet<>();

        for (Tree t : tbrNeighbors) {
            TreeRootedHolder holder = new TreeRootedHolder(t, idGroup);
            assertNotEquals(baseTreeHolder, holder,
                    "Otoczenie TBR nie może zawierać drzewa tożsamego z bazowym!");
            uniqueTrees.add(holder);
        }

        assertEquals(tbrNeighbors.size(), uniqueTrees.size(),
                "Wszystkie wygenerowane drzewa TBR przekazane do Consumer muszą być unikalne izomorficznie!");
    }

    @Test
    void testTbrNeighborsPreserveLeafSet() {
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),4),(5,6));");
        List<Tree> tbrNeighbors = collectNeighbors(tbrUtils, baseTree);
        int expectedLeaves = baseTree.getExternalNodeCount();

        for (Tree neighbor : tbrNeighbors) {
            assertEquals(expectedLeaves, neighbor.getExternalNodeCount(),
                    "Żaden ruch TBR nie może modyfikować liczby liści.");
            assertNotNull(neighbor.getRoot(), "Drzewo po modyfikacji musi mieć poprawny korzeń.");
            assertNull(neighbor.getRoot().getParent(), "Korzeń nie może posiadać wskaźnika na rodzica.");
        }
    }

    @Test
    void testAllTbrNeighborsAreExactlyOneStepAway() throws TreeCmpException {
        // Małe drzewo 5-liściowe dla błyskawicznego wyliczenia heurystyki
        Tree baseTree = TreeCreator.getTreeFromString("(((1,2),3),(4,5));");
        List<Tree> tbrNeighbors = collectNeighbors(tbrUtils, baseTree);

        TbrHeuristicMetric tbrMetric = new TbrHeuristicMetric(new RFClusterMetric(), true, "RFC");

        for (Tree neighbor : tbrNeighbors) {
            double dist = tbrMetric.getDistance(baseTree, neighbor);
            assertEquals(1.0, dist, 0.000001,
                    "Każdy sąsiad wygenerowany przez TbrUtils musi być w odległości dokładnie 1 ruchu TBR.");
        }
    }

    // =========================================================================
    // 2. TESTY WALIDACJI RUCHU I TWORZENIA DRZEWA (createTbrTree / isValidTbrMove)
    // =========================================================================

    @Test
    void testIsValidTbrMoveRejections() {
        Tree tree = TreeCreator.getTreeFromString("((((1,2),3),4),(5,6));");
        List<Node> allNodes = tbrUtils.getAllNodes(tree);

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

        // 1. Target będący bezpośrednim rodzicem odcinanego węzła musi być odrzucony
        assertFalse(tbrUtils.isValidTbrMove(pruneNode, rerootNode, pruneNode.getParent()),
                "Target będący rodzicem odcinanego węzła musi być odrzucony.");

        // 2. Target znajdujący się wewnątrz odciętego poddrzewa musi być odrzucony (zapobiega pętli)
        assertFalse(tbrUtils.isValidTbrMove(pruneNode, rerootNode, rerootNode),
                "Target wewnątrz odcinanego poddrzewa musi być odrzucony.");

        // 3. Zarówno target, jak i rodzic pruneNode w korzeniu -> ruch trywialny/niedozwolony
        Node directRootChild = root.getChild(0);
        assertFalse(tbrUtils.isValidTbrMove(directRootChild, directRootChild.getChild(0), root),
                "Ruch, gdzie target i rodzic cięcia to korzeń, musi być odrzucony.");
    }

    @Test
    void testCreateTbrTreeBranches() {
        // Testujemy obie gałęzie:
        // - Gałąź 1: pruneNode == rerootNode (rSPR)
        // - Gałąź 2: pruneNode != rerootNode (rTBR z fizycznym przekorzenieniem)
        Tree tree = TreeCreator.getTreeFromString("((((1,2),3),4),(5,6));");
        List<Node> allNodes = tbrUtils.getAllNodes(tree);

        Tree sprTreeResult = null;
        Tree tbrTreeResult = null;

        for (Node prune : allNodes) {
            if (prune.isRoot() || prune.getParent() == null) continue;

            List<Node> rerootNodes = tbrUtils.getSubtreeNodes(prune);
            for (Node reroot : rerootNodes) {
                for (Node target : allNodes) {
                    if (tbrUtils.isValidTbrMove(prune, reroot, target)) {
                        if (prune == reroot && sprTreeResult == null) {
                            sprTreeResult = tbrUtils.createTbrTree(tree, prune, reroot, target);
                        } else if (prune != reroot && tbrTreeResult == null) {
                            tbrTreeResult = tbrUtils.createTbrTree(tree, prune, reroot, target);
                        }
                    }
                    if (sprTreeResult != null && tbrTreeResult != null) break;
                }
                if (sprTreeResult != null && tbrTreeResult != null) break;
            }
            if (sprTreeResult != null && tbrTreeResult != null) break;
        }

        // Sprawdzamy gałąź rSPR
        assertNotNull(sprTreeResult, "createTbrTree powinno poprawnie wygenerować drzewo dla pruneNode == rerootNode.");
        assertEquals(tree.getExternalNodeCount(), sprTreeResult.getExternalNodeCount(),
                "Drzewo rSPR musi zachować identyczną liczbę liści.");

        // Sprawdzamy gałąź rTBR
        assertNotNull(tbrTreeResult, "createTbrTree powinno poprawnie wygenerować drzewo dla pruneNode != rerootNode.");
        assertEquals(tree.getExternalNodeCount(), tbrTreeResult.getExternalNodeCount(),
                "Drzewo rTBR musi zachować identyczną liczbę liści.");
    }
}