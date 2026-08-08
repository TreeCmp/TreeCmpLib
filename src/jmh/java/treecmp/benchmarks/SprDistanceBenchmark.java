package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
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

    private void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        boolean isRooted = false;

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

        // 1. Wczytujemy drzewa z plików (zgodnie z metodologią innych benchmarków)
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

        // 2. Fallback do generatora losowego, jeśli brak pliku
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

        // Lekka weryfikacja (tylko dla bardzo małych drzew, by nie wieszać Setupu na godziny!)
        if (treeSize <= 30) {
            System.out.println("\n" + "=".repeat(60));
            System.out.printf(" WERYFIKACJA PEŁNEGO PRZEBIEGU SPR/uSPR (%s) N=%d%n", metricName, treeSize);
            System.out.println("-".repeat(60));

            double distIncr = incrementalMetric.getDistance(new SimpleTree(t1ForIncr), t2);
            double distClassic = classicMetric.getDistance(new SimpleTree(t1), t2);

            System.out.printf("Classic     : %.2f%n", distClassic);
            System.out.printf("Incremental : %.2f%n", distIncr);

            if (Math.abs(distClassic - distIncr) < 1e-6) {
                System.out.println("** STATUS: ZGODNOŚĆ POTWIERDZONA [OK] **");
            } else {
                System.out.println("!! STATUS: ROZBIEŻNOŚĆ WYNIKÓW (Możliwe inne lokalne minimum) !!");
            }
            System.out.println("=".repeat(60) + "\n");
        }
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
        // ZABEZPIECZENIE: Pełny krok trwale mutuje topologię.
        // Koniecznie wysyłamy sklonowane drzewo (new SimpleTree), by każda iteracja JMH szła od początku!
        return classicMetric.getDistance(new SimpleTree(t1), t2);
    }

    @Benchmark
    public double benchmarkIncrementalFullRun() {
        return incrementalMetric.getDistance(new SimpleTree(t1ForIncr), t2);
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = true;

        String[] treeSizes = SprDistanceBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            // 1. N <= 20: Bardzo małe drzewa - puszczamy pełny pakiet Classic
            if (size <= 20) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"},
                        SprDistanceBenchmark.class.getSimpleName(),
                        quickEstimate);
            }
            // 2. N <= 30: Dla SPR wariant Classic na MP i M3 staje się bardzo powolny
            else if (size <= 30) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC"},
                        SprDistanceBenchmark.class.getSimpleName() + ".benchmarkClassicFullRun",
                        quickEstimate);
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"},
                        SprDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun",
                        quickEstimate);
            }
            // 3. N <= 50: Classic SPR dla MS i MC to już ryzyko timeoutów. Tylko RF i RFC.
            else if (size <= 50) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC"},
                        SprDistanceBenchmark.class.getSimpleName() + ".benchmarkClassicFullRun",
                        quickEstimate);
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"},
                        SprDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun",
                        quickEstimate);
            }
            // 4. N <= 120: Inkremental SPR radzi sobie z każdą metryką. Classic już nigdzie nie zdąży.
            else if (size <= 120) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"},
                        SprDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun",
                        quickEstimate);
            }
            // 5. N <= 300: Zostawiamy szybsze metryki inkrementalne. M3 SPR Incr może być bardzo wolne.
            else if (size <= 300) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP"},
                        SprDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun",
                        quickEstimate);
            }
            // 6. N > 300: Testujemy skalowanie tylko szybkich RF i RFC Incremental
            else {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC"},
                        SprDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun",
                        quickEstimate);
            }
        }
    }

    private static void runJmh(String sizeStr, String[] metrics, String includeRegex, boolean quickEstimate) throws Exception {
        ChainedOptionsBuilder builder = new OptionsBuilder()
                .include(includeRegex)
                .param("treeSize", sizeStr)
                .param("metricName", metrics)
                .jvmArgs("-Xms4g", "-Xmx16g");

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

        new Runner(builder.build()).run();
    }
}