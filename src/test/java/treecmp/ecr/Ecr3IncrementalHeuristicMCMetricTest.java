package treecmp.ecr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristicMCMetric;
import treecmp.util.TestTreeFactory;

class Ecr3IncrementalHeuristicMCMetricTest {

    private Ecr3IncrementalHeuristicMCMetric metric;
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        // Testujemy inkrementalną heurystykę 3-sECR dla Matching Cluster (MC - Rooted)
        metric = new Ecr3IncrementalHeuristicMCMetric();
    }

    @Test
    void testDistanceToSelfIsZero() {
        // Arrange (Dla MC pobieramy drzewo UKORZENIONE)
        // 3-sECR wymaga klastra 4 węzłów wewnętrznych, więc bezpiecznie bierzemy 6-liściowego caterpillara
        Tree t1 = TestTreeFactory.sixLeavesRootedCaterpillarTree();

        // Act
        double distance = metric.getDistance(t1, t1);

        // Assert
        assertEquals(0.0, distance, DELTA,
                "Dystans MC dla identycznych drzew ukorzenionych w 3-sECR musi wynosić 0.0");
    }

    @Test
    void testIncrementalHeuristicResolvesLargerDistance() {
        // Arrange: Bierzemy dwa różne, większe drzewa ukorzenione
        Tree t1 = TestTreeFactory.tenLeavesBinaryRootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesBinaryRootedTree2();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        assertTrue(distance > 0.0, "Dystans MC między różnymi drzewami ukorzenionymi musi być większy niż 0");
        System.out.println("Dystans 3-sECR MC Incremental Heuristic (10 liści) wyniósł: " + distance);
    }
}