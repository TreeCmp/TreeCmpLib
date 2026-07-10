package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.nni.NniUtils;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class NniSingleStepBenchmark {

    @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    public String metricName;

    @Param({"10", "20", "30", "50", "70", "100"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Tree t1ForIncr;

    private TreeNeighborhoodUtils classicUtils;
    private Metric classicMetric;
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

        // =========================================================================
        // WZORZEC STRATEGII (Kompozycja):
        // Dynamiczne wstrzykiwanie silnika inkrementalnego do uniwersalnej heurystyki NNI
        // =========================================================================
        switch (metricName) {
            case "RF":
                isRooted = false; classicProtectionLimit = 250;
                classicMetric = new RFMetric();
                incrementalMetric = new NniIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true; classicProtectionLimit = 250;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new NniIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false; classicProtectionLimit = 250;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new NniIncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true; classicProtectionLimit = 250;
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new NniIncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true; classicProtectionLimit = 250;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new NniIncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false; classicProtectionLimit = 150; // O(N^5)
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new NniIncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metricName);
        }

        if (isRooted) {
            t1 = TestTreeFactory.randomRootedBinaryTree(treeSize, 12345L);
            t2 = TestTreeFactory.randomRootedBinaryTree(treeSize, 67890L);
            t1ForIncr = TestTreeFactory.randomRootedBinaryTree(treeSize, 12345L);
        } else {
            t1 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
            t2 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 67890L);
            t1ForIncr = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
        }

        assignNumbers(t1); assignNumbers(t2); assignNumbers(t1ForIncr);

        // NniUtils przyjmuje boolean "unrooted"
        classicUtils = new NniUtils(!isRooted);

        System.out.println("\n" + "=".repeat(60));
        System.out.printf(" WERYFIKACJA NNI (%s) DLA ROZMIARU: %d%n", metricName, treeSize);
        System.out.println("-".repeat(60));

        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.evaluateSingleStep(t1ForIncr, t2);
        long timeIncr = System.nanoTime() - startIncr;
        System.out.printf("Incremental 1-Step %-3s : %.2f (czas: %,d ns)%n", metricName, distIncr, timeIncr);

        if (treeSize <= classicProtectionLimit) {
            try {
                long startClassic = System.nanoTime();
                Tree[] neighbors = classicUtils.generateNeighbours(t1);
                double bestClassicDist = Double.POSITIVE_INFINITY;
                for (Tree n : neighbors) {
                    double d = classicMetric.getDistance(n, t2);
                    if (d < bestClassicDist) bestClassicDist = d;
                }
                long timeClassic = System.nanoTime() - startClassic;
                System.out.printf("Classic 1-Step %-3s     : %.2f (czas: %,d ns)%n", metricName, bestClassicDist, timeClassic);

                if (bestClassicDist == distIncr || Math.abs(bestClassicDist - distIncr) < 1e-9) {
                    System.out.println("** STATUS: ZGODNOŚĆ POTWIERDZONA [OK] **");
                } else {
                    System.out.println("!! STATUS: ROZBIEŻNOŚĆ WYNIKÓW [BŁĄD!] !!");
                }
            } catch (Throwable t) {
                System.out.println("Classic 1-Step         : [BŁĄD KLASYCZNEJ IMPLEMENTACJI]");
            }
        } else {
            System.out.printf("Classic 1-Step %-3s     : Pominięto (Limit bezpieczeństwa N<=%d)%n", metricName, classicProtectionLimit);
        }
        System.out.println("=".repeat(60) + "\n");
    }

    @Benchmark
    public double benchmarkClassicSingleStep() {
        if (treeSize > classicProtectionLimit) return Double.NaN;
        try {
            Tree[] neighbors = classicUtils.generateNeighbours(t1);
            double bestDist = Double.POSITIVE_INFINITY;
            for (Tree n : neighbors) {
                double d = classicMetric.getDistance(n, t2);
                if (d < bestDist) bestDist = d;
            }
            return bestDist;
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    @Benchmark
    public double benchmarkIncrementalSingleStep() {
        return incrementalMetric.evaluateSingleStep(t1ForIncr, t2);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(NniSingleStepBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}