package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.results.RunResult;

import pal.tree.SimpleTree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;

import static treecmp.benchmarks.singleStep.AbstractSingleStepBenchmark.isQuickEstimate;

public class TbrSingleStepBenchmark extends AbstractSingleStepBenchmark {

    private TreeNeighborhoodUtils classicUtils;

    @Override
    @Setup(Level.Trial)
    public void setup() {
        super.setup();
    }

    @Override
    protected void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            case "RF":
                isRooted = false;
                classicMetric = new RFMetric();
                incrementalMetric = new UtbrIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new TbrIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new UtbrIncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                treecmp.config.IOSettings.getIOSettings().setOptMsMcByRf(true);
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new TbrIncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new TbrIncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new UtbrIncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Unknown metric: " + metric);
        }

        // Wspólne ładowanie/generowanie drzew z AbstractSingleStepBenchmark
        loadOrGenerateTrees(size, isRooted);

        classicUtils = isRooted ? new TbrUtils() : new UTbrUtils();
    }

    @Override
    protected double evaluateClassicBestDist() {
        final double[] bestDist = {Double.POSITIVE_INFINITY};

        if (classicUtils != null) {
            classicUtils.forEachNeighbour(t1, neighbor -> {
                double d;
                try {
                    if (neighbor instanceof SimpleTree) {
                        ((SimpleTree) neighbor).createNodeList();
                    }
                    d = classicMetric.getDistance(neighbor, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }
                if (d < bestDist[0]) {
                    bestDist[0] = d;
                }
            });
        }

        return bestDist[0];
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();
        String className = TbrSingleStepBenchmark.class.getSimpleName();
        List<RunResult> allResults = new ArrayList<>();

        int[] sizes = {10, 20, 30, 50, 80, 120, 200, 300, 500, 800, 1200};

        for (int size : sizes) {
            String sizeStr = String.valueOf(size);

            if (size <= 50) {
                // N <= 50: Pełny Classic + Incremental dla WSZYSTKICH 6 metryk.
                // M3 Classic przy N=30 zajmuje ~0.4s, a przy N=50 tylko ~4s!
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));
            } else if (size <= 80) {
                // N = 80: Rozszerzamy Classic o MS (~16.8s) i M3 (~32.6s).
                // Classic trwa od 3.5s (RF) do 32.6s (M3) - mieści się w budżecie.
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));
            } else if (size <= 120) {
                // N = 120: RF Classic trwa ~14.6s - dociągamy linię RF Classic.
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RFC", "MS", "MC", "MP", "M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 200) {
                // N = 200: Pełny zestaw 6 metryk Incremental
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 300) {
                // N = 300: RF, RFC, MC, MP Incremental
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MC", "MP"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 500) {
                // N = 500: RF, RFC, MP Incremental
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MP"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else {
                // N = 800 oraz 1200: RFC Incremental
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RFC"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            }
        }

        AbstractSingleStepBenchmark.exportToCsv("benchmark_single_step_TBR.csv", allResults, "TBR");
    }
}