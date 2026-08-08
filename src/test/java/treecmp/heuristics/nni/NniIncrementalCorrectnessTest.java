package treecmp.heuristics.nni;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

@DisplayName("Weryfikacja poprawności 1-krokowego NniIncrementalHeuristic vs klasyczne NniUtils")
public class NniIncrementalCorrectnessTest {

    private static final double EPSILON = 1e-9;

    /**
     * Generator przypadków testowych:
     * Kombinacja metryk (RF, RFC, MS, MC, MP, M3), rozmiarów drzew (10..100) oraz par ziarn losowości.
     */
    private static Stream<Arguments> provideTestParameters() {
        String[] metrics = {"RF", "RFC", "MS", "MC", "MP", "M3"};
        int[] treeSizes = {10, 20, 50, 100};
        long[][] seedPairs = {
                {12345L, 67890L},
                {42L, 999L},
                {2026L, 1337L}
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
    }

    @ParameterizedTest(name = "Metryka: {0} | n={1} | seeds=[{2}, {3}]")
    @MethodSource("provideTestParameters")
    @DisplayName("Porównanie minimalnej odległości w otoczeniu NNI (Incr vs Classic)")
    void testSingleStepBestNeighborMatch(String metricName, int treeSize, long seed1, long seed2) throws Exception {
        // 1. Konfiguracja metryki klasycznej i przyrostowej
        boolean isRooted = false;
        Metric classicMetric;
        IncrementalHeuristicBaseMetric incrementalMetric;

        switch (metricName) {
            case "RF":
                isRooted = false;
                classicMetric = new RFMetric();
                incrementalMetric = new NniIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new NniIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new NniIncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new NniIncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new NniIncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new NniIncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metricName);
        }

        // 2. Generowanie deterministycznych drzew testowych
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

        TreeNeighborhoodUtils classicUtils = new NniUtils(!isRooted);

        // 3. Obliczenie wyniku inkrementalnego
        double distIncr = incrementalMetric.evaluateSingleStep(t1ForIncr, t2);

        // 4. Obliczenie wyniku klasycznego (przegląd całego wygenerowanego otoczenia w locie)
        final double[] bestClassicDist = { Double.POSITIVE_INFINITY };
        final int[] neighborCount = { 0 };

        classicUtils.forEachNeighbour(t1, n -> {
            neighborCount[0]++;
            assignNumbers(n);
            try {
                double d = classicMetric.getDistance(n, t2);
                if (d < bestClassicDist[0]) {
                    bestClassicDist[0] = d;
                }
            } catch (Exception e) {
                throw new RuntimeException("Błąd w trakcie obliczania dystansu klasycznego w teście NNI", e);
            }
        });

        // Sprawdzamy, czy wygenerowano jakiekolwiek sąsiedztwo
        assertTrue(neighborCount[0] > 0, "Otoczenie NNI nie powinno być puste");

        // 5. Asercja — weryfikacja zgodności najlepszego kroku
        assertEquals(
                bestClassicDist[0],
                distIncr,
                EPSILON,
                String.format(
                        "Niezgodność dla metryki %s (n=%d, seeds=%d/%d)! Classic=%.6f vs Incr=%.6f",
                        metricName, treeSize, seed1, seed2, bestClassicDist[0], distIncr
                )
        );
    }
}