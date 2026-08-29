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

        String[] treeSizes = Ecr2SingleStepBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        List<org.openjdk.jmh.results.RunResult> allResults = new ArrayList<>();
        String className = Ecr2SingleStepBenchmark.class.getSimpleName();

        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            if (size <= 120) {
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));
            } else if (size <= 200) {
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 300) {
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF", "RFC"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"MS", "MC", "MP"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 500) {
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RFC", "MS", "MC", "MP"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 3000) {
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else {
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF", "RFC"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            }
        }

        // Zrzut jednym wywołaniem na sam koniec!
        AbstractSingleStepBenchmark.exportToCsv("benchmark_single_step_ECR2.csv", allResults, "ECR2");
    }

    // Usunięta funkcja runJmh! Wszystko jest dziedziczone statycznie z AbstractSingleStepBenchmark!
}