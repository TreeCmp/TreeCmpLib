package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Bazowa klasa stanu i silnika dla wszystkich eksperymentów Single-Step (NNI, SPR, TBR, ECR).
 * Hermetyzuje parametry JMH, wczytywanie datasetów oraz obie metody benchmarkowe.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public abstract class AbstractSingleStepBenchmark {

    @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    public String metricName;

    @Param({"10", "20", "30", "50", "80", "120", "200", "300", "500", "800", "1200", "2000", "3000", "5000", "8000", "12000", "20000", "30000", "50000", "80000", "120000"})
    public int treeSize;

    protected Tree t1;
    protected Tree t2;
    protected Tree t1ForIncr;

    protected Metric classicMetric;
    protected IncrementalHeuristicBaseMetric incrementalMetric;

    public static boolean isQuickEstimate() {
        return Boolean.parseBoolean(System.getProperty("quick", "false"));
    }

    // --- Cykl życia JMH ---

    @Setup(Level.Trial)
    public void setup() {
        initMetricsAndTrees(metricName, treeSize);
    }

    /**
     * Inicjalizacja metryk i struktur właściwych dla danego sąsiedztwa (np. NniUtils, SprUtils).
     */
    protected abstract void initMetricsAndTrees(String metric, int size);

    /**
     * Klasyczne przeszukanie sąsiedztwa – implementowane w klasach konkretnych.
     */
    protected abstract double evaluateClassicBestDist() throws Exception;

    // --- Wspólne metody benchmarkowe ---

    @Benchmark
    public double benchmarkClassicSingleStep() {
        try {
            return evaluateClassicBestDist();
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    @Benchmark
    public double benchmarkIncrementalSingleStep() {
        return incrementalMetric.evaluateSingleStep(t1ForIncr, t2);
    }

    // --- Wspólna logika wczytywania i generowania drzew ---

    protected void loadOrGenerateTrees(int size, boolean isRooted) {
        File datasetFile = findDatasetFile(size, isRooted);
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
            System.out.println("OSTRZEŻENIE: Brak odpowiedniego pliku w datasets/ dla N=" + size + " (" + (isRooted ? "rb" : "ub") + "). Używam TestTreeFactory.");
            if (isRooted) {
                t1 = TestTreeFactory.randomRootedBinaryTree(size, 12345L);
                t2 = TestTreeFactory.randomRootedBinaryTree(size, 67890L);
                t1ForIncr = TestTreeFactory.randomRootedBinaryTree(size, 12345L);
            } else {
                t1 = TestTreeFactory.randomUnrootedBinaryTree(size, 12345L);
                t2 = TestTreeFactory.randomUnrootedBinaryTree(size, 67890L);
                t1ForIncr = TestTreeFactory.randomUnrootedBinaryTree(size, 12345L);
            }
        }

        assignNumbers(t1);
        assignNumbers(t2);
        assignNumbers(t1ForIncr);
    }

    protected static void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    protected File findDatasetFile(int size, boolean isRooted) {
        File dir = new File("datasets");
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }

        String prefix = "n" + size + "y";
        String suffix = (isRooted ? "rb" : "ub") + ".newick";

        File[] matchingFiles = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(suffix));
        if (matchingFiles != null && matchingFiles.length > 0) {
            return matchingFiles[0];
        }
        return null;
    }

    protected static List<Tree> loadTrees(String filename, int limit) {
        List<Tree> trees = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null && trees.size() < limit) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    Tree t = TreeCreator.getTreeFromString(line);
                    if (t != null) {
                        trees.add(t);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Błąd podczas wczytywania z pliku " + filename + ": " + e.getMessage());
        }
        return trees;
    }

    // --- Uruchamianie JMH i eksport wyników ---

    public static List<RunResult> runJmh(String sizeStr, String[] metrics, String includeRegex, boolean quickEstimate) throws Exception {
        ChainedOptionsBuilder builder = new OptionsBuilder()
                .include(includeRegex)
                .param("treeSize", sizeStr)
                .param("metricName", metrics)
                .addProfiler("gc");

        if (quickEstimate) {
            builder.warmupIterations(1)
                    .warmupTime(TimeValue.seconds(1))
                    .measurementIterations(1)
                    .measurementTime(TimeValue.seconds(1))
                    .forks(1)
                    .warmupForks(0);
        } else {
            builder.warmupIterations(5)
                    .warmupTime(TimeValue.seconds(2))
                    .measurementIterations(5)
                    .measurementTime(TimeValue.seconds(2))
                    .forks(2)
                    .warmupForks(1);
        }

        // new ArrayList<>() konwertuje Collection<RunResult> z JMH na List<RunResult>
        return new ArrayList<>(new Runner(builder.build()).run());
    }

    /**
     * Przeciążenie runJmh pobierające tryb automatycznie z właściwości systemowej -Dquick.
     */
    public static List<RunResult> runJmh(String treeSize, String[] metrics, String benchmarkTarget) throws Exception {
        return runJmh(treeSize, metrics, benchmarkTarget, isQuickEstimate());
    }

    public static void exportToCsv(String filename, List<RunResult> results, String neighborhood) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("Neighborhood,Size,IsRooted,Metric,Variant,TimeUs,AllocBytesPerOp");

            for (RunResult r : results) {
                String metric = r.getParams().getParam("metricName");
                String size = r.getParams().getParam("treeSize");
                String benchmark = r.getParams().getBenchmark();

                String variant = benchmark.contains("Incremental") ? "2. Incremental" : "1. Classic";
                boolean isRooted = metric.equals("RFC") || metric.equals("MC") || metric.equals("MP");
                double timeUs = r.getPrimaryResult().getScore();

                double allocBytes = Double.NaN;
                for (Result sec : r.getSecondaryResults().values()) {
                    if (sec.getLabel().equals("gc.alloc.rate.norm")) {
                        allocBytes = sec.getScore();
                        break;
                    }
                }

                pw.printf(Locale.US, "%s,%s,%b,%s,%s,%.4f,%.2f%n",
                        neighborhood, size, isRooted, metric, variant, timeUs, allocBytes);
            }
            System.out.println("Zapisano wyniki Single-Step (" + neighborhood + ") do pliku: " + filename);
        }
    }
}