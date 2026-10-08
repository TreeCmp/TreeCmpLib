package treecmp.benchmarks.distance;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.results.RunResult;
import treecmp.benchmarks.distance.AbstractDistanceBenchmark;
import treecmp.heuristics.tbr.TbrHeuristicMetric;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;

public class TbrDistanceBenchmark extends AbstractDistanceBenchmark {

    @Param({"10", "20", "30", "50", "80", "120", "200"})
    public int treeSize;

    @Override
    protected void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            case "RF":
                isRooted = false;
                classicMetric     = new TbrHeuristicMetric(new RFMetric(), false, "RF");
                incrementalMetric = new UtbrIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric     = new TbrHeuristicMetric(new RFClusterMetric(), true, "RFC");
                incrementalMetric = new TbrIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric     = new TbrHeuristicMetric(new MatchingSplitMetric(), false, "MS");
                incrementalMetric = new UtbrIncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicMetric     = new TbrHeuristicMetric(new MatchingClusterMetric(), true, "MC");
                incrementalMetric = new TbrIncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric     = new TbrHeuristicMetric(new MatchingPairMetric(), true, "MP");
                incrementalMetric = new TbrIncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric     = new TbrHeuristicMetric(new MatchingTripletMetric(), false, "M3");
                incrementalMetric = new UtbrIncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metric);
        }

        loadOrGenerateTrees(size, isRooted);
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();
        String className = TbrDistanceBenchmark.class.getSimpleName();
        String incrOnly = className + ".benchmarkIncrementalFullRun";
        List<RunResult> allResults = new ArrayList<>();

        int[] sizes = {10, 20, 30, 50, 80, 120, 200};

        for (int size : sizes) {
            String sizeStr = String.valueOf(size);

            if (size <= 20) {
                // N <= 20: Wszystkie 6 metryk Classic + Incremental
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));
            } else if (size <= 30) {
                // N = 30: Classic tylko szybkie RF/RFC; Incremental dla wszystkich
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, className, quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"MS", "MC", "MP", "M3"}, incrOnly, quickEstimate));
            } else if (size <= 50) {
                // N = 50: Classic RF/RFC; Incremental bez M3
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, className, quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"MS", "MC", "MP"}, incrOnly, quickEstimate));
            } else {
                // N >= 80: Wyłącznie Incrementalne RF, RFC, MS, MC, MP
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP"}, incrOnly, quickEstimate));
            }
        }

        exportToCsv("benchmark_distance_TBR.csv", allResults, "TBR", "TimeMs");
    }
}