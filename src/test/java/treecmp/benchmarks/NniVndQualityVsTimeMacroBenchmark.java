package treecmp.benchmarks;

import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.nni.NniClassicHeuristic;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.heuristics.spr.SprHeuristicMetric;
import treecmp.heuristics.spr.UsprHeuristicMetric;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.heuristics.tbr.TbrHeuristicMetric;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
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

import java.io.PrintWriter;
import java.util.*;

public class NniVndQualityVsTimeMacroBenchmark extends AbstractQualityMacroBenchmark {

    // =========================================================================
    // FLAGI KONTROLNE DIAGNOSTYKI ECR / VND
    // =========================================================================
    public static boolean ENABLE_DIAGNOSTIC_ASSERTIONS = true;
    public static boolean THROW_ON_DIAGNOSTIC_MISMATCH = false;
    public static boolean ENABLE_LOGGING = false;

    private final int maxAllowedClassicVndSize;
    private long globalNniT, globalEcr2T, globalEcr3T, globalSprT, globalTbrT;

    static class TimeProfiler {
        private static final ThreadLocal<Map<String, Long>> times = ThreadLocal.withInitial(HashMap::new);
        public static void add(String phase, long timeNs) { times.get().put(phase, times.get().getOrDefault(phase, 0L) + timeNs); }
        public static void reset() { times.get().clear(); }
        public static long get(String phase) { return times.get().getOrDefault(phase, 0L); }
    }

    static class MetricSetupVnd {
        String name;
        Metric classicNni, incrementalNni;
        Metric classicVndFull, classicVndShort, classicVndTbr;
        Metric incrementalVndFull, incrementalVndShort, incrementalVndTbr;
        Metric classicVndFullTie, classicVndShortTie, classicVndTbrTie;
        Metric incrementalVndFullTie, incrementalVndShortTie, incrementalVndTbrTie;

        public MetricSetupVnd(String name, Metric classicNni, Metric incrementalNni,
                              Metric classicVndFull, Metric classicVndShort, Metric classicVndTbr,
                              Metric incrementalVndFull, Metric incrementalVndShort, Metric incrementalVndTbr,
                              Metric classicVndFullTie, Metric classicVndShortTie, Metric classicVndTbrTie,
                              Metric incrementalVndFullTie, Metric incrementalVndShortTie, Metric incrementalVndTbrTie) {
            this.name = name; this.classicNni = classicNni; this.incrementalNni = incrementalNni;
            this.classicVndFull = classicVndFull; this.classicVndShort = classicVndShort; this.classicVndTbr = classicVndTbr;
            this.incrementalVndFull = incrementalVndFull; this.incrementalVndShort = incrementalVndShort; this.incrementalVndTbr = incrementalVndTbr;
            this.classicVndFullTie = classicVndFullTie; this.classicVndShortTie = classicVndShortTie; this.classicVndTbrTie = classicVndTbrTie;
            this.incrementalVndFullTie = incrementalVndFullTie; this.incrementalVndShortTie = incrementalVndShortTie; this.incrementalVndTbrTie = incrementalVndTbrTie;
        }
    }

    public NniVndQualityVsTimeMacroBenchmark() {
        this.MAX_ALLOC_PER_PAIR_BYTES = 100L * 1024 * 1024 * 1024; // 100 GB

        long maxHeapBytes = Runtime.getRuntime().maxMemory();
        double maxHeapGb = maxHeapBytes / (1024.0 * 1024.0 * 1024.0);

        this.maxAllowedClassicVndSize = determineMaxClassicVndSize(maxHeapGb);

        System.out.println("======================================================================");
        System.out.printf("[MEMORY CONFIG] Detected JVM Max Heap: %.2f GB%n", maxHeapGb);
        System.out.printf("[MEMORY CONFIG] Max allowed tree size (N) for Classic VND: %d%n", maxAllowedClassicVndSize);
        System.out.printf("[DIAGNOSTIC CONFIG] Assertions Enabled: %b | Throw on error: %b%n",
                ENABLE_DIAGNOSTIC_ASSERTIONS, THROW_ON_DIAGNOSTIC_MISMATCH);
        System.out.printf("[LOGGING CONFIG] NNI Trajectory Logging (ENABLE_LOGGING): %b%n", ENABLE_LOGGING);
        if (ENABLE_LOGGING) {
            System.out.println("  -> [WARNING] Detailed NNI logging is active. Timing metrics will reflect I/O and trajectory synthesis overhead!");
        }
        System.out.println("======================================================================");
    }

    private int determineMaxClassicVndSize(double maxHeapGb) {
        if (maxHeapGb >= 110.0) { return 120; }
        else if (maxHeapGb >= 85.0) { return 80; }
        else if (maxHeapGb >= 28.0) { return 50; }
        else if (maxHeapGb >= 14.0) { return 30; }
        else { return 20; }
    }

    public static void main(String[] args) {
        boolean enableLogging = false;
        List<String> cleanArgs = new ArrayList<>();

        for (String arg : args) {
            String lower = arg.toLowerCase().trim();
            if (lower.equals("--log") || lower.equals("-log") || lower.equals("log")
                    || lower.equals("--enable-logging") || lower.equals("-enable-logging")
                    || lower.equals("enable-logging") || lower.equals("enable_logging")
                    || lower.equals("enablelogging")
                    || lower.startsWith("--log=") || lower.startsWith("-log=") || lower.startsWith("log=")
                    || lower.startsWith("--enable-logging=") || lower.startsWith("enable_logging=")) {
                if (lower.contains("=")) {
                    String val = lower.substring(lower.indexOf('=') + 1);
                    enableLogging = "true".equalsIgnoreCase(val) || "1".equals(val);
                } else {
                    enableLogging = true;
                }
            } else {
                cleanArgs.add(arg);
            }
        }

        if (!enableLogging) {
            String sysProp = System.getProperty("ENABLE_LOGGING", System.getProperty("log", "false"));
            enableLogging = "true".equalsIgnoreCase(sysProp) || "1".equals(sysProp);
        }

        ENABLE_LOGGING = enableLogging;
        treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic.ENABLE_LOGGING = enableLogging;
        treecmp.heuristics.vnd.NniVndHeuristic.ENABLE_LOGGING = enableLogging;

        String baseCsvName = enableLogging
                ? "benchmark_quality_VND_WITH_NNI_LOGS_OVERHEAD"
                : "benchmark_quality_VND";

        new NniVndQualityVsTimeMacroBenchmark().runBenchmark(
                cleanArgs.toArray(new String[0]),
                "VND ULTIMATE QUALITY VS TIME MACRO-BENCHMARK (100 TREE PAIRS)",
                baseCsvName,
                new int[]{10, 20, 30, 50, 80, 120}
        );
    }

    @Override
    protected void evaluateVariant(int size, boolean isRooted, String metricName, String variantName, Metric heuristic,
                                   List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName) {
        if (heuristic == null) return;

        if (isClassicVndVariant(variantName) && size > maxAllowedClassicVndSize) {
            printSkipped(metricName, variantName, "Skip(RAM Limit)", "Max N=" + maxAllowedClassicVndSize);
            return;
        }

        super.evaluateVariant(size, isRooted, metricName, variantName, heuristic, trees, blacklist, history, csvFileName);
    }

    private boolean isClassicVndVariant(String variantName) {
        return variantName.contains("VND") && variantName.contains("Classic");
    }

    @Override
    protected void runEvaluationsForSize(int size, boolean rooted, List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName) {
        List<MetricSetupVnd> metricsToTest = rooted ? getRootedMetrics() : getUnrootedMetrics();
        for (MetricSetupVnd setup : metricsToTest) {
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "1. NNI (Classic)", setup.classicNni, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "2. NNI (Incremental)", setup.incrementalNni, trees, blacklist, history, csvFileName);

            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "3. VND NNI->ECR->SPR->TBR (Classic)", setup.classicVndFull, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "4. VND NNI->SPR->TBR (Classic)", setup.classicVndShort, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "5. VND NNI->TBR (Classic)", setup.classicVndTbr, trees, blacklist, history, csvFileName);

            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "6. VND NNI->ECR->SPR->TBR (Inc)", setup.incrementalVndFull, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "7. VND NNI->SPR->TBR (Inc)", setup.incrementalVndShort, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "8. VND NNI->TBR (Inc)", setup.incrementalVndTbr, trees, blacklist, history, csvFileName);

            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "9. VND NNI->ECR->SPR->TBR (Classic + Tie)", setup.classicVndFullTie, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "10. VND NNI->SPR->TBR (Classic + Tie)", setup.classicVndShortTie, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "11. VND NNI->TBR (Classic + Tie)", setup.classicVndTbrTie, trees, blacklist, history, csvFileName);

            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "12. VND NNI->ECR->SPR->TBR (Inc + Tie)", setup.incrementalVndFullTie, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "13. VND NNI->SPR->TBR (Inc + Tie)", setup.incrementalVndShortTie, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "14. VND NNI->TBR (Inc + Tie)", setup.incrementalVndTbrTie, trees, blacklist, history, csvFileName);

            System.out.println("-".repeat(160));
        }
    }

    @Override
    protected String getCsvHeader() {
        String columns = "Size,IsRooted,Metric,Variant,PairIndex,Success,Distance,TotalTimeMs,NniTimeNs,Ecr2TimeNs,Ecr3TimeNs,SprTimeNs,TbrTimeNs,AllocBytes,PeakRamBytes";
        if (ENABLE_LOGGING) {
            return "# WARNING: NNI trajectory logging (ENABLE_LOGGING=true) was active during this benchmark run.\n"
                    + "# Disk I/O, Newick serialization, and intermediate trajectory synthesis overhead significantly inflate TotalTimeMs and phase times!\n"
                    + columns;
        }
        return columns;
    }

    @Override
    protected void writeCsvRow(PrintWriter csvWriter, int size, boolean isRooted, String metricName, String variantName, int pairIndex, boolean isSuccess, double dist, double pairTimeMs, long pairAllocatedBytes, long currentPeak) {
        long ptNni = TimeProfiler.get("NNI");
        long ptEcr2 = TimeProfiler.get("ECR2");
        long ptEcr3 = TimeProfiler.get("ECR3");
        long ptSpr = TimeProfiler.get("SPR");
        long ptTbr = TimeProfiler.get("TBR");
        csvWriter.printf(Locale.US, "%d,%b,%s,\"%s\",%d,%b,%.4f,%.4f,%d,%d,%d,%d,%d,%d,%d%n",
                size, isRooted, metricName, variantName, pairIndex, isSuccess, dist, pairTimeMs, ptNni, ptEcr2, ptEcr3, ptSpr, ptTbr, pairAllocatedBytes, currentPeak);
    }

    @Override
    protected String getExtraTableHeaderInfo() { return "Time Breakdown"; }

    @Override
    protected void resetCustomStats() {
        globalNniT = 0; globalEcr2T = 0; globalEcr3T = 0; globalSprT = 0; globalTbrT = 0;
    }

    @Override
    protected void onBeforePair() { TimeProfiler.reset(); }

    @Override
    protected void onAfterPair() {
        globalNniT += TimeProfiler.get("NNI");
        globalEcr2T += TimeProfiler.get("ECR2");
        globalEcr3T += TimeProfiler.get("ECR3");
        globalSprT += TimeProfiler.get("SPR");
        globalTbrT += TimeProfiler.get("TBR");
    }

    @Override
    protected String getExtraConsoleBreakdown(String variantName) {
        boolean isVndFull = variantName.contains("NNI->ECR->SPR->TBR");
        boolean isVndShort = variantName.contains("NNI->SPR->TBR");
        boolean isVndTbr = variantName.contains("NNI->TBR");
        long totalPhases = globalNniT + globalEcr2T + globalEcr3T + globalSprT + globalTbrT;

        if (isVndFull && totalPhases > 0) {
            return String.format("[NNI:%2d%% ecr2:%2d%% ecr3:%2d%% SPR:%2d%% TBR:%2d%%]",
                    Math.round(globalNniT * 100.0 / totalPhases),
                    Math.round(globalEcr2T * 100.0 / totalPhases),
                    Math.round(globalEcr3T * 100.0 / totalPhases),
                    Math.round(globalSprT * 100.0 / totalPhases),
                    Math.round(globalTbrT * 100.0 / totalPhases));
        } else if (isVndShort) {
            long shortTotal = globalNniT + globalSprT + globalTbrT;
            if (shortTotal > 0) {
                return String.format("[NNI:%2d%% SPR:%2d%% TBR:%2d%%]",
                        Math.round(globalNniT * 100.0 / shortTotal),
                        Math.round(globalSprT * 100.0 / shortTotal),
                        Math.round(globalTbrT * 100.0 / shortTotal));
            }
        } else if (isVndTbr) {
            long tbrTotal = globalNniT + globalTbrT;
            if (tbrTotal > 0) {
                return String.format("[NNI:%2d%% TBR:%2d%%]",
                        Math.round(globalNniT * 100.0 / tbrTotal),
                        Math.round(globalTbrT * 100.0 / tbrTotal));
            }
        }
        return "N/A";
    }

    // =========================================================================
    // METODY FABRYCZNE KROKÓW Z WBUDOWANĄ DIAGNOSTYKĄ ASERCJI
    // =========================================================================

    private static HeuristicBaseMetric createClassicNniStep(Metric m, boolean isRooted, String sn) {
        return new NniClassicHeuristic(m, isRooted, sn) {
            @Override public double performLocalDescent(Tree t1, Tree t2) {
                long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2);
                TimeProfiler.add("NNI", System.nanoTime() - s); return r;
            }
        };
    }

    private static HeuristicBaseMetric createClassicEcr2Step(Metric m, boolean isRooted, String sn) {
        return new Ecr2ClassicHeuristic(m, isRooted, sn) {
            @Override public double performLocalDescent(Tree t1, Tree t2) {
                long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2);
                TimeProfiler.add("ECR2", System.nanoTime() - s); return r;
            }
        };
    }

    private static HeuristicBaseMetric createClassicEcr3Step(Metric m, boolean isRooted, String sn) {
        return new Ecr3ClassicHeuristic(m, isRooted, sn) {
            @Override public double performLocalDescent(Tree t1, Tree t2) {
                long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2);
                TimeProfiler.add("ECR3", System.nanoTime() - s); return r;
            }
        };
    }

    private static HeuristicBaseMetric createClassicSprStep(Metric m, Metric tie, boolean isRooted, String sn) {
        if (isRooted) {
            return new SprHeuristicMetric(m, tie, true, sn) {
                @Override public double performLocalDescent(Tree t1, Tree t2) {
                    long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2);
                    TimeProfiler.add("SPR", System.nanoTime() - s); return r;
                }
            };
        } else {
            return new UsprHeuristicMetric(m, tie, sn) {
                @Override public double performLocalDescent(Tree t1, Tree t2) {
                    long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2);
                    TimeProfiler.add("SPR", System.nanoTime() - s); return r;
                }
            };
        }
    }

    private static HeuristicBaseMetric createClassicTbrStep(Metric m, boolean isRooted, String sn) {
        return createClassicTbrStep(m, null, isRooted, sn);
    }

    private static HeuristicBaseMetric createClassicTbrStep(Metric m, Metric tie, boolean isRooted, String sn) {
        return new TbrHeuristicMetric(m, tie, isRooted, sn) {
            @Override public double performLocalDescent(Tree t1, Tree t2) {
                long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2);
                TimeProfiler.add("TBR", System.nanoTime() - s); return r;
            }
        };
    }

    private static IncrementalHeuristicBaseMetric createIncNniStep(IncrementalMetric im, Metric classicMetric, String sn) {
        return new NniIncrementalHeuristic(im, sn) {
            @Override public double performLocalDescent(Tree t1, Tree t2) {
                long s = System.nanoTime();
                double r = super.performLocalDescent(t1, t2);
                TimeProfiler.add("NNI", System.nanoTime() - s);

                if (ENABLE_DIAGNOSTIC_ASSERTIONS && classicMetric != null) {
                    Tree best = getLastOptimumTree() != null ? getLastOptimumTree() : t1;
                    double actualClassic = 0;
                    try {
                        actualClassic = classicMetric.getDistance(best, t2);
                    } catch (TreeCmpException e) {
                        throw new RuntimeException(e);
                    }
                    if (Math.abs(r - actualClassic) > 1e-5) {
                        String msg = String.format("[NNI DESCENT MISMATCH] Metric=%s | Reported=%.4f vs ActualClassic=%.4f",
                                sn, r, actualClassic);
                        System.err.println(msg);
                        if (THROW_ON_DIAGNOSTIC_MISMATCH) throw new AssertionError(msg);
                    }
                }
                return r;
            }
        };
    }

    private static IncrementalHeuristicBaseMetric createIncEcr2Step(IncrementalMetric im, Metric classicMetric, String sn) {
        return new Ecr2IncrementalHeuristic(im, sn) {
            private Tree currentTargetTree;

            @Override
            public double performLocalDescent(Tree startTree, Tree targetTree) {
                this.currentTargetTree = targetTree;
                long s = System.nanoTime();
                double r = super.performLocalDescent(startTree, targetTree);
                TimeProfiler.add("ECR2", System.nanoTime() - s);

                if (ENABLE_DIAGNOSTIC_ASSERTIONS && classicMetric != null) {
                    Tree best = getLastOptimumTree() != null ? getLastOptimumTree() : startTree;
                    double actualClassic = 0;
                    try {
                        actualClassic = classicMetric.getDistance(best, targetTree);
                    } catch (TreeCmpException e) {
                        throw new RuntimeException(e);
                    }
                    if (Math.abs(r - actualClassic) > 1e-5) {
                        String msg = String.format("[ECR2 DESCENT MISMATCH] Metric=%s | Reported=%.4f vs ActualClassic=%.4f",
                                sn, r, actualClassic);
                        System.err.println(msg);
                        if (THROW_ON_DIAGNOSTIC_MISMATCH) throw new AssertionError(msg);
                    }
                }
                return r;
            }

            @Override
            protected void searchNeighborhood(Tree currentTree) {
                double distBefore = im.getCurrentDistance();
                super.searchNeighborhood(currentTree);
                double distAfter = im.getCurrentDistance();

                if (ENABLE_DIAGNOSTIC_ASSERTIONS && Math.abs(distBefore - distAfter) > 1e-5) {
                    String msg = String.format("[ECR2 ROLLBACK LEAK DETECTED!] Metric=%s | DistBefore=%.4f, DistAfter=%.4f (Leak: %+.4f)",
                            sn, distBefore, distAfter, (distAfter - distBefore));
                    System.err.println(msg);
                    if (THROW_ON_DIAGNOSTIC_MISMATCH) throw new AssertionError(msg);
                }
            }

            @Override
            protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
                Tree nextTree = super.applyPhysicalMove(tree, move);
                if (nextTree instanceof pal.tree.SimpleTree) {
                    ((pal.tree.SimpleTree) nextTree).createNodeList();
                }
                if (ENABLE_DIAGNOSTIC_ASSERTIONS && classicMetric != null && nextTree != null && currentTargetTree != null) {
                    double expectedByMove = this.bestDist;
                    double actualClassic = 0;
                    try {
                        actualClassic = classicMetric.getDistance(nextTree, currentTargetTree);
                    } catch (TreeCmpException e) {
                        throw new RuntimeException(e);
                    }
                    if (Math.abs(expectedByMove - actualClassic) > 1e-5) {
                        String msg = String.format("[ECR2 MOVE EVALUATION MISMATCH] Metric=%s | Move=%s | Evaluated=%.4f vs PhysicalClassic=%.4f",
                                sn, move, expectedByMove, actualClassic);
                        System.err.println(msg);
                        if (THROW_ON_DIAGNOSTIC_MISMATCH) throw new AssertionError(msg);
                    }
                }
                return nextTree;
            }
        };
    }

    private static IncrementalHeuristicBaseMetric createIncEcr3Step(IncrementalMetric im, Metric classicMetric, String sn) {
        return new Ecr3IncrementalHeuristic(im, sn) {
            private Tree currentTargetTree;

            @Override
            public double performLocalDescent(Tree startTree, Tree targetTree) {
                this.currentTargetTree = targetTree;
                long s = System.nanoTime();
                double r = super.performLocalDescent(startTree, targetTree);
                TimeProfiler.add("ECR3", System.nanoTime() - s);

                if (ENABLE_DIAGNOSTIC_ASSERTIONS && classicMetric != null) {
                    Tree best = getLastOptimumTree() != null ? getLastOptimumTree() : startTree;
                    double actualClassic = 0;
                    try {
                        actualClassic = classicMetric.getDistance(best, targetTree);
                    } catch (TreeCmpException e) {
                        throw new RuntimeException(e);
                    }
                    if (Math.abs(r - actualClassic) > 1e-5) {
                        String msg = String.format("[ECR3 DESCENT MISMATCH] Metric=%s | Reported=%.4f vs ActualClassic=%.4f",
                                sn, r, actualClassic);
                        System.err.println(msg);
                        if (THROW_ON_DIAGNOSTIC_MISMATCH) throw new AssertionError(msg);
                    }
                }
                return r;
            }

            @Override
            protected void searchNeighborhood(Tree currentTree) {
                double distBefore = im.getCurrentDistance();
                super.searchNeighborhood(currentTree);
                double distAfter = im.getCurrentDistance();

                if (ENABLE_DIAGNOSTIC_ASSERTIONS && Math.abs(distBefore - distAfter) > 1e-5) {
                    String msg = String.format("[ECR3 ROLLBACK LEAK DETECTED!] Metric=%s | DistBefore=%.4f, DistAfter=%.4f (Leak: %+.4f)",
                            sn, distBefore, distAfter, (distAfter - distBefore));
                    System.err.println(msg);
                    if (THROW_ON_DIAGNOSTIC_MISMATCH) throw new AssertionError(msg);
                }
            }

            @Override
            protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
                Tree nextTree = super.applyPhysicalMove(tree, move);
                if (nextTree instanceof pal.tree.SimpleTree) {
                    ((pal.tree.SimpleTree) nextTree).createNodeList();
                }
                if (ENABLE_DIAGNOSTIC_ASSERTIONS && classicMetric != null && nextTree != null && currentTargetTree != null) {
                    double expectedByMove = this.bestDist;
                    double actualClassic = 0;
                    try {
                        actualClassic = classicMetric.getDistance(nextTree, currentTargetTree);
                    } catch (TreeCmpException e) {
                        throw new RuntimeException(e);
                    }
                    if (Math.abs(expectedByMove - actualClassic) > 1e-5) {
                        String msg = String.format("[ECR3 MOVE EVALUATION MISMATCH] Metric=%s | Move=%s | Evaluated=%.4f vs PhysicalClassic=%.4f",
                                sn, move, expectedByMove, actualClassic);
                        System.err.println(msg);
                        if (THROW_ON_DIAGNOSTIC_MISMATCH) throw new AssertionError(msg);
                    }
                }
                return nextTree;
            }
        };
    }

    private static IncrementalHeuristicBaseMetric createIncSprStep(IncrementalMetric im, IncrementalMetric tie, Metric classicMetric, boolean isRooted, String sn) {
        if (isRooted) {
            return new SprIncrementalHeuristicMetric(im, tie, sn) {
                @Override public double performLocalDescent(Tree t1, Tree t2) {
                    long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2);
                    TimeProfiler.add("SPR", System.nanoTime() - s);
                    if (ENABLE_DIAGNOSTIC_ASSERTIONS && classicMetric != null) {
                        Tree best = getLastOptimumTree() != null ? getLastOptimumTree() : t1;
                        double actualClassic = 0;
                        try {
                            actualClassic = classicMetric.getDistance(best, t2);
                        } catch (TreeCmpException e) {
                            throw new RuntimeException(e);
                        }
                        if (Math.abs(r - actualClassic) > 1e-5) {
                            String msg = String.format("[SPR DESCENT MISMATCH] Metric=%s | Reported=%.4f vs ActualClassic=%.4f",
                                    sn, r, actualClassic);
                            System.err.println(msg);
                            if (THROW_ON_DIAGNOSTIC_MISMATCH) throw new AssertionError(msg);
                        }
                    }
                    return r;
                }
            };
        } else {
            return new UsprIncrementalHeuristicMetric(im, tie, sn) {
                @Override public double performLocalDescent(Tree t1, Tree t2) {
                    long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2);
                    TimeProfiler.add("SPR", System.nanoTime() - s);
                    if (ENABLE_DIAGNOSTIC_ASSERTIONS && classicMetric != null) {
                        Tree best = getLastOptimumTree() != null ? getLastOptimumTree() : t1;
                        double actualClassic = 0;
                        try {
                            actualClassic = classicMetric.getDistance(best, t2);
                        } catch (TreeCmpException e) {
                            throw new RuntimeException(e);
                        }
                        if (Math.abs(r - actualClassic) > 1e-5) {
                            String msg = String.format("[uSPR DESCENT MISMATCH] Metric=%s | Reported=%.4f vs ActualClassic=%.4f",
                                    sn, r, actualClassic);
                            System.err.println(msg);
                            if (THROW_ON_DIAGNOSTIC_MISMATCH) throw new AssertionError(msg);
                        }
                    }
                    return r;
                }
            };
        }
    }

    private static IncrementalHeuristicBaseMetric createIncTbrStep(IncrementalMetric im, IncrementalMetric tie, Metric classicMetric, boolean isRooted, String sn) {
        if (isRooted) {
            return new TbrIncrementalHeuristic(im, tie, sn) {
                @Override public double performLocalDescent(Tree t1, Tree t2) {
                    long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2);
                    TimeProfiler.add("TBR", System.nanoTime() - s);
                    if (ENABLE_DIAGNOSTIC_ASSERTIONS && classicMetric != null) {
                        Tree best = getLastOptimumTree() != null ? getLastOptimumTree() : t1;
                        double actualClassic = 0;
                        try {
                            actualClassic = classicMetric.getDistance(best, t2);
                        } catch (TreeCmpException e) {
                            throw new RuntimeException(e);
                        }
                        if (Math.abs(r - actualClassic) > 1e-5) {
                            String msg = String.format("[rTBR DESCENT MISMATCH] Metric=%s | Reported=%.4f vs ActualClassic=%.4f",
                                    sn, r, actualClassic);
                            System.err.println(msg);
                            if (THROW_ON_DIAGNOSTIC_MISMATCH) throw new AssertionError(msg);
                        }
                    }
                    return r;
                }
            };
        } else {
            return new UtbrIncrementalHeuristic(im, tie, sn) {
                @Override public double performLocalDescent(Tree t1, Tree t2) {
                    long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2);
                    TimeProfiler.add("TBR", System.nanoTime() - s);
                    if (ENABLE_DIAGNOSTIC_ASSERTIONS && classicMetric != null) {
                        Tree best = getLastOptimumTree() != null ? getLastOptimumTree() : t1;
                        double actualClassic = 0;
                        try {
                            actualClassic = classicMetric.getDistance(best, t2);
                        } catch (TreeCmpException e) {
                            throw new RuntimeException(e);
                        }
                        if (Math.abs(r - actualClassic) > 1e-5) {
                            String msg = String.format("[uTBR DESCENT MISMATCH] Metric=%s | Reported=%.4f vs ActualClassic=%.4f",
                                    sn, r, actualClassic);
                            System.err.println(msg);
                            if (THROW_ON_DIAGNOSTIC_MISMATCH) throw new AssertionError(msg);
                        }
                    }
                    return r;
                }
            };
        }
    }

    // =========================================================================
    // BUDOWA ŁAŃCUCHÓW VND (POWIĄZANIE METRYK INKREMENTALNYCH Z KLASYCZNYMI)
    // =========================================================================

    private static Metric buildClassicVndFull(Metric classicMetric, boolean isRooted, String shortName) {
        return new NniVndHeuristic(Arrays.asList(
                createClassicNniStep(classicMetric, isRooted, shortName),
                createClassicEcr2Step(classicMetric, isRooted, shortName),
                createClassicEcr3Step(classicMetric, isRooted, shortName),
                createClassicSprStep(classicMetric, null, isRooted, shortName),
                createClassicTbrStep(classicMetric, isRooted, shortName)
        ), shortName);
    }

    private static Metric buildClassicVndShort(Metric classicMetric, boolean isRooted, String shortName) {
        return new NniVndHeuristic(Arrays.asList(
                createClassicNniStep(classicMetric, isRooted, shortName),
                createClassicSprStep(classicMetric, null, isRooted, shortName),
                createClassicTbrStep(classicMetric, isRooted, shortName)
        ), shortName);
    }

    private static Metric buildClassicVndTbr(Metric classicMetric, boolean isRooted, String shortName) {
        return new NniVndHeuristic(Arrays.asList(
                createClassicNniStep(classicMetric, isRooted, shortName),
                createClassicTbrStep(classicMetric, isRooted, shortName)
        ), shortName);
    }

    private static Metric buildIncrementalVndFull(IncrementalMetric incMetric, Metric classicMetric, boolean isRooted, String shortName) {
        return new NniVndIncrementalHeuristic(Arrays.asList(
                createIncNniStep(incMetric, classicMetric, shortName),
                createIncEcr2Step(incMetric, classicMetric, shortName),
                createIncEcr3Step(incMetric, classicMetric, shortName),
                createIncSprStep(incMetric, null, classicMetric, isRooted, shortName),
                createIncTbrStep(incMetric, null, classicMetric, isRooted, shortName)
        ), null, shortName);
    }

    private static Metric buildIncrementalVndShort(IncrementalMetric incMetric, Metric classicMetric, boolean isRooted, String shortName) {
        return new NniVndIncrementalHeuristic(Arrays.asList(
                createIncNniStep(incMetric, classicMetric, shortName),
                createIncSprStep(incMetric, null, classicMetric, isRooted, shortName),
                createIncTbrStep(incMetric, null, classicMetric, isRooted, shortName)
        ), null, shortName);
    }

    private static Metric buildIncrementalVndTbr(IncrementalMetric incMetric, Metric classicMetric, boolean isRooted, String shortName) {
        return new NniVndIncrementalHeuristic(Arrays.asList(
                createIncNniStep(incMetric, classicMetric, shortName),
                createIncTbrStep(incMetric, null, classicMetric, isRooted, shortName)
        ), null, shortName);
    }

    private static Metric buildClassicVndFullTie(Metric classicMetric, Metric tieMetric, boolean isRooted, String shortName) {
        return new NniVndHeuristic(Arrays.asList(
                createClassicNniStep(classicMetric, isRooted, shortName),
                createClassicEcr2Step(classicMetric, isRooted, shortName),
                createClassicEcr3Step(classicMetric, isRooted, shortName),
                createClassicSprStep(classicMetric, tieMetric, isRooted, shortName),
                createClassicTbrStep(classicMetric, tieMetric, isRooted, shortName)
        ), shortName);
    }

    private static Metric buildClassicVndShortTie(Metric classicMetric, Metric tieMetric, boolean isRooted, String shortName) {
        return new NniVndHeuristic(Arrays.asList(
                createClassicNniStep(classicMetric, isRooted, shortName),
                createClassicSprStep(classicMetric, tieMetric, isRooted, shortName),
                createClassicTbrStep(classicMetric, tieMetric, isRooted, shortName)
        ), shortName);
    }

    private static Metric buildClassicVndTbrTie(Metric classicMetric, Metric tieMetric, boolean isRooted, String shortName) {
        return new NniVndHeuristic(Arrays.asList(
                createClassicNniStep(classicMetric, isRooted, shortName),
                createClassicTbrStep(classicMetric, tieMetric, isRooted, shortName)
        ), shortName);
    }

    private static Metric buildIncrementalVndFullTie(IncrementalMetric incMetric, IncrementalMetric tieIncMetric,
                                                     Metric classicMetric, Metric tieClassicMetric, boolean isRooted, String shortName) {
        return new NniVndIncrementalHeuristic(Arrays.asList(
                createIncNniStep(incMetric, classicMetric, shortName),
                createIncEcr2Step(incMetric, classicMetric, shortName),
                createIncEcr3Step(incMetric, classicMetric, shortName),
                createIncSprStep(incMetric, tieIncMetric, classicMetric, isRooted, shortName),
                createIncTbrStep(incMetric, tieIncMetric, classicMetric, isRooted, shortName)
        ), null, shortName);
    }

    private static Metric buildIncrementalVndShortTie(IncrementalMetric incMetric, IncrementalMetric tieIncMetric,
                                                      Metric classicMetric, Metric tieClassicMetric, boolean isRooted, String shortName) {
        return new NniVndIncrementalHeuristic(Arrays.asList(
                createIncNniStep(incMetric, classicMetric, shortName),
                createIncSprStep(incMetric, tieIncMetric, classicMetric, isRooted, shortName),
                createIncTbrStep(incMetric, tieIncMetric, classicMetric, isRooted, shortName)
        ), null, shortName);
    }

    private static Metric buildIncrementalVndTbrTie(IncrementalMetric incMetric, IncrementalMetric tieIncMetric,
                                                    Metric classicMetric, Metric tieClassicMetric, boolean isRooted, String shortName) {
        return new NniVndIncrementalHeuristic(Arrays.asList(
                createIncNniStep(incMetric, classicMetric, shortName),
                createIncTbrStep(incMetric, tieIncMetric, classicMetric, isRooted, shortName)
        ), null, shortName);
    }

    private List<MetricSetupVnd> getRootedMetrics() {
        List<MetricSetupVnd> list = new ArrayList<>();

        list.add(new MetricSetupVnd("RFCluster",
                new NniClassicHeuristic(new RFClusterMetric(), true, "RFC"),
                new NniIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC"),
                buildClassicVndFull(new RFClusterMetric(), true, "RFC"),
                buildClassicVndShort(new RFClusterMetric(), true, "RFC"),
                buildClassicVndTbr(new RFClusterMetric(), true, "RFC"),
                buildIncrementalVndFull(new RFClusterIncrementalMetric(), new RFClusterMetric(), true, "RFC"),
                buildIncrementalVndShort(new RFClusterIncrementalMetric(), new RFClusterMetric(), true, "RFC"),
                buildIncrementalVndTbr(new RFClusterIncrementalMetric(), new RFClusterMetric(), true, "RFC"),
                null, null, null, null, null, null));

        list.add(new MetricSetupVnd("MC",
                new NniClassicHeuristic(new MatchingClusterMetric(), true, "MC"),
                new NniIncrementalHeuristic(new MCIncrementalMetric(), "MC"),
                buildClassicVndFull(new MatchingClusterMetric(), true, "MC"),
                buildClassicVndShort(new MatchingClusterMetric(), true, "MC"),
                buildClassicVndTbr(new MatchingClusterMetric(), true, "MC"),
                buildIncrementalVndFull(new MCIncrementalMetric(), new MatchingClusterMetric(), true, "MC"),
                buildIncrementalVndShort(new MCIncrementalMetric(), new MatchingClusterMetric(), true, "MC"),
                buildIncrementalVndTbr(new MCIncrementalMetric(), new MatchingClusterMetric(), true, "MC"),
                buildClassicVndFullTie(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                buildClassicVndShortTie(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                buildClassicVndTbrTie(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                buildIncrementalVndFullTie(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                buildIncrementalVndShortTie(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                buildIncrementalVndTbrTie(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF")));

        list.add(new MetricSetupVnd("MP",
                new NniClassicHeuristic(new MatchingPairMetric(), true, "MP"),
                new NniIncrementalHeuristic(new MPIncrementalMetric(), "MP"),
                buildClassicVndFull(new MatchingPairMetric(), true, "MP"),
                buildClassicVndShort(new MatchingPairMetric(), true, "MP"),
                buildClassicVndTbr(new MatchingPairMetric(), true, "MP"),
                buildIncrementalVndFull(new MPIncrementalMetric(), new MatchingPairMetric(), true, "MP"),
                buildIncrementalVndShort(new MPIncrementalMetric(), new MatchingPairMetric(), true, "MP"),
                buildIncrementalVndTbr(new MPIncrementalMetric(), new MatchingPairMetric(), true, "MP"),
                buildClassicVndFullTie(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                buildClassicVndShortTie(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                buildClassicVndTbrTie(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                buildIncrementalVndFullTie(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                buildIncrementalVndShortTie(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                buildIncrementalVndTbrTie(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF")));

        return list;
    }

    private List<MetricSetupVnd> getUnrootedMetrics() {
        List<MetricSetupVnd> list = new ArrayList<>();

        list.add(new MetricSetupVnd("RF",
                new NniClassicHeuristic(new RFMetric(), false, "RF"),
                new NniIncrementalHeuristic(new RFIncrementalMetric(), "RF"),
                buildClassicVndFull(new RFMetric(), false, "RF"),
                buildClassicVndShort(new RFMetric(), false, "RF"),
                buildClassicVndTbr(new RFMetric(), false, "RF"),
                buildIncrementalVndFull(new RFIncrementalMetric(), new RFMetric(), false, "RF"),
                buildIncrementalVndShort(new RFIncrementalMetric(), new RFMetric(), false, "RF"),
                buildIncrementalVndTbr(new RFIncrementalMetric(), new RFMetric(), false, "RF"),
                null, null, null, null, null, null));

        list.add(new MetricSetupVnd("MS",
                new NniClassicHeuristic(new MatchingSplitMetric(), false, "MS"),
                new NniIncrementalHeuristic(new MSIncrementalMetric(), "MS"),
                buildClassicVndFull(new MatchingSplitMetric(), false, "MS"),
                buildClassicVndShort(new MatchingSplitMetric(), false, "MS"),
                buildClassicVndTbr(new MatchingSplitMetric(), false, "MS"),
                buildIncrementalVndFull(new MSIncrementalMetric(), new MatchingSplitMetric(), false, "MS"),
                buildIncrementalVndShort(new MSIncrementalMetric(), new MatchingSplitMetric(), false, "MS"),
                buildIncrementalVndTbr(new MSIncrementalMetric(), new MatchingSplitMetric(), false, "MS"),
                buildClassicVndFullTie(new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                buildClassicVndShortTie(new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                buildClassicVndTbrTie(new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                buildIncrementalVndFullTie(new MSIncrementalMetric(), new RFIncrementalMetric(), new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                buildIncrementalVndShortTie(new MSIncrementalMetric(), new RFIncrementalMetric(), new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                buildIncrementalVndTbrTie(new MSIncrementalMetric(), new RFIncrementalMetric(), new MatchingSplitMetric(), new RFMetric(), false, "MS_RF")));

        list.add(new MetricSetupVnd("M3",
                new NniClassicHeuristic(new MatchingTripletMetric(), false, "M3"),
                new NniIncrementalHeuristic(new M3IncrementalMetric(), "M3"),
                buildClassicVndFull(new MatchingTripletMetric(), false, "M3"),
                buildClassicVndShort(new MatchingTripletMetric(), false, "M3"),
                buildClassicVndTbr(new MatchingTripletMetric(), false, "M3"),
                buildIncrementalVndFull(new M3IncrementalMetric(), new MatchingTripletMetric(), false, "M3"),
                buildIncrementalVndShort(new M3IncrementalMetric(), new MatchingTripletMetric(), false, "M3"),
                buildIncrementalVndTbr(new M3IncrementalMetric(), new MatchingTripletMetric(), false, "M3"),
                buildClassicVndFullTie(new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                buildClassicVndShortTie(new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                buildClassicVndTbrTie(new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                buildIncrementalVndFullTie(new M3IncrementalMetric(), new RFIncrementalMetric(), new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                buildIncrementalVndShortTie(new M3IncrementalMetric(), new RFIncrementalMetric(), new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                buildIncrementalVndTbrTie(new M3IncrementalMetric(), new RFIncrementalMetric(), new MatchingTripletMetric(), new RFMetric(), false, "M3_RF")));

        return list;
    }
}