package treecmp.ecr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristicMCMetric;
import treecmp.util.TestTreeFactory;

class Ecr2IncrementalHeuristicMCMetricTest {

    private Ecr2IncrementalHeuristicMCMetric metric;
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        // Testujemy inkrementalną heurystykę 2-sECR dla Matching Cluster (MC - Rooted)
        metric = new Ecr2IncrementalHeuristicMCMetric();
    }

    @Test
    void testDistanceToSelfIsZero() {
        // Arrange (Dla MC pobieramy drzewo UKORZENIONE)
        // 2-sECR wymaga odpowiedniej głębokości (co najmniej 4 węzłów wewnętrznych w łańcuchu dla bezpieczeństwa)
        Tree t1 = TestTreeFactory.fiveLeavesRootedCaterpillarTree();

        // Act
        double distance = metric.getDistance(t1, t1);

        // Assert
        assertEquals(0.0, distance, DELTA,
                "Dystans MC dla identycznych drzew ukorzenionych musi wynosić 0.0");
    }

    @Test
    void testIncrementalHeuristicResolvesLargerDistance() {
        // Arrange: Bierzemy dwa różne, większe drzewa ukorzenione
        // Uwaga: Upewnij się, że w TestTreeFactory istnieją metody dla 10-liściowych drzew ukorzenionych.
        // Jeśli nie, możesz tu użyć np. sixLeavesRootedCaterpillarTree oraz innego 6-liściowego ukorzenionego.
        Tree t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        assertTrue(distance > 0.0, "Dystans MC między różnymi drzewami ukorzenionymi musi być większy niż 0");
        System.out.println("Dystans 2-sECR MC Incremental Heuristic (10 liści) wyniósł: " + distance);
    }
}