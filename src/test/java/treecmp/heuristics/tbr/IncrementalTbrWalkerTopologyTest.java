package treecmp.heuristics.tbr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.tbr.acc.IncrementalTbrWalker;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class IncrementalTbrWalkerTopologyTest {

    @Test
    @DisplayName("Weryfikacja pełnej zgodności przestrzeni ruchów IncrementalTbrWalker z kanonicznym rTBR")
    void testExactMoveParityWithClassicTbrWalker() {
        Tree tree = TestTreeFactory.sixLeavesRootedBalancedTree();

        // 1. Zbiór kanonicznych ruchów TBR wygenerowany niezależnie
        Set<String> classicMoves = generateCanonicalTbrMoves(tree);

        // 2. Weryfikacja IncrementalTbrWalker z produkcyjną metryką RFClusterIncrementalMetric
        Set<String> incrementalMoves = new HashSet<>();
        IncrementalTbrWalker incWalker = new IncrementalTbrWalker();

        RFClusterIncrementalMetric metric = new RFClusterIncrementalMetric();
        metric.initCalculationState(tree, tree);

        incWalker.walk(tree, metric, (dist, prune, reroot, target) -> {
            incrementalMoves.add(formatMove(prune, reroot, target));
        });

        assertEquals(classicMoves.size(), incrementalMoves.size(),
                "Liczba odwiedzonych ruchów TBR musi być identyczna!");
        assertEquals(classicMoves, incrementalMoves,
                "IncrementalTbrWalker odwiedził inny zbiór ruchów niż kanoniczne otoczenie TBR!");
    }

    @Test
    @DisplayName("Pojedynczy daleki ruch TBR (o koszcie NNI > 1) musi dawać dystans TBR == 1.0, zachowując wagę NNI dla VND")
    void testSingleNonLocalTbrMoveReportsUnitTbrDistanceAndPreservesNniCost() throws Exception {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(12, 101L);
        assignNumbers(t1);

        // Wybieramy daleki ruch TBR, który wymaga > 1 kroków NNI
        TbrUtils utils = new TbrUtils();
        List<Node> allNodes = new ArrayList<>();
        collectAllNodes(t1.getRoot(), allNodes);

        Tree t2 = null;
        RFClusterMetric rfc = new RFClusterMetric();
        for (Node prune : allNodes) {
            if (prune.isRoot() || prune.getParent() == null || prune.isLeaf()) continue;
            Node reroot = prune.getChild(0);
            for (Node target : allNodes) {
                if (utils.isValidTbrMove(prune, reroot, target)) {
                    Tree candidate = utils.createTbrTree(t1, prune, reroot, target);
                    if (candidate != null) {
                        assignNumbers(candidate);
                        // Szukamy sąsiada 1-TBR oddalonego o więcej niż 1 krok NNI (d_RFC >= 3)
                        if (rfc.getDistance(t1, candidate) >= 3.0) {
                            t2 = candidate;
                            break;
                        }
                    }
                }
            }
            if (t2 != null) break;
        }
        assertNotNull(t2, "Powinien istnieć nielokalny sąsiad TBR o koszcie NNI > 1");

        TbrIncrementalHeuristic incTbr = new TbrIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
        double distIncr = incTbr.getDistance(new SimpleTree(t1), new SimpleTree(t2));

        // 1. Sąsiad powstały przez 1 ruch TBR musi mieć odległość TBR równą dokładnie 1.0 (a nie koszt NNI!)
        assertEquals(1.0, distIncr, 1e-9,
                "Incremental TBR dla sąsiada 1-TBR musi zwrócić 1.0 (a nie wagę dekompozycji NNI!)");

        // 2. Weryfikacja spójności wewnętrznych liczników dla TBR oraz VND-NNI
        assertEquals(1, incTbr.getTbrStepsCount(), "Liczba kroków TBR musi wynosić 1");
        assertEquals(1, incTbr.getAccumulatedSteps(), "getAccumulatedSteps() w TBR musi zliczać kroki jednostkowe (1)");
        assertTrue(incTbr.getAccumulatedNniCost() > 1.0,
                "getAccumulatedNniCost() musi zachować wagę dekompozycji NNI (> 1) na potrzeby VND-NNI");
    }

    @ParameterizedTest(name = "Metryka: {0}")
    @ValueSource(strings = {"RF", "RFC", "MS", "MC", "MP", "M3"})
    @DisplayName("Weryfikacja jednostkowego zliczania kroków TBR w getDistance() dla wszystkich 6 metryk")
    void testFullDescentReportsUnitStepsInsteadOfNniEquivalentCost(String metricName) {
        boolean isRooted;
        TbrIncrementalHeuristic incrementalHeuristic;

        switch (metricName) {
            case "RF":
                isRooted = false;
                incrementalHeuristic = new TbrIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                incrementalHeuristic = new TbrIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                incrementalHeuristic = new TbrIncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MS_RF");
                break;
            case "MC":
                isRooted = true;
                incrementalHeuristic = new TbrIncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MC_RF");
                break;
            case "MP":
                isRooted = true;
                incrementalHeuristic = new TbrIncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MP_RF");
                break;
            case "M3":
                isRooted = false;
                incrementalHeuristic = new TbrIncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3_RF");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metricName);
        }

        Tree t1 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(12, 42L)
                : TestTreeFactory.randomUnrootedBinaryTree(12, 42L);
        Tree t2 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(12, 99L)
                : TestTreeFactory.randomUnrootedBinaryTree(12, 99L);

        assignNumbers(t1);
        assignNumbers(t2);

        double incrDist = incrementalHeuristic.getDistance(new SimpleTree(t1), new SimpleTree(t2));

        assertFalse(Double.isInfinite(incrDist), "Heurystyka inkrementalna TBR powinna zbiec do drzewa docelowego");
        assertEquals((double) incrementalHeuristic.getTbrStepsCount(), incrDist, 1e-9,
                "getDistance() musi zwracać liczbę kroków TBR (tbrStepsCount), a nie skumulowany koszt NNI!");
        assertEquals(incrementalHeuristic.getTbrStepsCount(), incrementalHeuristic.getAccumulatedSteps(),
                "accumulatedSteps musi odpowiadać liczbie kroków TBR");
        assertTrue(incrementalHeuristic.getAccumulatedNniCost() > incrDist,
                "Dla losowych drzew (N=12) skumulowany koszt NNI (dla VND) musi być ostro większy od liczby kroków TBR");
        assertTrue(incrDist <= 10.0,
                "Średnia liczba kroków TBR dla N=12 nie powinna przekraczać 10 (było: " + incrDist + ")");
    }

    /**
     * Kanoniczny generator wyznaczający poprawną przestrzeń ruchów ukorzenionego TBR:
     * - Prune (P): dowolny węzeł poza korzeniem,
     * - Reroot (R): dowolny węzeł w odciętym poddrzewie T1,
     * - Target (T): dowolny węzeł w T2, z wykluczeniem zapadniętego rodzica P
     *               oraz ruchu tożsamościowego na rodzeństwo (gdy R == P).
     */
    private Set<String> generateCanonicalTbrMoves(Tree tree) {
        Set<String> moves = new HashSet<>();
        List<Node> allNodes = new ArrayList<>();
        collectAllNodes(tree.getRoot(), allNodes);

        for (Node prune : allNodes) {
            if (prune.isRoot() || prune.getParent() == null) continue;

            Node parent = prune.getParent();
            Node sibling = getSibling(prune);

            List<Node> t1Nodes = new ArrayList<>();
            collectAllNodes(prune, t1Nodes);
            Set<Node> t1Set = new HashSet<>(t1Nodes);

            for (Node reroot : t1Nodes) {
                for (Node target : allNodes) {
                    if (t1Set.contains(target)) continue;
                    if (target == parent) continue;
                    if (reroot == prune && target == sibling) continue;

                    moves.add(formatMove(prune, reroot, target));
                }
            }
        }
        return moves;
    }

    private Node getSibling(Node node) {
        Node parent = node.getParent();
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            Node child = parent.getChild(i);
            if (child != node) return child;
        }
        return null;
    }

    private void collectAllNodes(Node node, List<Node> list) {
        if (node != null) {
            list.add(node);
            for (int i = 0; i < node.getChildCount(); i++) {
                collectAllNodes(node.getChild(i), list);
            }
        }
    }

    private void assignNumbers(Tree tree) {
        TreeUtils.computeParentPointers(tree.getRoot());
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            tree.getExternalNode(i).setNumber(i);
        }
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            tree.getInternalNode(i).setNumber(i);
        }
    }

    private String formatMove(Node prune, Node reroot, Node target) {
        return String.format("P:%s_R:%s_T:%s",
                prune.isLeaf() ? prune.getIdentifier().getName() : "i" + prune.getNumber(),
                reroot.isLeaf() ? reroot.getIdentifier().getName() : "i" + reroot.getNumber(),
                target.isLeaf() ? target.getIdentifier().getName() : "i" + target.getNumber());
    }
}