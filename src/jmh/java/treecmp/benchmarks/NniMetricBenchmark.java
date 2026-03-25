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
    @Param({"10", "100", "500"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private NniHeuristicRFMetric classicMetric;
    private NniIncrementalHeuristicRFMetric incrementalMetric;

    @Setup(Level.Trial)
    public void setup() {
        // Logika wyboru drzewa na podstawie parametru treeSize
        if (treeSize == 10) {
            t1 = TestTreeFactory.randomBinaryTree(10, 12345L);
            t2 = TestTreeFactory.randomBinaryTree(10, 67890L);
        } else if (treeSize == 100) {
            t1 = TestTreeFactory.hundredLeavesTree1();
            t2 = TestTreeFactory.hundredLeavesTree2();
        } else if (treeSize == 500) {
            t1 = TestTreeFactory.fiveHundredLeavesTree1();
            t2 = TestTreeFactory.fiveHundredLeavesTree2();
        }

        classicMetric = new NniHeuristicRFMetric();
        incrementalMetric = new NniIncrementalHeuristicRFMetric();

        System.out.println("\n[SETUP] Przygotowano drzewa o rozmiarze: " + treeSize);
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