package treecmp.spr;

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
        // 1. ZBIERAMY OTOCZENIE STARĄ, KLASYCZNĄ METODĄ
        // ====================================================================
        Tree[] oldNeighbors = usprUtils.generateNeighbours(baseTree);

        // Zapisujemy klasyczne drzewa do HashSetu używając TreeUnrootedHolder,
        // który poprawnie rozpoznaje izomorfizm topologiczny dla drzew bez wiodącego korzenia.
        Set<TreeHolder> oldSet = new HashSet<>();
        for (Tree t : oldNeighbors) {
            oldSet.add(new TreeUnrootedHolder(t, idGroup));
        }

        // ====================================================================
        // 2. ZBIERAMY OTOCZENIE NOWĄ METODĄ (LENIWY GENERATOR)
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

        // A. Sprawdzamy surową liczność wygenerowanego otoczenia (czy pętle nie gubią gałęzi)
        assertEquals(oldNeighbors.length, newNeighborsList.size(),
                "Leniwy generator uSPR zwrócił inną liczbę drzew niż metoda klasyczna!");

        // B. Sprawdzamy liczność po matematycznym odfiltrowaniu izomorfizmów
        assertEquals(oldSet.size(), newSet.size(),
                "Liczba unikalnych topologii matematycznych (izomorfizmów nieukorzenionych) nie zgadza się!");

        // C. Ostateczny dowód: wzajemne zawieranie się zbiorów
        assertTrue(oldSet.containsAll(newSet) && newSet.containsAll(oldSet),
                "Wygenerowane otoczenia uSPR nie pokrywają się w 100%! Brakuje topologii lub nowa logika Split Hasha zawiodła.");

        System.out.println("Test zaliczony! Leniwy generator uSPR stworzył idealne otoczenie o rozmiarze: " + oldSet.size() + " unikalnych topologii.");
    }
}