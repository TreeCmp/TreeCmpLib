package treecmp.heuristics.spr;

import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeHolder;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test regresyjny weryfikujący poprawność leniwego generatora otoczenia SPR.
 * Zapewnia, że nowa metoda o złożoności pamięciowej O(1) generuje w 100%
 * identyczną przestrzeń topologiczną co klasyczny, ciężki generator.
 */
public class SprUtilsNeighborhoodTest {

 /*   @Test
    void testForEachSprTreeMatchesClassicGenerator() {
        SprUtils sprUtils = new SprUtils();

        // Używamy drzewa 10-liściowego (wystarczająco duże otoczenie, by wykryć każdą anomalię,
        // a jednocześnie na tyle małe, by test wykonał się w ułamku sekundy).
        Tree baseTree = TestTreeFactory.tenLeavesRootedTree1();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        // ====================================================================
        // 1. ZBIERAMY OTOCZENIE STARĄ, KLASYCZNĄ METODĄ (Używamy zmienionej nazwy)
        // ====================================================================
        Tree[] oldNeighbors = sprUtils.generateNeighbours(baseTree);

        // Zapisujemy klasyczne drzewa do HashSetu z TreeRootedHolder,
        // który poprawnie rozpoznaje izomorfizm topologiczny.
        Set<TreeHolder> oldSet = new HashSet<>();
        for (Tree t : oldNeighbors) {
            oldSet.add(new TreeRootedHolder(t, idGroup));
        }

        // ====================================================================
        // 2. ZBIERAMY OTOCZENIE NOWĄ METODĄ (LENIWY GENERATOR)
        // ====================================================================
        Set<TreeHolder> newSet = new HashSet<>();
        List<Tree> newNeighborsList = new ArrayList<>();

        sprUtils.forEachSprTree(baseTree, tree -> {
            newNeighborsList.add(tree);
            newSet.add(new TreeRootedHolder(tree, idGroup));
        });

        // ====================================================================
        // 3. WERYFIKACJA (ASERCJE)
        // ====================================================================

        // A. Sprawdzamy liczność wygenerowanego otoczenia
        assertEquals(oldNeighbors.length, newNeighborsList.size(),
                "Leniwy generator zwrócił inną liczbę drzew niż metoda klasyczna!");

        // B. Sprawdzamy liczność po odfiltrowaniu izomorfizmów
        assertEquals(oldSet.size(), newSet.size(),
                "Liczba unikalnych topologii matematycznych (izomorfizmów) nie zgadza się!");

        // C. Ostateczny dowód: sprawdzamy czy Zbiór A zawiera Zbiór B i odwrotnie
        assertTrue(oldSet.containsAll(newSet) && newSet.containsAll(oldSet),
                "Wygenerowane otoczenia nie pokrywają się w 100%! Brakuje topologii lub wygenerowano błędne.");

        System.out.println("Test zaliczony! Leniwy generator stworzył idealne otoczenie o rozmiarze: " + oldSet.size() + " unikalnych drzew.");
    }*/
}