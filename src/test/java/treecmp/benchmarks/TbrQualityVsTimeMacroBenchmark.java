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

    public enum MetricFilter {
        ALL,
        ROOTED,
        UNROOTED;

        public static MetricFilter parse(String token) {
            if (token == null) return null;
            String s = token.trim().toLowerCase();
            if (s.startsWith("--")) {
                s = s.substring(2);
            } else if (s.startsWith("-")) {
                s = s.substring(1);
            }
            switch (s) {
                case "r":
                case "rooted":
                    return ROOTED;
                case "u":
                case "unrooted":
                    return UNROOTED;
                case "a":
                case "all":
                case "both":
                    return ALL;
                default:
                    return null;
            }
        }
    }

    private final int maxAllowedClassicTbrSize;
    private final MetricFilter metricFilter;

    private static class BenchmarkConfig {
        final int[] sizes;
        final MetricFilter filter;
        final boolean customSizesSpecified;

        BenchmarkConfig(int[] sizes, MetricFilter filter, boolean customSizesSpecified) {
            this.sizes = sizes;
            this.filter = filter;
            this.customSizesSpecified = customSizesSpecified;
        }
    }

    public TbrQualityVsTimeMacroBenchmark() {
        this(MetricFilter.ALL);
    }

    public TbrQualityVsTimeMacroBenchmark(MetricFilter metricFilter) {
        this.metricFilter = metricFilter != null ? metricFilter : MetricFilter.ALL;
        this.MAX_ALLOC_PER_PAIR_BYTES = 100L * 1024 * 1024 * 1024; // 100 GB

        long maxHeapBytes = Runtime.getRuntime().maxMemory();
        double maxHeapGb = maxHeapBytes / (1024.0 * 1024.0 * 1024.0);

        this.maxAllowedClassicTbrSize = determineMaxClassicTbrSize(maxHeapGb);

        System.out.println("======================================================================");
        System.out.printf("[MEMORY CONFIG] Detected JVM Max Heap: %.2f GB (-Xmx)%n", maxHeapGb);
        System.out.printf("[MEMORY CONFIG] Max allowed tree size (N) for Classic TBR: %d%n", maxAllowedClassicTbrSize);
        System.out.printf("[FILTER CONFIG] Metric target: %s%n", this.metricFilter);
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
        int[] defaultSizes = new int[]{10, 20, 30, 50, 80, 120, 200};
        BenchmarkConfig config = parseArguments(args, defaultSizes);

        if (config.customSizesSpecified) {
            System.out.println("[CONFIG] Custom tree sizes requested: " + Arrays.toString(config.sizes));
        }
        System.out.println("[CONFIG] Metric execution mode: " + config.filter);

        new TbrQualityVsTimeMacroBenchmark(config.filter).runBenchmark(
                new String[0],
                "TBR QUALITY VS TIME MACRO-BENCHMARK (100 TREE PAIRS)",
                "benchmark_results_TBR",
                config.sizes
        );
    }

    private static BenchmarkConfig parseArguments(String[] args, int[] defaultSizes) {
        MetricFilter filter = MetricFilter.ALL;
        if (args == null || args.length == 0) {
            return new BenchmarkConfig(defaultSizes, filter, false);
        }

        List<Integer> sizesList = new ArrayList<>();
        for (String arg : args) {
            String[] tokens = arg.split("[,;\\s]+");
            for (String token : tokens) {
                token = token.trim();
                if (token.isEmpty()) continue;

                MetricFilter parsedFilter = MetricFilter.parse(token);
                if (parsedFilter != null) {
                    filter = parsedFilter;
                } else {
                    try {
                        sizesList.add(Integer.parseInt(token));
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Invalid parameter '" + token + "' (ignored).");
                    }
                }
            }
        }

        boolean hasCustomSizes = !sizesList.isEmpty();
        int[] chosenSizes;
        if (hasCustomSizes) {
            chosenSizes = new int[sizesList.size()];
            for (int i = 0; i < sizesList.size(); i++) {
                chosenSizes[i] = sizesList.get(i);
            }
        } else {
            chosenSizes = defaultSizes;
        }

        return new BenchmarkConfig(chosenSizes, filter, hasCustomSizes);
    }

    @Override
    protected void evaluateVariant(int size, boolean isRooted, String metricName, String variantName, Metric heuristic,
                                   List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName) {
        if (heuristic == null) return;

        if (metricFilter == MetricFilter.ROOTED && !isRooted) return;
        if (metricFilter == MetricFilter.UNROOTED && isRooted) return;

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
        if (metricFilter == MetricFilter.ROOTED && !rooted) return;
        if (metricFilter == MetricFilter.UNROOTED && rooted) return;

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
                new TbrIncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MCinc_RF")
        ));
        list.add(new MetricSetup("MP",
                new TbrHeuristicMetric(new MatchingPairMetric(), true, "MP_Pure"),
                new TbrIncrementalHeuristic(new MPIncrementalMetric(), "MPinc"),
                new TbrHeuristicMetric(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                new TbrIncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MPinc_RF")
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
                new UtbrIncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MSinc_RF")
        ));
        list.add(new MetricSetup("M3",
                new TbrHeuristicMetric(new MatchingTripletMetric(), false, "M3_Pure"),
                new UtbrIncrementalHeuristic(new M3IncrementalMetric(), "M3inc"),
                new TbrHeuristicMetric(new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                new UtbrIncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3inc_RF")
        ));
        return list;
    }
}