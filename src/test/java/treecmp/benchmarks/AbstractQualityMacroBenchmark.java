package treecmp.benchmarks;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.metrics.Metric;
import treecmp.util.TreeCreator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.*;

public abstract class AbstractQualityMacroBenchmark {

    public static class HistoryRecord {
        public int n;
        public long allocPerPairBytes;
        public HistoryRecord(int n, long allocPerPairBytes) {
            this.n = n;
            this.allocPerPairBytes = allocPerPairBytes;
        }
    }

    public static class MetricSetup {
        public String name;
        public Metric classicPure;
        public Metric incrementalPure;
        public Metric classicFiltered;
        public Metric incrementalFiltered;

        public MetricSetup(String name, Metric classicPure, Metric incrementalPure, Metric classicFiltered, Metric incrementalFiltered) {
            this.name = name;
            this.classicPure = classicPure;
            this.incrementalPure = incrementalPure;
            this.classicFiltered = classicFiltered;
            this.incrementalFiltered = incrementalFiltered;
        }
    }

    protected boolean ENABLE_TIMEOUT = false;
    protected long TIMEOUT_MS = 10L * 24 * 60 * 60 * 1000; // 10 days default

    protected boolean ENABLE_ALLOC_GUARD = false;
    protected long MAX_ALLOC_PER_PAIR_BYTES = 32L * 1024 * 1024 * 1024; // 32 GB default
    protected long PEAK_MEMORY_THRESHOLD_BYTES = 300L * 1024 * 1024 * 1024; // 300 GB default

    protected String metricColFormat = "%-15s";
    protected String variantColFormat = "%-30s";

    protected void runBenchmark(String[] args, String title, String csvPrefix, int[] defaultSizes) {
        System.out.println("========================================================================================================================================================================");
        System.out.println("                                         " + title);
        System.out.println("========================================================================================================================================================================");

        int[] sizes;
        if (args.length > 0) {
            try {
                int explicitSize = Integer.parseInt(args[0]);
                sizes = new int[]{explicitSize};
                System.out.println("Single size mode. Running only for N = " + explicitSize);
            } catch (NumberFormatException e) {
                System.err.println("Error: Provided parameter '" + args[0] + "' is not a valid integer.");
                return;
            }
        } else {
            sizes = defaultSizes;
            System.out.println("No arguments provided. Running default sequence: " + Arrays.toString(sizes));
        }

        Set<String> blacklist = new HashSet<>();
        Map<String, List<HistoryRecord>> history = new HashMap<>();

        for (int size : sizes) {
            System.out.println("\n\n########################################################################################################################################################################");
            System.out.println("                                                      TREE SIZE: N=" + size);
            System.out.println("########################################################################################################################################################################");

            runForSizeAndType(size, true, blacklist, history, csvPrefix);
            runForSizeAndType(size, false, blacklist, history, csvPrefix);
        }
    }

    private void runForSizeAndType(int size, boolean rooted, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvPrefix) {
        String treeType = rooted ? "ROOTED (rb)" : "UNROOTED (ub)";
        String suffix = rooted ? "rb" : "ub";
        String fileName = "datasets/n" + size + "y200" + suffix + ".newick";
        File file = new File(fileName);

        File resultsDir = new File("results");
        if (!resultsDir.exists()) {
            resultsDir.mkdirs();
        }
        String csvFileName = "results/" + csvPrefix + "_N" + size + "_" + (rooted ? "rooted" : "unrooted") + ".csv";

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFileName, false))) {
            pw.println(getCsvHeader());
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

        printTableHeader(size, treeType, fileName);
        runEvaluationsForSize(size, rooted, trees, blacklist, history, csvFileName);
    }

    protected abstract void runEvaluationsForSize(int size, boolean rooted, List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName);

    protected String getCsvHeader() {
        return "Size,IsRooted,Metric,Variant,PairIndex,Success,Distance,TotalTimeMs,AllocBytes,PeakRamBytes";
    }

    protected void printTableHeader(int size, String treeType, String fileName) {
        System.out.println("\n--- RESULTS FOR SIZE N=" + size + " | " + treeType + " (" + fileName + ") ---");
        System.out.printf(metricColFormat + " | " + variantColFormat + " | %-12s | %-15s | %-15s | %-15s | %-12s | %-12s %s\n",
                "Metric", "Heuristic Variant", "Successes", "Avg Distance", "Total Time", "Time/Pair", "Alloc/Pair", "Peak RAM", getExtraTableHeaderInfo());
        System.out.println("-".repeat(160));
    }

    protected String getExtraTableHeaderInfo() { return ""; }

    protected void evaluateVariant(int size, boolean isRooted, String metricName, String variantName, Metric heuristic, List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName) {
        if (heuristic == null) return;

        String blacklistKey = metricName + "|" + variantName;
        boolean isIncremental = variantName.contains("Inc");

        if (blacklist.contains(blacklistKey)) {
            printSkipped(metricName, variantName, "Skip(Blacklist)", "N/A");
            return;
        }

        if (history.containsKey(blacklistKey)) {
            List<HistoryRecord> pastRuns = history.get(blacklistKey);
            double predictedAllocBytesPerPair = 0.0;
            if (pastRuns.size() >= 2) {
                double kAlloc = calculateLogLogExponent(pastRuns);
                double logCAlloc = calculateLogLogConstant(pastRuns, kAlloc);
                predictedAllocBytesPerPair = Math.exp(logCAlloc) * Math.pow(size, kAlloc);
            } else {
                HistoryRecord last = pastRuns.get(0);
                predictedAllocBytesPerPair = last.allocPerPairBytes * Math.pow((double) size / last.n, 3.5);
            }

            if (ENABLE_ALLOC_GUARD && !isIncremental && predictedAllocBytesPerPair > MAX_ALLOC_PER_PAIR_BYTES) {
                blacklist.add(blacklistKey);
                String predStr = String.format(Locale.US, "~%.1f GB/p", predictedAllocBytesPerPair / (1024.0 * 1024.0 * 1024.0));
                printSkipped(metricName, variantName, "Skip(MemChurn)", predStr);
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

        resetCustomStats();

        long overallStartMs = System.currentTimeMillis();
        com.sun.management.ThreadMXBean threadBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long currentThreadId = Thread.currentThread().getId();
        List<MemoryPoolMXBean> memoryPools = ManagementFactory.getMemoryPoolMXBeans();

        printIntermediateResult(metricName, variantName, successCount, processedPairs, totalPairs, timedOut, oomOccurred, totalDistance, totalTimeNs, totalAllocatedBytes, maxPeakBytes, false);

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
                    if (pool.getType() == MemoryType.HEAP) pool.resetPeakUsage();
                }

                onBeforePair();
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
                        if (pool.getType() == MemoryType.HEAP) currentPeak += pool.getPeakUsage().getUsed();
                    }
                    if (currentPeak > maxPeakBytes) maxPeakBytes = currentPeak;

                    onAfterPair();
                    writeCsvRow(csvWriter, size, isRooted, metricName, variantName, (i / 2 + 1), isSuccess, dist, pairTimeMs, pairAllocatedBytes, currentPeak);
                    csvWriter.flush();

                    processedPairs++;
                    if (processedPairs < totalPairs) {
                        printIntermediateResult(metricName, variantName, successCount, processedPairs, totalPairs, timedOut, false, totalDistance, totalTimeNs, totalAllocatedBytes, maxPeakBytes, false);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("\nError writing to CSV file: " + e.getMessage());
        }

        printIntermediateResult(metricName, variantName, successCount, processedPairs, totalPairs, timedOut, oomOccurred, totalDistance, totalTimeNs, totalAllocatedBytes, maxPeakBytes, true);

        if (!oomOccurred && processedPairs > 0) {
            long avgBytesPerPair = totalAllocatedBytes / processedPairs;
            history.computeIfAbsent(blacklistKey, k -> new ArrayList<>()).add(new HistoryRecord(size, avgBytesPerPair));

            if (ENABLE_ALLOC_GUARD && !isIncremental && avgBytesPerPair > MAX_ALLOC_PER_PAIR_BYTES) {
                blacklist.add(blacklistKey);
                System.out.printf("      -> [!] Variant %s exceeded ALLOCATION PER PAIR threshold (%.2f GB). Blacklisted to protect GC.%n", variantName, avgBytesPerPair / (1024.0 * 1024.0 * 1024.0));
            } else if (ENABLE_ALLOC_GUARD && !isIncremental && maxPeakBytes > PEAK_MEMORY_THRESHOLD_BYTES) {
                blacklist.add(blacklistKey);
                System.out.printf("      -> [!] Variant %s exceeded PEAK MEMORY threshold (%.2f GB). Blacklisted to protect JVM.%n", variantName, maxPeakBytes / (1024.0 * 1024.0 * 1024.0));
            }
        }
    }

    protected void printSkipped(String metricName, String variantName, String reason, String allocStr) {
        System.out.printf(metricColFormat + " | " + variantColFormat + " | %-12s | %-15s | %-15.0f ms | %-15.2f ms | %-12s | %-12s\n",
                metricName, variantName, reason, "Risk", 0.0, 0.0, allocStr, "Skipped");
    }

    protected void writeCsvRow(PrintWriter csvWriter, int size, boolean isRooted, String metricName, String variantName, int pairIndex, boolean isSuccess, double dist, double pairTimeMs, long pairAllocatedBytes, long currentPeak) {
        csvWriter.printf(Locale.US, "%d,%b,%s,\"%s\",%d,%b,%.4f,%.4f,%d,%d%n",
                size, isRooted, metricName, variantName, pairIndex, isSuccess, dist, pairTimeMs, pairAllocatedBytes, currentPeak);
    }

    protected void printIntermediateResult(String metricName, String variantName, int successCount, int processedPairs, int totalPairs,
                                           boolean timedOut, boolean oomOccurred, double totalDistance, long totalTimeNs,
                                           long totalAllocatedBytes, long maxPeakBytes, boolean isFinal) {
        double avgDistance = successCount > 0 ? (totalDistance / successCount) : 0.0;
        double totalTimeMs = totalTimeNs / 1_000_000.0;
        double avgTimeMs = processedPairs > 0 ? (totalTimeMs / processedPairs) : 0.0;
        long avgBytesPerPair = processedPairs > 0 ? (totalAllocatedBytes / processedPairs) : 0;

        String distStr, successStr, memoryStr, peakStr;

        if (oomOccurred) {
            successStr = successCount + "/" + (processedPairs + 1) + " (Crash!)";
            distStr = "OOM"; memoryStr = "OOM"; peakStr = "OOM";
        } else {
            String status = timedOut ? " (TO)" : "";
            successStr = successCount + "/" + processedPairs + status;
            distStr = processedPairs > 0 ? (successCount > 0 ? String.format(Locale.US, "%.4f", avgDistance) : "None(Inf)") : "Run...";
            memoryStr = formatMemory(avgBytesPerPair);
            peakStr = formatMemory(maxPeakBytes);
        }

        String extra = getExtraConsoleBreakdown(variantName);
        if (!isFinal) {
            String shortMet = metricName.length() > 14 ? metricName.substring(0, 14) : metricName;
            String shortVar = variantName.length() > 28 ? variantName.substring(0, 26) + ".." : variantName;
            String shortDist = distStr.length() > 8 ? distStr.substring(0, 8) : distStr;

            System.out.print("\033[2K\r");
            System.out.printf("=> [%-13s] %-28s | %3d/%-3d | D: %-8s | %5.1fs | %s", shortMet, shortVar, processedPairs, totalPairs, shortDist, totalTimeMs / 1000.0, extra);
            System.out.flush();
        } else {
            System.out.print("\033[2K\r");
            System.out.printf(metricColFormat + " | " + variantColFormat + " | %-12s | %-15s | %-15.0f ms | %-15.2f ms | %-12s | %-12s %s\n",
                    metricName, variantName, successStr, distStr, totalTimeMs, avgTimeMs, memoryStr, peakStr, extra.equals("N/A") ? "" : "| " + extra);
            System.out.flush();
        }
    }

    protected void resetCustomStats() {}
    protected void onBeforePair() {}
    protected void onAfterPair() {}
    protected String getExtraConsoleBreakdown(String variantName) { return "N/A"; }

    protected void forceCleanMemory() {
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    protected double calculateLogLogExponent(List<HistoryRecord> pastRuns) {
        double sumLogN = 0, sumLogR = 0, sumLogNLogR = 0, sumLogNSq = 0;
        int nPoints = pastRuns.size();
        for (HistoryRecord hr : pastRuns) {
            double x = Math.log(hr.n);
            double y = Math.log(hr.allocPerPairBytes);
            sumLogN += x; sumLogR += y; sumLogNLogR += x * y; sumLogNSq += x * x;
        }
        double denominator = (nPoints * sumLogNSq - sumLogN * sumLogN);
        if (denominator == 0.0) return 3.5;
        return Math.max((nPoints * sumLogNLogR - sumLogN * sumLogR) / denominator, 1.0);
    }

    protected double calculateLogLogConstant(List<HistoryRecord> pastRuns, double k) {
        double sumLogN = 0, sumLogR = 0;
        for (HistoryRecord hr : pastRuns) {
            sumLogN += Math.log(hr.n);
            sumLogR += Math.log(hr.allocPerPairBytes);
        }
        return (sumLogR - k * sumLogN) / pastRuns.size();
    }

    protected String formatMemory(double bytes) {
        if (bytes >= 1024 * 1024 * 1024) return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        if (bytes >= 1024 * 1024) return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        if (bytes >= 1024) return String.format(Locale.US, "%.2f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.0f B", bytes);
    }

    protected List<Tree> loadTrees(String filename) {
        List<Tree> trees = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    Tree t = TreeCreator.getTreeFromString(line);
                    if (t != null) trees.add(t);
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading from " + filename + ": " + e.getMessage());
        }
        return trees;
    }

    protected void assignNumbers(Tree t) {
        if (t instanceof SimpleTree) {
            ((SimpleTree) t).createNodeList();
        }
    }
}