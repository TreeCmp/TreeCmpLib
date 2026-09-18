package treecmp.benchmarks;

import pal.tree.Tree;
import treecmp.heuristics.spr.SprHeuristicMetric;
import treecmp.heuristics.spr.UsprHeuristicMetric;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.*;

public class SprQualityVsTimeMacroBenchmark extends AbstractQualityMacroBenchmark {

    private final int maxAllowedClassicSprSize;

    public SprQualityVsTimeMacroBenchmark() {
        this.MAX_ALLOC_PER_PAIR_BYTES = 100L * 1024 * 1024 * 1024; // 100 GB

        long maxHeapBytes = Runtime.getRuntime().maxMemory();
        double maxHeapGb = maxHeapBytes / (1024.0 * 1024.0 * 1024.0);

        this.maxAllowedClassicSprSize = determineMaxClassicSprSize(maxHeapGb);

        System.out.println("======================================================================");
        System.out.printf("[MEMORY CONFIG] Detected JVM Max Heap: %.2f GB (-Xmx)%n", maxHeapGb);
        System.out.printf("[MEMORY CONFIG] Max allowed tree size (N) for Classic SPR: %d%n", maxAllowedClassicSprSize);
        System.out.println("======================================================================");
    }

    private int determineMaxClassicSprSize(double maxHeapGb) {
        if (maxHeapGb >= 85.0) {
            return 80;
        } else if (maxHeapGb >= 28.0) {
            return 50;
        } else if (maxHeapGb >= 14.0) {
            return 30;
        } else {
            return 20;
        }
    }

    public static void main(String[] args) {
        treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic.ENABLE_LOGGING = false;

        new SprQualityVsTimeMacroBenchmark().runBenchmark(
                args,
                "SPR QUALITY VS TIME MACRO-BENCHMARK (100 TREE PAIRS)",
                "benchmark_quality_SPR",
                new int[]{10, 20, 30, 50, 80, 120, 200}
        );
    }

    @Override
    protected void evaluateVariant(int size, boolean isRooted, String metricName, String variantName, Metric heuristic,
                                   List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName) {
        if (heuristic == null) return;

        if (isClassicVariant(variantName) && size > maxAllowedClassicSprSize) {
            printSkipped(metricName, variantName, "Skip(RAM Limit)", "Max N=" + maxAllowedClassicSprSize);
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
                new SprHeuristicMetric(new RFClusterMetric(), true, "RFC"),
                new SprIncrementalHeuristicMetric(new RFClusterIncrementalMetric(), "RFCinc"),
                null, null
        ));
        list.add(new MetricSetup("MC",
                new SprHeuristicMetric(new MatchingClusterMetric(), true, "MC_Pure"),
                new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), "MCinc"),
                new SprHeuristicMetric(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MCinc_RF")
        ));
        list.add(new MetricSetup("MP",
                new SprHeuristicMetric(new MatchingPairMetric(), true, "MP_Pure"),
                new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), "MPinc"),
                new SprHeuristicMetric(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MPinc_RF")
        ));
        return list;
    }

    private List<MetricSetup> getUnrootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();
        list.add(new MetricSetup("RF",
                new UsprHeuristicMetric(new RFMetric(), "RF"),
                new UsprIncrementalHeuristicMetric(new RFIncrementalMetric(), "RFinc"),
                null, null
        ));
        list.add(new MetricSetup("MS",
                new UsprHeuristicMetric(new MatchingSplitMetric(), "MS_Pure"),
                new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), "MSinc"),
                new UsprHeuristicMetric(new MatchingSplitMetric(), new RFMetric(), "MS_RF"),
                new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), new RFIncrementalMetric(), "MSinc_RF")
        ));
        list.add(new MetricSetup("M3",
                new UsprHeuristicMetric(new MatchingTripletMetric(), "M3_Pure"),
                new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3inc"),
                new UsprHeuristicMetric(new MatchingTripletMetric(), new RFMetric(), "M3_RF"),
                new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3inc_RF")
        ));
        return list;
    }
}