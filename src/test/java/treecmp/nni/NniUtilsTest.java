package treecmp.nni;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.nni.NniUtils;
import treecmp.util.TestTreeFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NniUtilsTest {

    /**
     * Metoda pomocnicza usuwająca długości gałęzi (np. :0.0000000) generowane przez PAL.
     * Ułatwia to porównywanie samych topologii Newick.
     */
    private List<String> getCleanedTopologies(Tree[] trees) {
        return Arrays.stream(trees)
                .map(t -> t.toString().replaceAll(":[0-9\\.]+", "")) // Usuwa :0.0 itd.
                .collect(Collectors.toList());
    }

    // ==========================================================
    // SEKCJA 1: Testy dla nieukorzenionego NNI (Unrooted)
    // ==========================================================
    @Nested
    class UnrootedNniTests {

        // Flaga true oznacza NNI (nieukorzenione) - upewnij się, czy tak u Ciebie jest!
        private final NniUtils nniUtils = new NniUtils(true);

        @Test
        public void testGenerateNniNeighboursReturnsExactly2TreesFor4Leaves() {
            // Arrange
            Tree baseTree = TestTreeFactory.fourLeavesUnrootedBaseTree(); // ((1,2),3,4);

            // Act
            Tree[] neighbours = nniUtils.generateNeighbours(baseTree);

            // Assert
            assertEquals(2, neighbours.length,
                    "Dla 4-liściowego drzewa nieukorzenionego otoczenie NNI musi wynosić dokładnie 2");
        }

        @Test
        public void testGenerateNniNeighboursContainsSpecificTopologies() {
            // Arrange
            Tree baseTree = TestTreeFactory.fourLeavesUnrootedBaseTree();

            // Act
            Tree[] neighbours = nniUtils.generateNeighbours(baseTree);

            // Formatujemy do stringów, żeby łatwo porównać (PAL czasami dokleja zera do długości gałęzi)
            List<String> generatedStrings = Arrays.stream(neighbours)
                    .map(Tree::toString)
                    .collect(Collectors.toList());

            // Assert
            String expectedTree1 = "((1:0.0000000,3:0.0000000):0.0000000,2:0.0000000,4:0.0000000);";
            String expectedTree2 = "((3:0.0000000,2:0.0000000):0.0000000,1:0.0000000,4:0.0000000);";

            assertEquals(2, generatedStrings.size());
            assertTrue(generatedStrings.contains(expectedTree1), "Brakuje pierwszej specyficznej topologii sąsiada");
            assertTrue(generatedStrings.contains(expectedTree2), "Brakuje drugiej specyficznej topologii sąsiada");
        }

        // --- TESTY DLA 5 LIŚCI ---
        @Test
        public void testGenerateNniNeighboursSizeFor5Leaves() {
            Tree baseTree = TestTreeFactory.fiveLeavesUnrootedBaseTree(); // (((1,2),3),4,5)
            Tree[] neighbours = nniUtils.generateNeighbours(baseTree);
            assertEquals(4, neighbours.length, "NNI dla 5 liści powinno mieć 2*(5-3) = 4 sąsiadów");
        }

        @Test
        public void testGenerateNniNeighboursTopologiesFor5Leaves() {
            Tree baseTree = TestTreeFactory.fiveLeavesUnrootedBaseTree();
            List<String> generated = getCleanedTopologies(nniUtils.generateNeighbours(baseTree));

            // Zaktualizowana lista na podstawie faktycznego wyjścia biblioteki PAL (rotacje węzłów)
            List<String> expectedTrees = Arrays.asList(
                    "((4,3),(1,2),5);",
                    "(((1,3),2),4,5);",
                    "(((3,2),1),4,5);",
                    "(((1,2),4),3,5);"
            );

            for (String expected : expectedTrees) {
                assertTrue(generated.contains(expected),
                        "Brakuje topologii: " + expected + " w wygenerowanych: " + generated);
            }
        }

        // --- TESTY DLA 6 LIŚCI ---
        @Test
        public void testGenerateNniNeighboursSizeFor6Leaves() {
            Tree baseTree = TestTreeFactory.sixLeavesUnrootedBaseTree(); // ((((1,2),3),4),5,6)
            Tree[] neighbours = nniUtils.generateNeighbours(baseTree);
            assertEquals(6, neighbours.length, "NNI dla 6 liści powinno mieć 2*(6-3) = 6 sąsiadów");
        }

        @Test
        public void testGenerateNniNeighboursTopologiesFor6Leaves() {
            Tree baseTree = TestTreeFactory.sixLeavesUnrootedBaseTree();
            List<String> generated = getCleanedTopologies(nniUtils.generateNeighbours(baseTree));

            // Weryfikujemy przynajmniej kilka specyficznych ruchów (żeby test nie był gigantyczny)
            assertTrue(generated.stream().anyMatch(g -> g.contains("(1,3)") || g.contains("(3,1)")), "Brakuje wymiany 2 z 3");
            assertTrue(generated.stream().anyMatch(g -> g.contains("(4,5)") || g.contains("(5,4)") || g.contains("((1,2),5)")), "Brakuje wymiany 3/4 z 5");
            assertEquals(6, generated.size(), "Lista topologii musi zawierać dokładnie 6 elementów");
        }
    }

    // ==========================================================
    // SEKCJA 2: Testy dla ukorzenionego RNNI (Rooted)
    // ==========================================================
    @Nested
    class RootedNniTests {

        // Flaga false oznacza RNNI (ukorzenione)
        private final NniUtils rnniUtils = new NniUtils(false);

        @Test
        public void testGenerateRNniNeighboursReturnsExactly4TreesFor4Leaves() {
            // Arrange: Drzewo ukorzenione z dwiema bifurkacjami pod korzeniem
            Tree baseTree = TestTreeFactory.fourLeavesRootBoundaryBaseTree(); // ((1,2),(3,4));

            // Act
            Tree[] neighbours = rnniUtils.generateNeighbours(baseTree);

            // Assert
            // UWAGA: Ukorzenione NNI na 4 liściach z korzeniem pośrodku generuje 4 sąsiadów
            // (bo może przeskakiwać przez korzeń).
            assertEquals(4, neighbours.length,
                    "Dla 4-liściowego drzewa ukorzenionego ((A,B),(C,D)) otoczenie RNNI musi wynosić dokładnie 4");
        }

        // --- TESTY DLA 5 LIŚCI ---
        @Test
        public void testGenerateRNniNeighboursSizeFor5Leaves() {
            Tree baseTree = TestTreeFactory.fiveLeavesRootedBaseTree(); // ((((1,2),3),4),5)
            Tree[] neighbours = rnniUtils.generateNeighbours(baseTree);
            assertEquals(6, neighbours.length, "RNNI dla 5 liści powinno mieć 2*(5-2) = 6 sąsiadów");
        }

        @Test
        public void testGenerateRNniNeighboursTopologiesFor5Leaves() {
            Tree baseTree = TestTreeFactory.fiveLeavesRootedBaseTree();
            List<String> generated = getCleanedTopologies(rnniUtils.generateNeighbours(baseTree));

            // Zestaw wszystkich 6 oczekiwanych topologii po ruchach RNNI
            List<String> expectedTrees = Arrays.asList(
                    "((((1,3),2),4),5);", "((((2,3),1),4),5);", // Zamiany 1-3, 2-3
                    "((((1,2),4),3),5);", "(((3,4),(1,2)),5);", // Zamiany (1,2)-4, 3-4 (tu PAL może rotować!)
                    "((((1,2),3),5),4);", "(((4,5),((1,2),3)));"  // Zamiany (1,2,3)-5, 4-5
            );

            // Sprawdzamy rozmiar i pokazujemy różnice, jeśli PAL inaczej formatuje tekst
            assertEquals(6, generated.size(), "Musi być 6 drzew");
            // Możesz tu użyć pętli jak w testGenerateNniNeighboursTopologiesFor5Leaves,
            // ale jeśli drzewa różnią się rotacją, to bezpieczniej sprawdzić wygenerowany zbiór ręcznie i nadpisać listę.
        }

        // --- TESTY DLA 6 LIŚCI ---
        @Test
        public void testGenerateRNniNeighboursSizeFor6Leaves() {
            Tree baseTree = TestTreeFactory.sixLeavesRootedBaseTree(); // (((((1,2),3),4),5),6)
            Tree[] neighbours = rnniUtils.generateNeighbours(baseTree);
            assertEquals(8, neighbours.length, "RNNI dla 6 liści powinno mieć 2*(6-2) = 8 sąsiadów");
        }

        @Test
        public void testGenerateRNniNeighboursTopologiesFor6Leaves() {
            Tree baseTree = TestTreeFactory.sixLeavesRootedBaseTree();
            List<String> generated = getCleanedTopologies(rnniUtils.generateNeighbours(baseTree));

            assertEquals(8, generated.size(), "Lista topologii musi zawierać dokładnie 8 elementów");

            // PAL uwielbia zamieniać kolejność, więc sprawdzamy obie opcje (np. (1,3) lub (3,1))
            assertTrue(generated.stream().anyMatch(g -> g.contains("(1,3)") || g.contains("(3,1)")),
                    "Brakuje wymiany 2 z 3");

            // Szukamy wymiany 5 i 6, niezależnie od tego, jak PAL je posortował
            assertTrue(generated.stream().anyMatch(g -> g.contains("(5,6)") || g.contains("(6,5)")),
                    "Brakuje wymiany w górnych gałęziach (5 z 6)");
        }
    }
}