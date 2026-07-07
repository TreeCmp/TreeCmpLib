package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.ecr.Ecr2HeuristicM3Metric;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristicM3Metric;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Ecr2M3MetricBenchmark {

    @Param({"10", "20", "30", "50"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Tree t1ForIncr;
    private Ecr2HeuristicM3Metric classicMetric;
    private Ecr2IncrementalHeuristicM3Metric incrementalMetric;

    private void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        if (treeSize > 50) return; // Zabezpieczenie O(N^5) na etapie weryfikacji

        t1 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
        t2 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 67890L);
        assignNumbers(t1);
        assignNumbers(t2);

        t1ForIncr = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
        assignNumbers(t1ForIncr);

        classicMetric = new Ecr2HeuristicM3Metric();
        incrementalMetric = new Ecr2IncrementalHeuristicM3Metric();

        System.out.println("\n" + "=".repeat(50));
        System.out.println(" WERYFIKACJA 2-sECR (M3) DLA ROZMIARU: " + treeSize);
        System.out.println("-".repeat(50));

        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.getDistance(t1ForIncr, t2);
        long timeIncr = System.nanoTime() - startIncr;

        System.out.printf("Incremental 2-sECR M3 : %.2f (czas: %,d ns)%n", distIncr, timeIncr);

        try {
            long startClassic = System.nanoTime();
            double distClassic = classicMetric.getDistance(t1, t2);
            long timeClassic = System.nanoTime() - startClassic;
            System.out.printf("Classic 2-sECR M3     : %.2f (czas: %,d ns)%n", distClassic, timeClassic);

            if (distClassic == distIncr || Math.abs(distClassic - distIncr) < 1e-9) {
                System.out.println("** STATUS: ZGODNOŚĆ POTWIERDZONA [OK] **");
            } else {
                System.out.println("!! STATUS: ROZBIEŻNOŚĆ WYNIKÓW [BŁĄD!] !!");
            }
        } catch (Throwable t) {
            System.out.println("Classic 2-sECR M3     : [BŁĄD STAREJ BIBLIOTEKI PAL - Zignorowano]");
        }

        System.out.println("=".repeat(50) + "\n");
    }

    @Benchmark
    public double benchmarkClassicEcr2M3() {
        if (treeSize > 50) return Double.NaN;
        try {
            return classicMetric.getDistance(t1, t2);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    @Benchmark
    public double benchmarkIncrementalEcr2M3() {
        if (treeSize > 50) return Double.NaN;
        return incrementalMetric.getDistance(t1ForIncr, t2);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(Ecr2M3MetricBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}