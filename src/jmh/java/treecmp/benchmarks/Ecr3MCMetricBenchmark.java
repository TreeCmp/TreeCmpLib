package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.Tree;
import treecmp.heuristics.ecr.Ecr3HeuristicMCMetric;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristicMCMetric;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Ecr3MCMetricBenchmark {

    @Param({"10", "20", "30", "50", "100", "200"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Ecr3HeuristicMCMetric classicMetric;
    private Ecr3IncrementalHeuristicMCMetric incrementalMetric;

    @Setup(Level.Trial)
    public void setup() {
        // MC działa na drzewach UKORZENIONYCH (Rooted)
        t1 = TestTreeFactory.randomRootedBinaryTree(treeSize, 12345L);
        t2 = TestTreeFactory.randomRootedBinaryTree(treeSize, 67890L);

        classicMetric = new Ecr3HeuristicMCMetric();
        incrementalMetric = new Ecr3IncrementalHeuristicMCMetric();

        System.out.println("\n" + "=".repeat(50));
        System.out.println(" WERYFIKACJA 3-sECR (MC) DLA ROZMIARU: " + treeSize);
        System.out.println("-".repeat(50));

        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.getDistance(t1, t2);
        long timeIncr = System.nanoTime() - startIncr;

        System.out.printf("Incremental 3-sECR MC : %.2f (czas: %,d ns)%n", distIncr, timeIncr);

        // Zabezpieczenie w fazie Setup: ograniczamy klasyka do 30 liści (inaczej tu by utknął)
        if (treeSize <= 30) {
            long startClassic = System.nanoTime();
            double distClassic = classicMetric.getDistance(t1, t2);
            long timeClassic = System.nanoTime() - startClassic;

            System.out.printf("Classic 3-sECR MC     : %.2f (czas: %,d ns)%n", distClassic, timeClassic);

            if (distClassic == distIncr || Math.abs(distClassic - distIncr) < 1e-9) {
                System.out.println("** STATUS: ZGODNOŚĆ POTWIERDZONA [OK] **");
            } else {
                System.out.println("!! STATUS: ROZBIEŻNOŚĆ WYNIKÓW [BŁĄD!] !!");
            }
        } else {
            System.out.println("Classic 3-sECR MC     : Pominięto weryfikację (Eksplozja kombinatoryczna + O(n^3) = timeout)");
        }

        System.out.println("=".repeat(50) + "\n");
    }

    @Benchmark
    public double benchmarkClassicEcr3MC() {
        // ZABEZPIECZENIE: Dla drzew większych niż 30 przerywamy bieg algorytmu,
        // by nie czekać dni na wynik O(n^4).
        if (treeSize > 30) {
            return Double.NaN;
        }
        return classicMetric.getDistance(t1, t2);
    }

    @Benchmark
    public double benchmarkIncrementalEcr3MC() {
        // Wersja inkrementalna leci zawsze, dla każdego rozmiaru drzewa!
        return incrementalMetric.getDistance(t1, t2);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(Ecr3MCMetricBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}