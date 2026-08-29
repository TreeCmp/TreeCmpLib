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
import treecmp.heuristics.spr.SprHeuristicMetric;
import treecmp.heuristics.spr.UsprHeuristicMetric;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS) // Pełny przebieg mierzymy w milisekundach
@State(Scope.Benchmark)
public class SprDistanceBenchmark {

    @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    public String metricName;

    // Pełne skale dla inkrementalnej wspinaczki
    @Param({"10", "20", "30", "50", "80", "120", "200", "300", "500", "800", "1200", "2000"})
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
                classicMetric     = new UsprHeuristicMetric(new RFMetric(), "RF");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric     = new SprHeuristicMetric(new RFClusterMetric(), true, "RFC");
                incrementalMetric = new SprIncrementalHeuristicMetric(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric     = new UsprHeuristicMetric(new MatchingSplitMetric(), "MS");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicMetric     = new SprHeuristicMetric(new MatchingClusterMetric(), true, "MC");
                incrementalMetric = new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric     = new SprHeuristicMetric(new MatchingPairMetric(), true, "MP");
                incrementalMetric = new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric     = new UsprHeuristicMetric(new MatchingTripletMetric(), "M3");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3");
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

        assignNumbers(t1); assignNumbers(t2); assignNumbers(t1ForIncr);
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
                System.err.printf("%n[!] OOM ZŁAPANY W CLASSIC | Metryka: %s | Drzewa: N=%d [!]%n", metricName, treeSize);
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
                System.err.printf("%n[!] OOM ZŁAPANY W INCREMENTAL | Metryka: %s | Drzewa: N=%d [!]%n", metricName, treeSize);
                incrOomReported = true;
            }
            System.gc();
            return Double.NaN;
        }
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = true;

        String[] treeSizes = SprDistanceBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        // Lista do gromadzenia wszystkich wyników JMH
        List<RunResult> allResults = new ArrayList<>();

        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            if (size <= 30) {
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC"}, SprDistanceBenchmark.class.getSimpleName() + ".benchmarkClassicFullRun", quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC"}, SprDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun", quickEstimate));
            } else if (size <= 50) {
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC"}, SprDistanceBenchmark.class.getSimpleName() + ".benchmarkClassicFullRun", quickEstimate));
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC"}, SprDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun", quickEstimate));
            } else if (size <= 300) {
                allResults.addAll(runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC"}, SprDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun", quickEstimate));
            }
        }

        // Generowanie eleganckiej tabeli podsumowującej!
        System.out.println("\n\n==========================================================================================");
        System.out.println("                 PODSUMOWANIE JMH: CZAS I ALOKACJA PAMIĘCI (NA 1 PARĘ DRZEW)");
        System.out.println("==========================================================================================");
        System.out.printf("%-10s | %-15s | %-10s | %-15s | %-20s%n", "Rozmiar N", "Wariant", "Metryka", "Czas (ms/para)", "Alokacja RAM (per para)");
        System.out.println("-".repeat(90));

        for (RunResult r : allResults) {
            String benchmarkName = r.getParams().getBenchmark();
            String variant = benchmarkName.contains("Classic") ? "Classic" : "Incremental";
            String metric = r.getParams().getParam("metricName");
            String size = r.getParams().getParam("treeSize");

            double timeMs = r.getPrimaryResult().getScore();

            // Pobieranie zużycia pamięci z profilera GC
            double bytesPerOp = 0.0;
            if (r.getSecondaryResults().containsKey("gc.alloc.rate.norm")) {
                bytesPerOp = r.getSecondaryResults().get("gc.alloc.rate.norm").getScore();
            }

            String timeStr = Double.isNaN(timeMs) ? "OOM" : String.format(Locale.US, "%.2f", timeMs);

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
                // MUSI być odkomentowane, by zebrać dane do nowej kolumny!
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

        // Zwracamy wyniki zamiast tylko uruchamiać
        return new Runner(builder.build()).run();
    }
}