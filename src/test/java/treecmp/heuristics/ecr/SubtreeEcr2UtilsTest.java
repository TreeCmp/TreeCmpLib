package treecmp.heuristics.ecr;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SubtreeEcr2UtilsTest {

    private List<String> getCleanedTopologies(List<Tree> trees) {
        return trees.stream()
                .map(t -> t.toString().replaceAll(":[0-9\\.]+", ""))
                .collect(Collectors.toList());
    }

    // ==========================================================
    // SEKCJA 1: Testy dla nieukorzenionego 2-sECR (Unrooted)
    // ==========================================================
    @Nested
    class UnrootedEcr2Tests {

        private final SubtreeEcr2Utils ecr2Utils = new SubtreeEcr2Utils(true);

        @Test
        public void testForEachNeighbourReturns0For4Leaves() {
            // Dla 4 liści (1 krawędź wewnętrzna) ruch 2-sECR nie jest możliwy!
            Tree baseTree = TestTreeFactory.fourLeavesUnrootedStarTree();

            List<Tree> neighbours = new ArrayList<>();
            ecr2Utils.forEachNeighbour(baseTree, neighbours::add);

            assertEquals(0, neighbours.size(),
                    "Dla 4-liściowego drzewa nieukorzenionego otoczenie 2-sECR musi wynosić 0");
        }

        @Test
        public void testForEachNeighbourSizeFor5Leaves() {
            // Dla 5 liści mamy dokładnie 2 krawędzie wewnętrzne i 1 punkt styku.
            // Powinna powstać dokładnie 1 gwiazda stopnia 5, czyli 14 nowych topologii.
            Tree baseTree = TestTreeFactory.fiveLeavesUnrootedCaterpillarTree();

            List<Tree> neighbours = new ArrayList<>();
            ecr2Utils.forEachNeighbour(baseTree, neighbours::add);

            assertEquals(14, neighbours.size(),
                    "2-sECR dla 5 liści powinno wygenerować 14 sąsiadów (1 gwiazda * 14)");
        }

        @Test
        public void testForEachNeighbourSizeFor6Leaves() {
            // Drzewo Caterpillar dla 6 liści ma 3 krawędzie wew. (e1, e2, e3).
            // Mamy 2 klastry 2-krawędziowe: {e1, e2} oraz {e2, e3}.
            // Oba klastry współdzielą krawędź e2. Ponieważ ruchy NNI na współdzielonej krawędzi e2
            // (których jest dokładnie 2) zostaną wygenerowane przez oba klastry,
            // użyty w algorytmie HashSet usunie te 2 duplikaty izomorficzne.
            // Wynik: (2 gwiazdy * 14) - 2 duplikaty = 26 unikalnych sąsiadów.
            Tree baseTree = TestTreeFactory.sixLeavesUnrootedCaterpillarTree();

            List<Tree> neighbours = new ArrayList<>();
            ecr2Utils.forEachNeighbour(baseTree, neighbours::add);

            assertEquals(26, neighbours.size(),
                    "2-sECR dla 6 liści (Caterpillar) powinno mieć 26 unikalnych sąsiadów");
        }
    }

    // ==========================================================
    // SEKCJA 2: Testy dla ukorzenionego 2-sECR (Rooted)
    // ==========================================================
    @Nested
    class RootedEcr2Tests {

        private final SubtreeEcr2Utils rEcr2Utils = new SubtreeEcr2Utils(false);

        @Test
        public void testForEachNeighbourSizeFor5Leaves() {
            // Ukorzenione drzewo 5-liściowe ma (5-2) = 3 krawędzie wewnętrzne.
            // W układzie Caterpillar tworzą one łańcuch, dając 2 klastry 2-krawędziowe,
            // które współdzielą jedną środkową krawędź (identycznie jak w nieukorzenionym 6-liściowym).
            // Algorytm eliminuje 2 powielone ruchy RNNI na współdzielonej krawędzi.
            // Wynik: 14 + 14 - 2 = 26 unikalnych ukorzenionych sąsiadów.
            Tree baseTree = TestTreeFactory.fiveLeavesRootedCaterpillarTree();

            List<Tree> neighbours = new ArrayList<>();
            rEcr2Utils.forEachNeighbour(baseTree, neighbours::add);

            assertEquals(26, neighbours.size(),
                    "Rozmiar otoczenia ukorzenionego 2-sECR dla 5 liści (Caterpillar) musi wynosić 26");
        }
    }
}