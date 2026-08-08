package treecmp.heuristics.ecr;

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
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristic;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

@DisplayName("Weryfikacja poprawności 1-krokowego Ecr3IncrementalHeuristic vs klasyczne SubtreeEcr3Utils")
public class Ecr3IncrementalCorrectnessTest {

    private static final double EPSILON = 1e-9;

    /**
     * Generator parametrów dla 3-sECR:
     * Otoczenie o wielkości O(N^3), dlatego testujemy mniejsze drzewa (10..30),
     * co gwarantuje błyskawiczne wykonanie zestawu testów jednostkowych.
     */
    private static Stream<Arguments> provideTestParameters() {
        // Wszystkie 6 metryk — ukorzenione i nieukorzenione:
        String[] metrics = {"RF", "RFC", "MS", "MC", "MP", "M3"};
        // Dla 3-sECR startujemy od n=20, aby zagwarantować obecność klastrów z 4 węzłami:
        int[] treeSizes = {20, 30, 50};
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
    @DisplayName("Porównanie minimalnej odległości w otoczeniu 3-sECR (Incr vs Classic)")
    void testSingleStepBestNeighborMatch(String metricName, int treeSize, long seed1, long seed2) throws Exception {
        boolean isRooted = false;
        Metric classicMetric;
        IncrementalHeuristicBaseMetric incrementalMetric;

        switch (metricName) {
            case "RF":
                isRooted = false;
                classicMetric = new RFMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new M3IncrementalMetric(), "M3");
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

        TreeNeighborhoodUtils classicUtils = new SubtreeEcr3Utils(!isRooted);

        // 1. Obliczenie wyniku inkrementalnego
        double distIncr = incrementalMetric.evaluateSingleStep(t1ForIncr, t2);

        // 2. Wygenerowanie otoczenia klasycznego w locie (leniwa ewaluacja)
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
                throw new RuntimeException("Błąd podczas ewaluacji odległości w teście 3-sECR", e);
            }
        });

        // Jeśli dla danej małej topologii brak klastra o rozmiarze 4, pomiń test w czytelny sposób:
        org.junit.jupiter.api.Assumptions.assumeTrue(
                neighborCount[0] > 0,
                String.format("Pomijam: topologia (n=%d, seed=%d) nie posiada klastra 4 węzłów dla 3-sECR", treeSize, seed1)
        );

        // 3. Weryfikacja zgodności
        assertEquals(
                bestClassicDist[0],
                distIncr,
                EPSILON,
                String.format(
                        "Niezgodność w 3-sECR (%s) dla n=%d (seeds=%d/%d)! Classic=%.6f vs Incr=%.6f",
                        metricName, treeSize, seed1, seed2, bestClassicDist[0], distIncr
                )
        );
    }
}