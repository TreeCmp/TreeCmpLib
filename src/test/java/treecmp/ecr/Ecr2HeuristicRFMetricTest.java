package treecmp.ecr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import treecmp.heuristics.ecr.Ecr2HeuristicRFMetric;
import treecmp.util.TestTreeFactory;

class Ecr2HeuristicRFMetricTest {

    private Ecr2HeuristicRFMetric metric;
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        // Testujemy klasyczne 2-sECR na splitach (nieukorzenione)
        metric = new Ecr2HeuristicRFMetric();
    }

    @Test
    void testDistanceToSelfIsZero() {
        // Arrange
        // Bierzemy 5 liści, bo dla 4 liści ECR nic nie robi (0 klastrów)
        Tree t1 = TestTreeFactory.fiveLeavesUnrootedCaterpillarTree();

        // Act
        double distance = metric.getDistance(t1, t1);

        // Assert
        assertEquals(0.0, distance, DELTA,
                "Heurystyka 2-sECR dla dwóch identycznych drzew musi zwrócić 0.0");
    }

    @Test
    void testHeuristicResolvesLargerDistanceWithoutCrashing() {
        // Arrange: Bierzemy dwa różne drzewa 10-liściowe
        Tree t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        Tree t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        // Act
        double distance = metric.getDistance(t1, t2);

        // Assert
        assertTrue(distance > 0.0, "Dystans między różnymi drzewami musi być większy niż 0");
        System.out.println("Dystans 2-sECR Heuristic dla drzew 10-liściowych (RF) wyniósł: " + distance);
    }
}