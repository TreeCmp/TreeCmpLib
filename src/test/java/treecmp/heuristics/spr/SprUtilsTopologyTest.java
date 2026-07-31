package treecmp.heuristics.spr;

import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.spr.SprUtils;
import treecmp.util.TreeCreator;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SprUtilsTopologyTest {

    private void verifyRSprNeighborhood(String treeNewick, String testName) {
        Tree baseTree = TreeCreator.getTreeFromString(treeNewick);
        SprUtils sprUtils = new SprUtils();

        Tree[] sprNeighbors = sprUtils.generateNeighbours(baseTree);

        // Wyliczamy dokładną liczbę unikalnych topologii rSPR ze wzoru Allena i Steela (2001)
        int exactMathSize = sprUtils.calcSprNeighbours(baseTree);

        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
        Set<TreeRootedHolder> uniqueSprTrees = new HashSet<>();

        for (Tree t : sprNeighbors) {
            uniqueSprTrees.add(new TreeRootedHolder(t, idGroup));
        }

        TreeRootedHolder baseTreeHolder = new TreeRootedHolder(baseTree, idGroup);

        // REGUŁA 1: Brak duplikatów
        assertEquals(sprNeighbors.length, uniqueSprTrees.size(),
                testName + " -> Wykryto duplikaty! Oczekiwano " + sprNeighbors.length + " unikalnych.");

        // REGUŁA 2: Brak drzewa bazowego
        assertFalse(uniqueSprTrees.contains(baseTreeHolder),
                testName + " -> Generator rSPR zwrócił drzewo bazowe (odległość 0)!");

        // REGUŁA 3: Perfekcyjna zgodność ze wzorem matematycznym
        assertEquals(exactMathSize, sprNeighbors.length,
                testName + " -> Rozmiar otoczenia rSPR jest niezgodny z twierdzeniem matematycznym!");

        System.out.println(testName + " | Unikalne wygenerowane topologie: " + sprNeighbors.length + " | Wyliczone ze wzoru: " + exactMathSize);
    }

    @Test void test_N5_Balanced() { verifyRSprNeighborhood("(((1,2),(3,4)),5);", "5 liści (Zrównoważone)"); }

    @Test void test_N6_Caterpillar() { verifyRSprNeighborhood("(((((1,2),3),4),5),6);", "6 liści (Grzebień)"); }

    @Test void test_N8_Balanced() { verifyRSprNeighborhood("(((1,2),(3,4)),((5,6),(7,8)));", "8 liści (Zrównoważone)"); }

    @Test void test_N10_Caterpillar() { verifyRSprNeighborhood("(((((((((1,2),3),4),5),6),7),8),9),10);", "10 liści (Grzebień)"); }
}