package treecmp.benchmarks;

import pal.tree.SimpleTree;
import pal.tree.Tree;

import treecmp.heuristics.nni.NniClassicHeuristic;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.heuristics.spr.SprHeuristicMetric;
import treecmp.heuristics.spr.UsprHeuristicMetric;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.heuristics.vnd.NniVndHeuristic;
import treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;

import treecmp.heuristics.ecr.Ecr2ClassicHeuristic;
import treecmp.heuristics.ecr.Ecr3ClassicHeuristic;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristic;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristic;

import treecmp.metrics.Metric;
import treecmp.metrics.IncrementalMetric;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class NniVndQualityVsTimeMacroBenchmark {

    // ========================================================================
    // PROFILER CZASU (Mierzy czasy per para drzew!)
    // ========================================================================
    static class TimeProfiler {
        private static final ThreadLocal<Map<String, Long>> times = ThreadLocal.withInitial(HashMap::new);

        public static void add(String phase, long timeNs) {
            times.get().put(phase, times.get().getOrDefault(phase, 0L) + timeNs);
        }

        public static void reset() {
            times.get().clear();
        }

        public static long get(String phase) {
            return times.get().getOrDefault(phase, 0L);
        }
    }

    static class MetricSetup {
        String name;
        Metric classicNni;
        Metric incrementalNni;
        Metric classicVndFull;
        Metric classicVndShort;
        Metric incrementalVndFull;
        Metric incrementalVndShort;

        public MetricSetup(String name, Metric classicNni, Metric incrementalNni,
                           Metric classicVndFull, Metric classicVndShort,
                           Metric incrementalVndFull, Metric incrementalVndShort) {
            this.name = name;
            this.classicNni = classicNni;
            this.incrementalNni = incrementalNni;
            this.classicVndFull = classicVndFull;
            this.classicVndShort = classicVndShort;
            this.incrementalVndFull = incrementalVndFull;
            this.incrementalVndShort = incrementalVndShort;
        }
    }

    static class HistoryRecord {
        int n;
        long allocPerPairBytes;

        public HistoryRecord(int n, long allocPerPairBytes) {
            this.n = n;
            this.allocPerPairBytes = allocPerPairBytes;
        }
    }

    public static void main(String[] args) {
        treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic.ENABLE_LOGGING = false;
        treecmp.heuristics.vnd.NniVndHeuristic.ENABLE_LOGGING = false;

        System.out.println("=================================================================================================================================================================================================");
        System.out.println("                                         VND ULTIMATE QUALITY VS TIME MACRO-BENCHMARK (100 TREE PAIRS)");
        System.out.println("=================================================================================================================================================================================================");

        runAllSuites();
    }

    private static void runAllSuites() {
        int[] sizes = {10, 20, 30, 50, 80, 120};
        List<MetricSetup> rootedMetrics = getRootedMetrics();
        List<MetricSetup> unrootedMetrics = getUnrootedMetrics();

        Set<String> blacklist = new HashSet<>();
        Map<String, List<HistoryRecord>> history = new HashMap<>();

        for (int size : sizes) {
            System.out.println("\n\n#################################################################################################################################################################################################");
            System.out.println("                                                   ROZMIAR DRZEW: N=" + size);
            System.out.println("#################################################################################################################################################################################################");

            runForSizeAndType(size, true, rootedMetrics, blacklist, history);
            runForSizeAndType(size, false, unrootedMetrics, blacklist, history);
        }
    }

    private static void runForSizeAndType(int size, boolean rooted, List<MetricSetup> metricsToTest, Set<String> blacklist, Map<String, List<HistoryRecord>> history) {
        String treeType = rooted ? "ROOTED (rb)" : "UNROOTED (ub)";
        String suffix = rooted ? "rb" : "ub";
        String fileName = "datasets/n" + size + "y200" + suffix + ".newick";
        File file = new File(fileName);

        // Generujemy dynamiczną nazwę pliku dla obecnego N i typu drzewa
        String csvFileName = "benchmark_results_N" + size + "_" + (rooted ? "rooted" : "unrooted") + ".csv";

        // Inicjalizujemy nowy plik CSV, dodając nowe kolumny dla Pamięci i Sukcesu
        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFileName, false))) {
            pw.println("Size,IsRooted,Metric,Variant,PairIndex,Success,Distance,TotalTimeMs,NniTimeNs,Ecr2TimeNs,Ecr3TimeNs,SprTimeNs,AllocBytes,PeakRamBytes");
        } catch (Exception e) {
            System.err.println("Nie udało się utworzyć pliku CSV: " + csvFileName);
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
        System.out.printf("%-12s | %-32s | %-15s | %-15s | %-15s | %-15s | %-12s | %-12s | %s",
                "Metric", "Heuristic Variant", "Successes", "Avg Distance", "Total Time", "Time/Pair", "Alloc/Pair", "Peak RAM", "Time Breakdown");
        System.out.println();
        System.out.println("-".repeat(185));

        for (MetricSetup setup : metricsToTest) {
            forceCleanMemory();
            evaluateAndReport(size, rooted, setup.name, "1. NNI (Classic)", setup.classicNni, trees, blacklist, history, csvFileName);

            forceCleanMemory();
            evaluateAndReport(size, rooted, setup.name, "2. NNI (Incremental)", setup.incrementalNni, trees, blacklist, history, csvFileName);

            forceCleanMemory();
            evaluateAndReport(size, rooted, setup.name, "3. VND NNI->ECR->SPR (Classic)", setup.classicVndFull, trees, blacklist, history, csvFileName);

            forceCleanMemory();
            evaluateAndReport(size, rooted, setup.name, "4. VND NNI->SPR (Classic)", setup.classicVndShort, trees, blacklist, history, csvFileName);

            forceCleanMemory();
            evaluateAndReport(size, rooted, setup.name, "5. VND NNI->ECR->SPR (Inc)", setup.incrementalVndFull, trees, blacklist, history, csvFileName);

            forceCleanMemory();
            evaluateAndReport(size, rooted, setup.name, "6. VND NNI->SPR (Inc)", setup.incrementalVndShort, trees, blacklist, history, csvFileName);

            System.out.println("-".repeat(185));
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
                                                long totalAllocatedBytes, long maxPeakBytes, boolean isVndFull, boolean isVndShort,
                                                long globalNniT, long globalEcr2T, long globalEcr3T, long globalSprT, boolean isFinal) {

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

            long totalPhases = globalNniT + globalEcr2T + globalEcr3T + globalSprT;

            if (isVndFull && totalPhases > 0) {
                breakdownStr = String.format("[NNI:%2d%% ecr2:%2d%% ecr3:%2d%% SPR:%2d%%]",
                        Math.round(globalNniT * 100.0 / totalPhases), Math.round(globalEcr2T * 100.0 / totalPhases),
                        Math.round(globalEcr3T * 100.0 / totalPhases), Math.round(globalSprT * 100.0 / totalPhases));
            } else if (isVndShort) {
                long shortTotal = globalNniT + globalSprT;
                if (shortTotal > 0) {
                    breakdownStr = String.format("[NNI:%2d%% SPR:%2d%%]",
                            Math.round(globalNniT * 100.0 / shortTotal), Math.round(globalSprT * 100.0 / shortTotal));
                }
            }
        }

        if (!isFinal) {
            String shortMet = metricName.length() > 9 ? metricName.substring(0, 9) : metricName;
            String shortVar = variantName.length() > 20 ? variantName.substring(0, 18) + ".." : variantName;
            String shortDist = distStr.length() > 8 ? distStr.substring(0, 8) : distStr;

            System.out.print("\033[2K\r");
            System.out.printf("=> [%-9s] %-20s | %3d/%-3d | D: %-8s | %5.1fs | %s",
                    shortMet, shortVar, processedPairs, totalPairs, shortDist, totalTimeMs / 1000.0, breakdownStr);
            System.out.flush();
        } else {
            System.out.print("\033[2K\r");
            System.out.printf("%-12s | %-32s | %-15s | %-15s | %-15.0f ms | %-15.2f ms | %-12s | %-12s | %s\n",
                    metricName, variantName, successStr, distStr, totalTimeMs, avgTimeMs, memoryStr, peakStr, breakdownStr);
            System.out.flush();
        }
    }

    private static void evaluateAndReport(int size, boolean isRooted, String metricName, String variantName, Metric heuristic, List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName) {
        if (heuristic == null) return;

        String blacklistKey = metricName + "|" + variantName;
        boolean isIncremental = variantName.contains("Inc");
        boolean isVndFull = variantName.contains("VND NNI->ECR->SPR");
        boolean isVndShort = variantName.contains("VND NNI->SPR");

        long TIMEOUT_MS = 10L * 24 * 60 * 60 * 1000; // 10 dni
        long MAX_ALLOC_PER_PAIR_BYTES = 100L * 1024 * 1024 * 1024; // 100 GB
        long PEAK_MEMORY_THRESHOLD_BYTES = 300L * 1024 * 1024 * 1024; // 300 GB

        if (blacklist.contains(blacklistKey)) {
            System.out.printf("%-12s | %-32s | %-15s | %-15s | %-15.0f ms | %-15.2f ms | %-12s | %-12s | %s\n",
                    metricName, variantName, "Skip(Blacklist)", "Risk", 0.0, 0.0, "Skipped", "Skipped", "N/A");
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
                predictedAllocBytesPerPair = last.allocPerPairBytes * Math.pow((double)size / last.n, 3.5);
            }

            if (!isIncremental && predictedAllocBytesPerPair > MAX_ALLOC_PER_PAIR_BYTES) {
                blacklist.add(blacklistKey);
                String predStr = String.format(Locale.US, "~%.1f GB/p", predictedAllocBytesPerPair / (1024.0 * 1024.0 * 1024.0));
                System.out.printf("%-12s | %-32s | %-15s | %-15s | %-15.0f ms | %-15.2f ms | %-12s | %-12s | %s\n",
                        metricName, variantName, "Skip(MemChurn)", "Risk", 0.0, 0.0, predStr, "Skipped", "N/A");
                return;
            }
        }

        double totalDistance = 0.0;
        long totalTimeNs = 0;
        long totalAllocatedBytes = 0;
        long maxPeakBytes = 0;

        // Akumulatory globalnego czasu faz
        long globalNniT = 0, globalEcr2T = 0, globalEcr3T = 0, globalSprT = 0;

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
                timedOut, oomOccurred, totalDistance, totalTimeNs, totalAllocatedBytes, maxPeakBytes, isVndFull, isVndShort,
                globalNniT, globalEcr2T, globalEcr3T, globalSprT, false);

        // Otwieramy plik docelowy w trybie dopisywania (Append Mode)
        try (PrintWriter csvWriter = new PrintWriter(new FileWriter(csvFileName, true))) {

            for (int i = 0; i < trees.size(); i += 2) {
                if (System.currentTimeMillis() - overallStartMs > TIMEOUT_MS) {
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

                TimeProfiler.reset();
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
                    System.err.println("\nBŁĄD w parze nr " + (i / 2 + 1) + " [" + metricName + " | " + variantName + "]: " + e.getMessage());
                }

                if (!oomOccurred) {
                    long allocEnd = threadBean.getThreadAllocatedBytes(currentThreadId);

                    // Bezpośrednie wyliczenie pamięci zaalokowanej dla TEJ pary
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

                    // Pobieramy czasy faz z tej jednej pary
                    long ptNni = TimeProfiler.get("NNI");
                    long ptEcr2 = TimeProfiler.get("ECR2");
                    long ptEcr3 = TimeProfiler.get("ECR3");
                    long ptSpr = TimeProfiler.get("SPR");

                    // Zapis do pliku CSV wraz z pamięcią i wskaźnikiem Success
                    csvWriter.printf(Locale.US, "%d,%b,%s,\"%s\",%d,%b,%.4f,%.4f,%d,%d,%d,%d,%d,%d%n",
                            size, isRooted, metricName, variantName, (i / 2 + 1), isSuccess, dist, pairTimeMs, ptNni, ptEcr2, ptEcr3, ptSpr, pairAllocatedBytes, currentPeak);
                    csvWriter.flush();

                    globalNniT += ptNni;
                    globalEcr2T += ptEcr2;
                    globalEcr3T += ptEcr3;
                    globalSprT += ptSpr;

                    processedPairs++;

                    if (processedPairs < totalPairs) {
                        printIntermediateResult(metricName, variantName, successCount, processedPairs, totalPairs,
                                timedOut, false, totalDistance, totalTimeNs, totalAllocatedBytes, maxPeakBytes, isVndFull, isVndShort,
                                globalNniT, globalEcr2T, globalEcr3T, globalSprT, false);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("\nBłąd podczas zapisu do pliku CSV: " + e.getMessage());
        }

        printIntermediateResult(metricName, variantName, successCount, processedPairs, totalPairs,
                timedOut, oomOccurred, totalDistance, totalTimeNs, totalAllocatedBytes, maxPeakBytes, isVndFull, isVndShort,
                globalNniT, globalEcr2T, globalEcr3T, globalSprT, true);

        if (!oomOccurred && processedPairs > 0) {
            long avgBytesPerPair = totalAllocatedBytes / processedPairs;
            history.computeIfAbsent(blacklistKey, k -> new ArrayList<>()).add(new HistoryRecord(size, avgBytesPerPair));

            if (!isIncremental && avgBytesPerPair > MAX_ALLOC_PER_PAIR_BYTES) {
                blacklist.add(blacklistKey);
                System.out.printf("      -> [!] Wariant %s przekroczył próg ALOKACJI NA PARĘ (%.2f GB). Zablokowano w celu ochrony GC.%n",
                        variantName, avgBytesPerPair / (1024.0 * 1024.0 * 1024.0));
            } else if (!isIncremental && maxPeakBytes > PEAK_MEMORY_THRESHOLD_BYTES) {
                blacklist.add(blacklistKey);
                System.out.printf("      -> [!] Wariant %s przekroczył próg SZCZYTOWEJ PAMIĘCI (%.2f GB). Zablokowano w celu ochrony JVM.%n",
                        variantName, maxPeakBytes / (1024.0 * 1024.0 * 1024.0));
            }
        }
    }

    private static double calculateLogLogExponent(List<HistoryRecord> pastRuns) {
        double sumLogN = 0, sumLogR = 0, sumLogNLogR = 0, sumLogNSq = 0;
        int nPoints = pastRuns.size();

        for (HistoryRecord hr : pastRuns) {
            double x = Math.log(hr.n);
            double y = Math.log(hr.allocPerPairBytes);
            sumLogN += x;
            sumLogR += y;
            sumLogNLogR += x * y;
            sumLogNSq += x * x;
        }

        double denominator = (nPoints * sumLogNSq - sumLogN * sumLogN);
        if (denominator == 0.0) return 3.5;

        double k = (nPoints * sumLogNLogR - sumLogN * sumLogR) / denominator;
        return Math.max(k, 1.0);
    }

    private static double calculateLogLogConstant(List<HistoryRecord> pastRuns, double k) {
        double sumLogN = 0, sumLogR = 0;
        for (HistoryRecord hr : pastRuns) {
            sumLogN += Math.log(hr.n);
            sumLogR += Math.log(hr.allocPerPairBytes);
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

    // ========================================================================
    // BUILDERY
    // ========================================================================

    private static Metric buildClassicVndFull(Metric classicMetric, boolean isRooted, String shortName) {
        HeuristicBaseMetric sprStep = isRooted ?
                new SprHeuristicMetric(classicMetric, isRooted, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r;
                    }
                } :
                new UsprHeuristicMetric(classicMetric, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r;
                    }
                };

        List<HeuristicBaseMetric> chain = Arrays.asList(
                new NniClassicHeuristic(classicMetric, isRooted, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("NNI", System.nanoTime() - s); return r;
                    }
                },
                new Ecr2ClassicHeuristic(classicMetric, isRooted, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("ECR2", System.nanoTime() - s); return r;
                    }
                },
                new Ecr3ClassicHeuristic(classicMetric, isRooted, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("ECR3", System.nanoTime() - s); return r;
                    }
                },
                sprStep
        );
        return new NniVndHeuristic(chain, shortName);
    }

    private static Metric buildClassicVndShort(Metric classicMetric, boolean isRooted, String shortName) {
        HeuristicBaseMetric sprStep = isRooted ?
                new SprHeuristicMetric(classicMetric, isRooted, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r;
                    }
                } :
                new UsprHeuristicMetric(classicMetric, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r;
                    }
                };

        List<HeuristicBaseMetric> chain = Arrays.asList(
                new NniClassicHeuristic(classicMetric, isRooted, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("NNI", System.nanoTime() - s); return r;
                    }
                },
                sprStep
        );
        return new NniVndHeuristic(chain, shortName);
    }

    private static Metric buildIncrementalVndFull(IncrementalMetric incMetric, boolean isRooted, String shortName) {
        IncrementalHeuristicBaseMetric sprStep = isRooted ?
                new SprIncrementalHeuristicMetric(incMetric, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r;
                    }
                } :
                new UsprIncrementalHeuristicMetric(incMetric, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r;
                    }
                };

        List<IncrementalHeuristicBaseMetric> chain = Arrays.asList(
                new NniIncrementalHeuristic(incMetric, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("NNI", System.nanoTime() - s); return r;
                    }
                },
                new Ecr2IncrementalHeuristic(incMetric, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("ECR2", System.nanoTime() - s); return r;
                    }
                },
                new Ecr3IncrementalHeuristic(incMetric, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("ECR3", System.nanoTime() - s); return r;
                    }
                },
                sprStep
        );
        return new NniVndIncrementalHeuristic(chain, null, shortName);
    }

    private static Metric buildIncrementalVndShort(IncrementalMetric incMetric, boolean isRooted, String shortName) {
        IncrementalHeuristicBaseMetric sprStep = isRooted ?
                new SprIncrementalHeuristicMetric(incMetric, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r;
                    }
                } :
                new UsprIncrementalHeuristicMetric(incMetric, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r;
                    }
                };

        List<IncrementalHeuristicBaseMetric> chain = Arrays.asList(
                new NniIncrementalHeuristic(incMetric, shortName) {
                    @Override public double performLocalDescent(Tree t1, Tree t2) {
                        long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("NNI", System.nanoTime() - s); return r;
                    }
                },
                sprStep
        );
        return new NniVndIncrementalHeuristic(chain, null, shortName);
    }

    private static List<MetricSetup> getRootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();

        list.add(new MetricSetup("RFCluster",
                new NniClassicHeuristic(new RFClusterMetric(), true, "RFC"),
                new NniIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC"),
                buildClassicVndFull(new RFClusterMetric(), true, "RFC"),
                buildClassicVndShort(new RFClusterMetric(), true, "RFC"),
                buildIncrementalVndFull(new RFClusterIncrementalMetric(), true, "RFC"),
                buildIncrementalVndShort(new RFClusterIncrementalMetric(), true, "RFC")
        ));

        list.add(new MetricSetup("MC",
                new NniClassicHeuristic(new MatchingClusterMetric(), true, "MC"),
                new NniIncrementalHeuristic(new MCIncrementalMetric(), "MC"),
                buildClassicVndFull(new MatchingClusterMetric(), true, "MC"),
                buildClassicVndShort(new MatchingClusterMetric(), true, "MC"),
                buildIncrementalVndFull(new MCIncrementalMetric(), true, "MC"),
                buildIncrementalVndShort(new MCIncrementalMetric(), true, "MC")
        ));

        list.add(new MetricSetup("MP",
                new NniClassicHeuristic(new MatchingPairMetric(), true, "MP"),
                new NniIncrementalHeuristic(new MPIncrementalMetric(), "MP"),
                buildClassicVndFull(new MatchingPairMetric(), true, "MP"),
                buildClassicVndShort(new MatchingPairMetric(), true, "MP"),
                buildIncrementalVndFull(new MPIncrementalMetric(), true, "MP"),
                buildIncrementalVndShort(new MPIncrementalMetric(), true, "MP")
        ));

        return list;
    }

    private static List<MetricSetup> getUnrootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();

        list.add(new MetricSetup("RF",
                new NniClassicHeuristic(new RFMetric(), false, "RF"),
                new NniIncrementalHeuristic(new RFIncrementalMetric(), "RF"),
                buildClassicVndFull(new RFMetric(), false, "RF"),
                buildClassicVndShort(new RFMetric(), false, "RF"),
                buildIncrementalVndFull(new RFIncrementalMetric(), false, "RF"),
                buildIncrementalVndShort(new RFIncrementalMetric(), false, "RF")
        ));

        list.add(new MetricSetup("MS",
                new NniClassicHeuristic(new MatchingSplitMetric(), false, "MS"),
                new NniIncrementalHeuristic(new MSIncrementalMetric(), "MS"),
                buildClassicVndFull(new MatchingSplitMetric(), false, "MS"),
                buildClassicVndShort(new MatchingSplitMetric(), false, "MS"),
                buildIncrementalVndFull(new MSIncrementalMetric(), false, "MS"),
                buildIncrementalVndShort(new MSIncrementalMetric(), false, "MS")
        ));

        list.add(new MetricSetup("M3",
                new NniClassicHeuristic(new MatchingTripletMetric(), false, "M3"),
                new NniIncrementalHeuristic(new M3IncrementalMetric(), "M3"),
                buildClassicVndFull(new MatchingTripletMetric(), false, "M3"),
                buildClassicVndShort(new MatchingTripletMetric(), false, "M3"),
                buildIncrementalVndFull(new M3IncrementalMetric(), false, "M3"),
                buildIncrementalVndShort(new M3IncrementalMetric(), false, "M3")
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