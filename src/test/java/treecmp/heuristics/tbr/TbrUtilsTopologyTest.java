package treecmp.heuristics.tbr;

import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.spr.SprUtils;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TbrUtilsTopologyTest {

    private void verifyTbrNeighborhood(Tree baseTree, String testName, int expectedTbrSize) {
        TbrUtils tbrUtils = new TbrUtils();
        SprUtils sprUtils = new SprUtils();

        List<Tree> tbrNeighbors = new ArrayList<>();
        tbrUtils.forEachNeighbour(baseTree, tbrNeighbors::add);
        int sprSize = sprUtils.calcSprNeighbours(baseTree);

        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
        Set<TreeRootedHolder> uniqueTbrTrees = new HashSet<>();

        for (Tree t : tbrNeighbors) {
            assertEquals(baseTree.getExternalNodeCount(), t.getExternalNodeCount(),
                    testName + " -> Wykryto utratę liści w drzewie sąsiada!");
            uniqueTbrTrees.add(new TreeRootedHolder(t, idGroup));
        }

        TreeRootedHolder baseTreeHolder = new TreeRootedHolder(baseTree, idGroup);

        // REGUŁA 1: Brak duplikatów
        assertEquals(tbrNeighbors.size(), uniqueTbrTrees.size(),
                testName + " -> Wykryto duplikaty! Oczekiwano " + tbrNeighbors.size() + " unikalnych.");

        // REGUŁA 2: Brak drzewa bazowego
        assertFalse(uniqueTbrTrees.contains(baseTreeHolder),
                testName + " -> Generator zwrócił drzewo bazowe (odległość 0)!");

        // REGUŁA 3: Otoczenie rTBR >= rSPR
        assertTrue(tbrNeighbors.size() >= sprSize,
                testName + " -> Otoczenie rTBR (" + tbrNeighbors.size() + ") mniejsze niż rSPR (" + sprSize + ")!");

        // REGUŁA 4: Sprawdzenie dokładnego rozmiaru ze Złotego Wzorca
        assertEquals(expectedTbrSize, tbrNeighbors.size(),
                testName + " -> Rozmiar rTBR jest niezgodny z oczekiwanym wzorcem dla tej topologii!");
    }

    // ==========================================
    // PRZYPADKI TESTOWE DLA rTBR (Rooted)
    // ==========================================

    @Test
    void test_N4_Caterpillar() {
        verifyTbrNeighborhood(TestTreeFactory.fourLeavesRootedCaterpillarTree(), "4 liście (Grzebień)", 10);
    }

    @Test
    void test_N5_Balanced() {
        verifyTbrNeighborhood(TestTreeFactory.fiveLeavesRootedBalancedTree(), "5 liści (Zrównoważone)", 26);
    }

    @Test
    void test_N5_Caterpillar() {
        verifyTbrNeighborhood(TestTreeFactory.fiveLeavesRootedCaterpillarTree(), "5 liści (Grzebień)", 30);
    }

    @Test
    void test_N6_Balanced() {
        verifyTbrNeighborhood(TestTreeFactory.sixLeavesRootedBalancedTree(), "6 liści (Zrównoważone)", 60);
    }

    @Test
    void test_N6_Caterpillar() {
        verifyTbrNeighborhood(TestTreeFactory.sixLeavesRootedCaterpillarTree(), "6 liści (Grzebień)", 66);
    }

    @Test
    void test_N8_Balanced() {
        verifyTbrNeighborhood(TestTreeFactory.eightLeavesRootedBalancedTree(), "8 liści (Idealnie Zrównoważone)", 172);
    }

    @Test
    void test_N8_Caterpillar() {
        verifyTbrNeighborhood(TestTreeFactory.eightLeavesRootedCaterpillarTree(), "8 liści (Grzebień)", 202);
    }

    @Test
    void test_N10_Balanced() {
        verifyTbrNeighborhood(TestTreeFactory.tenLeavesRootedBalancedTree(), "10 liści (Zrównoważone)", 380);
    }

    @Test
    void test_N10_Caterpillar() {
        verifyTbrNeighborhood(TestTreeFactory.tenLeavesRootedCaterpillarTree(), "10 liści (Grzebień)", 450);
    }

    @Test
    void test_N15_Complex() {
        verifyTbrNeighborhood(TestTreeFactory.fifteenLeavesRootedComplexTree(), "15 liści (Złożone)", 1208);
    }
}