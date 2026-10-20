package treecmp.heuristics.spr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

@DisplayName("Weryfikacja poprawności heurystyk i odległości rSPR / uSPR")
public class SprIncrementalCorrectnessTest {

    private static final double EPSILON = 1e-9;

    private static Stream<Arguments> provideTestParameters() {
        String[] metrics = {"RF", "RFC", "MS", "MC", "MP", "M3"};
        int[] treeSizes = {10, 15, 20};
        long[][] seedPairs = {
                {12345L, 67890L},
                {42L, 999L}
        };

        List<Arguments> arguments = new ArrayList<>();
        for (String metric : metrics) {
            for (int size : treeSizes) {
                for (long[] seeds : seedPairs) {
                    arguments.add(Arguments.of(metric, size, seeds[0], seeds[1]));
                }
            }
        }
        return arguments.stream();
    }

    private static void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
        TreeUtils.computeParentPointers(tree.getRoot());
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            tree.getExternalNode(i).setNumber(i);
        }
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            tree.getInternalNode(i).setNumber(i);
        }
    }

    @ParameterizedTest(name = "Metryka: {0} | n={1} | seeds=[{2}, {3}]")
    @MethodSource("provideTestParameters")
    @DisplayName("Porównanie minimalnej odległości w otoczeniu 1-krokowym SPR/uSPR (Incr vs Classic)")
    void testSingleStepBestNeighborMatch(String metricName, int treeSize, long seed1, long seed2) throws Exception {
        boolean isRooted = false;
        Metric classicMetric;
        IncrementalHeuristicBaseMetric incrementalMetric;

        switch (metricName) {
            case "RF":
                isRooted = false;
                classicMetric = new RFMetric();
                incrementalMetric = new UsprIncrementalHeuristicMetric(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                treecmp.config.IOSettings.getIOSettings().setOptMsMcByRf(true);
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metricName);
        }

        Tree t1, t2, t1ForIncr;
        if (isRooted) {
            t1 = TestTreeFactory.randomRootedBinaryTree(treeSize, seed1);
            t2 = TestTreeFactory.randomRootedBinaryTree(treeSize, seed2);
            t1ForIncr = TestTreeFactory.randomRootedBinaryTree(treeSize, seed1);
        } else {
            t1 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, seed1);
            t2 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, seed2);
            t1ForIncr = TestTreeFactory.randomUnrootedBinaryTree(treeSize, seed1);
        }

        assignNumbers(t1);
        assignNumbers(t2);
        assignNumbers(t1ForIncr);

        TreeNeighborhoodUtils classicUtils = isRooted ? new SprUtils() : new UsprUtils();

        double distIncr = incrementalMetric.evaluateSingleStep(t1ForIncr, t2);

        final double[] bestClassicDist = {Double.POSITIVE_INFINITY};
        final int[] neighborCount = {0};

        if (classicUtils instanceof SprUtils) {
            ((SprUtils) classicUtils).forEachSprTree(t1, neighbor -> {
                neighborCount[0]++;
                double d;
                try {
                    assignNumbers(neighbor);
                    d = classicMetric.getDistance(neighbor, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }
                if (d < bestClassicDist[0]) bestClassicDist[0] = d;
            });
        } else if (classicUtils instanceof UsprUtils) {
            ((UsprUtils) classicUtils).forEachUsprTree(t1, neighbor -> {
                neighborCount[0]++;
                double d;
                try {
                    assignNumbers(neighbor);
                    d = classicMetric.getDistance(neighbor, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }
                if (d < bestClassicDist[0]) bestClassicDist[0] = d;
            });
        }

        assertTrue(neighborCount[0] > 0, "Otoczenie SPR nie powinno być puste");

        assertEquals(
                bestClassicDist[0],
                distIncr,
                EPSILON,
                String.format(
                        "Niezgodność w SPR (%s) dla n=%d (seeds=%d/%d)! Classic=%.6f vs Incr=%.6f",
                        metricName, treeSize, seed1, seed2, bestClassicDist[0], distIncr
                )
        );
    }

    @ParameterizedTest(name = "Metryka: {0}")
    @ValueSource(strings = {"RF", "RFC", "MS", "MC", "MP", "M3"})
    @DisplayName("Weryfikacja jednostkowego zliczania kroków SPR/uSPR w getDistance()")
    void testFullDescentReportsUnitStepsInsteadOfNniEquivalentCost(String metricName) {
        boolean isRooted;
        IncrementalHeuristicBaseMetric heuristic;

        // Uzywamy wariantu z tie-breakerem RF dla metryk parujących, aby zagwarantować bezwzględne dotarcie do optimum
        switch (metricName) {
            case "RF":
                isRooted = false;
                heuristic = new UsprIncrementalHeuristicMetric(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                heuristic = new SprIncrementalHeuristicMetric(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                heuristic = new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), new RFIncrementalMetric(), "MS_RF");
                break;
            case "MC":
                isRooted = true;
                heuristic = new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MC_RF");
                break;
            case "MP":
                isRooted = true;
                heuristic = new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MP_RF");
                break;
            case "M3":
                isRooted = false;
                heuristic = new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3_RF");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metricName);
        }

        Tree t1 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(10, 42L)
                : TestTreeFactory.randomUnrootedBinaryTree(10, 42L);
        Tree t2 = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(10, 99L)
                : TestTreeFactory.randomUnrootedBinaryTree(10, 99L);

        assignNumbers(t1);
        assignNumbers(t2);

        double incrDist = heuristic.getDistance(new SimpleTree(t1), new SimpleTree(t2));

        assertFalse(Double.isInfinite(incrDist), "Heurystyka inkrementalna SPR/uSPR powinna zbiec do drzewa docelowego");

        int sprSteps = (heuristic instanceof SprIncrementalHeuristicMetric) ?
                ((SprIncrementalHeuristicMetric) heuristic).getSprStepsCount() :
                ((UsprIncrementalHeuristicMetric) heuristic).getSprStepsCount();

        assertEquals((double) sprSteps, incrDist, EPSILON,
                "getDistance() musi zwracać liczbę kroków SPR, a nie skumulowany koszt NNI!");
        assertEquals(sprSteps, heuristic.getAccumulatedSteps(),
                "accumulatedSteps musi odpowiadać liczbie kroków SPR");
        assertTrue(heuristic.getAccumulatedNniCost() > incrDist,
                "Skumulowany koszt NNI (dla VND) musi być ostro większy od liczby kroków SPR");
    }

    @Test
    @DisplayName("Weryfikacja jednostkowego zliczania kroków uSPR dla M3 (Pure) na nielokalnym sąsiedzie 1-uSPR")
    void testUsprM3PureReportsUnitStepForSingleMove() throws Exception {
        Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(10, 42L);
        assignNumbers(t1);

        // Wybieramy sąsiada oddalonego o 1 ruch uSPR, ale o koszcie NNI > 1
        UsprUtils utils = new UsprUtils();
        Tree t2 = null;
        MatchingTripletMetric m3Classic = new MatchingTripletMetric();

        for (int i = 0; i < t1.getInternalNodeCount(); i++) {
            Node source = t1.getInternalNode(i);
            for (int j = 0; j < t1.getInternalNodeCount(); j++) {
                Node target = t1.getInternalNode(j);
                if (source != target && source.getParent() != target && target.getParent() != source) {
                    Tree candidate = utils.createUsprTree(t1, source, target);
                    if (candidate != null) {
                        assignNumbers(candidate);
                        // Upewniamy się, że to faktyczny ruch uSPR o odległości > 0
                        if (m3Classic.getDistance(t1, candidate) > 0.0) {
                            t2 = candidate;
                            break;
                        }
                    }
                }
            }
            if (t2 != null) break;
        }
        assertNotNull(t2, "Powinien istnieć poprawny sąsiad 1-uSPR");

        UsprIncrementalHeuristicMetric m3Heuristic =
                new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3");

        double dist = m3Heuristic.getDistance(new SimpleTree(t1), new SimpleTree(t2));

        // 1. Zbieżność w 1 kroku
        assertFalse(Double.isInfinite(dist), "Heurystyka powinna znaleźć sąsiada 1-uSPR w dokładnie 1 kroku");

        // 2. Weryfikacja jednostkowego kosztu SPR vs koszt NNI
        assertEquals(1.0, dist, EPSILON,
                "getDistance() musi zwrócić 1.0 (jeden krok SPR), a nie wagę dekompozycji NNI!");
        assertEquals(1, m3Heuristic.getSprStepsCount());
        assertEquals(1, m3Heuristic.getAccumulatedSteps());
        assertTrue(m3Heuristic.getAccumulatedNniCost() >= 1.0);
    }

    @Test
    @DisplayName("Weryfikacja pełnego zejścia uSPR M3 (z tie-breakerem RF) i jednostkowego zliczania kroków")
    void testUsprM3ConvergenceAndStepCount() {
        // Używamy wariantu z filtrem remisów RF, aby uniknąć utknięcia na plateau losowych drzew
        UsprIncrementalHeuristicMetric m3Heuristic =
                new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3_RF");

        Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(10, 42L);
        Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(10, 99L);

        assignNumbers(t1);
        assignNumbers(t2);

        double dist = m3Heuristic.getDistance(new SimpleTree(t1), new SimpleTree(t2));

        // 1. Sprawdzenie zbieżności do drzewa docelowego
        assertFalse(Double.isInfinite(dist),
                "Heurystyka uSPR M3 (z tie-breakerem RF) powinna zbiec do odległości 0.0");

        int sprSteps = m3Heuristic.getSprStepsCount();

        // 2. Dystans musi odpowiadać faktycznej liczbie kroków uSPR
        assertEquals((double) sprSteps, dist, EPSILON,
                "getDistance() musi zwracać jednostkową liczbę kroków uSPR (sprStepsCount)");
        assertEquals(sprSteps, m3Heuristic.getAccumulatedSteps(),
                "accumulatedSteps musi być równe liczbie kroków uSPR");

        // 3. Sprawdzenie, że nie został zwrócony skumulowany koszt NNI
        assertTrue(m3Heuristic.getAccumulatedNniCost() >= dist,
                "Koszt NNI (dla VND) powinien być >= odległości uSPR");
        assertTrue(dist <= 10.0,
                "Dystans uSPR dla N=10 nie powinien przekraczać rozmiaru drzewa (zwrócono: " + dist + ")");
    }

}