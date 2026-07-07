package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.Tree;
import treecmp.heuristics.ecr.Ecr3HeuristicMSMetric;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristicMSMetric;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Ecr3MSMetricBenchmark {

    // Przywróciliśmy 500, bo wersja inkrementalna poradzi sobie z tym bez problemu!
    @Param({"10", "20", "30", "50", "100", "200"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Ecr3HeuristicMSMetric classicMetric;
    private Ecr3IncrementalHeuristicMSMetric incrementalMetric;

    @Setup(Level.Trial)
    public void setup() {
        t1 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
        t2 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 67890L);

        classicMetric = new Ecr3HeuristicMSMetric();
        incrementalMetric = new Ecr3IncrementalHeuristicMSMetric();

        System.out.println("\n" + "=".repeat(50));
        System.out.println(" WERYFIKACJA 3-sECR (MS) DLA ROZMIARU: " + treeSize);
        System.out.println("-".repeat(50));

        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.getDistance(t1, t2);
        long timeIncr = System.nanoTime() - startIncr;

        System.out.printf("Incremental 3-sECR MS : %.2f (czas: %,d ns)%n", distIncr, timeIncr);

        // Zabezpieczenie w fazie Setup: ograniczamy klasyka do 30 liści (inaczej tu by utknął)
        if (treeSize <= 30) {
            long startClassic = System.nanoTime();
            double distClassic = classicMetric.getDistance(t1, t2);
            long timeClassic = System.nanoTime() - startClassic;

            System.out.printf("Classic 3-sECR MS     : %.2f (czas: %,d ns)%n", distClassic, timeClassic);

            if (distClassic == distIncr || Math.abs(distClassic - distIncr) < 1e-9) {
                System.out.println("** STATUS: ZGODNOŚĆ POTWIERDZONA [OK] **");
            } else {
                System.out.println("!! STATUS: ROZBIEŻNOŚĆ WYNIKÓW [BŁĄD!] !!");
            }
        } else {
            System.out.println("Classic 3-sECR MS     : Pominięto weryfikację (Eksplozja kombinatoryczna + O(n^3) = timeout)");
        }

        System.out.println("=".repeat(50) + "\n");
    }

    @Benchmark
    public double benchmarkClassicEcr3MS() {
        // ZABEZPIECZENIE: Dla drzew większych niż 30 przerywamy bieg algorytmu,
        // by nie czekać dni lub tygodni na wynik O(n^4).
        if (treeSize > 50) {
            return Double.NaN;
        }
        return classicMetric.getDistance(t1, t2);
    }

    @Benchmark
    public double benchmarkIncrementalEcr3MS() {
        // Wersja inkrementalna leci zawsze, dla każdego rozmiaru drzewa!
        return incrementalMetric.getDistance(t1, t2);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(Ecr3MSMetricBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}