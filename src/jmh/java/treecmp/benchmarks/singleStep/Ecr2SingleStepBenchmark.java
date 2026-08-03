package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristic;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Ecr2SingleStepBenchmark {

    @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    public String metricName;

    @Param({"10", "20", "30", "50", "80", "120"/*, "200", "300", "500", "800", "1200", "2000" */})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Tree t1ForIncr;

    private SubtreeEcr2Utils classicUtils;
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
        // Instead of dedicated classes for each metric, we inject an incremental
        // engine (e.g., M3IncrementalMetric) into a universal heuristic.
        // =========================================================================
        switch (metricName) {
            case "RF":
                isRooted = false; classicProtectionLimit = 500;
                classicMetric = new RFMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true; classicProtectionLimit = 500;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false; classicProtectionLimit = 200;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true; classicProtectionLimit = 200;
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true; classicProtectionLimit = 50;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false; classicProtectionLimit = 50;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new M3IncrementalMetric(), "M3");
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

        // SubtreeEcr2Utils accepts boolean "unrooted"
        classicUtils = new SubtreeEcr2Utils(!isRooted);

        System.out.println("\n" + "=".repeat(60));
        System.out.printf(" VERIFICATION 2-sECR (%s) FOR SIZE: %d%n", metricName, treeSize);
        System.out.println("-".repeat(60));

        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.evaluateSingleStep(t1ForIncr, t2);
        long timeIncr = System.nanoTime() - startIncr;
        System.out.printf("Incremental 1-Step %-3s : %.2f (time: %,d ns)%n", metricName, distIncr, timeIncr);

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
                System.out.printf("Classic 1-Step %-3s     : %.2f (time: %,d ns)%n", metricName, bestClassicDist, timeClassic);

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
                .include(Ecr2SingleStepBenchmark.class.getSimpleName())
                .addProfiler("stack")
                // .addProfiler("gc")
                .build();
        new Runner(opt).run();
    }
}