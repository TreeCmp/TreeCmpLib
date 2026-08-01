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
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS) // Milliseconds, since TBR evaluates O(n^3) neighbors in 1 step
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class TbrSingleStepBenchmark {

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
        // STRATEGY PATTERN (Composition):
        // Injecting incremental engine into a universal TBR/uTBR heuristic.
        // =========================================================================
        switch (metricName) {
            case "RF":
                isRooted = false; classicProtectionLimit = 50;
                classicMetric = new RFMetric();
                incrementalMetric = new UtbrIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true; classicProtectionLimit = 50;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new TbrIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false; classicProtectionLimit = 30;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new UtbrIncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true; classicProtectionLimit = 30;
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new TbrIncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true; classicProtectionLimit = 30;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new TbrIncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false; classicProtectionLimit = 20; // Massive complexity for classic M3
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new UtbrIncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Unknown metric: " + metricName);
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

        // Choice of classic generator (Rooted vs Unrooted)
        classicUtils = isRooted ? new TbrUtils() : new UTbrUtils();

        System.out.println("\n" + "=".repeat(60));
        System.out.printf(" VERIFICATION 1-STEP TBR/uTBR (%s) FOR SIZE: %d%n", metricName, treeSize);
        System.out.println("-".repeat(60));

        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.evaluateSingleStep(t1ForIncr, t2);
        long timeIncr = System.nanoTime() - startIncr;
        System.out.printf("Incremental 1-Step %-3s : %.2f (time: %,d ms)%n", metricName, distIncr, timeIncr / 1_000_000);

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
                System.out.printf("Classic 1-Step %-3s     : %.2f (time: %,d ms)%n", metricName, bestClassicDist, timeClassic / 1_000_000);

                if (bestClassicDist == distIncr || Math.abs(bestClassicDist - distIncr) < 1e-9) {
                    System.out.println("** STATUS: MATCH CONFIRMED [OK] **");
                } else {
                    System.out.println("!! STATUS: MISMATCH DETECTED [ERROR!] !!");
                }
            } catch (Throwable t) {
                System.out.println("Classic 1-Step         : [CLASSIC IMPLEMENTATION ERROR]");
            }
        } else {
            System.out.printf("Classic 1-Step %-3s     : Skipped (Safety limit N<=%d)%n", metricName, classicProtectionLimit);
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
                .include(TbrSingleStepBenchmark.class.getSimpleName())
                .addProfiler("stack")
                // .addProfiler("gc")
                .build();
        new Runner(opt).run();
    }
}