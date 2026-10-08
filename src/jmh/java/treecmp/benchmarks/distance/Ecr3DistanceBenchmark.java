package treecmp.benchmarks.distance;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.results.RunResult;
import treecmp.benchmarks.distance.AbstractDistanceBenchmark;
import treecmp.heuristics.ecr.Ecr3ClassicHeuristic;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;

public class Ecr3DistanceBenchmark extends AbstractDistanceBenchmark {

    @Param({"10", "20", "30", "50", "80", "120"})
    public int treeSize;

    @Override
    protected void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            case "RF":
                isRooted = false;
                classicMetric     = new Ecr3ClassicHeuristic(new RFMetric(), isRooted, "RF");
                incrementalMetric = new Ecr3IncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric     = new Ecr3ClassicHeuristic(new RFClusterMetric(), isRooted, "RFC");
                incrementalMetric = new Ecr3IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric     = new Ecr3ClassicHeuristic(new MatchingSplitMetric(), isRooted, "MS");
                incrementalMetric = new Ecr3IncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicMetric     = new Ecr3ClassicHeuristic(new MatchingClusterMetric(), isRooted, "MC");
                incrementalMetric = new Ecr3IncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric     = new Ecr3ClassicHeuristic(new MatchingPairMetric(), isRooted, "MP");
                incrementalMetric = new Ecr3IncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric     = new Ecr3ClassicHeuristic(new MatchingTripletMetric(), isRooted, "M3");
                incrementalMetric = new Ecr3IncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metric);
        }

        loadOrGenerateTrees(size, isRooted);
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();
        String className = Ecr3DistanceBenchmark.class.getSimpleName();
        String incrOnly = className + ".benchmarkIncrementalFullRun";
        List<RunResult> allResults = new ArrayList<>();

        int[] sizes = {10, 20, 30, 50, 80, 120};

        for (int size : sizes) {
            String sizeStr = String.valueOf(size);

            if (size <= 20) {
                // N <= 20: Wszystkie 6 metryk Classic + Incremental
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));
            } else if (size <= 30) {
                // N = 30: Classic dla RF, RFC, MS; reszta Incremental
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS"}, className, quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"MC", "MP", "M3"}, incrOnly, quickEstimate));
            } else if (size <= 50) {
                // N = 50: Classic tylko RF i RFC
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, className, quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"MS", "MC", "MP"}, incrOnly, quickEstimate));
            } else {
                // N >= 80: Tylko Incremental RF, RFC, MS, MC
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC"}, incrOnly, quickEstimate));
            }
        }

        exportToCsv("benchmark_distance_ECR3.csv", allResults, "ECR3", "TimeMs");
    }
}