package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
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
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class SprSingleStepBenchmark {

    @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    public String metricName;

    @Param({"10", "20", "30", "50", "80", "120", "200", "300", "500", "800", "1200", "2000", "3000", "5000", "8000", "12000", "20000", "30000", "50000", "80000", "120000"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Tree t1ForIncr;

    private TreeNeighborhoodUtils classicUtils;
    private Metric classicMetric;
    private IncrementalHeuristicBaseMetric incrementalMetric;

    private static void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @Setup(Level.Trial)
    public void setup() throws Exception {
        initMetricsAndTrees(metricName, treeSize);

        // Lekka weryfikacja poprawności TYLKO dla małych drzew (N <= 30),
        // aby nie blokować metody setup() przed startem benchmarku!
        if (treeSize <= 30) {
            double distIncr = incrementalMetric.evaluateSingleStep(t1ForIncr, t2);
            double bestClassicDist = evaluateClassicBestDist();
            boolean isMatch = (bestClassicDist == distIncr || Math.abs(bestClassicDist - distIncr) < 1e-9);
            if (!isMatch) {
                throw new IllegalStateException(String.format(
                        "Mismatch in SPR/uSPR (%s) for size %d! Classic=%.4f vs Incr=%.4f",
                        metricName, treeSize, bestClassicDist, distIncr
                ));
            }
        }
    }

    private void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            case "RF":
                isRooted = false;
                classicMetric = new RFMetric();
                incrementalMetric = new UsprIncrementalHeuristicMetric(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                treecmp.config.IOSettings.getIOSettings().setOptMsMcByRf(true);
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Unknown metric: " + metric);
        }

        // 1. Próba szybkiego wczytania drzew z plików datasetu
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

        // 2. Fallback do generatora losowego
        if (!loadedFromFile) {
            System.out.println("OSTRZEŻENIE: Brak pliku w datasets/ dla N=" + size + " (" + (isRooted ? "rb" : "ub") + "). Używam TestTreeFactory.");
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

        classicUtils = isRooted ? new SprUtils() : new UsprUtils();
    }

    private File findDatasetFile(int size, boolean isRooted) {
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

    private static List<Tree> loadTrees(String filename, int limit) {
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
            System.err.println("Błąd wczytywania z pliku " + filename + ": " + e.getMessage());
        }
        return trees;
    }

    private double evaluateClassicBestDist() {
        final double[] bestDist = {Double.POSITIVE_INFINITY};

        if (classicUtils instanceof SprUtils) {
            ((SprUtils) classicUtils).forEachSprTree(t1, neighbor -> {
                double d = 0;
                try {
                    if (neighbor instanceof SimpleTree) {
                        ((SimpleTree) neighbor).createNodeList();
                    }
                    d = classicMetric.getDistance(neighbor, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }
                if (d < bestDist[0]) bestDist[0] = d;
            });
        } else if (classicUtils instanceof UsprUtils) {
            ((UsprUtils) classicUtils).forEachUsprTree(t1, neighbor -> {
                double d = 0;
                try {
                    if (neighbor instanceof SimpleTree) {
                        ((SimpleTree) neighbor).createNodeList();
                    }
                    d = classicMetric.getDistance(neighbor, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }
                if (d < bestDist[0]) bestDist[0] = d;
            });
        }
        return bestDist[0];
    }

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

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = true;

        String[] treeSizes = SprSingleStepBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            // 1. N <= 50: Pełny pakiet (RF, RFC, MS, MC, MP, M3) jako Classic + Incremental
            if (size <= 50) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"},
                        SprSingleStepBenchmark.class.getSimpleName(),
                        quickEstimate);
            }
            // 2. N <= 80: Odcinamy M3 Classic; RF, RFC, MS, MC, MP jako Classic + Incr; M3 tylko Incr
            else if (size <= 80) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP"},
                        SprSingleStepBenchmark.class.getSimpleName(),
                        quickEstimate);
                runJmh(sizeStr,
                        new String[]{"M3"},
                        SprSingleStepBenchmark.class.getSimpleName() + ".benchmarkIncrementalSingleStep",
                        quickEstimate);
            }
            // 3. N <= 120: Odcinamy MS, MC, MP Classic oraz M3 Incr;
            // RF i RFC jako Classic + Incr; MS, MC, MP tylko Incr
            else if (size <= 120) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC"},
                        SprSingleStepBenchmark.class.getSimpleName(),
                        quickEstimate);
                runJmh(sizeStr,
                        new String[]{"MS", "MC", "MP"},
                        SprSingleStepBenchmark.class.getSimpleName() + ".benchmarkIncrementalSingleStep",
                        quickEstimate);
            }
            // 4. N <= 200: Odcinamy RFC Classic; RF jako Classic + Incr;
            // RFC, MS, MC, MP jako Incr
            else if (size <= 200) {
                runJmh(sizeStr,
                        new String[]{"RF"},
                        SprSingleStepBenchmark.class.getSimpleName(),
                        quickEstimate);
                runJmh(sizeStr,
                        new String[]{"RFC", "MS", "MC", "MP"},
                        SprSingleStepBenchmark.class.getSimpleName() + ".benchmarkIncrementalSingleStep",
                        quickEstimate);
            }
            // 5. N <= 300: KONIEC z wersjami Classic; RF, RFC, MS, MC, MP jako Incremental
            else if (size <= 300) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP"},
                        SprSingleStepBenchmark.class.getSimpleName() + ".benchmarkIncrementalSingleStep",
                        quickEstimate);
            }
            // 6. N <= 500: Odcinamy MP Incr; RF, RFC, MS, MC jako Incremental
            // (MS i MC zajmą tu ok. 1,3 - 2,5 minuty na test)
            else if (size <= 500) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC"},
                        SprSingleStepBenchmark.class.getSimpleName() + ".benchmarkIncrementalSingleStep",
                        quickEstimate);
            }
            // 7. N <= 1200: Odcinamy MS i MC Incr (trwałyby > 25 min!); zostają RF i RFC Incremental
            else if (size <= 1200) {
                runJmh(sizeStr,
                        new String[]{"RF", "RFC"},
                        SprSingleStepBenchmark.class.getSimpleName() + ".benchmarkIncrementalSingleStep",
                        quickEstimate);
            }
            // 8. N > 1200: Odcinamy RF Incr (trwałby > 4 min przy N=2000);
            // Zostaje niesamowicie szybka RFC Incremental (skaluje się do wielkich drzew w kilka sekund!)
            else if (size <= 12000) {
                runJmh(sizeStr,
                        new String[]{"RFC"},
                        SprSingleStepBenchmark.class.getSimpleName() + ".benchmarkIncrementalSingleStep",
                        quickEstimate);
            }
        }
    }

    private static void runJmh(String sizeStr, String[] metrics, String includeRegex, boolean quickEstimate) throws Exception {
        ChainedOptionsBuilder builder = new OptionsBuilder()
                .include(includeRegex)
                .param("treeSize", sizeStr)
                .param("metricName", metrics)
                .jvmArgs("-Xms4g", "-Xmx16g")
                // .addProfiler("stack")
                ;

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

        new Runner(builder.build()).run();
    }
}