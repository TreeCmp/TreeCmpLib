package treecmp.benchmarks.distance;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.results.RunResult;
import treecmp.benchmarks.distance.AbstractDistanceBenchmark;
import treecmp.heuristics.spr.SprHeuristicMetric;
import treecmp.heuristics.spr.UsprHeuristicMetric;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;

public class SprDistanceBenchmark extends AbstractDistanceBenchmark {

    @Param({"10", "20", "30", "50", "80", "120", "200", "300", "500", "800", "1200", "2000"})
    public int treeSize; // przesłonięcie zakresu rozmiarów dla SPR

    @Override
    protected void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            case "RF":
                isRooted = false;
                classicMetric     = new UsprHeuristicMetric(new RFMetric(), "RF");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric     = new SprHeuristicMetric(new RFClusterMetric(), true, "RFC");
                incrementalMetric = new SprIncrementalHeuristicMetric(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric     = new UsprHeuristicMetric(new MatchingSplitMetric(), "MS");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicMetric     = new SprHeuristicMetric(new MatchingClusterMetric(), true, "MC");
                incrementalMetric = new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric     = new SprHeuristicMetric(new MatchingPairMetric(), true, "MP");
                incrementalMetric = new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric     = new UsprHeuristicMetric(new MatchingTripletMetric(), "M3");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metric);
        }

        loadOrGenerateTrees(size, isRooted);
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();
        String className = SprDistanceBenchmark.class.getSimpleName();
        String incrOnly = className + ".benchmarkIncrementalFullRun";
        List<RunResult> allResults = new ArrayList<>();

        int[] sizes = {10, 20, 30, 50, 80, 120, 200, 300, 500, 800, 1200, 2000};

        for (int size : sizes) {
            String sizeStr = String.valueOf(size);

            if (size <= 50) {
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));
            } else if (size <= 120) {
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, className, quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"MS", "MC", "MP", "M3"}, incrOnly, quickEstimate));
            } else {
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC"}, incrOnly, quickEstimate));
            }
        }

        exportToCsv("benchmark_distance_SPR.csv", allResults, "SPR", "TimeMs");
    }
}