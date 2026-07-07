package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.Tree;
import treecmp.heuristics.ecr.Ecr2HeuristicRFMetric;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristicRFMetric;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Ecr2RFMetricBenchmark {

    @Param({"10", "20", "30", "50", "100", "200", "500"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Ecr2HeuristicRFMetric classicMetric;
    private Ecr2IncrementalHeuristicRFMetric incrementalMetric;

    @Setup(Level.Trial)
    public void setup() {
        // Dla 2-sECR RF operujemy na drzewach nieukorzenionych
        t1 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
        t2 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 67890L);

        classicMetric = new Ecr2HeuristicRFMetric();
        incrementalMetric = new Ecr2IncrementalHeuristicRFMetric();

        System.out.println("\n" + "=".repeat(50));
        System.out.println(" WERYFIKACJA 2-sECR (RF) DLA ROZMIARU: " + treeSize);
        System.out.println("-".repeat(50));

        // Pomiar dla wersji Inkrementalnej (z sondowaniem)
        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.getDistance(t1, t2);
        long timeIncr = System.nanoTime() - startIncr;

        System.out.printf("Incremental 2-sECR : %.2f (czas: %,d ns)%n", distIncr, timeIncr);

        // Zabezpieczenie przed zbyt długim oczekiwaniem dla bardzo dużych drzew
        if (treeSize <= 200) {
            long startClassic = System.nanoTime();
            double distClassic = classicMetric.getDistance(t1, t2);
            long timeClassic = System.nanoTime() - startClassic;

            System.out.printf("Classic 2-sECR     : %.2f (czas: %,d ns)%n", distClassic, timeClassic);

            if (Math.abs(distClassic - distIncr) < 1e-9) {
                System.out.println("** STATUS: ZGODNOŚĆ POTWIERDZONA [OK] **");
            } else {
                System.out.println("!! STATUS: ROZBIEŻNOŚĆ WYNIKÓW [BŁĄD!] !!");
            }
        } else {
            System.out.println("Classic 2-sECR     : Pominięto weryfikację (zbyt duży rozmiar na Setup)");
        }

        System.out.println("=".repeat(50) + "\n");
    }

    @Benchmark
    public double benchmarkClassicEcr2() {
        return classicMetric.getDistance(t1, t2);
    }

    @Benchmark
    public double benchmarkIncrementalEcr2() {
        return incrementalMetric.getDistance(t1, t2);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(Ecr2RFMetricBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}