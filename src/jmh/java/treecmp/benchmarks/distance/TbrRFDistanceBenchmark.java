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

/**
 * Benchmark pełnego przebiegu wspinaczki w otoczeniu TBR z tie-breakerem RF/RFC dla remisów.
 * Obejmuje 13 metryk głównych (6 ukorzenionych + 7 nieukorzenionych).
 */
public class TbrRFDistanceBenchmark extends AbstractDistanceBenchmark {

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
                classicMetric = new TbrHeuristicMetric(new CopheneticL2Metric(), new RFClusterMetric(), true, "CopheneticL2");
                break;
            case "RMAST":
                isRooted = true;
                classicMetric = new TbrHeuristicMetric(new RMASTMetric(), new RFClusterMetric(), true, "RMAST");
                break;
            case "MC":
                isRooted = true;
                classicMetric = new TbrHeuristicMetric(new MatchingClusterMetricO3(), new RFClusterMetric(), true, "MC");
                incrementalMetric = new TbrIncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new TbrHeuristicMetric(new MatchingPairMetric(), new RFClusterMetric(), true, "MP");
                incrementalMetric = new TbrIncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "NodalL2Splitted":
                isRooted = true;
                classicMetric = new TbrHeuristicMetric(new NodalL2SplittedMetric(), new RFClusterMetric(), true, "NodalL2Splitted");
                break;
            case "Triplet":
                isRooted = true;
                classicMetric = new TbrHeuristicMetric(new TripletMetric(), new RFClusterMetric(), true, "Triplet");
                break;

            // --- NIEUKORZENIONE (tie-breaker: RFMetric) ---
            case "M3":
                isRooted = false;
                classicMetric = new TbrHeuristicMetric(new MatchingTripletMetric(), new RFMetric(), false, "M3");
                incrementalMetric = new UtbrIncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            case "MPU":
                isRooted = false;
                classicMetric = new TbrHeuristicMetric(new MatchingPairUnrootedMetric(), new RFMetric(), false, "MPU");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new TbrHeuristicMetric(new MatchingSplitMetric(), new RFMetric(), false, "MS");
                incrementalMetric = new UtbrIncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "NodalL2":
                isRooted = false;
                classicMetric = new TbrHeuristicMetric(new NodalL2Metric(), new RFMetric(), false, "NodalL2");
                break;
            case "Quartet":
                isRooted = false;
                classicMetric = new TbrHeuristicMetric(new QuartetMetricLong(), new RFMetric(), false, "Quartet");
                break;
            case "UMAST":
                isRooted = false;
                classicMetric = new TbrHeuristicMetric(new UMASTMetric(), new RFMetric(), false, "UMAST");
                break;
            case "RF":
                isRooted = false;
                classicMetric = new TbrHeuristicMetric(new RFMetric(), false, "RF");
                incrementalMetric = new UtbrIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metric);
        }

        loadOrGenerateTrees(size, isRooted);
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();
        String className = TbrRFDistanceBenchmark.class.getName();
        String classicOnly = className + "\\.benchmarkClassicFullRun";
        String incrOnly = className + "\\.benchmarkIncrementalFullRun";
        List<RunResult> allResults = new ArrayList<>();

        int[] sizes = {10, 20, 30, 50, 80, 120, 200};

        for (int size : sizes) {
            String sizeStr = String.valueOf(size);

            // --- 1. HARMONOGRAM DLA WARIANTU KLASYCZNEGO (TBR CLASSIC O(N^3)) ---
            if (size <= 30) {
                // N=10, 20, 30: Pełny komplet wszystkich 13 metryk w otoczeniu TBR (~34 min łącznie)
                allResults.addAll(runJmh(sizeStr,
                        new String[]{"CopheneticL2", "RMAST", "MC", "MP", "NodalL2Splitted", "Triplet", "M3", "MPU", "MS", "NodalL2", "Quartet", "UMAST", "RF"},
                        classicOnly, quickEstimate));
            } else if (size <= 50) {
                // N=50: 11 z 13 metryk Classic (~2h 15m).
                // Odcinamy tylko najcięższe UMAST oraz Quartet.
                allResults.addAll(runJmh(sizeStr,
                        new String[]{"RF", "CopheneticL2", "NodalL2", "NodalL2Splitted", "RMAST", "MP", "MPU", "MC", "MS", "M3", "Triplet"},
                        classicOnly, quickEstimate));
            }
            // N >= 80: Brak Classic (sąsiedztwo O(n^3) przekracza 10 min/op)

            // --- 2. HARMONOGRAM DLA WARIANTU INKREMENTALNEGO (TBR INCREMENTAL) ---
            if (size <= 50) {
                // N=10, 20, 30, 50: Pełny komplet wszystkich 5 metryk inkrementalnych (wraz z M3) (~24 min łącznie)
                allResults.addAll(runJmh(sizeStr,
                        new String[]{"RF", "MS", "MC", "MP", "M3"},
                        incrOnly, quickEstimate));
            } else if (size <= 80) {
                // N=80: 4 metryki inkrementalne - wyłączamy sześcienne M3 w uTBR (~45 min)
                allResults.addAll(runJmh(sizeStr,
                        new String[]{"RF", "MS", "MC", "MP"},
                        incrOnly, quickEstimate));
            } else if (size <= 120) {
                // N=120: 3 metryki inkrementalne - odcinamy MS (w uTBR przy N=120 grozi timeoutem) (~45 min)
                allResults.addAll(runJmh(sizeStr,
                        new String[]{"RF", "MC", "MP"},
                        incrOnly, quickEstimate));
            } else {
                // N=200: Tylko najszybsze metryki (MP oraz RF) - bez MS, M3 oraz ciężkiego MC (~1h 15m)
                allResults.addAll(runJmh(sizeStr,
                        new String[]{"MP", "RF"},
                        incrOnly, quickEstimate));
            }
        }

        exportToCsv("benchmark_distance_TBR_RF.csv", allResults, "TBR-RF", "TimeMs");
    }
}