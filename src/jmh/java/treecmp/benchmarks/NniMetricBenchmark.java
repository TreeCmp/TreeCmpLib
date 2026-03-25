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

@BenchmarkMode(Mode.AverageTime) // Mierzymy średni czas wykonania jednej operacji
@OutputTimeUnit(TimeUnit.MICROSECONDS) // Wynik w mikrosekundach
@State(Scope.Benchmark) // Obiekty t1, t2 i metryki są współdzielone w ramach benchmarku
@Warmup(iterations = 3, time = 1) // 3 cykle rozgrzewki po 1 sekundzie
@Measurement(iterations = 5, time = 1) // 5 właściwych pomiarów po 1 sekundzie
@Fork(1) // Uruchomienie w 1 osobnym procesie JVM
public class NniMetricBenchmark {

    private Tree t1;
    private Tree t2;
    private NniHeuristicRFMetric classicMetric;
    private NniIncrementalHeuristicRFMetric incrementalMetric;

    @Setup(Level.Trial)
    public void setup() {
        // Inicjalizacja danych - wykonuje się raz przed pomiarami
        // Dzięki temu czas budowania drzew nie wpływa na wynik benchmarku
        t1 = TestTreeFactory.tenLeavesBinaryUnrootedTree1();
        t2 = TestTreeFactory.tenLeavesBinaryUnrootedTree2();

        classicMetric = new NniHeuristicRFMetric();
        incrementalMetric = new NniIncrementalHeuristicRFMetric();

        System.out.println("\n[SETUP] Drzewa przygotowane. Rozpoczynam pomiar dla nieukorzenionych (splitów)...");
    }

    @Benchmark
    public double benchmarkClassicNni() {
        // Pomiar klasycznej heurystyki NNI
        return classicMetric.getDistance(t1, t2);
    }

    @Benchmark
    public double benchmarkIncrementalNni() {
        // Pomiar wersji inkrementalnej
        return incrementalMetric.getDistance(t1, t2);
    }

    /**
     * Główna metoda pozwalająca na uruchomienie benchmarku bezpośrednio z IDE.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(NniMetricBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}