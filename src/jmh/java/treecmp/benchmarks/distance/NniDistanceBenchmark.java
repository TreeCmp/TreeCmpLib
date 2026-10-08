package treecmp.benchmarks.distance;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.results.RunResult;
import treecmp.benchmarks.distance.AbstractDistanceBenchmark;
import treecmp.heuristics.nni.NniClassicHeuristic;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;

public class NniDistanceBenchmark extends AbstractDistanceBenchmark {

    // Parametr opcjonalnego filtra remisów RF[cite: 14]
    @Param({"Pure", "RF_Tie"})
    public String variant;

    @Param({"10", "20", "30", "50", "80", "120", "200"})
    public int treeSize;

    @Override
    protected void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;
        boolean useRfFilter = "RF_Tie".equals(variant);

        switch (metric) {
            case "RF":
                isRooted = false;
                if (useRfFilter) return; // Filtr nie ma zastosowania do samej bazy RF[cite: 14]
                classicMetric     = new NniClassicHeuristic(new RFMetric(), isRooted, "RF");
                incrementalMetric = new NniIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                if (useRfFilter) return;
                classicMetric     = new NniClassicHeuristic(new RFClusterMetric(), isRooted, "RFC");
                incrementalMetric = new NniIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric     = useRfFilter ?
                        new NniClassicHeuristic(new MatchingSplitMetric(), new RFMetric(), isRooted, "MS_RF") :
                        new NniClassicHeuristic(new MatchingSplitMetric(), isRooted, "MS_Pure");
                incrementalMetric = useRfFilter ?
                        new NniIncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MSinc_RF") :
                        new NniIncrementalHeuristic(new MSIncrementalMetric(), "MSinc_Pure");
                break;
            case "MC":
                isRooted = true;
                classicMetric     = useRfFilter ?
                        new NniClassicHeuristic(new MatchingClusterMetric(), new RFClusterMetric(), isRooted, "MC_RF") :
                        new NniClassicHeuristic(new MatchingClusterMetric(), isRooted, "MC_Pure");
                incrementalMetric = useRfFilter ?
                        new NniIncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MCinc_RF") :
                        new NniIncrementalHeuristic(new MCIncrementalMetric(), "MCinc_Pure");
                break;
            case "MP":
                isRooted = true;
                classicMetric     = useRfFilter ?
                        new NniClassicHeuristic(new MatchingPairMetric(), new RFClusterMetric(), isRooted, "MP_RF") :
                        new NniClassicHeuristic(new MatchingPairMetric(), isRooted, "MP_Pure");
                incrementalMetric = useRfFilter ?
                        new NniIncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MPinc_RF") :
                        new NniIncrementalHeuristic(new MPIncrementalMetric(), "MPinc_Pure");
                break;
            case "M3":
                isRooted = false;
                classicMetric     = useRfFilter ?
                        new NniClassicHeuristic(new MatchingTripletMetric(), new RFMetric(), isRooted, "M3_RF") :
                        new NniClassicHeuristic(new MatchingTripletMetric(), isRooted, "M3_Pure");
                incrementalMetric = useRfFilter ?
                        new NniIncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3inc_RF") :
                        new NniIncrementalHeuristic(new M3IncrementalMetric(), "M3inc_Pure");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metric);
        }

        loadOrGenerateTrees(size, isRooted);
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();
        String className = NniDistanceBenchmark.class.getSimpleName();
        String incrOnly = className + ".benchmarkIncrementalFullRun";
        List<RunResult> allResults = new ArrayList<>();

        int[] sizes = {10, 20, 30, 50, 80, 120, 200};

        for (int size : sizes) {
            String sizeStr = String.valueOf(size);

            if (size <= 30) {
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));
            } else if (size <= 50) {
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP"}, className, quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"M3"}, incrOnly, quickEstimate));
            } else if (size <= 80) {
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS"}, className, quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"MC", "MP", "M3"}, incrOnly, quickEstimate));
            } else {
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, className, quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"MS", "MC", "MP", "M3"}, incrOnly, quickEstimate));
            }
        }

        exportToCsv("benchmark_distance_NNI.csv", allResults, "NNI", "TimeMs");
    }
}