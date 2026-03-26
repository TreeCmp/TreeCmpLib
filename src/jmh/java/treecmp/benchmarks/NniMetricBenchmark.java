package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.Tree;
import treecmp.heuristics.nni.NniHeuristicRFMetric;
import treecmp.heuristics.nni.NniIncrementalHeuristicRFMetric;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class NniMetricBenchmark {

    /**
     * JMH automatycznie uruchomi benchmark dla każdej z tych wartości.
     * To pozwoli nam zobaczyć, jak algorytmy skalują się wraz ze wzrostem drzewa.
     */
    @Param({"4","5","6","7","8","9","10", "100", "500"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private NniHeuristicRFMetric classicMetric;
    private NniIncrementalHeuristicRFMetric incrementalMetric;

    @Setup(Level.Trial)
    public void setup() {
        t1 = TestTreeFactory.randomUnrootedTree(treeSize, 12345L);
        t2 = TestTreeFactory.randomUnrootedTree(treeSize, 67890L);

        classicMetric = new NniHeuristicRFMetric();
        incrementalMetric = new NniIncrementalHeuristicRFMetric();

        // 2. WERYFIKACJA WYNIKÓW I WYDRUK
        System.out.println("\n" + "=".repeat(50));
        System.out.println(" WERYFIKACJA NNI DLA ROZMIARU: " + treeSize);
        System.out.println("-".repeat(50));

        // Pomiar czasu dla Classic (jednorazowy w Setup)
        long startClassic = System.nanoTime();
        double distClassic = classicMetric.getDistance(t1, t2);
        long timeClassic = System.nanoTime() - startClassic;

        // Pomiar czasu dla Incremental (jednorazowy w Setup)
        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.getDistance(t1, t2);
        long timeIncr = System.nanoTime() - startIncr;

        System.out.printf("Classic NNI     : %.2f (czas: %,d ns)%n", distClassic, timeClassic);
        System.out.printf("Incremental NNI : %.2f (czas: %,d ns)%n", distIncr, timeIncr);

        // Sprawdzenie poprawności
        if (Math.abs(distClassic - distIncr) < 1e-9) {
            System.out.println("** STATUS: WYNIKI IDENTYCZNE **");
        } else {
            System.out.println("!! STATUS: RÓŻNE WYNIKI NNI !!");
        }
        System.out.println("=".repeat(50) + "\n");
    }

    @Benchmark
    public double benchmarkClassicNni() {
        return classicMetric.getDistance(t1, t2);
    }

    @Benchmark
    public double benchmarkIncrementalNni() {
        return incrementalMetric.getDistance(t1, t2);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(NniMetricBenchmark.class.getSimpleName())
                // Dodajemy parametr do raportu, żeby widzieć rozmiar drzewa w tabeli
                .build();

        new Runner(opt).run();
    }
}