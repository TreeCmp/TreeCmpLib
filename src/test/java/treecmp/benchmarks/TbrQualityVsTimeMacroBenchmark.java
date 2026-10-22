package treecmp.benchmarks;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.tbr.TbrHeuristicMetric;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class TbrQualityVsTimeMacroBenchmark extends AbstractQualityMacroBenchmark {

    public static boolean ENABLE_LOGGING = false;

    private final int maxAllowedClassicTbrSize;
    private static int currentPairCounter = 0;

    public TbrQualityVsTimeMacroBenchmark() {
        this.MAX_ALLOC_PER_PAIR_BYTES = 100L * 1024 * 1024 * 1024; // 100 GB

        long maxHeapBytes = Runtime.getRuntime().maxMemory();
        double maxHeapGb = maxHeapBytes / (1024.0 * 1024.0 * 1024.0);

        this.maxAllowedClassicTbrSize = determineMaxClassicTbrSize(maxHeapGb);

        System.out.println("======================================================================");
        System.out.printf(Locale.US, "[MEMORY CONFIG] Detected JVM Max Heap: %.2f GB (-Xmx)%n", maxHeapGb);
        System.out.printf(Locale.US, "[MEMORY CONFIG] Max allowed tree size (N) for Classic TBR: %d%n", maxAllowedClassicTbrSize);
        System.out.printf(Locale.US, "[LOGGING CONFIG] 1-TBR/1-uTBR Trajectory Logging (ENABLE_LOGGING): %b%n", ENABLE_LOGGING);
        if (ENABLE_LOGGING) {
            System.out.println("  -> [PATH FORMAT] logs/{size}/proof_TBR_{metric}_{variant}_N{size}_pair{pairIndex}_{timestamp}_{hexUid}.txt");
            System.out.println("  -> [AUTO-CLEANUP] Incomplete runs (D > 0.0) are omitted / automatically cleaned up.");
        }
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
        treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic.ENABLE_LOGGING = false;

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

        String baseCsvName = enableLogging
                ? "benchmark_results_TBR_WITH_LOGS_OVERHEAD"
                : "benchmark_results_TBR";

        new TbrQualityVsTimeMacroBenchmark().runBenchmark(
                cleanArgs.toArray(new String[0]),
                "TBR QUALITY VS TIME MACRO-BENCHMARK (100 TREE PAIRS)",
                baseCsvName,
                new int[]{10, 20, 30, 50, 80, 120, 200}
        );
    }

    @Override
    protected String getCsvHeader() {
        String columns = super.getCsvHeader();
        if (ENABLE_LOGGING) {
            return "# WARNING: 1-TBR/1-uTBR trajectory logging (ENABLE_LOGGING=true) was active during this benchmark run.\n"
                    + "# Disk I/O and Newick serialization overhead inflate TotalTimeMs!\n"
                    + columns;
        }
        return columns;
    }

    @Override
    protected void onBeforePair() {
        currentPairCounter++;
    }

    @Override
    protected void evaluateVariant(int size, boolean isRooted, String metricName, String variantName, Metric heuristic,
                                   List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName) {
        if (heuristic == null) return;

        if (isClassicVariant(variantName) && size > maxAllowedClassicTbrSize) {
            printSkipped(metricName, variantName, "Skip(RAM Limit)", "Max N=" + maxAllowedClassicTbrSize);
            return;
        }

        currentPairCounter = 0;

        Metric metricToRun = ENABLE_LOGGING
                ? wrapWithTbrTrajectoryLogging(heuristic, size, isRooted, metricName, variantName)
                : heuristic;

        super.evaluateVariant(size, isRooted, metricName, variantName, metricToRun, trees, blacklist, history, csvFileName);
    }

    private boolean isClassicVariant(String variantName) {
        return variantName.contains("Classic");
    }

    private Metric wrapWithTbrTrajectoryLogging(Metric baseMetric, int size, boolean isRooted, String metricName, String variantName) {
        Metric evalMetric = getBaseTopologicalMetric(metricName);

        return new Metric() {
            @Override
            public double getDistance(Tree t1, Tree t2, int... indexes) throws TreeCmpException {
                if (!ENABLE_LOGGING) {
                    return baseMetric.getDistance(t1, t2, indexes);
                }

                int pairIdx = (indexes != null && indexes.length > 0) ? indexes[0] : currentPairCounter;

                Tree startCopy = new SimpleTree(t1);
                if (startCopy instanceof SimpleTree) {
                    ((SimpleTree) startCopy).createNodeList();
                }

                double dist = baseMetric.getDistance(t1, t2, indexes);
                boolean isSuccess = !Double.isInfinite(dist) && !Double.isNaN(dist) && dist < Double.MAX_VALUE / 2.0;

                if (isSuccess) {
                    List<Tree> trajectory = null;
                    if (baseMetric instanceof HeuristicBaseMetric) {
                        HeuristicBaseMetric hbm = (HeuristicBaseMetric) baseMetric;
                        trajectory = hbm.getLastOptimumTrajectory(startCopy);
                        if ((trajectory == null || trajectory.isEmpty()) && hbm.getLastOptimumTree() != null) {
                            trajectory = Collections.singletonList(hbm.getLastOptimumTree());
                        }
                    } else if (baseMetric instanceof IncrementalHeuristicBaseMetric) {
                        IncrementalHeuristicBaseMetric ihbm = (IncrementalHeuristicBaseMetric) baseMetric;
                        trajectory = ihbm.getLastOptimumTrajectory(startCopy);
                        if ((trajectory == null || trajectory.isEmpty()) && ihbm.getLastOptimumTree() != null) {
                            trajectory = Collections.singletonList(ihbm.getLastOptimumTree());
                        }
                    }

                    if (trajectory != null && !trajectory.isEmpty()) {
                        boolean saved = writeTbrProofFile(size, isRooted, metricName, variantName, pairIdx, startCopy, t2, trajectory, evalMetric);
                        if (saved) {
                            dist = trajectory.size();
                        }
                    }
                }

                return dist;
            }

            @Override public String getName() { return baseMetric.getName(); }
            @Override public String getDescription() { return baseMetric.getDescription(); }
            @Override public boolean isRooted() { return baseMetric.isRooted(); }
            @Override public boolean isWeighted() { return baseMetric.isWeighted(); }
            @Override public boolean isDiffLeafSets() { return baseMetric.isDiffLeafSets(); }
            @Override public String getCommandLineName() { return baseMetric.getCommandLineName(); }
            @Override public void setCommandLineName(String cln) { baseMetric.setCommandLineName(cln); }
            @Override public void setName(String name) { baseMetric.setName(name); }
            @Override public void setDescription(String d) { baseMetric.setDescription(d); }
            @Override public void initData() { baseMetric.initData(); }
            @Override public treecmp.common.AlignInfo getAlignment() { return baseMetric.getAlignment(); }
        };
    }

    private boolean writeTbrProofFile(int size, boolean isRooted, String metricName, String variantName, int pairIdx,
                                      Tree startTree, Tree targetTree, List<Tree> trajectory, Metric evalMetric) {
        double finalDist;
        try {
            Tree lastTree = trajectory.get(trajectory.size() - 1);
            finalDist = evalMetric.getDistance(lastTree, targetTree);
        } catch (Exception e) {
            finalDist = Double.POSITIVE_INFINITY;
        }

        if (Math.abs(finalDist) > 1e-9) {
            return false;
        }

        File dir = new File("logs/" + size);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String safeVariant = variantName.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String hexUid = UUID.randomUUID().toString().substring(0, 8);
        String moveType = isRooted ? "1-rTBR" : "1-uTBR";

        File outFile = new File(dir, String.format(Locale.US,
                "proof_TBR_%s_%s_N%d_pair%d_%s_%s.txt",
                metricName, safeVariant, size, pairIdx, timestamp, hexUid));

        try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
            double initDist = evalMetric.getDistance(startTree, targetTree);

            pw.println("================================================================================");
            pw.println("                      " + moveType + " TRAJECTORY PROOF CERTIFICATE");
            pw.println("================================================================================");
            pw.printf(Locale.US, "Tree Size (N)     : %d%n", size);
            pw.printf(Locale.US, "Tree Type         : %s%n", isRooted ? "ROOTED" : "UNROOTED");
            pw.printf(Locale.US, "Step Operation    : %s (each consecutive tree is exactly 1 move apart)%n", moveType);
            pw.printf(Locale.US, "Topological Metric: %s%n", metricName);
            pw.printf(Locale.US, "Heuristic Variant : %s%n", variantName);
            pw.printf(Locale.US, "Pair Index        : %d%n", pairIdx);
            pw.printf(Locale.US, "Total %s Steps: %d%n", moveType, trajectory.size());
            pw.printf(Locale.US, "Initial Distance  : %.4f%n", initDist);
            pw.printf(Locale.US, "Final Distance    : %.4f%n", finalDist);
            pw.println("--------------------------------------------------------------------------------");
            pw.println("START TREE (Step 0):");
            pw.println(treeToNewick(startTree));
            pw.println("TARGET TREE:");
            pw.println(treeToNewick(targetTree));
            pw.println("================================================================================");
            pw.println("STEP-BY-STEP TRAJECTORY (Consecutive trees differ by exactly " + moveType + "):");
            pw.println("--------------------------------------------------------------------------------");

            pw.printf(Locale.US, "[Step %3d] (%s = %.4f) %s%n", 0, metricName, initDist, treeToNewick(startTree));

            for (int i = 0; i < trajectory.size(); i++) {
                Tree stepTree = trajectory.get(i);
                double stepDist = evalMetric.getDistance(stepTree, targetTree);
                pw.printf(Locale.US, "[Step %3d] (%s = %.4f) %s%n", (i + 1), metricName, stepDist, treeToNewick(stepTree));
            }

            pw.println("================================================================================");
            return true;
        } catch (Exception e) {
            if (outFile.exists()) {
                outFile.delete();
            }
            System.err.println("Failed to write TBR proof log: " + e.getMessage());
            return false;
        }
    }

    private String treeToNewick(Tree tree) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        TreeUtils.printNH(tree, pw, false, false);
        pw.flush();
        String newick = sw.toString().trim();
        if (!newick.endsWith(";")) {
            newick += ";";
        }
        return newick;
    }

    private Metric getBaseTopologicalMetric(String metricName) {
        switch (metricName) {
            case "RFCluster": return new RFClusterMetric();
            case "MC": return new MatchingClusterMetric();
            case "MP": return new MatchingPairMetric();
            case "RF": return new RFMetric();
            case "MS": return new MatchingSplitMetric();
            case "M3": return new MatchingTripletMetric();
            default: return new RFMetric();
        }
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