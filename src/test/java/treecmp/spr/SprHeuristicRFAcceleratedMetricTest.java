package treecmp.spr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pal.tree.Tree;
import treecmp.util.TestTreeFactory;

// Pamiętaj o zaimportowaniu klasy, która w Twoim projekcie parsuje stringi na drzewa, np.:
// import treecmp.common.TreeCreator;

import treecmp.heuristics.spr.SprHeuristicRFCAcceleratedMetric;
import treecmp.heuristics.spr.SprHeuristicRFCMetric;
import treecmp.util.TreeCreator;

import java.util.stream.Stream;

class SprHeuristicRFAcceleratedMetricTest {

    // Tolerancja błędu dla porównywania liczb zmiennoprzecinkowych (double)
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        // Tu możesz dodać kod wykonywany przed każdym testem, jeśli będzie potrzebny
    }

    @AfterEach
    void tearDown() {
        // Tu możesz dodać kod sprzątający po każdym teście
    }

    // Ten test uruchomi się wielokrotnie dla każdej pary z metody dostarczającej dane
    @ParameterizedTest(name = "Test dla {2} liści")
    @MethodSource("provideTreePairsForTesting")
    void testAcceleratedMetricMatchesNaiveMetric(String newick1, String newick2, String testName) {

        Tree tree1 = TreeCreator.getTreeFromString(newick1);
        Tree tree2 = TreeCreator.getTreeFromString(newick2);

        SprHeuristicRFCMetric notAcceleratedMetric = new SprHeuristicRFCMetric();
        SprHeuristicRFCAcceleratedMetric acceleratedMetric = new SprHeuristicRFCAcceleratedMetric();
        double expectedDistance = notAcceleratedMetric.getDistance(tree1, tree2);
        double actualDistance = acceleratedMetric.getDistance(tree1, tree2);

        assertEquals(expectedDistance, actualDistance, DELTA,
                "The output of the optimized SPR heuristic must be identical to the unoptimized one.");
    }

    @Test
    void testDistanceShouldBeOneIfTargetIsExactlyOneSprMoveAway() {
        // Korzystamy z fabryki - czyste, w pełni binarne drzewa ukorzenione
        Tree baseTree = TestTreeFactory.fourLeavesBalancedTree1();
        Tree targetTree = TestTreeFactory.fourLeavesCaterpillarTree1();

        SprHeuristicRFCAcceleratedMetric metric = new SprHeuristicRFCAcceleratedMetric();
        double dist = metric.getDistance(baseTree, targetTree);

        assertEquals(1.0, dist, DELTA,
                "If the target is in the SPR environment, the minimum SPR distance found must be 1.0");
    }

    @Test
    void testDistanceForIdenticalTreesShouldBeZero() {
        Tree tree = TreeCreator.getTreeFromString("(((1,2),3),(4,5));");
        SprHeuristicRFCAcceleratedMetric metric = new SprHeuristicRFCAcceleratedMetric();
        double dist = metric.getDistance(tree, tree);
        assertEquals(0.0, dist, DELTA,
                "The distance calculated for two identical trees should be 0.0");
    }

    // --- GENERATOR DANYCH (Data Provider) ---
    static Stream<Arguments> provideTreePairsForTesting() {
        return Stream.of(
                // --- ROZMIAR 5 ---

                // Test 1: Dystans 1 - Prosta rotacja typu caterpillar
                Arguments.of("((((1,2),3),4),5);", "((((1,2),4),3),5);", "5 liści: Proste przesunięcie liścia 3 (d=1)"),

                // Test 2: Dystans 2 - Zmiana z caterpillar na zbalansowane
                // Częsty błąd: Algorytmy mogą błędnie uznać to za 1 ruch
                Arguments.of("((((1,2),3),4),5);", "(((1,2),3),(4,5));", "5 liści: Przesunięcie klastra (4,5) (d=1) - UWAGA: zależy od korzenia"),

                // Test 3: Dystans 2 - Zamiana liści w głębokim klastrze
                Arguments.of("((((1,2),3),4),5);", "((((1,3),2),4),5);", "5 liści: Zamiana 2 i 3 (d=1)"),

                // Test 4: Dystans 2 - Bardziej złożona reorganizacja
                Arguments.of("((((1,2),3),4),5);", "((1,4),((2,3),5));", "5 liści: Silna reorganizacja (d=2)"),


                // --- ROZMIAR 6 ---

                // Test 5: Dystans 1 - Przesunięcie całego poddrzewa (cherry)
                Arguments.of("((((1,2),3),4),(5,6));", "(((1,2),3),(4,(5,6)));", "6 liści: Przesunięcie wiśni (5,6) o jeden poziom (d=1)"),

                // Test 6: Dystans 2 - Klasyczna "pułapka kwartetu" (Rooted vs Unrooted)
                // Jeśli algorytm zwróci 1, oznacza to, że traktuje drzewo jako nieukorzenione.
                Arguments.of("(((1,2),(3,4)),(5,6));", "(((1,3),(2,4)),(5,6));", "6 liści: Pułapka kwartetu (rSPR=2, uSPR=1)"),

                // Test 7: Dystans 2 - Przesunięcie wewnętrzne
                Arguments.of("((((1,2),3),4),(5,6));", "(((1,2),(5,6)),(3,4));", "6 liści: Zamiana miejscami poddrzew (d=2)"),

                // Test 8: Dystans 3 - Maksymalne skomplikowanie dla 6 liści (Wersja Binarna)
                Arguments.of("(((1,2),(3,4)),(5,6));", "(((1,6),(2,4)),(3,5));", "6 liści: Maksymalna odległość (d=3)"),

                // Rozmiar 10
                Arguments.of("((((((1,2),3),4),5),6),(((7,8),9),10));", "((((((1,3),2),4),5),6),(((7,8),9),10));", "Drzewa średnie (10 liści)"),
                Arguments.of("(((1,2),(3,4)),((5,6),((7,8),(9,10))));", "(((1,4),(2,3)),((5,6),((7,8),(9,10))));", "Drzewa średnie wężowe (10 liści)"),

                // Rozmiar 12
                Arguments.of("(((((((1,2),3),4),5),6),7),((((8,9),10),11),12));", "(((((((1,3),2),4),5),6),7),((((8,9),10),11),12));", "Drzewa duże (12 liści)"),

                // Rozmiar 15
                Arguments.of("((((1,2),(3,4)),((5,6),(7,8))),((((9,10),(11,12)),13),(14,15)));", "((((1,4),(2,3)),((5,6),(7,8))),((((9,10),(11,12)),13),(14,15)));", "Drzewa duże (15 liści)"),

                // Rozmiar 20
                Arguments.of("(((((((((1,2),3),4),5),6),7),8),9),((((((((10,11),12),13),14),15),16),17),((18,19),20)));",
                        "(((((((((1,3),2),4),5),6),7),8),9),((((((((10,11),12),13),14),15),16),17),((18,19),20)));",
                        "Drzewa bardzo duże (20 liści)")
        );
    }
}