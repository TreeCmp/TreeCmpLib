package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.Tree;
import treecmp.heuristics.ecr.Ecr2HeuristicMSMetric;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristicMSMetric;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Ecr2MSMetricBenchmark {

    @Param({"10", "20", "30", "50", "100", "200", "500"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Ecr2HeuristicMSMetric classicMetric;
    private Ecr2IncrementalHeuristicMSMetric incrementalMetric;

    @Setup(Level.Trial)
    public void setup() {
        // MS działa na drzewach nieukorzenionych
        t1 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
        t2 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 67890L);

        classicMetric = new Ecr2HeuristicMSMetric();
        incrementalMetric = new Ecr2IncrementalHeuristicMSMetric();

        System.out.println("\n" + "=".repeat(50));
        System.out.println(" WERYFIKACJA 2-sECR (MS) DLA ROZMIARU: " + treeSize);
        System.out.println("-".repeat(50));

        // Pomiar dla wersji Inkrementalnej (szybki LAP warm-start)
        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.getDistance(t1, t2);
        long timeIncr = System.nanoTime() - startIncr;

        System.out.printf("Incremental 2-sECR MS : %.2f (czas: %,d ns)%n", distIncr, timeIncr);

        // Zabezpieczenie przed timeoutem – LAP O(n^3) zabija klasyczną wersję
        if (treeSize <= 50) {
            long startClassic = System.nanoTime();
            double distClassic = classicMetric.getDistance(t1, t2);
            long timeClassic = System.nanoTime() - startClassic;

            System.out.printf("Classic 2-sECR MS     : %.2f (czas: %,d ns)%n", distClassic, timeClassic);

            if (distClassic == distIncr || Math.abs(distClassic - distIncr) < 1e-9) {
                System.out.println("** STATUS: ZGODNOŚĆ POTWIERDZONA [OK] **");
            } else {
                System.out.println("!! STATUS: ROZBIEŻNOŚĆ WYNIKÓW [BŁĄD!] !!");
            }
        } else {
            System.out.println("Classic 2-sECR MS     : Pominięto weryfikację (LAP od zera zbyt wolny dla drzew > 50)");
        }

        System.out.println("=".repeat(50) + "\n");
    }

    @Benchmark
    public double benchmarkClassicEcr2MS() {
        return classicMetric.getDistance(t1, t2);
    }

    @Benchmark
    public double benchmarkIncrementalEcr2MS() {
        return incrementalMetric.getDistance(t1, t2);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(Ecr2MSMetricBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}