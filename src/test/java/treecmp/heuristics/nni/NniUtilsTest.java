package treecmp.heuristics.nni;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NniUtilsTest {

    /**
     * Metoda pomocnicza usuwająca długości gałęzi (np. :0.0000000) generowane przez PAL.
     * Ułatwia to porównywanie samych topologii Newick.
     */
    private List<String> getCleanedTopologies(List<Tree> trees) {
        return trees.stream()
                .map(t -> t.toString().replaceAll(":[0-9\\.]+", "")) // Usuwa :0.0 itd.
                .collect(Collectors.toList());
    }

    // ==========================================================
    // SEKCJA 1: Testy dla nieukorzenionego NNI (Unrooted)
    // ==========================================================
    @Nested
    class UnrootedNniTests {

        // Flaga true oznacza NNI (nieukorzenione)
        private final NniUtils nniUtils = new NniUtils(true);

        @Test
        public void testGenerateNniNeighboursReturnsExactly2TreesFor4Leaves() {
            // Arrange
            Tree baseTree = TestTreeFactory.fourLeavesUnrootedStarTree(); // ((1,2),3,4);
            List<Tree> neighbours = new ArrayList<>();

            // Act - Zbieramy wyniki z nowej, zoptymalizowanej metody
            nniUtils.forEachNniTree(baseTree, neighbours::add);

            // Assert
            assertEquals(2, neighbours.size(),
                    "Dla 4-liściowego drzewa nieukorzenionego otoczenie NNI musi wynosić dokładnie 2");
        }

        @Test
        public void testGenerateNniNeighboursContainsSpecificTopologies() {
            // Arrange
            Tree baseTree = TestTreeFactory.fourLeavesUnrootedStarTree();
            List<Tree> neighbours = new ArrayList<>();

            // Act
            nniUtils.forEachNniTree(baseTree, neighbours::add);

            // Formatujemy do stringów, żeby łatwo porównać
            List<String> generatedStrings = neighbours.stream()
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
            Tree baseTree = TestTreeFactory.fiveLeavesUnrootedCaterpillarTree(); // (((1,2),3),4,5)
            List<Tree> neighbours = new ArrayList<>();
            nniUtils.forEachNniTree(baseTree, neighbours::add);

            assertEquals(4, neighbours.size(), "NNI dla 5 liści powinno mieć 2*(5-3) = 4 sąsiadów");
        }

        @Test
        public void testGenerateNniNeighboursTopologiesFor5Leaves() {
            Tree baseTree = TestTreeFactory.fiveLeavesUnrootedCaterpillarTree();
            List<Tree> neighbours = new ArrayList<>();
            nniUtils.forEachNniTree(baseTree, neighbours::add);

            List<String> generated = getCleanedTopologies(neighbours);

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
            Tree baseTree = TestTreeFactory.sixLeavesUnrootedCaterpillarTree(); // ((((1,2),3),4),5,6)
            List<Tree> neighbours = new ArrayList<>();
            nniUtils.forEachNniTree(baseTree, neighbours::add);

            assertEquals(6, neighbours.size(), "NNI dla 6 liści powinno mieć 2*(6-3) = 6 sąsiadów");
        }

        @Test
        public void testGenerateNniNeighboursTopologiesFor6Leaves() {
            Tree baseTree = TestTreeFactory.sixLeavesUnrootedCaterpillarTree();
            List<Tree> neighbours = new ArrayList<>();
            nniUtils.forEachNniTree(baseTree, neighbours::add);

            List<String> generated = getCleanedTopologies(neighbours);

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
            // Arrange
            Tree baseTree = TestTreeFactory.fourLeavesBalancedTree1(); // ((1,2),(3,4));
            List<Tree> neighbours = new ArrayList<>();

            // Act
            rnniUtils.forEachNniTree(baseTree, neighbours::add);

            // Assert
            assertEquals(4, neighbours.size(),
                    "Dla 4-liściowego drzewa ukorzenionego ((A,B),(C,D)) otoczenie RNNI musi wynosić dokładnie 4");
        }

        // --- TESTY DLA 5 LIŚCI ---
        @Test
        public void testGenerateRNniNeighboursSizeFor5Leaves() {
            Tree baseTree = TestTreeFactory.fiveLeavesRootedCaterpillarTree(); // ((((1,2),3),4),5)
            List<Tree> neighbours = new ArrayList<>();
            rnniUtils.forEachNniTree(baseTree, neighbours::add);

            assertEquals(6, neighbours.size(), "RNNI dla 5 liści powinno mieć 2*(5-2) = 6 sąsiadów");
        }

        @Test
        public void testGenerateRNniNeighboursTopologiesFor5Leaves() {
            Tree baseTree = TestTreeFactory.fiveLeavesRootedCaterpillarTree();
            List<Tree> neighbours = new ArrayList<>();
            rnniUtils.forEachNniTree(baseTree, neighbours::add);

            List<String> generated = getCleanedTopologies(neighbours);

            assertEquals(6, generated.size(), "Musi być 6 drzew");
        }

        // --- TESTY DLA 6 LIŚCI ---
        @Test
        public void testGenerateRNniNeighboursSizeFor6Leaves() {
            Tree baseTree = TestTreeFactory.sixLeavesRootedCaterpillarTree(); // (((((1,2),3),4),5),6)
            List<Tree> neighbours = new ArrayList<>();
            rnniUtils.forEachNniTree(baseTree, neighbours::add);

            assertEquals(8, neighbours.size(), "RNNI dla 6 liści powinno mieć 2*(6-2) = 8 sąsiadów");
        }

        @Test
        public void testGenerateRNniNeighboursTopologiesFor6Leaves() {
            Tree baseTree = TestTreeFactory.sixLeavesRootedCaterpillarTree();
            List<Tree> neighbours = new ArrayList<>();
            rnniUtils.forEachNniTree(baseTree, neighbours::add);

            List<String> generated = getCleanedTopologies(neighbours);

            assertEquals(8, generated.size(), "Lista topologii musi zawierać dokładnie 8 elementów");

            assertTrue(generated.stream().anyMatch(g -> g.contains("(1,3)") || g.contains("(3,1)")),
                    "Brakuje wymiany 2 z 3");

            assertTrue(generated.stream().anyMatch(g -> g.contains("(5,6)") || g.contains("(6,5)")),
                    "Brakuje wymiany w górnych gałęziach (5 z 6)");
        }
    }
}