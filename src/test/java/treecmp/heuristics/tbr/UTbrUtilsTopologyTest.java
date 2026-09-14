package treecmp.heuristics.tbr;

import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeUnrootedHolder;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UTbrUtilsTopologyTest {

    private void verifyUTbrNeighborhood(Tree baseTree, String testName, int expectedTbrSize) {
        UTbrUtils utbrUtils = new UTbrUtils();
        UsprUtils usprUtils = new UsprUtils();

        List<Tree> tbrNeighbors = new ArrayList<>();
        utbrUtils.forEachNeighbour(baseTree, tbrNeighbors::add);
        int sprSize = usprUtils.calcUsprNeighbours(baseTree);

        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
        Set<TreeUnrootedHolder> uniqueTbrTrees = new HashSet<>();

        for (Tree t : tbrNeighbors) {
            assertEquals(baseTree.getExternalNodeCount(), t.getExternalNodeCount(),
                    testName + " -> Wykryto utratę liści w drzewie sąsiada!");
            try {
                uniqueTbrTrees.add(new TreeUnrootedHolder(t, idGroup));
            } catch (Exception e) {
                fail("Błąd biblioteki PAL podczas haszowania topologii w teście: " + testName);
            }
        }

        TreeUnrootedHolder baseTreeHolder = null;
        try {
            baseTreeHolder = new TreeUnrootedHolder(baseTree, idGroup);
        } catch (Exception e) {
            fail("Nie można utworzyć holdera dla drzewa bazowego.");
        }

        // REGUŁA 1: Brak duplikatów
        assertEquals(tbrNeighbors.size(), uniqueTbrTrees.size(),
                testName + " -> Wykryto duplikaty! Lista ma " + tbrNeighbors.size() + " elementów, ale unikalnych jest " + uniqueTbrTrees.size());

        // REGUŁA 2: Brak drzewa bazowego
        assertFalse(uniqueTbrTrees.contains(baseTreeHolder),
                testName + " -> Generator zwrócił drzewo identyczne z bazowym (dystans 0)!");

        // REGUŁA 3: Otoczenie uTBR >= uSPR
        assertTrue(tbrNeighbors.size() >= sprSize,
                testName + " -> Otoczenie uTBR (" + tbrNeighbors.size() + ") jest mniejsze niż uSPR (" + sprSize + ")!");

        // REGUŁA 4: Sprawdzenie dokładnego rozmiaru ze Złotego Wzorca
        assertEquals(expectedTbrSize, tbrNeighbors.size(),
                testName + " -> Rozmiar uTBR jest niezgodny z oczekiwanym wzorcem dla tej topologii!");
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

    @Test void test_N10_Balanced() { verifyUTbrNeighborhood(TestTreeFactory.tenLeavesUnrootedBalancedTree(), "10 liści (Zrównoważone)", 246); }

    @Test void test_N10_Caterpillar() { verifyUTbrNeighborhood(TestTreeFactory.tenLeavesUnrootedCaterpillarTree(), "10 liści (Grzebień)", 322); }

    @Test void test_N15_Complex() { verifyUTbrNeighborhood(TestTreeFactory.fifteenLeavesUnrootedComplexTree(), "15 liści (Złożone, losowe)", 1008); }
}
