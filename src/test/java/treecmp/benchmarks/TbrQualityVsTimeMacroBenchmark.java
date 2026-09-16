package treecmp.benchmarks;

import pal.tree.Tree;
import treecmp.heuristics.tbr.TbrHeuristicMetric;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.*;

public class TbrQualityVsTimeMacroBenchmark extends AbstractQualityMacroBenchmark {

    private final int maxAllowedClassicTbrSize;

    public TbrQualityVsTimeMacroBenchmark() {
        this.MAX_ALLOC_PER_PAIR_BYTES = 100L * 1024 * 1024 * 1024; // 100 GB

        long maxHeapBytes = Runtime.getRuntime().maxMemory();
        double maxHeapGb = maxHeapBytes / (1024.0 * 1024.0 * 1024.0);

        this.maxAllowedClassicTbrSize = determineMaxClassicTbrSize(maxHeapGb);

        System.out.println("======================================================================");
        System.out.printf("[MEMORY CONFIG] Detected JVM Max Heap: %.2f GB (-Xmx)%n", maxHeapGb);
        System.out.printf("[MEMORY CONFIG] Max allowed tree size (N) for Classic TBR: %d%n", maxAllowedClassicTbrSize);
        System.out.println("======================================================================");
    }

    private int determineMaxClassicTbrSize(double maxHeapGb) {
        if (maxHeapGb >= 85.0) {
            return 50;
        } else if (maxHeapGb >= 28.0) {
            return 30;
        } else if (maxHeapGb >= 14.0) {
            return 20;
        } else {
            return 10;
        }
    }

    public static void main(String[] args) {
        new TbrQualityVsTimeMacroBenchmark().runBenchmark(
                args,
                "TBR QUALITY VS TIME MACRO-BENCHMARK (100 TREE PAIRS)",
                "benchmark_results_TBR",
                new int[]{10, 20, 30, 50, 80, 120, 200}
        );
    }

    @Override
    protected void evaluateVariant(int size, boolean isRooted, String metricName, String variantName, Metric heuristic,
                                   List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName) {
        if (heuristic == null) return;

        if (isClassicVariant(variantName) && size > maxAllowedClassicTbrSize) {
            printSkipped(metricName, variantName, "Skip(RAM Limit)", "Max N=" + maxAllowedClassicTbrSize);
            return;
        }

        super.evaluateVariant(size, isRooted, metricName, variantName, heuristic, trees, blacklist, history, csvFileName);
    }

    private boolean isClassicVariant(String variantName) {
        return variantName.contains("Classic");
    }

    @Override
    protected void runEvaluationsForSize(int size, boolean rooted, List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName) {
        List<MetricSetup> metricsToTest = rooted ? getRootedMetrics() : getUnrootedMetrics();
        for (MetricSetup setup : metricsToTest) {
            forceCleanMemory();
            evaluateVariant(size, rooted, setup.name, "Classic (Pure)", setup.classicPure, trees, blacklist, history, csvFileName);

            forceCleanMemory();
            evaluateVariant(size, rooted, setup.name, "Increm. (Pure)", setup.incrementalPure, trees, blacklist, history, csvFileName);

            forceCleanMemory();
            evaluateVariant(size, rooted, setup.name, "Classic + RF (Tie)", setup.classicFiltered, trees, blacklist, history, csvFileName);

            forceCleanMemory();
            evaluateVariant(size, rooted, setup.name, "Increm. + RF (Tie)", setup.incrementalFiltered, trees, blacklist, history, csvFileName);

            System.out.println("-".repeat(160));
        }
    }

    private List<MetricSetup> getRootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();
        list.add(new MetricSetup("RFCluster",
                new TbrHeuristicMetric(new RFClusterMetric(), true, "RFC"),
                new TbrIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFCinc"),
                null, null
        ));
        list.add(new MetricSetup("MC",
                new TbrHeuristicMetric(new MatchingClusterMetric(), true, "MC_Pure"),
                new TbrIncrementalHeuristic(new MCIncrementalMetric(), "MCinc"),
                new TbrHeuristicMetric(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                new TbrIncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MC_RFinc")
        ));
        list.add(new MetricSetup("MP",
                new TbrHeuristicMetric(new MatchingPairMetric(), true, "MP_Pure"),
                new TbrIncrementalHeuristic(new MPIncrementalMetric(), "MPinc"),
                new TbrHeuristicMetric(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                new TbrIncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MP_RFinc")
        ));
        return list;
    }

    private List<MetricSetup> getUnrootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();
        list.add(new MetricSetup("RF",
                new TbrHeuristicMetric(new RFMetric(), false, "RF"),
                new UtbrIncrementalHeuristic(new RFIncrementalMetric(), "RFinc"),
                null, null
        ));
        list.add(new MetricSetup("MS",
                new TbrHeuristicMetric(new MatchingSplitMetric(), false, "MS_Pure"),
                new UtbrIncrementalHeuristic(new MSIncrementalMetric(), "MSinc"),
                new TbrHeuristicMetric(new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                new UtbrIncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MS_RFinc")
        ));
        list.add(new MetricSetup("M3",
                new TbrHeuristicMetric(new MatchingTripletMetric(), false, "M3_Pure"),
                new UtbrIncrementalHeuristic(new M3IncrementalMetric(), "M3inc"),
                new TbrHeuristicMetric(new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                new UtbrIncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3_RFinc")
        ));
        return list;
    }
}
