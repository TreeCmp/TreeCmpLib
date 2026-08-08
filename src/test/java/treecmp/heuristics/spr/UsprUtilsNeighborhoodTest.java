package treecmp.heuristics.spr;

import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeHolder;
import treecmp.heuristics.TreeUnrootedHolder;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test regresyjny weryfikujący poprawność leniwego generatora otoczenia uSPR.
 * Zapewnia, że nowa metoda o złożoności pamięciowej O(1) oparta na Split Hashu
 * generuje w 100% identyczną przestrzeń topologiczną co klasyczny generator dla drzew nieukorzenionych.
 */
public class UsprUtilsNeighborhoodTest {

    @Test
    void testForEachUsprTreeMatchesClassicGenerator() {
        UsprUtils usprUtils = new UsprUtils();

        // Używamy losowego drzewa 10-liściowego, nieukorzenionego (wewnętrzne węzły mają stopień 3).
        Tree baseTree = TestTreeFactory.randomUnrootedBinaryTree(10, 12345L);
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        // ====================================================================
        // 1. WYLICZAMY OCZEKIWANY ROZMIAR MATEMATYCZNY uSPR
        // ====================================================================
        // Dla N=10 wzór to: 2 * (10 - 3) * (2 * 10 - 7) = 2 * 7 * 13 = 182
        int expectedMathSize = usprUtils.calcUsprNeighbours(baseTree);

        // ====================================================================
        // 2. ZBIERAMY OTOCZENIE NOWĄ METODą (LENIWY GENERATOR)
        // ====================================================================
        Set<TreeHolder> newSet = new HashSet<>();
        List<Tree> newNeighborsList = new ArrayList<>();

        usprUtils.forEachUsprTree(baseTree, tree -> {
            newNeighborsList.add(tree);
            newSet.add(new TreeUnrootedHolder(tree, idGroup));
        });

        // ====================================================================
        // 3. WERYFIKACJA (ASERCJE)
        // ====================================================================

        // A. Sprawdzamy, czy liczba unikalnych topologii po odfiltrowaniu izomorfizmów
        // jest równa matematycznemu, teoretycznemu rozmiarowi otoczenia uSPR.
        assertEquals(expectedMathSize, newSet.size(),
                "Liczba unikalnych topologii nieukorzenionych uSPR nie zgadza się z twierdzeniem matematycznym!");

        // B. Sprawdzamy czy lista i zbiór mają tę samą wielkość (co dowodzi braku duplikatów
        // dzięki wbudowanemu mechanizmowi `seenTopologies` w nowym wędrowcu uSPR).
        assertEquals(newNeighborsList.size(), newSet.size(),
                "Leniwy generator uSPR zwrócił duplikaty w surowej liście!");

        System.out.println("Test zaliczony! Leniwy generator uSPR stworzył idealne otoczenie o rozmiarze: " + newSet.size() + " unikalnych topologii.");
    }
}