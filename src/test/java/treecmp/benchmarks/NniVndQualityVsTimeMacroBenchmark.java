package treecmp.benchmarks;

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

import java.io.PrintWriter;
import java.util.*;

public class NniVndQualityVsTimeMacroBenchmark extends AbstractQualityMacroBenchmark {

    static class TimeProfiler {
        private static final ThreadLocal<Map<String, Long>> times = ThreadLocal.withInitial(HashMap::new);
        public static void add(String phase, long timeNs) { times.get().put(phase, times.get().getOrDefault(phase, 0L) + timeNs); }
        public static void reset() { times.get().clear(); }
        public static long get(String phase) { return times.get().getOrDefault(phase, 0L); }
    }

    static class MetricSetupVnd {
        String name;
        Metric classicNni, incrementalNni;
        Metric classicVndFull, classicVndShort;
        Metric incrementalVndFull, incrementalVndShort;

        public MetricSetupVnd(String name, Metric classicNni, Metric incrementalNni, Metric classicVndFull, Metric classicVndShort, Metric incrementalVndFull, Metric incrementalVndShort) {
            this.name = name; this.classicNni = classicNni; this.incrementalNni = incrementalNni;
            this.classicVndFull = classicVndFull; this.classicVndShort = classicVndShort;
            this.incrementalVndFull = incrementalVndFull; this.incrementalVndShort = incrementalVndShort;
        }
    }

    private long globalNniT, globalEcr2T, globalEcr3T, globalSprT;

    public NniVndQualityVsTimeMacroBenchmark() {
        this.MAX_ALLOC_PER_PAIR_BYTES = 100L * 1024 * 1024 * 1024; // 100 GB for VND
    }

    public static void main(String[] args) {
        treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic.ENABLE_LOGGING = false;
        treecmp.heuristics.vnd.NniVndHeuristic.ENABLE_LOGGING = false;
        new NniVndQualityVsTimeMacroBenchmark().runBenchmark(
                args,
                "VND ULTIMATE QUALITY VS TIME MACRO-BENCHMARK (100 TREE PAIRS)",
                "benchmark_results_VND",
                new int[]{10, 20, 30, 50, 80, 120}
        );
    }

    @Override
    protected void runEvaluationsForSize(int size, boolean rooted, List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName) {
        List<MetricSetupVnd> metricsToTest = rooted ? getRootedMetrics() : getUnrootedMetrics();
        for (MetricSetupVnd setup : metricsToTest) {
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "1. NNI (Classic)", setup.classicNni, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "2. NNI (Incremental)", setup.incrementalNni, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "3. VND NNI->ECR->SPR (Classic)", setup.classicVndFull, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "4. VND NNI->SPR (Classic)", setup.classicVndShort, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "5. VND NNI->ECR->SPR (Inc)", setup.incrementalVndFull, trees, blacklist, history, csvFileName);
            forceCleanMemory(); evaluateVariant(size, rooted, setup.name, "6. VND NNI->SPR (Inc)", setup.incrementalVndShort, trees, blacklist, history, csvFileName);
            System.out.println("-".repeat(160));
        }
    }

    // --- VND Specific reporting overrides ---

    @Override
    protected String getCsvHeader() {
        return "Size,IsRooted,Metric,Variant,PairIndex,Success,Distance,TotalTimeMs,NniTimeNs,Ecr2TimeNs,Ecr3TimeNs,SprTimeNs,AllocBytes,PeakRamBytes";
    }

    @Override
    protected void writeCsvRow(PrintWriter csvWriter, int size, boolean isRooted, String metricName, String variantName, int pairIndex, boolean isSuccess, double dist, double pairTimeMs, long pairAllocatedBytes, long currentPeak) {
        long ptNni = TimeProfiler.get("NNI");
        long ptEcr2 = TimeProfiler.get("ECR2");
        long ptEcr3 = TimeProfiler.get("ECR3");
        long ptSpr = TimeProfiler.get("SPR");
        csvWriter.printf(Locale.US, "%d,%b,%s,\"%s\",%d,%b,%.4f,%.4f,%d,%d,%d,%d,%d,%d%n",
                size, isRooted, metricName, variantName, pairIndex, isSuccess, dist, pairTimeMs, ptNni, ptEcr2, ptEcr3, ptSpr, pairAllocatedBytes, currentPeak);
    }

    @Override
    protected String getExtraTableHeaderInfo() { return "Time Breakdown"; }

    @Override
    protected void resetCustomStats() {
        globalNniT = 0; globalEcr2T = 0; globalEcr3T = 0; globalSprT = 0;
    }

    @Override
    protected void onBeforePair() { TimeProfiler.reset(); }

    @Override
    protected void onAfterPair() {
        globalNniT += TimeProfiler.get("NNI");
        globalEcr2T += TimeProfiler.get("ECR2");
        globalEcr3T += TimeProfiler.get("ECR3");
        globalSprT += TimeProfiler.get("SPR");
    }

    @Override
    protected String getExtraConsoleBreakdown(String variantName) {
        boolean isVndFull = variantName.contains("VND NNI->ECR->SPR");
        boolean isVndShort = variantName.contains("VND NNI->SPR");
        long totalPhases = globalNniT + globalEcr2T + globalEcr3T + globalSprT;

        if (isVndFull && totalPhases > 0) {
            return String.format("[NNI:%2d%% ecr2:%2d%% ecr3:%2d%% SPR:%2d%%]", Math.round(globalNniT * 100.0 / totalPhases), Math.round(globalEcr2T * 100.0 / totalPhases), Math.round(globalEcr3T * 100.0 / totalPhases), Math.round(globalSprT * 100.0 / totalPhases));
        } else if (isVndShort) {
            long shortTotal = globalNniT + globalSprT;
            if (shortTotal > 0) {
                return String.format("[NNI:%2d%% SPR:%2d%%]", Math.round(globalNniT * 100.0 / shortTotal), Math.round(globalSprT * 100.0 / shortTotal));
            }
        }
        return "N/A";
    }

    // --- Builders ---

    private static Metric buildClassicVndFull(Metric classicMetric, boolean isRooted, String shortName) {
        HeuristicBaseMetric sprStep = isRooted ? new SprHeuristicMetric(classicMetric, isRooted, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r; } } : new UsprHeuristicMetric(classicMetric, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r; } };
        return new NniVndHeuristic(Arrays.asList(
                new NniClassicHeuristic(classicMetric, isRooted, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("NNI", System.nanoTime() - s); return r; } },
                new Ecr2ClassicHeuristic(classicMetric, isRooted, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("ECR2", System.nanoTime() - s); return r; } },
                new Ecr3ClassicHeuristic(classicMetric, isRooted, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("ECR3", System.nanoTime() - s); return r; } },
                sprStep
        ), shortName);
    }

    private static Metric buildClassicVndShort(Metric classicMetric, boolean isRooted, String shortName) {
        HeuristicBaseMetric sprStep = isRooted ? new SprHeuristicMetric(classicMetric, isRooted, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r; } } : new UsprHeuristicMetric(classicMetric, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r; } };
        return new NniVndHeuristic(Arrays.asList(
                new NniClassicHeuristic(classicMetric, isRooted, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("NNI", System.nanoTime() - s); return r; } },
                sprStep
        ), shortName);
    }

    private static Metric buildIncrementalVndFull(IncrementalMetric incMetric, boolean isRooted, String shortName) {
        IncrementalHeuristicBaseMetric sprStep = isRooted ? new SprIncrementalHeuristicMetric(incMetric, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r; } } : new UsprIncrementalHeuristicMetric(incMetric, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r; } };
        return new NniVndIncrementalHeuristic(Arrays.asList(
                new NniIncrementalHeuristic(incMetric, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("NNI", System.nanoTime() - s); return r; } },
                new Ecr2IncrementalHeuristic(incMetric, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("ECR2", System.nanoTime() - s); return r; } },
                new Ecr3IncrementalHeuristic(incMetric, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("ECR3", System.nanoTime() - s); return r; } },
                sprStep
        ), null, shortName);
    }

    private static Metric buildIncrementalVndShort(IncrementalMetric incMetric, boolean isRooted, String shortName) {
        IncrementalHeuristicBaseMetric sprStep = isRooted ? new SprIncrementalHeuristicMetric(incMetric, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r; } } : new UsprIncrementalHeuristicMetric(incMetric, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("SPR", System.nanoTime() - s); return r; } };
        return new NniVndIncrementalHeuristic(Arrays.asList(
                new NniIncrementalHeuristic(incMetric, shortName) { @Override public double performLocalDescent(Tree t1, Tree t2) { long s = System.nanoTime(); double r = super.performLocalDescent(t1, t2); TimeProfiler.add("NNI", System.nanoTime() - s); return r; } },
                sprStep
        ), null, shortName);
    }

    private List<MetricSetupVnd> getRootedMetrics() {
        List<MetricSetupVnd> list = new ArrayList<>();
        list.add(new MetricSetupVnd("RFCluster", new NniClassicHeuristic(new RFClusterMetric(), true, "RFC"), new NniIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC"), buildClassicVndFull(new RFClusterMetric(), true, "RFC"), buildClassicVndShort(new RFClusterMetric(), true, "RFC"), buildIncrementalVndFull(new RFClusterIncrementalMetric(), true, "RFC"), buildIncrementalVndShort(new RFClusterIncrementalMetric(), true, "RFC")));
        list.add(new MetricSetupVnd("MC", new NniClassicHeuristic(new MatchingClusterMetric(), true, "MC"), new NniIncrementalHeuristic(new MCIncrementalMetric(), "MC"), buildClassicVndFull(new MatchingClusterMetric(), true, "MC"), buildClassicVndShort(new MatchingClusterMetric(), true, "MC"), buildIncrementalVndFull(new MCIncrementalMetric(), true, "MC"), buildIncrementalVndShort(new MCIncrementalMetric(), true, "MC")));
        list.add(new MetricSetupVnd("MP", new NniClassicHeuristic(new MatchingPairMetric(), true, "MP"), new NniIncrementalHeuristic(new MPIncrementalMetric(), "MP"), buildClassicVndFull(new MatchingPairMetric(), true, "MP"), buildClassicVndShort(new MatchingPairMetric(), true, "MP"), buildIncrementalVndFull(new MPIncrementalMetric(), true, "MP"), buildIncrementalVndShort(new MPIncrementalMetric(), true, "MP")));
        return list;
    }

    private List<MetricSetupVnd> getUnrootedMetrics() {
        List<MetricSetupVnd> list = new ArrayList<>();
        list.add(new MetricSetupVnd("RF", new NniClassicHeuristic(new RFMetric(), false, "RF"), new NniIncrementalHeuristic(new RFIncrementalMetric(), "RF"), buildClassicVndFull(new RFMetric(), false, "RF"), buildClassicVndShort(new RFMetric(), false, "RF"), buildIncrementalVndFull(new RFIncrementalMetric(), false, "RF"), buildIncrementalVndShort(new RFIncrementalMetric(), false, "RF")));
        list.add(new MetricSetupVnd("MS", new NniClassicHeuristic(new MatchingSplitMetric(), false, "MS"), new NniIncrementalHeuristic(new MSIncrementalMetric(), "MS"), buildClassicVndFull(new MatchingSplitMetric(), false, "MS"), buildClassicVndShort(new MatchingSplitMetric(), false, "MS"), buildIncrementalVndFull(new MSIncrementalMetric(), false, "MS"), buildIncrementalVndShort(new MSIncrementalMetric(), false, "MS")));
        list.add(new MetricSetupVnd("M3", new NniClassicHeuristic(new MatchingTripletMetric(), false, "M3"), new NniIncrementalHeuristic(new M3IncrementalMetric(), "M3"), buildClassicVndFull(new MatchingTripletMetric(), false, "M3"), buildClassicVndShort(new MatchingTripletMetric(), false, "M3"), buildIncrementalVndFull(new M3IncrementalMetric(), false, "M3"), buildIncrementalVndShort(new M3IncrementalMetric(), false, "M3")));
        return list;
    }
}