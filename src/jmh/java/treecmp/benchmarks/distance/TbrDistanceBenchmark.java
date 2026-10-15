package treecmp.benchmarks.distance;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.results.RunResult;
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
        String className   = TbrDistanceBenchmark.class.getName();
        String classicOnly = className + "\\.benchmarkClassicFullRun";
        String incrOnly    = className + "\\.benchmarkIncrementalFullRun";

        List<RunResult> allResults = new ArrayList<>();

        // Dynamiczny odczyt rozmiarów drzew z adnotacji @Param (zasada DRY)
        String[] treeSizes = TbrDistanceBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        String[] allMetrics = {"RF", "RFC", "MS", "MC", "MP", "M3"};

        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            if (size <= 20) {
                // N <= 20: Pełny zestaw 6 metryk — najpierw Classic, potem Incremental
                allResults.addAll(runJmh(sizeStr, allMetrics, classicOnly, quickEstimate));
                allResults.addAll(runJmh(sizeStr, allMetrics, incrOnly, quickEstimate));
            } else if (size <= 30) {
                // N = 30: Classic tylko dla szybkich RF/RFC; Incremental w jednym bloku dla wszystkich 6 metryk
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, classicOnly, quickEstimate));
                allResults.addAll(runJmh(sizeStr, allMetrics, incrOnly, quickEstimate));
            } else if (size <= 50) {
                // N = 50: Opcjonalnie Classic RF/RFC (~65-92 s/op — odkomentuj, jeśli chcesz mieć punkt odniesienia jak w SPR dla N=80):
                // allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, classicOnly, quickEstimate));

                // Incremental bez M3 (ze względu na koszt O(n^3) przy wspinaczce)
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP"}, incrOnly, quickEstimate));
            } else if (size <= 80) {
                // N = 80: Tylko Incremental; bez MS (>6 min/op) oraz M3
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MC", "MP"}, incrOnly, quickEstimate));
            } else {
                // N = 120 i N = 200: Wyłącznie Incrementalne metryki ukorzenione (TbrIncrementalHeuristic)
                // Nieukorzenione uTBR (RF, MS, M3) mają zbyt wysoką złożoność powyżej N = 80
                allResults.addAll(runJmh(sizeStr, new String[]{"RFC", "MC", "MP"}, incrOnly, quickEstimate));
            }
        }

        exportToCsv("benchmark_distance_TBR.csv", allResults, "TBR", "TimeMs");
    }
}