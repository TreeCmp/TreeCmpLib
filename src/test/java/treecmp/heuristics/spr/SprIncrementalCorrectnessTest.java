package treecmp.heuristics.spr;

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
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

@DisplayName("Weryfikacja poprawności 1-krokowego SPR/uSPR vs klasyczne SprUtils/UsprUtils")
public class SprIncrementalCorrectnessTest {

    private static final double EPSILON = 1e-9;

    private static Stream<Arguments> provideTestParameters() {
        String[] metrics = {"RF", "RFC", "MS", "MC", "MP", "M3"};
        // Dla SPR zalecane rozmiary N <= 50, aby testy unitowe kończyły się błyskawicznie
        int[] treeSizes = {10, 20, 30, 50};
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
    }

    @ParameterizedTest(name = "Metryka: {0} | n={1} | seeds=[{2}, {3}]")
    @MethodSource("provideTestParameters")
    @DisplayName("Porównanie minimalnej odległości w otoczeniu SPR/uSPR (Incr vs Classic)")
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

        // 1. Obliczenie wyniku inkrementalnego
        double distIncr = incrementalMetric.evaluateSingleStep(t1ForIncr, t2);

        // 2. Obliczenie wyniku klasycznego za pomocą wywołań zwrotnych (callbacks)
        final double[] bestClassicDist = {Double.POSITIVE_INFINITY};
        final int[] neighborCount = {0};

        if (classicUtils instanceof SprUtils) {
            ((SprUtils) classicUtils).forEachSprTree(t1, neighbor -> {
                neighborCount[0]++;
                double d = 0;
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
                double d = 0;
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

        // 3. Weryfikacja zgodności najlepszego kroku
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
}