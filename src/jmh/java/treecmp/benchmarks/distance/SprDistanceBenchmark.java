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

    @Param({"10", "20", "30", "50", "80", "120", "200"})
    public int treeSize; // Ograniczenie do N=200 (bez tie-breakera N>=300 przekracza budzet czasowy i timeout)

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
        String className = SprDistanceBenchmark.class.getName();
        String classicOnly = className + "\\.benchmarkClassicFullRun";
        String incrOnly = className + "\\.benchmarkIncrementalFullRun";
        List<RunResult> allResults = new ArrayList<>();

        // Ograniczamy do N=200 (~4.5 godz. lacznego czasu dla calego benchmarku)
        int[] sizes = {10, 20, 30, 50, 80, 120, 200};

        for (int size : sizes) {
            String sizeStr = String.valueOf(size);

            // --- 1. WARIANT KLASYCZNY (CLASSIC) ---
            if (size <= 50) {
                // N=10, 20, 30, 50: Pelny zestaw wszystkich 6 metryk w wersji Classic (~36 min lacznie)
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, classicOnly, quickEstimate));
            } else if (size <= 80) {
                // N=80: Szybkie bazowe RF i RFC w wersji Classic (~17.5 min)
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, classicOnly, quickEstimate));
            }
            // N >= 120: Brak Classic (przekracza limit czasu na iteracje)

            // --- 2. WARIANT INKREMENTALNY (INCREMENTAL) ---
            if (size <= 120) {
                // N <= 120: Pelny zestaw wszystkich 6 akcelerowanych metryk (~1h 05m lacznie)
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, incrOnly, quickEstimate));
            } else {
                // N=200: 5 metryk inkrementalnych - bez "MS" (~2h 29m)
                allResults.addAll(runJmh(sizeStr, new String[]{"RFC", "MP", "M3", "MC", "RF"}, incrOnly, quickEstimate));
            }
        }

        exportToCsv("benchmark_distance_SPR.csv", allResults, "SPR", "TimeMs");
    }
}