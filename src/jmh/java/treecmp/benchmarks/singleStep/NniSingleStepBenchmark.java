package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.results.RunResult;
import pal.tree.SimpleTree;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.nni.NniUtils;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;

public class NniSingleStepBenchmark extends AbstractSingleStepBenchmark {

    private TreeNeighborhoodUtils classicUtils;

    @Override
    protected void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
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
                throw new IllegalArgumentException("Unknown metric: " + metric);
        }

        // Wspólna metoda pobierająca drzewo z datasetu lub generująca losowe (w klasie bazowej)
        loadOrGenerateTrees(size, isRooted);

        classicUtils = new NniUtils(!isRooted);
    }

    @Override
    protected double evaluateClassicBestDist() throws Exception {
        final double[] bestDist = {Double.POSITIVE_INFINITY};

        classicUtils.forEachNeighbour(t1, neighbor -> {
            try {
                if (neighbor instanceof SimpleTree) {
                    ((SimpleTree) neighbor).createNodeList();
                }
                double d = classicMetric.getDistance(neighbor, t2);
                if (d < bestDist[0]) {
                    bestDist[0] = d;
                }
            } catch (Exception e) {
                throw new RuntimeException("Błąd podczas ewaluacji dystansu w sąsiedztwie NNI", e);
            }
        });

        return bestDist[0];
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();
        String className = NniSingleStepBenchmark.class.getSimpleName();
        List<RunResult> allResults = new ArrayList<>();

        int[] sizes = {10, 20, 30, 50, 80, 120, 200, 300, 500, 800, 1200, 2000, 3000, 5000, 8000, 12000, 20000, 30000, 50000, 80000, 120000};

        for (int size : sizes) {
            String sizeStr = String.valueOf(size);

            if (size <= 300) {
                // N <= 300: Pełny zestaw Classic + Incremental dla WSZYSTKICH 6 metryk!
                // Przy N=300: M3 Classic ~5.4s, MS/MC ~1.4s, MP ~0.2s, RF/RFC < 0.1s.
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));
            } else if (size <= 500) {
                // N = 500: Pełny Classic dla 6 metryk (M3 Classic ~28s, MS/MC ~6.2s, MP ~0.7s)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));
            } else if (size <= 800) {
                // N = 800: Classic dla RF, RFC, MP (< 2.2s) oraz MS, MC (~25s). M3 tylko Incr.
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 1200) {
                // N = 1200: Classic dla RF, RFC, MP (< 5.8s). Incr dla MC, M3.
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MP"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"MC", "M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 3000) {
                // N = 2000-3000: Classic dla RF i RFC (< 15s). Incr dla MC i M3.
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"MC", "M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 5000) {
                // N = 5000: Ostatni krok Classic dla RF i RFC (~25-45s)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC"}, className, quickEstimate));
            } else {
                // N > 5000 (do 120 000): Wyłącznie szybkie RF i RFC Incremental
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            }
        }

        AbstractSingleStepBenchmark.exportToCsv("benchmark_single_step_NNI.csv", allResults, "NNI");
    }
}