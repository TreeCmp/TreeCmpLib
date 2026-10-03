package treecmp.metrics.topological.acc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.util.TestTreeFactory;
import treecmp.heuristics.moves.NniMove;

// 1. Klasa jest ABSTRACT - JUnit nie uruchomi jej bezpośrednio
public abstract class BaseRFIncrementalMetricTest {

    // 2. Typem pola jest klasa bazowa metryk RF (posiadająca applyNni / undoNni)
    protected BaseRFIncrementalMetric metric;
    protected static final double DELTA = 0.000001;

    protected Tree t1;
    protected Tree t2;

    // 3. Klasy potomne dostarczają instancję rozszerzającą BaseRFIncrementalMetric
    protected abstract BaseRFIncrementalMetric createMetricInstance();

    @BeforeEach
    void setUp() {
        metric = createMetricInstance();

        t1 = TestTreeFactory.fiveLeavesRootedCaterpillarTree();
        t2 = TestTreeFactory.fiveLeavesTargetTree();
    }

    @Test
    void testInitialDistanceFor5Leaves() {
        metric.initCalculationState(t1, t2);
        assertEquals(1.0, metric.getCurrentDistance(), DELTA,
                "Początkowy dystans powinien wynosić 1.0 dla tych drzew");
    }

    @Test
    void testApplyNniReducesDistance() {
        metric.initCalculationState(t1, t2);
        Node node2 = TreeUtils.getNodeByName(t1, "2");
        Node node3 = TreeUtils.getNodeByName(t1, "3");
        NniMove move = new NniMove(node2, node3);

        double distAfterMove = metric.applyNni(move);

        assertEquals(0.0, distAfterMove, DELTA,
                "Ruch upodabniający drzewo bazowe do docelowego powinien zredukować dystans do 0.0");
    }

    @Test
    void testUndoNniRestoresOriginalDistance() {
        metric.initCalculationState(t1, t2);
        double initialDist = metric.getCurrentDistance();

        Node node2 = TreeUtils.getNodeByName(t1, "2");
        Node node3 = TreeUtils.getNodeByName(t1, "3");
        NniMove move = new NniMove(node2, node3);

        metric.applyNni(move);
        metric.undoNni(move);

        assertEquals(initialDist, metric.getCurrentDistance(), DELTA,
                "Po operacji undo dystans musi idealnie wrócić do wartości bazowej");
    }

    @Test
    void testMoveAtRootBoundary() {
        Tree rootBoundaryBase = TestTreeFactory.fourLeavesBalancedTree1();
        Tree rootBoundaryTarget = TestTreeFactory.fourLeavesBalancedTree2();

        metric.initCalculationState(rootBoundaryBase, rootBoundaryTarget);

        Node n1 = TreeUtils.getNodeByName(rootBoundaryBase, "1");
        Node n3 = TreeUtils.getNodeByName(rootBoundaryBase, "3");
        NniMove move = new NniMove(n1, n3);

        metric.applyNni(move);

        assertDoesNotThrow(() -> metric.undoNni(move),
                "Undo nie powinno rzucać wyjątków nawet przy korzeniu");
    }

    @Test
    void testMultipleNniMovesAndUndosMaintainStateConsistency() {
        // Arrange
        metric.initCalculationState(t1, t2);
        double initialDist = metric.getCurrentDistance();

        Node node2 = TreeUtils.getNodeByName(t1, "2");
        Node node3 = TreeUtils.getNodeByName(t1, "3");
        Node node4 = TreeUtils.getNodeByName(t1, "4");

        NniMove move1 = new NniMove(node2, node3);
        NniMove move2 = new NniMove(node3, node4);

        // Act - KROK W PRZÓD
        double distAfterMove1 = metric.applyNni(move1);
        double distAfterMove2 = metric.applyNni(move2);

        assertEquals(1.0, distAfterMove2, DELTA,
                "Po drugim ruchu dystans powinien wzrosnąć z powrotem do 1.0");

        // Act & Assert - KROK W TYŁ (LIFO)
        metric.undoNni(move2);
        assertEquals(distAfterMove1, metric.getCurrentDistance(), DELTA,
                "Po cofnięciu drugiego ruchu, dystans musi wrócić dokładnie do stanu po pierwszym ruchu");

        metric.undoNni(move1);
        assertEquals(initialDist, metric.getCurrentDistance(), DELTA,
                "Po cofnięciu wszystkich ruchów, dystans i stosy muszą wrócić do idealnego stanu początkowego");
    }

    @Test
    void testComplexNniTrajectoryWithBranchingUndos() {
        // Arrange
        metric.initCalculationState(t1, t2);
        double initialDist = metric.getCurrentDistance();

        Node n1 = TreeUtils.getNodeByName(t1, "1");
        Node n2 = TreeUtils.getNodeByName(t1, "2");
        Node n3 = TreeUtils.getNodeByName(t1, "3");
        Node n4 = TreeUtils.getNodeByName(t1, "4");
        Node n5 = TreeUtils.getNodeByName(t1, "5");

        NniMove move1 = new NniMove(n2, n3);
        NniMove move2 = new NniMove(n4, n5);
        NniMove move3 = new NniMove(n1, n2);

        NniMove move4 = new NniMove(n1, n4);
        NniMove move5 = new NniMove(n3, n5);

        // ETAP 1
        double dist1 = metric.applyNni(move1);
        double dist2 = metric.applyNni(move2);
        double dist3 = metric.applyNni(move3);

        // ETAP 2
        metric.undoNni(move3);
        assertEquals(dist2, metric.getCurrentDistance(), DELTA,
                "Po wycofaniu move3 (ślepej uliczki) dystans musi wrócić idealnie do stanu po move2");

        // ETAP 3
        double dist4 = metric.applyNni(move4);
        double dist5 = metric.applyNni(move5);

        // ETAP 4 - Powrót LIFO
        metric.undoNni(move5);
        assertEquals(dist4, metric.getCurrentDistance(), DELTA,
                "Po cofnięciu move5, wracamy do stanu po move4");

        metric.undoNni(move4);
        assertEquals(dist2, metric.getCurrentDistance(), DELTA,
                "Po cofnięciu move4, wracamy do rozwidlenia (stan po move2)");

        metric.undoNni(move2);
        assertEquals(dist1, metric.getCurrentDistance(), DELTA,
                "Po cofnięciu move2, wracamy do stanu po move1");

        metric.undoNni(move1);
        assertEquals(initialDist, metric.getCurrentDistance(), DELTA,
                "Po wyczyszczeniu całego stosu, dystans musi wynosić dokładnie tyle samo, co na samym początku!");
    }
}