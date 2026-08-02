package treecmp.benchmarks;

import pal.tree.SimpleTree;
import pal.tree.Tree;

import treecmp.heuristics.nni.NniClassicHeuristic;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TreeCreator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NniQualityVsTimeMacroBenchmark {

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

    public static void main(String[] args) {
        System.out.println("===============================================================================================================");
        System.out.println("                               NNI QUALITY VS TIME MACRO-BENCHMARK (100 TREE PAIRS)");
        System.out.println("===============================================================================================================");

        runTestSuite(true);  // Phase 1: Rooted
        runTestSuite(false); // Phase 2: Unrooted
    }

    private static void runTestSuite(boolean rooted) {
        String treeType = rooted ? "ROOTED (rb)" : "UNROOTED (ub)";
        System.out.println("\n\n>>> STARTING TESTS FOR " + treeType + " TREES <<<");

        int[] sizes = {10, 20, 30, 50/*, 80, 120, 200*/};

        List<MetricSetup> metricsToTest = rooted ? getRootedMetrics() : getUnrootedMetrics();

        for (int size : sizes) {
            String suffix = rooted ? "rb" : "ub";
            String fileName = "datasets/n" + size + "y200" + suffix + ".newick";
            File file = new File(fileName);

            if (!file.exists()) {
                System.out.println("Missing file: " + fileName + " (Skipping)");
                continue;
            }

            List<Tree> trees = loadTrees(fileName);
            if (trees == null || trees.size() < 200) {
                System.out.println("File " + fileName + " does not contain enough trees.");
                continue;
            }

            System.out.println("\n--- RESULTS FOR SIZE N=" + size + " (" + fileName + ") ---");
            System.out.printf("%-18s | %-20s | %-12s | %-15s | %-15s | %-15s\n",
                    "Metric", "Heuristic Variant", "Successes", "Avg NNI Distance", "Total Time", "Time/Pair");
            System.out.println("-".repeat(110));

            for (MetricSetup setup : metricsToTest) {
                evaluateAndReport(setup.name, "Classic (Pure)", setup.classicPure, trees);
                evaluateAndReport(setup.name, "Incremental (Pure)", setup.incrementalPure, trees);
                evaluateAndReport(setup.name, "Classic + RF (Tie)", setup.classicFiltered, trees);
                evaluateAndReport(setup.name, "Incremental + RF (Tie)", setup.incrementalFiltered, trees);
                System.out.println("-".repeat(110));
            }
        }
    }

    private static void evaluateAndReport(String metricName, String variantName, Metric heuristic, List<Tree> trees) {
        if (heuristic == null) return;

        double totalDistance = 0.0;
        long totalTimeNs = 0;

        int totalPairs = trees.size() / 2;
        int processedPairs = 0;
        int successCount = 0;
        boolean timedOut = false;

        // Set time limit (5 hours in milliseconds)
        long TIMEOUT_MS = 5L * 60 * 60 * 1000;
        long overallStartMs = System.currentTimeMillis();

        for (int i = 0; i < trees.size(); i += 2) {
            if (System.currentTimeMillis() - overallStartMs > TIMEOUT_MS) {
                timedOut = true;
                break;
            }

            Tree t1 = new SimpleTree(trees.get(i));
            Tree t2 = new SimpleTree(trees.get(i + 1));
            assignNumbers(t1);
            assignNumbers(t2);

            long start = System.nanoTime();
            try {
                double dist = heuristic.getDistance(t1, t2);

                if (dist != Double.POSITIVE_INFINITY && !Double.isNaN(dist)) {
                    totalDistance += dist;
                    successCount++;
                }
            } catch (Exception e) {
                // Suppress errors
            }
            totalTimeNs += (System.nanoTime() - start);
            processedPairs++;
        }

        double avgDistance = successCount > 0 ? (totalDistance / successCount) : 0.0;
        double totalTimeMs = totalTimeNs / 1_000_000.0;
        double avgTimeMs = processedPairs > 0 ? (totalTimeMs / processedPairs) : 0.0;

        String distStr = successCount > 0 ? String.format(Locale.US, "%.4f", avgDistance) : "None (Inf)";
        String successStr = successCount + "/" + processedPairs + (timedOut ? " (TO)" : "");

        System.out.printf("%-18s | %-20s | %-12s | %-15s | %-12.0f ms | %-12.2f ms\n",
                metricName, variantName, successStr, distStr, totalTimeMs, avgTimeMs);
    }

    private static List<MetricSetup> getRootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();

        // For RFCluster we do not use filters, as it is a base metric
        list.add(new MetricSetup("RFCluster",
                new NniClassicHeuristic(new RFClusterMetric(), true, "RFC"),
                new NniIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFCinc"),
                null,
                null
        ));

        list.add(new MetricSetup("MC",
                new NniClassicHeuristic(new MatchingClusterMetric(), true, "MC_Pure"),
                new NniIncrementalHeuristic(new MCIncrementalMetric(), "MCinc"),
                new NniClassicHeuristic(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                new NniIncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MCinc_RF")
        ));

        list.add(new MetricSetup("MP",
                new NniClassicHeuristic(new MatchingPairMetric(), true, "MP_Pure"),
                new NniIncrementalHeuristic(new MPIncrementalMetric(), "MPinc"),
                new NniClassicHeuristic(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                new NniIncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MPinc_RF")
        ));

        return list;
    }

    private static List<MetricSetup> getUnrootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();

        // For RF we do not use filters, as it is a base metric
        list.add(new MetricSetup("RF",
                new NniClassicHeuristic(new RFMetric(), false, "RF"),
                new NniIncrementalHeuristic(new RFIncrementalMetric(), "RFinc"),
                null,
                null
        ));

        list.add(new MetricSetup("MS",
                new NniClassicHeuristic(new MatchingSplitMetric(), false, "MS_Pure"),
                new NniIncrementalHeuristic(new MSIncrementalMetric(), "MSinc"),
                new NniClassicHeuristic(new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                new NniIncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MSinc_RF")
        ));

        list.add(new MetricSetup("M3",
                new NniClassicHeuristic(new MatchingTripletMetric(), false, "M3_Pure"),
                new NniIncrementalHeuristic(new M3IncrementalMetric(), "M3inc"),
                new NniClassicHeuristic(new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                new NniIncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3inc_RF")
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