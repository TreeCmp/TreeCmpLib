package treecmp.metrics.topological;

import org.junit.jupiter.api.Test;
import treecmp.config.IOSettings;
import treecmp.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.*;

class MatchingClusterMetricO3Test {

    @Test
    void getMatchingClusterDistance_identicalTrees_returnsZero() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var mcm = new MatchingClusterMetricO3();

        double distance = mcm.getDistance(t1, t1);

        assertEquals(0.0, distance);
    }

    @Test
    void getMatchingClusterDistance_4leafsTrees_returnsFour() {
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();

        var mcm = new MatchingClusterMetricO3();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(4.0, distance);
    }

    @Test
    void getMatchingClusterDistance_10leafsTrees_returnsEightyFour() {
        var t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        var t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        var mcm = new MatchingClusterMetricO3();

        double distance = mcm.getDistance(t1, t2);

        assertEquals(23.0, distance);
    }

    @Test
    void getMatchingClusterAlignment_4leafsTrees_returnsCorrectAlignInfo() {
        // given
        var t1 = TestTreeFactory.fourLeavesTree1();
        var t2 = TestTreeFactory.fourLeavesTree2();
        var mcm = new MatchingClusterMetricO3();

        // Wymagane do poprawnej inicjalizacji wewnętrznych struktur do mapowania węzłów.
        // Zakładam, że IOSettings dostarcza odpowiedni setter lub konfigurację dla testów.
        IOSettings.getIOSettings().setGenAlignments(true);

        // when
        // Najpierw musimy wywołać obliczenie dystansu, co pod spodem:
        // 1. Zbuduje macierz assigncost
        // 2. Wypełni tablice costId2NumT1 oraz costId2NumT2
        // 3. Uruchomi LapSolver.lapShort zapisując rozwiązania w tablicach rowsol/colsol
        mcm.getDistance(t1, t2);

        // Teraz bezpiecznie pobieramy wygenerowany Alignment
        var alignInfo = mcm.getAlignment();

        // then
        assertNotNull(alignInfo);
        assertTrue(alignInfo.isUseClusters());
        assertEquals(4, alignInfo.getTotalCost());

        var aln = alignInfo.getAln();

        // Obiekt t1 i t2 mają po 3 węzły wewnętrzne (w tym korzeń).
        // Algorytm odrzuca korzeń, a obiekt aln inicjalizowany jest z wielkością (size - 1).
        // Oznacza to, że do dopasowania zostają dokładnie 2 pary węzłów.
        assertEquals(2, aln.length);

        // Drzewo 1 zawiera klastry: {A,B} i {C,D}.
        // Drzewo 2 zawiera klastry: {A,C} i {B,D}.
        // Wielkość każdego wynosi 2. Część wspólna dla dowolnej pary z nich (np. {A,B} z {A,C}) wynosi 1.
        // Koszt metryki Matching Cluster wyliczany jest jako: size1 + size2 - 2 * intersect.
        // Zatem każda sparowana para musi wygenerować koszt: 2 + 2 - 2 * 1 = 2.
        for (var pair : aln) {
            assertEquals(2, pair.cost);
            // Sprawdzamy, czy przypisane ID nie reprezentują wartości -1 (unpaired)
            assertTrue(pair.t1_node >= 0);
            assertTrue(pair.t2_node >= 0);
        }
    }

}
