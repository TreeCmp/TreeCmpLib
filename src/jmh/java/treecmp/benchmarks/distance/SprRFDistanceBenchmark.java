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

/**
 * Benchmark pełnego przebiegu wspinaczki w otoczeniu SPR z tie-breakerem RF/RFC dla remisów.
 * Obejmuje 13 metryk głównych (6 ukorzenionych + 7 nieukorzenionych).
 */
public class SprRFDistanceBenchmark extends AbstractDistanceBenchmark {

    @Param({
            // Metryki ukorzenione (tie-breaker: RFC)
            "CopheneticL2", "RMAST", "MC", "MP", "NodalL2Splitted", "Triplet",
            // Metryki nieukorzenione (tie-breaker: RF)
            "M3", "MPU", "MS", "NodalL2", "Quartet", "UMAST", "RF"
    })
    public String metricName;

    @Param({"10", "20", "30", "50", "80", "120", "200"})
    public int treeSize;

    @Override
    protected void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            // --- UKORZENIONE (tie-breaker: RFClusterMetric) ---
            case "CopheneticL2":
                isRooted = true;
                classicMetric = new SprHeuristicMetric(new CopheneticL2Metric(), new RFClusterMetric(), true, "CopheneticL2");
                break;
            case "RMAST":
                isRooted = true;
                classicMetric = new SprHeuristicMetric(new RMASTMetric(), new RFClusterMetric(), true, "RMAST");
                break;
            case "MC":
                isRooted = true;
                classicMetric = new SprHeuristicMetric(new MatchingClusterMetricO3(), new RFClusterMetric(), true, "MC");
                incrementalMetric = new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new SprHeuristicMetric(new MatchingPairMetric(), new RFClusterMetric(), true, "MP");
                incrementalMetric = new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), "MP");
                break;
            case "NodalL2Splitted":
                isRooted = true;
                classicMetric = new SprHeuristicMetric(new NodalL2SplittedMetric(), new RFClusterMetric(), true, "NodalL2Splitted");
                break;
            case "Triplet":
                isRooted = true;
                classicMetric = new SprHeuristicMetric(new TripletMetric(), new RFClusterMetric(), true, "Triplet");
                break;

            // --- NIEUKORZENIONE (tie-breaker: RFMetric) ---
            case "M3":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new MatchingTripletMetric(), new RFMetric(), "M3");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3");
                break;
            case "MPU":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new MatchingPairUnrootedMetric(), new RFMetric(), "MPU");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new MatchingSplitMetric(), new RFMetric(), "MS");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), "MS");
                break;
            case "NodalL2":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new NodalL2Metric(), new RFMetric(), "NodalL2");
                break;
            case "Quartet":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new QuartetMetricLong(), new RFMetric(), "Quartet");
                break;
            case "UMAST":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new UMASTMetric(), new RFMetric(), "UMAST");
                break;
            case "RF":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new RFMetric(), "RF");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new RFIncrementalMetric(), "RF");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metric);
        }

        loadOrGenerateTrees(size, isRooted);
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();
        String className = SprRFDistanceBenchmark.class.getSimpleName();
        String classicOnly = className + ".benchmarkClassicFullRun";
        String incrOnly = className + ".benchmarkIncrementalFullRun";
        List<RunResult> allResults = new ArrayList<>();

        int[] sizes = {10, 20, 30, 50, 80, 120, 200};

        for (int size : sizes) {
            String sizeStr = String.valueOf(size);

            // --- HARMONOGRAM DLA WARIANTU KLASYCZNEGO (CLASSIC) ---
            if (size <= 20) {
                // N=10, 20: Wszystkie 13 metryk w wariancie klasycznym
                allResults.addAll(runJmh(sizeStr,
                        new String[]{"CopheneticL2", "RMAST", "MC", "MP", "NodalL2Splitted", "Triplet", "M3", "MPU", "MS", "NodalL2", "Quartet", "UMAST", "RF"},
                        classicOnly, quickEstimate));
            } else if (size <= 30) {
                // N=30: Odcinamy bardzo ciężkie metryki sześcienne (Triplet, Quartet, M3)
                allResults.addAll(runJmh(sizeStr,
                        new String[]{"CopheneticL2", "RMAST", "MC", "MP", "NodalL2Splitted", "MPU", "MS", "NodalL2", "UMAST", "RF"},
                        classicOnly, quickEstimate));
            } else if (size <= 50) {
                // N=50: Zostawiamy średnio ciężkie i szybkie metryki
                allResults.addAll(runJmh(sizeStr,
                        new String[]{"RF", "MS", "MC", "MP", "CopheneticL2"},
                        classicOnly, quickEstimate));
            } else if (size <= 80) {
                // N=80: Tylko szybkie bazowe RF
                allResults.addAll(runJmh(sizeStr,
                        new String[]{"RF"},
                        classicOnly, quickEstimate));
            }

            // --- HARMONOGRAM DLA WARIANTU INKREMENTALNEGO (INCREMENTAL) ---
            // Uruchamiany aż do N=200 dla 5 metryk posiadających implementacje akcelerowane
            allResults.addAll(runJmh(sizeStr,
                    new String[]{"RF", "MS", "MC", "MP", "M3"},
                    incrOnly, quickEstimate));
        }

        exportToCsv("benchmark_distance_SPR_RF.csv", allResults, "SPR-RF", "TimeMs");
    }
}