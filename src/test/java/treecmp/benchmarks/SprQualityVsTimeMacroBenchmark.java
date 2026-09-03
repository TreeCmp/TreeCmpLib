package treecmp.benchmarks;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.spr.SprHeuristicMetric;
import treecmp.heuristics.spr.UsprHeuristicMetric;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TreeCreator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SprQualityVsTimeMacroBenchmark {

    static class MetricSetup {
        String name;
        treecmp.metrics.Metric classicPure;
        treecmp.metrics.Metric incrementalPure;
        treecmp.metrics.Metric classicFiltered;
        treecmp.metrics.Metric incrementalFiltered;

        public MetricSetup(String name,
                           treecmp.metrics.Metric classicPure,
                           treecmp.metrics.Metric incrementalPure,
                           treecmp.metrics.Metric classicFiltered,
                           treecmp.metrics.Metric incrementalFiltered) {
            this.name = name;
            this.classicPure = classicPure;
            this.incrementalPure = incrementalPure;
            this.classicFiltered = classicFiltered;
            this.incrementalFiltered = incrementalFiltered;
        }
    }

    static class HistoryRecord {
        int n;
        long allocPerPairBytes;
        long timePerPairNs;

        public HistoryRecord(int n, long allocPerPairBytes, long timePerPairNs) {
            this.n = n;
            this.allocPerPairBytes = allocPerPairBytes;
            this.timePerPairNs = timePerPairNs;
        }
    }

    public static void main(String[] args) {
        treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic.ENABLE_LOGGING = false;

        System.out.println("=======================================================================================================================================");
        System.out.println("                                         SPR QUALITY VS TIME MACRO-BENCHMARK (100 TREE PAIRS)");
        System.out.println("=======================================================================================================================================");

        int[] sizes;
        if (args.length > 0) {
            try {
                int explicitSize = Integer.parseInt(args[0]);
                sizes = new int[]{explicitSize};
                System.out.println("Single size mode. Running only for N = " + explicitSize);
            } catch (NumberFormatException e) {
                System.err.println("Error: Provided parameter '" + args[0] + "' is not a valid integer.");
                System.err.println("Usage: java treecmp.benchmarks.SprQualityVsTimeMacroBenchmark [tree_size]");
                return;
            }
        } else {
            sizes = new int[]{10, 20, 30, 50, 80, 120, 200};
            System.out.println("No arguments provided. Running default sequence: 10, 20, 30, 50, 80, 120, 200");
        }

        runAllSuites(sizes);
    }

    private static void runAllSuites(int[] sizes) {
        List<MetricSetup> rootedMetrics = getRootedMetrics();
        List<MetricSetup> unrootedMetrics = getUnrootedMetrics();

        Set<String> blacklist = new HashSet<>();
        Map<String, List<HistoryRecord>> history = new HashMap<>();

        for (int size : sizes) {
            System.out.println("\n\n#######################################################################################################################################");
            System.out.println("                                                      TREE SIZE: N=" + size);
            System.out.println("#######################################################################################################################################");

            runForSizeAndType(size, true, rootedMetrics, blacklist, history);
            runForSizeAndType(size, false, unrootedMetrics, blacklist, history);
        }
    }

    private static void runForSizeAndType(int size, boolean rooted, List<MetricSetup> metricsToTest, Set<String> blacklist, Map<String, List<HistoryRecord>> history) {
        String treeType = rooted ? "ROOTED (rb)" : "UNROOTED (ub)";
        String suffix = rooted ? "rb" : "ub";
        String fileName = "datasets/n" + size + "y200" + suffix + ".newick";
        File file = new File(fileName);

        String csvFileName = "benchmark_quality_SPR_N" + size + "_" + (rooted ? "rooted" : "unrooted") + ".csv";

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFileName, false))) {
            pw.println("Size,IsRooted,Metric,Variant,PairIndex,Success,Distance,TotalTimeMs,AllocBytes,PeakRamBytes");
        } catch (Exception e) {
            System.err.println("Failed to create CSV file: " + csvFileName);
        }

        System.out.println("\n>>> STARTING TESTS FOR SIZE N=" + size + " | " + treeType + " (" + fileName + ") <<<");

        if (!file.exists()) {
            System.out.println("Missing file: " + fileName + " (Skipping)");
            return;
        }

        List<Tree> trees = loadTrees(fileName);
        if (trees == null || trees.size() < 200) {
            System.out.println("File " + fileName + " does not contain enough trees.");
            return;
        }

        System.out.println("\n--- RESULTS FOR SIZE N=" + size + " | " + treeType + " (" + fileName + ") ---");
        System.out.printf("%-18s | %-20s | %-12s | %-15s | %-15s | %-15s | %-12s | %-12s\n",
                "Metric", "Heuristic Variant", "Successes", "Avg SPR Dist", "Total Time", "Time/Pair", "Alloc/Pair", "Peak RAM");
        System.out.println("-".repeat(140));

        for (MetricSetup setup : metricsToTest) {
            if (size < 200) {
                forceCleanMemory();
                evaluateAndReport(size, rooted, setup.name, "Classic (Pure)", setup.classicPure, trees, blacklist, history, csvFileName);
            }

            forceCleanMemory();
            evaluateAndReport(size, rooted, setup.name, "Increm. (Pure)", setup.incrementalPure, trees, blacklist, history, csvFileName);

            if (size < 200) {
                forceCleanMemory();
                evaluateAndReport(size, rooted, setup.name, "Classic + RF (Tie)", setup.classicFiltered, trees, blacklist, history, csvFileName);
            }

            forceCleanMemory();
            evaluateAndReport(size, rooted, setup.name, "Increm. + RF (Tie)", setup.incrementalFiltered, trees, blacklist, history, csvFileName);

            System.out.println("-".repeat(140));
        }
    }

    private static void forceCleanMemory() {
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void printIntermediateResult(String metricName, String variantName, int successCount, int processedPairs, int totalPairs,
                                                boolean timedOut, boolean oomOccurred, double totalDistance, long totalTimeNs,
                                                long totalAllocatedBytes, long maxPeakBytes, boolean isFinal) {

        double avgDistance = successCount > 0 ? (totalDistance / successCount) : 0.0;
        double totalTimeMs = totalTimeNs / 1_000_000.0;
        double avgTimeMs = processedPairs > 0 ? (totalTimeMs / processedPairs) : 0.0;
        long avgBytesPerPair = processedPairs > 0 ? (totalAllocatedBytes / processedPairs) : 0;

        String distStr;
        String successStr;
        String memoryStr;
        String peakStr;
        String breakdownStr = "N/A";

        if (oomOccurred) {
            successStr = successCount + "/" + (processedPairs + 1) + " (Crash!)";
            distStr = "OOM";
            memoryStr = "OOM";
            peakStr = "OOM";
        } else {
            String status = timedOut ? " (TO)" : "";
            successStr = successCount + "/" + processedPairs + status;
            distStr = processedPairs > 0 ? (successCount > 0 ? String.format(Locale.US, "%.4f", avgDistance) : "None(Inf)") : "Run...";
            memoryStr = formatMemory(avgBytesPerPair);
            peakStr = formatMemory(maxPeakBytes);
        }

        if (!isFinal) {
            String shortMet = metricName.length() > 16 ? metricName.substring(0, 16) : metricName;
            String shortVar = variantName.length() > 18 ? variantName.substring(0, 16) + ".." : variantName;
            String shortDist = distStr.length() > 8 ? distStr.substring(0, 8) : distStr;

            System.out.print("\033[2K\r");
            System.out.printf("=> [%-16s] %-18s | %3d/%-3d | D: %-8s | %5.1fs | %s",
                    shortMet, shortVar, processedPairs, totalPairs, shortDist, totalTimeMs / 1000.0, breakdownStr);
            System.out.flush();
        } else {
            System.out.print("\033[2K\r");
            System.out.printf("%-18s | %-20s | %-12s | %-15s | %-15.0f ms | %-15.2f ms | %-12s | %-12s\n",
                    metricName, variantName, successStr, distStr, totalTimeMs, avgTimeMs, memoryStr, peakStr);
            System.out.flush();
        }
    }

    private static void evaluateAndReport(int size, boolean isRooted, String metricName, String variantName, Metric heuristic, List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName) {
        if (heuristic == null) return;

        String blacklistKey = metricName + "|" + variantName;
        boolean isIncremental = variantName.contains("Increm.");

        boolean ENABLE_TIMEOUT = false;
        long TIMEOUT_MS = 600 * 60 * 1000;

        boolean ENABLE_ALLOC_GUARD = false;
        long MAX_ALLOC_PER_PAIR_BYTES = 32L * 1024 * 1024 * 1024;

        if (blacklist.contains(blacklistKey)) {
            System.out.printf("%-18s | %-20s | %-12s | %-15s | %-15.0f ms | %-15.2f ms | %-12s | %-12s\n",
                    metricName, variantName, "Skip(Blacklist)", "Risk", 0.0, 0.0, "Skipped", "Skipped");
            return;
        }

        if (history.containsKey(blacklistKey)) {
            List<HistoryRecord> pastRuns = history.get(blacklistKey);
            double predictedAllocBytesPerPair = 0.0;
            double predictedTimeMsTotal = 0.0;

            if (pastRuns.size() >= 2) {
                double kAlloc = calculateLogLogExponent(pastRuns, true);
                double logCAlloc = calculateLogLogConstant(pastRuns, kAlloc, true);
                predictedAllocBytesPerPair = Math.exp(logCAlloc) * Math.pow(size, kAlloc);

                double kTime = calculateLogLogExponent(pastRuns, false);
                double logCTime = calculateLogLogConstant(pastRuns, kTime, false);
                predictedTimeMsTotal = (Math.exp(logCTime) * Math.pow(size, kTime) * (trees.size() / 2)) / 1_000_000.0;
            } else {
                HistoryRecord last = pastRuns.get(0);
                predictedAllocBytesPerPair = last.allocPerPairBytes * Math.pow((double)size / last.n, 3.5);
                predictedTimeMsTotal = (last.timePerPairNs / 1_000_000.0) * Math.pow((double)size / last.n, 4.0) * (trees.size() / 2);
            }

            if (ENABLE_ALLOC_GUARD && !isIncremental && predictedAllocBytesPerPair > MAX_ALLOC_PER_PAIR_BYTES) {
                blacklist.add(blacklistKey);
                String predStr = String.format(Locale.US, "~%.1f GB/p", predictedAllocBytesPerPair / (1024.0 * 1024.0 * 1024.0));
                System.out.printf("%-18s | %-20s | %-12s | %-15s | %-15.0f ms | %-15.2f ms | %-12s | %-12s\n",
                        metricName, variantName, "Skip(MemChurn)", "Risk", 0.0, 0.0, predStr, "Skipped");
                return;
            }

            if (ENABLE_TIMEOUT && predictedTimeMsTotal > TIMEOUT_MS) {
                blacklist.add(blacklistKey);
                String predTimeStr = String.format(Locale.US, "~%.1f h", predictedTimeMsTotal / (1000.0 * 60 * 60));
                System.out.printf("%-18s | %-20s | %-12s | %-15s | %-15s | %-15s | %-12s | %-12s\n",
                        metricName, variantName, "Skip(PredTO)", "Risk", predTimeStr, "N/A", "Skipped", "Skipped");
                return;
            }
        }

        double totalDistance = 0.0;
        long totalTimeNs = 0;
        long totalAllocatedBytes = 0;
        long maxPeakBytes = 0;

        int totalPairs = trees.size() / 2;
        int processedPairs = 0;
        int successCount = 0;
        boolean timedOut = false;
        boolean oomOccurred = false;

        long overallStartMs = System.currentTimeMillis();
        com.sun.management.ThreadMXBean threadBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long currentThreadId = Thread.currentThread().getId();
        List<MemoryPoolMXBean> memoryPools = ManagementFactory.getMemoryPoolMXBeans();

        printIntermediateResult(metricName, variantName, successCount, processedPairs, totalPairs,
                timedOut, oomOccurred, totalDistance, totalTimeNs, totalAllocatedBytes, maxPeakBytes, false);

        try (PrintWriter csvWriter = new PrintWriter(new FileWriter(csvFileName, true))) {

            for (int i = 0; i < trees.size(); i += 2) {
                if (ENABLE_TIMEOUT && (System.currentTimeMillis() - overallStartMs > TIMEOUT_MS)) {
                    timedOut = true;
                    break;
                }

                Tree t1 = new SimpleTree(trees.get(i));
                Tree t2 = new SimpleTree(trees.get(i + 1));
                assignNumbers(t1);
                assignNumbers(t2);

                for (MemoryPoolMXBean pool : memoryPools) {
                    if (pool.getType() == MemoryType.HEAP) {
                        pool.resetPeakUsage();
                    }
                }

                long start = System.nanoTime();
                long allocStart = threadBean.getThreadAllocatedBytes(currentThreadId);
                double dist = Double.POSITIVE_INFINITY;
                boolean isSuccess = false;

                try {
                    dist = heuristic.getDistance(t1, t2);

                    if (dist != Double.POSITIVE_INFINITY && !Double.isNaN(dist)) {
                        totalDistance += dist;
                        successCount++;
                        isSuccess = true;
                    }
                } catch (OutOfMemoryError e) {
                    oomOccurred = true;
                    blacklist.add(blacklistKey);
                    forceCleanMemory();
                    totalTimeNs += (System.nanoTime() - start);
                    break;
                } catch (Exception e) {
                    System.err.println("\nERROR in pair #" + (i / 2 + 1) + " [" + metricName + " | " + variantName + "]: " + e.getMessage());
                }

                if (!oomOccurred) {
                    long allocEnd = threadBean.getThreadAllocatedBytes(currentThreadId);
                    long pairAllocatedBytes = allocEnd - allocStart;
                    long pairTimeNs = System.nanoTime() - start;
                    double pairTimeMs = pairTimeNs / 1_000_000.0;

                    totalAllocatedBytes += pairAllocatedBytes;
                    totalTimeNs += pairTimeNs;

                    long currentPeak = 0;
                    for (MemoryPoolMXBean pool : memoryPools) {
                        if (pool.getType() == MemoryType.HEAP) {
                            currentPeak += pool.getPeakUsage().getUsed();
                        }
                    }
                    if (currentPeak > maxPeakBytes) {
                        maxPeakBytes = currentPeak;
                    }

                    csvWriter.printf(Locale.US, "%d,%b,%s,\"%s\",%d,%b,%.4f,%.4f,%d,%d%n",
                            size, isRooted, metricName, variantName, (i / 2 + 1), isSuccess, dist, pairTimeMs, pairAllocatedBytes, currentPeak);
                    csvWriter.flush();

                    processedPairs++;

                    if (processedPairs < totalPairs) {
                        printIntermediateResult(metricName, variantName, successCount, processedPairs, totalPairs,
                                timedOut, false, totalDistance, totalTimeNs, totalAllocatedBytes, maxPeakBytes, false);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("\nError writing to CSV file: " + e.getMessage());
        }

        printIntermediateResult(metricName, variantName, successCount, processedPairs, totalPairs,
                timedOut, oomOccurred, totalDistance, totalTimeNs, totalAllocatedBytes, maxPeakBytes, true);

        if (!oomOccurred && processedPairs > 0) {
            long avgBytesPerPair = totalAllocatedBytes / processedPairs;
            long avgTimeNsPerPair = totalTimeNs / processedPairs;
            history.computeIfAbsent(blacklistKey, k -> new ArrayList<>()).add(new HistoryRecord(size, avgBytesPerPair, avgTimeNsPerPair));

            if (ENABLE_ALLOC_GUARD && !isIncremental && avgBytesPerPair > MAX_ALLOC_PER_PAIR_BYTES) {
                blacklist.add(blacklistKey);
                System.out.printf("      -> [!] Variant %s exceeded ALLOCATION PER PAIR threshold (%.2f GB). Blacklisted to protect GC.%n",
                        variantName, avgBytesPerPair / (1024.0 * 1024.0 * 1024.0));
            }
        }
    }

    private static double calculateLogLogExponent(List<HistoryRecord> pastRuns, boolean forMemory) {
        double sumLogN = 0, sumLogR = 0, sumLogNLogR = 0, sumLogNSq = 0;
        int nPoints = pastRuns.size();

        for (HistoryRecord hr : pastRuns) {
            double x = Math.log(hr.n);
            double y = Math.log(forMemory ? hr.allocPerPairBytes : hr.timePerPairNs);
            sumLogN += x;
            sumLogR += y;
            sumLogNLogR += x * y;
            sumLogNSq += x * x;
        }

        double denominator = (nPoints * sumLogNSq - sumLogN * sumLogN);
        if (denominator == 0.0) return forMemory ? 3.5 : 4.0;

        double k = (nPoints * sumLogNLogR - sumLogN * sumLogR) / denominator;
        return Math.max(k, 1.0);
    }

    private static double calculateLogLogConstant(List<HistoryRecord> pastRuns, double k, boolean forMemory) {
        double sumLogN = 0, sumLogR = 0;
        for (HistoryRecord hr : pastRuns) {
            sumLogN += Math.log(hr.n);
            sumLogR += Math.log(forMemory ? hr.allocPerPairBytes : hr.timePerPairNs);
        }
        return (sumLogR - k * sumLogN) / pastRuns.size();
    }

    private static String formatMemory(double bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024 * 1024) {
            return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024) {
            return String.format(Locale.US, "%.2f KB", bytes / 1024.0);
        } else {
            return String.format(Locale.US, "%.0f B", bytes);
        }
    }

    private static List<MetricSetup> getRootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();

        list.add(new MetricSetup("RFCluster",
                new SprHeuristicMetric(new RFClusterMetric(), true, "RFC"),
                new SprIncrementalHeuristicMetric(new RFClusterIncrementalMetric(), "RFCinc"),
                null,
                null
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

    private static List<MetricSetup> getUnrootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();

        list.add(new MetricSetup("RF",
                new UsprHeuristicMetric(new RFMetric(), "RF"),
                new UsprIncrementalHeuristicMetric(new RFIncrementalMetric(), "RFinc"),
                null,
                null
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

    private static List<Tree> loadTrees(String filename) {
        List<Tree> trees = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    Tree t = TreeCreator.getTreeFromString(line);
                    if (t != null) {
                        trees.add(t);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading from " + filename + ": " + e.getMessage());
        }
        return trees;
    }

    private static void assignNumbers(Tree t) {
        if (t instanceof SimpleTree) {
            ((SimpleTree) t).createNodeList();
        }
    }
}