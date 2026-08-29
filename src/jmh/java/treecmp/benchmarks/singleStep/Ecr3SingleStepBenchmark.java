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
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristic;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class Ecr3SingleStepBenchmark {

    @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    public String metricName;

    @Param({"10", "20", "30", "50", "80", "120", "200", "300", "500", "800", "1200", "2000", "3000", "5000", "8000", "12000", "20000", "30000", "50000", "80000", "120000"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Tree t1ForIncr;

    private SubtreeEcr3Utils classicUtils;
    private Metric classicMetric;
    private IncrementalHeuristicBaseMetric incrementalMetric;

    private static void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        initMetricsAndTrees(metricName, treeSize);
    }

    private void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            case "RF":
                isRooted = false;
                classicMetric = new RFMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Unknown metric: " + metric);
        }

        // 1. Szybkie wczytywanie gotowych drzew z datasetu
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

        // 2. Fallback do losowego generowania
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

        classicUtils = new SubtreeEcr3Utils(!isRooted);
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

    private double evaluateClassicBestDist() throws Exception {
        // Zawijamy w tablicę, by móc modyfikować zmienną z wnętrza wyrażenia lambda
        final double[] bestDist = { Double.POSITIVE_INFINITY };

        // Używamy uniwersalnego iteratora z klasy bazowej (obsłuży NNI, SPR i uSPR)
        classicUtils.forEachNeighbour(t1, neighbor -> {
            try {
                // Zabezpieczenie: odświeżenie indeksów węzłów, jeśli to SimpleTree
                if (neighbor instanceof pal.tree.SimpleTree) {
                    ((pal.tree.SimpleTree) neighbor).createNodeList();
                }

                // Obliczamy dystans dla nowo wygenerowanego sąsiada
                double d = classicMetric.getDistance(neighbor, t2);

                // Aktualizujemy najlepszy wynik
                if (d < bestDist[0]) {
                    bestDist[0] = d;
                }
            } catch (Exception e) {
                // Wyrażenia lambda nie mogą rzucać checked exceptions, więc rzutujemy na RuntimeException
                throw new RuntimeException("Błąd podczas ewaluacji dystansu w sąsiedztwie", e);
            }
        });

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