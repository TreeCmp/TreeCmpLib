package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.Tree;
import treecmp.heuristics.ecr.Ecr3HeuristicMPMetric;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristicMPMetric;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Ecr3MPMetricBenchmark {

    @Param({"10", "20", "30", "50", "100"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Ecr3HeuristicMPMetric classicMetric;
    private Ecr3IncrementalHeuristicMPMetric incrementalMetric;

    @Setup(Level.Trial)
    public void setup() {
        // MP działa na drzewach UKORZENIONYCH (Rooted)
        t1 = TestTreeFactory.randomRootedBinaryTree(treeSize, 12345L);
        t2 = TestTreeFactory.randomRootedBinaryTree(treeSize, 67890L);

        classicMetric = new Ecr3HeuristicMPMetric();
        incrementalMetric = new Ecr3IncrementalHeuristicMPMetric();

        System.out.println("\n" + "=".repeat(50));
        System.out.println(" WERYFIKACJA 3-sECR (MP) DLA ROZMIARU: " + treeSize);
        System.out.println("-".repeat(50));

        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.getDistance(t1, t2);
        long timeIncr = System.nanoTime() - startIncr;

        System.out.printf("Incremental 3-sECR MP : %.2f (czas: %,d ns)%n", distIncr, timeIncr);

        // Zabezpieczenie w fazie Setup: ograniczamy klasyka do 30 liści
        if (treeSize <= 30) {
            long startClassic = System.nanoTime();
            double distClassic = classicMetric.getDistance(t1, t2);
            long timeClassic = System.nanoTime() - startClassic;

            System.out.printf("Classic 3-sECR MP     : %.2f (czas: %,d ns)%n", distClassic, timeClassic);

            if (distClassic == distIncr || Math.abs(distClassic - distIncr) < 1e-9) {
                System.out.println("** STATUS: ZGODNOŚĆ POTWIERDZONA [OK] **");
            } else {
                System.out.println("!! STATUS: ROZBIEŻNOŚĆ WYNIKÓW [BŁĄD!] !!");
            }
        } else {
            System.out.println("Classic 3-sECR MP     : Pominięto weryfikację (Eksplozja kombinatoryczna + O(n^3) = timeout)");
        }

        System.out.println("=".repeat(50) + "\n");
    }

    @Benchmark
    public double benchmarkClassicEcr3MP() {
        // ZABEZPIECZENIE: Dla drzew większych niż 30 przerywamy bieg algorytmu,
        // by nie czekać dni na wynik O(N^4).
        if (treeSize > 30) {
            return Double.NaN;
        }
        return classicMetric.getDistance(t1, t2);
    }

    @Benchmark
    public double benchmarkIncrementalEcr3MP() {
        return incrementalMetric.getDistance(t1, t2);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(Ecr3MPMetricBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}