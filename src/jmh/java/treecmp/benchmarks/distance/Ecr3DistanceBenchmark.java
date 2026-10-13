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

    @Param({"10", "20", "30", "50", "80", "120", "200"})
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
        String className = Ecr3DistanceBenchmark.class.getName();
        String classicOnly = className + "\\.benchmarkClassicFullRun";
        String incrOnly = className + "\\.benchmarkIncrementalFullRun";
        List<RunResult> allResults = new ArrayList<>();

        // Rozszerzamy zakres do N=200 dla pełnej zgodności z SPR i TBR
        int[] sizes = {10, 20, 30, 50, 80, 120, 200};

        for (int size : sizes) {
            String sizeStr = String.valueOf(size);

            // --- 1. WARIANT KLASYCZNY (CLASSIC) ---
            if (size <= 20) {
                // N=10, 20: Wszystkie 6 metryk Classic
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, classicOnly, quickEstimate));
            } else if (size <= 30) {
                // N=30: Classic dla RF, RFC, MS (bez ciężkich sześciennych)
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS"}, classicOnly, quickEstimate));
            } else if (size <= 50) {
                // N=50: Classic tylko dla szybkich RF i RFC
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, classicOnly, quickEstimate));
            }
            // N >= 80: Brak Classic

            // --- 2. WARIANT INKREMENTALNY (INCREMENTAL) ---
            if (size <= 80) {
                // N <= 80: Pełny komplet 6 metryk (M3 i MP w ECR3 liczą się błyskawicznie)
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, incrOnly, quickEstimate));
            } else if (size <= 120) {
                // N=120: 5 metryk z przywróconym szybkim MP (odcinamy tylko M3)
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP"}, incrOnly, quickEstimate));
            } else {
                // N=200: Szybkie metryki inkrementalne (bez nieukorzenionego MS i sześciennego M3)
                allResults.addAll(runJmh(sizeStr, new String[]{"RFC", "MP", "MC", "RF"}, incrOnly, quickEstimate));
            }
        }

        exportToCsv("benchmark_distance_ECR3.csv", allResults, "ECR3", "TimeMs");
    }
}