package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.RunResult;

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
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

import static treecmp.benchmarks.singleStep.AbstractSingleStepBenchmark.isQuickEstimate;
import static treecmp.benchmarks.singleStep.AbstractSingleStepBenchmark.loadTrees;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class TbrSingleStepBenchmark {

    @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    public String metricName;

    // Rozszerzona lista rozmiarów o 800 i 1200 dla RFC
    @Param({"10", "20", "30", "50", "80", "120", "200", "300", "500", "800", "1200"})
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
    public void setup() {
        initMetricsAndTrees(metricName, treeSize);
    }

    private void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            case "RF":
                isRooted = false;
                classicMetric = new RFMetric();
                incrementalMetric = new UtbrIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new TbrIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new UtbrIncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                treecmp.config.IOSettings.getIOSettings().setOptMsMcByRf(true);
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new TbrIncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new TbrIncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new UtbrIncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Unknown metric: " + metric);
        }

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

        classicUtils = isRooted ? new TbrUtils() : new UTbrUtils();
    }

    private File findDatasetFile(int size, boolean isRooted) {
        File dir = new File("datasets");
        if (!dir.exists() || !dir.isDirectory()) return null;

        String prefix = "n" + size + "y";
        String suffix = (isRooted ? "rb" : "ub") + ".newick";

        File[] matchingFiles = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(suffix));
        return (matchingFiles != null && matchingFiles.length > 0) ? matchingFiles[0] : null;
    }

    private double evaluateClassicBestDist() {
        final double[] bestDist = {Double.POSITIVE_INFINITY};

        if (classicUtils != null) {
            classicUtils.forEachNeighbour(t1, neighbor -> {
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

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();
        String className = TbrSingleStepBenchmark.class.getSimpleName();
        List<RunResult> allResults = new ArrayList<>();

        int[] sizes = {10, 20, 30, 50, 80, 120, 200, 300, 500, 800, 1200};

        for (int size : sizes) {
            String sizeStr = String.valueOf(size);

            if (size <= 20) {
                // N <= 20: Pełny przekrój Classic + Incremental dla wszystkich 6 metryk
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));
            } else if (size <= 30) {
                // N = 30: Classic + Inc dla RF, RFC, MS, MC, MP; M3 wyłącznie Incremental
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 50) {
                // N = 50: Dołożono MS Classic (+16 s). Pełny Classic dla 5 metryk; M3 Incr
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 80) {
                // N = 80: Dołożono MC i MP Classic (+4 min). Classic dla RF, RFC, MC, MP; MS i M3 Incr
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF", "RFC", "MC", "MP"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"MS", "M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 120) {
                // N = 120: Pełny zestaw 6 metryk Incremental
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 200) {
                // N = 200: Pełny zestaw 6 metryk Incremental (w tym M3 ~15 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 300) {
                // N = 300: RF, RFC, MC, MP Incremental (~9 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF", "RFC", "MC", "MP"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 500) {
                // N = 500: Dołożono MP Incr (~6 min) obok RF i RFC
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RF", "RFC", "MP"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 800) {
                // N = 800: NOWOŚĆ – najszybsze RFC Incr (~2.5 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RFC"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else {
                // N = 1200: NOWOŚĆ – najszybsze RFC Incr (~7.5 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr, new String[]{"RFC"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            }
        }

        AbstractSingleStepBenchmark.exportToCsv("benchmark_single_step_TBR.csv", allResults, "TBR");
    }
}