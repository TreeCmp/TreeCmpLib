package treecmp.heuristics.tbr;

import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TbrUtilsTopologyTest {

    // Sygnatura przyjmuje teraz gotowy obiekt Tree prosto z TestTreeFactory
    private void verifyTbrNeighborhood(Tree baseTree, String testName, int expectedTbrSize) {
        TbrUtils tbrUtils = new TbrUtils();
        SprUtils sprUtils = new SprUtils();

        Tree[] tbrNeighbors = tbrUtils.generateNeighbours(baseTree);
        int sprSize = sprUtils.calcSprNeighbours(baseTree);

        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
        Set<TreeRootedHolder> uniqueTbrTrees = new HashSet<>();

        for (Tree t : tbrNeighbors) {
            uniqueTbrTrees.add(new TreeRootedHolder(t, idGroup));
        }

        TreeRootedHolder baseTreeHolder = new TreeRootedHolder(baseTree, idGroup);

        // REGUŁA 1: Brak duplikatów
        assertEquals(tbrNeighbors.length, uniqueTbrTrees.size(),
                testName + " -> Wykryto duplikaty! Oczekiwano " + tbrNeighbors.length + " unikalnych.");

        // REGUŁA 2: Brak drzewa bazowego
        assertFalse(uniqueTbrTrees.contains(baseTreeHolder),
                testName + " -> Generator zwrócił drzewo bazowe (odległość 0)!");

        // REGUŁA 3: Otoczenie rTBR >= rSPR (Zawsze prawdziwe dla tej samej topologii)
        assertTrue(tbrNeighbors.length >= sprSize,
                testName + " -> Otoczenie rTBR (" + tbrNeighbors.length + ") mniejsze niż rSPR (" + sprSize + ")!");

        // REGUŁA 4 (ZAMIAST WZORU): Twarde sprawdzenie dokładnego, zamrożonego rozmiaru
        assertEquals(expectedTbrSize, tbrNeighbors.length,
                testName + " -> Rozmiar rTBR jest niezgodny z oczekiwanym matematycznym wzorcem dla tej topologii!");

        System.out.println(testName + " | Rozmiar rSPR: " + sprSize + " | Rozmiar rTBR: " + tbrNeighbors.length + " (Oczekiwane: " + expectedTbrSize + ")");
    }

    // ==========================================
    // PRZYPADKI TESTOWE
    // (Zastąp XXX wartościami z konsoli z poprzedniego poprawnego uruchomienia!)
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