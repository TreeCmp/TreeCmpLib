package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
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
import treecmp.heuristics.nni.NniClassicHeuristic;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS) // Pełny przebieg wspinaczki mierzymy w milisekundach
@State(Scope.Benchmark)
public class NniDistanceBenchmark {

    @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    public String metricName;

    // Nowy parametr: pozwala zmierzyć zarówno czyste NNI, jak i NNI z filtrem RF przy remisach
    @Param({"Pure", "RF_Tie"})
    public String variant;

    @Param({"10", "20", "30", "50", "80", "120", "200"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Tree t1ForIncr;

    private HeuristicBaseMetric classicMetric;
    private IncrementalHeuristicBaseMetric incrementalMetric;

    private int classicProtectionLimit;

    private void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        boolean isRooted = false;
        boolean useRfFilter = "RF_Tie".equals(variant);

        switch (metricName) {
            case "RF":
                isRooted = false; classicProtectionLimit = 200;
                // RF jest metryką bazową – filtr RF_Tie nie ma tu zastosowania
                if (useRfFilter) return;
                classicMetric     = new NniClassicHeuristic(new RFMetric(), isRooted, "RF");
                incrementalMetric = new NniIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true; classicProtectionLimit = 200;
                // RFC jest metryką bazową – filtr RF_Tie nie ma tu zastosowania
                if (useRfFilter) return;
                classicMetric     = new NniClassicHeuristic(new RFClusterMetric(), isRooted, "RFC");
                incrementalMetric = new NniIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false; classicProtectionLimit = 100;
                classicMetric     = useRfFilter ?
                        new NniClassicHeuristic(new MatchingSplitMetric(), new RFMetric(), isRooted, "MS_RF") :
                        new NniClassicHeuristic(new MatchingSplitMetric(), isRooted, "MS_Pure");
                incrementalMetric = useRfFilter ?
                        new NniIncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MSinc_RF") :
                        new NniIncrementalHeuristic(new MSIncrementalMetric(), "MSinc_Pure");
                break;
            case "MC":
                isRooted = true; classicProtectionLimit = 100;
                classicMetric     = useRfFilter ?
                        new NniClassicHeuristic(new MatchingClusterMetric(), new RFClusterMetric(), isRooted, "MC_RF") :
                        new NniClassicHeuristic(new MatchingClusterMetric(), isRooted, "MC_Pure");
                incrementalMetric = useRfFilter ?
                        new NniIncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MCinc_RF") :
                        new NniIncrementalHeuristic(new MCIncrementalMetric(), "MCinc_Pure");
                break;
            case "MP":
                isRooted = true; classicProtectionLimit = 100;
                classicMetric     = useRfFilter ?
                        new NniClassicHeuristic(new MatchingPairMetric(), new RFClusterMetric(), isRooted, "MP_RF") :
                        new NniClassicHeuristic(new MatchingPairMetric(), isRooted, "MP_Pure");
                incrementalMetric = useRfFilter ?
                        new NniIncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MPinc_RF") :
                        new NniIncrementalHeuristic(new MPIncrementalMetric(), "MPinc_Pure");
                break;
            case "M3":
                isRooted = false; classicProtectionLimit = 50;
                classicMetric     = useRfFilter ?
                        new NniClassicHeuristic(new MatchingTripletMetric(), new RFMetric(), isRooted, "M3_RF") :
                        new NniClassicHeuristic(new MatchingTripletMetric(), isRooted, "M3_Pure");
                incrementalMetric = useRfFilter ?
                        new NniIncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3inc_RF") :
                        new NniIncrementalHeuristic(new M3IncrementalMetric(), "M3inc_Pure");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metricName);
        }

        // 1. Wczytujemy realne drzewa z datasetu (dokładnie te same co w testach makro!)
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

        // Lekka weryfikacja w konsoli podczas setupu
        if (incrementalMetric != null) {
            double distIncr = incrementalMetric.getDistance(new SimpleTree(t1ForIncr), t2);
            if (treeSize <= classicProtectionLimit && classicMetric != null) {
                double distClassic = classicMetric.getDistance(new SimpleTree(t1), t2);
                if (Math.abs(distClassic - distIncr) > 1e-6) {
                    System.out.printf(" [Uwaga: Inne minimum lokalne dla %s (%s) N=%d | Class=%.2f vs Incr=%.2f]%n",
                            metricName, variant, treeSize, distClassic, distIncr);
                }
            }
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
        if (treeSize > classicProtectionLimit || classicMetric == null) return Double.NaN;
        try {
            // KLUCZOWE: Przekazujemy kopię (new SimpleTree), aby wspinaczka nie mutowała t1 na stałe!
            return classicMetric.getDistance(new SimpleTree(t1), t2);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    @Benchmark
    public double benchmarkIncrementalFullRun() {
        if (incrementalMetric == null) return Double.NaN;
        // KLUCZOWE: Przekazujemy kopię (new SimpleTree), aby wspinaczka zaczynała się od zera w każdej iteracji JMH!
        return incrementalMetric.getDistance(new SimpleTree(t1ForIncr), t2);
    }

    public static void main(String[] args) throws Exception {
        // Możesz przełączać między szybkimi testami a pełnym pomiarem
        boolean quickEstimate = true;

        String[] treeSizes = NniDistanceBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            // 1. N <= 30: Dla bardzo małych drzew klasyczny algorytm daje radę we wszystkich metrykach
            if (size <= 30) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"},
                        NniDistanceBenchmark.class.getSimpleName(),
                        quickEstimate);
            }
            // 2. N <= 50: Odcinamy Classic dla najcięższego M3. M3 leci tylko w Incremental.
            else if (size <= 50) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP"},
                        NniDistanceBenchmark.class.getSimpleName(),
                        quickEstimate);
                runJmh(sizeStr,
                        new String[]{"M3"},
                        NniDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun",
                        quickEstimate);
            }
            // 3. N <= 80: Odcinamy Classic również dla MC i MP.
            else if (size <= 80) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS"},
                        NniDistanceBenchmark.class.getSimpleName(),
                        quickEstimate);
                runJmh(sizeStr,
                        new String[]{"MC", "MP", "M3"},
                        NniDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun",
                        quickEstimate);
            }
            // 4. N <= 200: Dla tych rozmiarów Classic wyrabia się tylko w prostych metrykach podziałowych (RF, RFC)
            else if (size <= 200) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC"},
                        NniDistanceBenchmark.class.getSimpleName(),
                        quickEstimate);
                runJmh(sizeStr,
                        new String[]{"MS", "MC", "MP", "M3"},
                        NniDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun",
                        quickEstimate);
            }
            // 5. N > 200: Przy tak dużych drzewach jakakolwiek próba pełnego zejścia klasycznego
            // zajęłaby wieki. Puszczamy WYŁĄCZNIE benchmarkIncrementalFullRun dla wszystkich metryk.
            else {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"},
                        NniDistanceBenchmark.class.getSimpleName() + ".benchmarkIncrementalFullRun",
                        quickEstimate);
            }
        }
    }

    private static void runJmh(String sizeStr, String[] metrics, String includeRegex, boolean quickEstimate) throws Exception {
        ChainedOptionsBuilder builder = new OptionsBuilder()
                .include(includeRegex)
                .param("treeSize", sizeStr)
                .param("metricName", metrics)
                // W NniDistanceBenchmark rozmiary sterty mogą być mniejsze niż w SPR, ale zachowujemy spójność
                .jvmArgs("-Xms4g", "-Xmx16g");

        if (quickEstimate) {
            builder.warmupIterations(1)
                    .warmupTime(TimeValue.seconds(1))
                    .measurementIterations(2) // Utrzymujemy 2 iteracje tak jak w Twoim logu
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

        new Runner(builder.build()).run();
    }
}