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

// Importy dla pełnej kaskady (upewnij się, że masz te klasy w projekcie)
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class VndQualityVsTimeMacroBenchmark {

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

    public static void main(String[] args) {
        System.out.println("===============================================================================================================");
        System.out.println("                         VND ULTIMATE QUALITY VS TIME MACRO-BENCHMARK (100 PAR DRZEW)");
        System.out.println("===============================================================================================================");

        // 1. Wyłączenie generowania plików dowodowych dla czystych pomiarów czasu
        treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic.ENABLE_LOGGING = false;
        treecmp.heuristics.vnd.NniVndHeuristic.ENABLE_LOGGING = false;

        runTestSuite(true);  // Faza 1: Ukorzenione (Rooted)
        runTestSuite(false); // Faza 2: Nieukorzenione (Unrooted)
    }

    private static void runTestSuite(boolean rooted) {
        String treeType = rooted ? "UKORZENIONYCH (rb)" : "NIEUKORZENIONYCH (ub)";
        System.out.println("\n\n>>> ROZPOCZYNAM TESTY DLA DRZEW " + treeType + " <<<");

        int[] sizes = {10, 20, 30, 50, 80};
        List<MetricSetup> metricsToTest = rooted ? getRootedMetrics() : getUnrootedMetrics();

        for (MetricSetup setup : metricsToTest) {
            System.out.println("\n====================================================================================================");
            System.out.println("TESTOWANA METRYKA: " + setup.name);
            System.out.println("====================================================================================================");

            for (int size : sizes) {
                String suffix = rooted ? "rb" : "ub";
                String fileName = "datasets/n" + size + "y200" + suffix + ".newick";
                File file = new File(fileName);

                if (!file.exists()) {
                    System.out.println("Brak pliku: " + fileName + " (Pominięcie rozmiaru N=" + size + ")");
                    continue;
                }

                List<Tree> trees = loadTrees(fileName);
                if (trees == null || trees.size() < 200) {
                    System.out.println("Plik " + fileName + " nie zawiera wystarczającej liczby drzew.");
                    continue;
                }

                System.out.println("\n--- WYNIKI DLA ROZMIARU N=" + size + " (" + fileName + ") ---");
                System.out.printf("%-35s | %-12s | %-15s | %-15s | %-15s\n",
                        "Wariant Heurystyki", "Sukcesy", "Sr. Dystans", "Calk. Czas", "Czas/Pare");
                System.out.println("-".repeat(100));

                evaluateAndReport("1. NNI (Classic)", setup.classicNni, trees);
                evaluateAndReport("2. NNI (Incremental)", setup.incrementalNni, trees);
                evaluateAndReport("3. VND NNI->ECR->SPR (Classic)", setup.classicVndFull, trees);
                evaluateAndReport("4. VND NNI->SPR (Classic)", setup.classicVndShort, trees);
                evaluateAndReport("5. VND NNI->ECR->SPR (Inc)", setup.incrementalVndFull, trees);
                evaluateAndReport("6. VND NNI->SPR (Inc)", setup.incrementalVndShort, trees);
                System.out.println("-".repeat(100));
            }
        }
    }

    private static void evaluateAndReport(String variantName, Metric heuristic, List<Tree> trees) {
        if (heuristic == null) return;

        double totalDistance = 0.0;
        long totalTimeNs = 0;

        int totalPairs = trees.size() / 2;
        int processedPairs = 0;
        int successCount = 0;
        boolean timedOut = false;

        // --- ZABEZPIECZENIE 5 GODZIN (TIMEOUT) ---
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
                System.err.println("BŁĄD dla " + variantName + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace(System.err);
            }
            totalTimeNs += (System.nanoTime() - start);
            processedPairs++;
        }

        double avgDistance = successCount > 0 ? (totalDistance / successCount) : 0.0;
        double totalTimeMs = totalTimeNs / 1_000_000.0;
        double avgTimeMs = processedPairs > 0 ? (totalTimeMs / processedPairs) : 0.0;

        String distStr = successCount > 0 ? String.format(Locale.US, "%.4f", avgDistance) : "Brak (Inf)";
        String successStr = successCount + "/" + processedPairs + (timedOut ? " (TO)" : "");

        System.out.printf("%-35s | %-12s | %-15s | %-12.0f ms | %-12.2f ms\n",
                variantName, successStr, distStr, totalTimeMs, avgTimeMs);
    }

    // =========================================================================
    // BUILDERY ORKIESTRATORÓW
    // =========================================================================

    private static Metric buildClassicVndFull(Metric classicMetric, boolean isRooted, String shortName) {
        HeuristicBaseMetric sprStep = isRooted ?
                new SprHeuristicMetric(classicMetric, isRooted, shortName) :
                new UsprHeuristicMetric(classicMetric, shortName);

        List<HeuristicBaseMetric> chain = Arrays.asList(
                new NniClassicHeuristic(classicMetric, isRooted, shortName),
                new Ecr2ClassicHeuristic(classicMetric, isRooted, shortName),
                new Ecr3ClassicHeuristic(classicMetric, isRooted, shortName),
                sprStep
        );
        return new NniVndHeuristic(chain, shortName);
    }

    private static Metric buildClassicVndShort(Metric classicMetric, boolean isRooted, String shortName) {
        HeuristicBaseMetric sprStep = isRooted ?
                new SprHeuristicMetric(classicMetric, isRooted, shortName) :
                new UsprHeuristicMetric(classicMetric, shortName);

        List<HeuristicBaseMetric> chain = Arrays.asList(
                new NniClassicHeuristic(classicMetric, isRooted, shortName),
                sprStep
        );
        return new NniVndHeuristic(chain, shortName);
    }
    private static Metric buildIncrementalVndFull(IncrementalMetric incMetric, boolean isRooted, String shortName) {
        // Dynamiczny wybór między SPR a uSPR w zależności od ukorzenienia
        IncrementalHeuristicBaseMetric sprStep = isRooted ?
                new SprIncrementalHeuristicMetric(incMetric, shortName) :
                new UsprIncrementalHeuristicMetric(incMetric, shortName);

        List<IncrementalHeuristicBaseMetric> chain = Arrays.asList(
                new NniIncrementalHeuristic(incMetric, shortName),
                // UWAGA: Jeśli ECR2 i ECR3 nie są gotowe dla drzew nieukorzenionych,
                // musisz je zabezpieczyć podobnym if-em. Zakładam, że mają wsparcie.
                new Ecr2IncrementalHeuristic(incMetric, shortName),
                new Ecr3IncrementalHeuristic(incMetric, shortName),
                sprStep
        );
        return new NniVndIncrementalHeuristic(chain, null, shortName);
    }

    private static Metric buildIncrementalVndShort(IncrementalMetric incMetric, boolean isRooted, String shortName) {
        // Dynamiczny wybór między SPR a uSPR w zależności od ukorzenienia
        IncrementalHeuristicBaseMetric sprStep = isRooted ?
                new SprIncrementalHeuristicMetric(incMetric, shortName) :
                new UsprIncrementalHeuristicMetric(incMetric, shortName);

        List<IncrementalHeuristicBaseMetric> chain = Arrays.asList(
                new NniIncrementalHeuristic(incMetric, shortName),
                sprStep
        );
        return new NniVndIncrementalHeuristic(chain, null, shortName);
    }

    // =========================================================================
    // LISTY METRYK (Rooted & Unrooted)
    // =========================================================================

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
            System.err.println("Błąd wczytywania z " + filename + ": " + e.getMessage());
        }
        return trees;
    }

    private static void assignNumbers(Tree t) {
        if (t instanceof SimpleTree) {
            ((SimpleTree) t).createNodeList();
        }
    }
}