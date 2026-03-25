package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

// Importujemy klasę Tree z biblioteki PAL, której używasz
import pal.tree.Tree;
// Importujemy Twoją klasę (upewnij się, że używasz konkretnej implementacji, bo SprIncrementalHeuristicMetric jest abstrakcyjna)
import treecmp.heuristics.spr.SprIncrementalHeuristicMetric;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS) // Algorytmy SPR są zazwyczaj szybkie, więc mikrosekundy będą dokładniejsze
public class AlgorithmComparison {

    private Tree testTree1;
    private Tree testTree2;
    private SprIncrementalHeuristicMetric oldAlgorithm;
    private SprIncrementalHeuristicMetric newAlgorithm;

    @Setup
    public void setup() {
        // 1. Tutaj zainicjalizuj swoje drzewa (np. wczytaj z pliku lub wygeneruj)
        // testTree1 = ...

        // 2. Zainicjalizuj obie wersje algorytmu
        // oldAlgorithm = new TwojaStaraImplementacja();
        // newAlgorithm = new TwojaNowaSzybszaImplementacja();
    }

    @Benchmark
    public void benchmarkOldVersion() {
        // Wywołaj metodę obliczeniową starego algorytmu
        // oldAlgorithm.getDistance(testTree1, testTree2);
    }

    @Benchmark
    public void benchmarkNewVersion() {
        // Wywołaj metodę obliczeniową nowego algorytmu
        // newAlgorithm.getDistance(testTree1, testTree2);
    }
}