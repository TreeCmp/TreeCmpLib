package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristicM3Metric;
import treecmp.metrics.topological.MatchingTripletMetric;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Ecr3M3SingleStepBenchmark {

    @Param({"10", "20", "30", "50", "70", "100"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Tree t1ForIncr;

    private SubtreeEcr3Utils classicEcr3Utils;
    private MatchingTripletMetric classicBaseM3;
    private Ecr3IncrementalHeuristicM3Metric incrementalMetric;

    private void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        t1 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
        t2 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 67890L);
        assignNumbers(t1); assignNumbers(t2);

        t1ForIncr = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
        assignNumbers(t1ForIncr);

        classicEcr3Utils = new SubtreeEcr3Utils(true);
        classicBaseM3 = new MatchingTripletMetric();
        incrementalMetric = new Ecr3IncrementalHeuristicM3Metric();

        System.out.println("\n" + "=".repeat(50));
        System.out.println(" WERYFIKACJA 1-KROKU 3-sECR (M3) DLA N: " + treeSize);
        System.out.println("-".repeat(50));

        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.evaluateSingleStep(t1ForIncr, t2);
        long timeIncr = System.nanoTime() - startIncr;
        System.out.printf("Incremental 1-Step M3 : %.2f (czas: %,d ns)%n", distIncr, timeIncr);

        if (treeSize <= 30) {
            try {
                long startClassic = System.nanoTime();
                Tree[] neighbors = classicEcr3Utils.generateNeighbours(t1);
                double bestClassicDist = Double.POSITIVE_INFINITY;
                for (Tree n : neighbors) {
                    double d = classicBaseM3.getDistance(n, t2);
                    if (d < bestClassicDist) bestClassicDist = d;
                }
                long timeClassic = System.nanoTime() - startClassic;
                System.out.printf("Classic 1-Step M3     : %.2f (czas: %,d ns)%n", bestClassicDist, timeClassic);

                if (bestClassicDist == distIncr || Math.abs(bestClassicDist - distIncr) < 1e-9) {
                    System.out.println("** STATUS: ZGODNOŚĆ POTWIERDZONA [OK] **");
                } else {
                    System.out.println("!! STATUS: ROZBIEŻNOŚĆ WYNIKÓW [BŁĄD!] !!");
                }
            } catch (Throwable t) {
                System.out.println("Classic 1-Step M3     : [BŁĄD STAREJ BIBLIOTEKI PAL]");
            }
        } else {
            System.out.println("Classic 1-Step M3     : Pominięto weryfikację (Ochrona przed O(N^6))");
        }
        System.out.println("=".repeat(50) + "\n");
    }

    @Benchmark
    public double benchmarkClassicSingleStep() {
        if (treeSize > 30) return Double.NaN;
        try {
            Tree[] neighbors = classicEcr3Utils.generateNeighbours(t1);
            double bestDist = Double.POSITIVE_INFINITY;
            for (Tree n : neighbors) {
                double d = classicBaseM3.getDistance(n, t2);
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
                .include(Ecr3M3SingleStepBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}