package treecmp.metrics.topological;

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

    // 2. Zmieniamy typ na klasę bazową Twoich metryk
    protected BaseRFIncrementalMetric metric;
    protected static final double DELTA = 0.000001;

    protected Tree t1;
    protected Tree t2;

    // 3. Wymuszamy na klasach potomnych dostarczenie konkretnej implementacji
    protected abstract BaseRFIncrementalMetric createMetricInstance();

    @BeforeEach
    void setUp() {
        // Zamiast "new RFCluster...", wywołujemy metodę abstrakcyjną
        metric = createMetricInstance();

        t1 = TestTreeFactory.fiveLeavesRootedBaseTree();
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

        // Znajdujemy węzły do wykonania serii ruchów na drzewie bazowym
        Node node2 = TreeUtils.getNodeByName(t1, "2");
        Node node3 = TreeUtils.getNodeByName(t1, "3");
        Node node4 = TreeUtils.getNodeByName(t1, "4");

        // Definiujemy dwa kolejne ruchy (trajektoria)
        // Ruch 1: zamiana 2 i 3 (zmienia klastry głęboko w drzewie)
        NniMove move1 = new NniMove(node2, node3);
        // Ruch 2: zamiana 3 i 4 (zmienia klastry wyżej w drzewie)
        NniMove move2 = new NniMove(node3, node4);

        // Act - KROK W PRZÓD (wykonujemy sekwencję)
        double distAfterMove1 = metric.applyNni(move1);
        double distAfterMove2 = metric.applyNni(move2);

        // Sprawdzamy, czy trajektoria w ogóle zmieniła dystans, żeby test miał sens
        assertEquals(1.0, distAfterMove2, DELTA,
                "Po drugim ruchu dystans powinien wzrosnąć z powrotem do 1.0");

        // Act & Assert - KROK W TYŁ (cofamy w odwrotnej kolejności - LIFO!)

        // 1. Cofamy drugi ruch
        metric.undoNni(move2);
        assertEquals(distAfterMove1, metric.getCurrentDistance(), DELTA,
                "Po cofnięciu drugiego ruchu, dystans musi wrócić dokładnie do stanu po pierwszym ruchu");

        // 2. Cofamy pierwszy ruch
        metric.undoNni(move1);
        assertEquals(initialDist, metric.getCurrentDistance(), DELTA,
                "Po cofnięciu wszystkich ruchów, dystans i stosy muszą wrócić do idealnego stanu początkowego");
    }

    @Test
    void testComplexNniTrajectoryWithBranchingUndos() {
        // Arrange
        metric.initCalculationState(t1, t2);
        double initialDist = metric.getCurrentDistance();

        // Pobieramy wszystkie liście, żeby zrobić prawdziwy chaos w klastrach
        Node n1 = TreeUtils.getNodeByName(t1, "1");
        Node n2 = TreeUtils.getNodeByName(t1, "2");
        Node n3 = TreeUtils.getNodeByName(t1, "3");
        Node n4 = TreeUtils.getNodeByName(t1, "4");
        Node n5 = TreeUtils.getNodeByName(t1, "5");

        // Definiujemy zestaw ruchów (niektóre blisko siebie, inne w różnych częściach drzewa)
        NniMove move1 = new NniMove(n2, n3); // Ten naprawia drzewo (dystans spada do 0.0)
        NniMove move2 = new NniMove(n4, n5); // Psuje górną część
        NniMove move3 = new NniMove(n1, n2); // Ślepa uliczka, którą zaraz cofniemy

        NniMove move4 = new NniMove(n1, n4); // Nowa ścieżka zamiast move3
        NniMove move5 = new NniMove(n3, n5); // Kolejny krok w nowej ścieżce

        // ==========================================================
        // ETAP 1: Idziemy głęboko w las (3 ruchy w przód)
        // ==========================================================
        double dist1 = metric.applyNni(move1);
        double dist2 = metric.applyNni(move2);
        double dist3 = metric.applyNni(move3); // To jest koniec naszej pierwszej gałęzi

        // ==========================================================
        // ETAP 2: Cofamy się o 1 krok (odrzucamy ślepą uliczkę)
        // ==========================================================
        metric.undoNni(move3);
        assertEquals(dist2, metric.getCurrentDistance(), DELTA,
                "Po wycofaniu move3 (ślepej uliczki) dystans musi wrócic idealnie do stanu po move2");

        // ==========================================================
        // ETAP 3: Zmieniamy zdanie i wchodzimy w nową gałąź (2 nowe ruchy)
        // ==========================================================
        double dist4 = metric.applyNni(move4);
        double dist5 = metric.applyNni(move5);

        // ==========================================================
        // ETAP 4: Wielki powrót do bazy (Cofamy 4 pozostałe ruchy na stosie)
        // ==========================================================
        // Zasada LIFO: cofamy od najnowszego do najstarszego

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