package treecmp.heuristics.spr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeHolder;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.spr.acc.IncrementalSprWalker;
import treecmp.metrics.topological.acc.MCIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test regresyjny weryfikujący poprawność generatorów otoczenia SPR.
 * Zapewnia, że generowanie drzew "po kolei" (O(1) pamięci) oraz inkrementalny
 * walker odwiedzają w 100% tę samą przestrzeń topologiczną.
 */
public class SprUtilsNeighborhoodTest {

    @Test
    @DisplayName("Weryfikacja sekwencyjnego generatora forEachSprTree względem definicji ruchów SPR")
    void testForEachSprTreeGeneratesCorrectNeighborhoodSequentially() {
        SprUtils sprUtils = new SprUtils();

        Tree baseTree = TestTreeFactory.tenLeavesRootedTree1();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        // ====================================================================
        // 1. ZBIERAMY OTOCZENIE KLASYCZNĄ DEFINICJĄ (PO KOLEI, CONSUMER)
        // ====================================================================
        Set<TreeHolder> classicRawSet = new HashSet<>();
        List<Tree> classicRawList = new ArrayList<>();

        generateRawClassicSprSequentially(sprUtils, baseTree, tree -> {
            classicRawList.add(tree);
            classicRawSet.add(new TreeRootedHolder(tree, idGroup));
        });

        // ====================================================================
        // 2. ZBIERAMY OTOCZENIE NOWYM GENERATOREM PRODUKCYJNYM (forEachSprTree)
        // ====================================================================
        Set<TreeHolder> lazySet = new HashSet<>();
        List<Tree> lazyList = new ArrayList<>();

        sprUtils.forEachSprTree(baseTree, tree -> {
            lazyList.add(tree);
            lazySet.add(new TreeRootedHolder(tree, idGroup));
        });

        // ====================================================================
        // 3. ASERCJE DLA GENERATORA PRODUKCYJNEGO
        // ====================================================================
        assertEquals(classicRawSet.size(), lazySet.size(),
                "Liczba unikalnych topologii SPR po odfiltrowaniu izomorfizmów nie zgadza się!");

        assertTrue(classicRawSet.containsAll(lazySet) && lazySet.containsAll(classicRawSet),
                "Wygenerowane otoczenia nie pokrywają się w 100%! Brakuje topologii lub wygenerowano błędne.");

        System.out.printf("Test zaliczony! Generator forEachSprTree utworzył %d unikalnych drzew.%n", lazySet.size());
    }

    @Test
    @DisplayName("Weryfikacja: IncrementalSprWalker vs SprUtils.forEachSprTree")
    void testIncrementalSprWalkerMatchesSprUtils() {
        SprUtils sprUtils = new SprUtils();
        Tree baseTree = TestTreeFactory.tenLeavesRootedTree1();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        // 1. Zbiór referencyjny z generatora SprUtils
        Set<TreeHolder> expectedSet = new HashSet<>();
        sprUtils.forEachSprTree(baseTree, tree -> expectedSet.add(new TreeRootedHolder(tree, idGroup)));

        // 2. Zbiór wygenerowany przez inkrementalny walker rSPR
        IncrementalSprWalker walker = new IncrementalSprWalker();
        MCIncrementalMetric dummyMetric = new MCIncrementalMetric();
        dummyMetric.initCalculationState(baseTree, baseTree);

        Set<TreeHolder> actualIncrementalSet = new HashSet<>();

        walker.walk(baseTree, dummyMetric, (currentDist, movingNode, targetNode) -> {
            if (sprUtils.isValidSprMove(movingNode, targetNode)) {
                Tree resultTree = sprUtils.createSprTree(baseTree, movingNode, targetNode);
                if (resultTree != null) {
                    if (resultTree instanceof pal.tree.SimpleTree) {
                        ((pal.tree.SimpleTree) resultTree).createNodeList();
                    }
                    actualIncrementalSet.add(new TreeRootedHolder(resultTree, idGroup));
                }
            }
        });

        // 3. Porównanie obu przestrzeni topologicznych
        assertEquals(expectedSet.size(), actualIncrementalSet.size(),
                "IncrementalSprWalker wygenerował inną liczbę unikalnych drzew niż SprUtils!");

        assertTrue(expectedSet.containsAll(actualIncrementalSet) && actualIncrementalSet.containsAll(expectedSet),
                "Drzewa odwiedzone przez IncrementalSprWalker nie pokrywają się ze zbiorem SprUtils!");

        System.out.printf("Test zaliczony! IncrementalSprWalker pokrywa w 100%% otoczenie SPR (%d drzew).%n",
                actualIncrementalSet.size());
    }

    /**
     * Pomocnicza metoda generująca ruchy SPR po kolei bez alokacji tablicy na raz.
     */
    private void generateRawClassicSprSequentially(SprUtils sprUtils, Tree tree, Consumer<Tree> action) {
        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();

        // 1. ext x ext
        for (int i = 0; i < extNum; i++) {
            Node s = tree.getExternalNode(i);
            for (int j = 0; j < extNum; j++) {
                Node t = tree.getExternalNode(j);
                yieldIfValid(sprUtils, tree, s, t, action);
            }
        }
        // 2. int x ext
        for (int i = 0; i < intNum; i++) {
            Node s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < extNum; j++) {
                Node t = tree.getExternalNode(j);
                yieldIfValid(sprUtils, tree, s, t, action);
            }
        }
        // 3. ext x int
        for (int i = 0; i < extNum; i++) {
            Node s = tree.getExternalNode(i);
            for (int j = 0; j < intNum; j++) {
                Node t = tree.getInternalNode(j);
                yieldIfValid(sprUtils, tree, s, t, action);
            }
        }
        // 4. int x int
        for (int i = 0; i < intNum; i++) {
            Node s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < intNum; j++) {
                Node t = tree.getInternalNode(j);
                yieldIfValid(sprUtils, tree, s, t, action);
            }
        }
    }

    private void yieldIfValid(SprUtils sprUtils, Tree baseTree, Node s, Node t, Consumer<Tree> action) {
        if (sprUtils.isValidSprMove(s, t)) {
            Tree res = sprUtils.createSprTree(baseTree, s, t);
            if (res != null) {
                action.accept(res);
            }
        }
    }
}