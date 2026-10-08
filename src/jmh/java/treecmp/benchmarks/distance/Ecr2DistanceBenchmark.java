package treecmp.benchmarks.distance;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.results.RunResult;
import treecmp.benchmarks.distance.AbstractDistanceBenchmark;
import treecmp.heuristics.ecr.Ecr2ClassicHeuristic;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;

public class Ecr2DistanceBenchmark extends AbstractDistanceBenchmark {

    @Param({"10", "20", "30", "50", "80", "120", "200"})
    public int treeSize;

    @Override
    protected void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            case "RF":
                isRooted = false;
                classicMetric     = new Ecr2ClassicHeuristic(new RFMetric(), isRooted, "RF");
                incrementalMetric = new Ecr2IncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric     = new Ecr2ClassicHeuristic(new RFClusterMetric(), isRooted, "RFC");
                incrementalMetric = new Ecr2IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric     = new Ecr2ClassicHeuristic(new MatchingSplitMetric(), isRooted, "MS");
                incrementalMetric = new Ecr2IncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicMetric     = new Ecr2ClassicHeuristic(new MatchingClusterMetric(), isRooted, "MC");
                incrementalMetric = new Ecr2IncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric     = new Ecr2ClassicHeuristic(new MatchingPairMetric(), isRooted, "MP");
                incrementalMetric = new Ecr2IncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric     = new Ecr2ClassicHeuristic(new MatchingTripletMetric(), isRooted, "M3");
                incrementalMetric = new Ecr2IncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metric);
        }

        loadOrGenerateTrees(size, isRooted);
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();
        String className = Ecr2DistanceBenchmark.class.getSimpleName();
        String incrOnly = className + ".benchmarkIncrementalFullRun";
        List<RunResult> allResults = new ArrayList<>();

        int[] sizes = {10, 20, 30, 50, 80, 120, 200};

        for (int size : sizes) {
            String sizeStr = String.valueOf(size);

            if (size <= 30) {
                // N <= 30: Pełny zestaw Classic i Incremental dla 6 metryk
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));
            } else if (size <= 50) {
                // N = 50: Classic dla RF, RFC, MS, MP; M3 i MC w Incremental
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MP"}, className, quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"MC", "M3"}, incrOnly, quickEstimate));
            } else if (size <= 80) {
                // N = 80: Classic tylko RF i RFC; reszta Incremental
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, className, quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"MS", "MC", "MP", "M3"}, incrOnly, quickEstimate));
            } else {
                // N >= 120: Wyłącznie Incremental (bez M3 na dużych drzewach)
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP"}, incrOnly, quickEstimate));
            }
        }

        exportToCsv("benchmark_distance_ECR2.csv", allResults, "ECR2", "TimeMs");
    }
}