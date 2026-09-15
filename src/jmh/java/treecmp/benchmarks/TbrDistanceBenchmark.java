package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.tbr.TbrHeuristicMetric;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class TbrDistanceBenchmark {

    @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    public String metricName;

    @Param({"10", "20", "30", "50", "80", "120", "200"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Tree t1ForIncr;

    private HeuristicBaseMetric classicMetric;
    private IncrementalHeuristicBaseMetric incrementalMetric;

    private boolean classicOomReported = false;
    private boolean incrOomReported = false;

    private void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        boolean isRooted = false;
        classicOomReported = false;
        incrOomReported = false;

        switch (metricName) {
            case "RF":
                isRooted = false;
                classicMetric     = new TbrHeuristicMetric(new RFMetric(), false, "RF");
                incrementalMetric = new UtbrIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric     = new TbrHeuristicMetric(new RFClusterMetric(), true, "RFC");
                incrementalMetric = new TbrIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric     = new TbrHeuristicMetric(new MatchingSplitMetric(), false, "MS");
                incrementalMetric = new UtbrIncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicMetric     = new TbrHeuristicMetric(new MatchingClusterMetric(), true, "MC");
                incrementalMetric = new TbrIncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric     = new TbrHeuristicMetric(new MatchingPairMetric(), true, "MP");
                incrementalMetric = new TbrIncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric     = new TbrHeuristicMetric(new MatchingTripletMetric(), false, "M3");
                incrementalMetric = new UtbrIncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metricName);
        }

        File datasetFile = findDatasetFile(treeSize, isRooted);
        boolean loadedFromFile = false;

        if (datasetFile != null && datasetFile.exists()) {
            List<Tree> loadedTrees = loadTrees(datasetFile.getPath(), 2);
            if (loadedTrees != null && loadedTrees.size() >= 2) {
                t1 = new SimpleTree(loadedTrees.get(0));
                t2 = new SimpleTree(loadedTrees.get(1));
                t1ForIncr = new SimpleTree(loadedTrees.get(0));
                loadedFromFile = true;
            }
        }

        if (!loadedFromFile) {
            if (isRooted) {
                t1 = TestTreeFactory.randomRootedBinaryTree(treeSize, 12345L);
                t2 = TestTreeFactory.randomRootedBinaryTree(treeSize, 67890L);
                t1ForIncr = TestTreeFactory.randomRootedBinaryTree(treeSize, 12345L);
            } else {
                t1 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
                t2 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 67890L);
                t1ForIncr = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
            }
        }

        assignNumbers(t1);
        assignNumbers(t2);
        assignNumbers(t1ForIncr);
    }

    private File findDatasetFile(int size, boolean isRooted) {
        File dir = new File("datasets");
        if (!dir.exists() || !dir.isDirectory()) return null;
        String prefix = "n" + size + "y";
        String suffix = (isRooted ? "rb" : "ub") + ".newick";
        File[] files = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(suffix));
        return (files != null && files.length > 0) ? files[0] : null;
    }

    private static List<Tree> loadTrees(String filename, int limit) {
        List<Tree> trees = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null && trees.size() < limit) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    Tree t = TreeCreator.getTreeFromString(line);
                    if (t != null) trees.add(t);
                }
            }
        } catch (Exception e) {
            System.err.println("Błąd wczytywania z pliku " + filename + ": " + e.getMessage());
        }
        return trees;
    }

    @Benchmark
    public double benchmarkClassicFullRun() {
        try {
            return classicMetric.getDistance(new SimpleTree(t1), t2);
        } catch (OutOfMemoryError e) {
            if (!classicOomReported) {
                System.err.printf("%n[!] OOM ZŁAPANY W CLASSIC TBR | Metryka: %s | Drzewa: N=%d [!]%n", metricName, treeSize);
                classicOomReported = true;
            }
            System.gc();
            return Double.NaN;
        }
    }

    @Benchmark
    public double benchmarkIncrementalFullRun() {
        try {
            return incrementalMetric.getDistance(new SimpleTree(t1ForIncr), t2);
        } catch (OutOfMemoryError e) {
            if (!incrOomReported) {
                System.err.printf("%n[!] OOM ZŁAPANY W INCREMENTAL TBR | Metryka: %s | Drzewa: N=%d [!]%n", metricName, treeSize);
                incrOomReported = true;
            }
            System.gc();
            return Double.NaN;
        }
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = true;

        String[] treeSizes = TbrDistanceBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        List<RunResult> allResults = new ArrayList<>();

        // Selektywne harmonogramowanie ze względu na kombinatoryczną eksplozję O(N^3)
        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            if (size <= 20) {
                // Dla N<=20 uruchamiamy Classic oraz Incremental dla wszystkich metryk
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, TbrDistanceBenchmark.class.getSimpleName() + ".benchmarkClassicFullRun", quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, TbrDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun", quickEstimate));
            } else if (size <= 30) {
                // Dla N=30 klasyczne metryki skojarzeniowe (MC, MP, MS, M3) dławią pamięć
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, TbrDistanceBenchmark.class.getSimpleName() + ".benchmarkClassicFullRun", quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, TbrDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun", quickEstimate));
            } else if (size <= 50) {
                // Dla N=50 tylko szybkie bitowe metryki RF/RFC w wariancie klasycznym
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, TbrDistanceBenchmark.class.getSimpleName() + ".benchmarkClassicFullRun", quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP"}, TbrDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun", quickEstimate));
            } else {
                // Dla N >= 80 wyłącznie skalowalne metryki inkrementalne
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP"}, TbrDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun", quickEstimate));
            }
        }

        System.out.println("\n\n==========================================================================================");
        System.out.println("            PODSUMOWANIE JMH TBR: CZAS I ALOKACJA PAMIĘCI (NA 1 PARĘ DRZEW)");
        System.out.println("==========================================================================================");
        System.out.printf("%-10s | %-15s | %-10s | %-15s | %-20s%n", "Rozmiar N", "Wariant", "Metryka", "Czas (ms/para)", "Alokacja RAM (per para)");
        System.out.println("-".repeat(90));

        for (RunResult r : allResults) {
            String benchmarkName = r.getParams().getBenchmark();
            String variant = benchmarkName.contains("Classic") ? "Classic" : "Incremental";
            String metric = r.getParams().getParam("metricName");
            String size = r.getParams().getParam("treeSize");

            double timeMs = r.getPrimaryResult().getScore();

            double bytesPerOp = 0.0;
            if (r.getSecondaryResults().containsKey("gc.alloc.rate.norm")) {
                bytesPerOp = r.getSecondaryResults().get("gc.alloc.rate.norm").getScore();
            }

            String timeStr = Double.isNaN(timeMs) ? "OOM/Timeout" : String.format(Locale.US, "%.2f", timeMs);

            String memoryStr;
            if (Double.isNaN(timeMs)) {
                memoryStr = "Brak (OOM)";
            } else if (bytesPerOp > 1024 * 1024 * 1024) {
                memoryStr = String.format(Locale.US, "%.2f GB", bytesPerOp / (1024.0 * 1024.0 * 1024.0));
            } else if (bytesPerOp > 1024 * 1024) {
                memoryStr = String.format(Locale.US, "%.2f MB", bytesPerOp / (1024.0 * 1024.0));
            } else if (bytesPerOp > 0) {
                memoryStr = String.format(Locale.US, "%.2f KB", bytesPerOp / 1024.0);
            } else {
                memoryStr = "Brak danych";
            }

            System.out.printf("%-10s | %-15s | %-10s | %-15s | %-20s%n", size, variant, metric, timeStr, memoryStr);
        }
        System.out.println("==========================================================================================\n");
    }

    private static Collection<RunResult> runJmh(String sizeStr, String[] metrics, String includeRegex, boolean quickEstimate) throws Exception {
        ChainedOptionsBuilder builder = new OptionsBuilder()
                .include(includeRegex)
                .param("treeSize", sizeStr)
                .param("metricName", metrics)
                .jvmArgs("-Xms4g", "-Xmx16g")
                .addProfiler("gc");

        if (quickEstimate) {
            builder.warmupIterations(1)
                    .warmupTime(TimeValue.seconds(1))
                    .measurementIterations(2)
                    .measurementTime(TimeValue.seconds(1))
                    .forks(1)
                    .warmupForks(0);
        } else {
            builder.warmupIterations(3)
                    .warmupTime(TimeValue.seconds(2))
                    .measurementIterations(5)
                    .measurementTime(TimeValue.seconds(2))
                    .forks(2)
                    .warmupForks(1);
        }

        return new Runner(builder.build()).run();
    }
}