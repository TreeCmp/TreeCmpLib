package treecmp.tbr;

import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeUnootedHolder;
import treecmp.heuristics.spr.USprUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.util.TestTreeFactory;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UTbrUtilsTopologyTest {

    private void verifyUTbrNeighborhood(Tree baseTree, String testName, int expectedTbrSize) {
        UTbrUtils utbrUtils = new UTbrUtils();
        USprUtils usprUtils = new USprUtils();

        Tree[] tbrNeighbors = utbrUtils.generateNeighbours(baseTree);
        int sprSize = usprUtils.calcUsprNeighbours(baseTree);

        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
        Set<TreeUnootedHolder> uniqueTbrTrees = new HashSet<>();

        for (Tree t : tbrNeighbors) {
            try {
                uniqueTbrTrees.add(new TreeUnootedHolder(t, idGroup));
            } catch (Exception e) {
                fail("Błąd biblioteki PAL podczas haszowania topologii w teście: " + testName);
            }
        }

        TreeUnootedHolder baseTreeHolder = null;
        try {
            baseTreeHolder = new TreeUnootedHolder(baseTree, idGroup);
        } catch (Exception e) {
            fail("Nie można utworzyć holdera dla drzewa bazowego.");
        }

        // REGUŁA 1: Brak duplikatów
        assertEquals(tbrNeighbors.length, uniqueTbrTrees.size(),
                testName + " -> Wykryto duplikaty! Tablica ma " + tbrNeighbors.length + " elementów, ale unikalnych jest " + uniqueTbrTrees.size());

        // REGUŁA 2: Brak drzewa bazowego (odległość > 0)
        assertFalse(uniqueTbrTrees.contains(baseTreeHolder),
                testName + " -> Generator zwrócił drzewo identyczne z bazowym (dystans 0)!");

        // REGUŁA 3: Otoczenie uTBR >= uSPR
        assertTrue(tbrNeighbors.length >= sprSize,
                testName + " -> Otoczenie uTBR (" + tbrNeighbors.length + ") jest mniejsze niż uSPR (" + sprSize + ")!");

        // REGUŁA 4: Twarde sprawdzenie dokładnego rozmiaru ze Złotego Wzorca
        assertEquals(expectedTbrSize, tbrNeighbors.length,
                testName + " -> Rozmiar uTBR jest niezgodny z oczekiwanym matematycznym wzorcem dla tej topologii!");

        System.out.println(testName + " | Rozmiar uSPR: " + sprSize + " | Rozmiar uTBR: " + tbrNeighbors.length + " (Oczekiwane: " + expectedTbrSize + ")");
    }

    // ==========================================
    // PRZYPADKI TESTOWE (Od N=4 do N=15)
    // ==========================================

    @Test void test_N4_Star() { verifyUTbrNeighborhood(TestTreeFactory.fourLeavesUnrootedStarTree(), "4 liście (Gwiazda)", 2); }

    @Test void test_N5_Caterpillar() { verifyUTbrNeighborhood(TestTreeFactory.fiveLeavesUnrootedCaterpillarTree(), "5 liści (Grzebień)", 12); }

    @Test void test_N6_Balanced() { verifyUTbrNeighborhood(TestTreeFactory.sixLeavesUnrootedBalancedTree(), "6 liści (Zrównoważone)", 30); }

    @Test void test_N6_Caterpillar() { verifyUTbrNeighborhood(TestTreeFactory.sixLeavesUnrootedCaterpillarTree(), "6 liści (Grzebień)", 34); }

    @Test void test_N8_Balanced() { verifyUTbrNeighborhood(TestTreeFactory.eightLeavesUnrootedBalancedTree(), "8 liści (Idealnie Zrównoważone)", 106); }

    @Test void test_N8_Caterpillar() { verifyUTbrNeighborhood(TestTreeFactory.eightLeavesUnrootedCaterpillarTree(), "8 liści (Grzebień)", 130); }

    @Test void test_N10_Balanced() { verifyUTbrNeighborhood(TestTreeFactory.tenLeavesUnrootedBalancedTree(), "10 liści (Zrównoważone)", 223); }

    @Test void test_N10_Caterpillar() { verifyUTbrNeighborhood(TestTreeFactory.tenLeavesUnrootedCaterpillarTree(), "10 liści (Grzebień)", 322); }

    @Test void test_N15_Complex() { verifyUTbrNeighborhood(TestTreeFactory.fifteenLeavesUnrootedComplexTree(), "15 liści (Złożone, losowe)", 1008); }
}