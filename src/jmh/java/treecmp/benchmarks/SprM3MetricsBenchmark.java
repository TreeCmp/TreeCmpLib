package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.util.TestTreeFactory;

import treecmp.heuristics.spr.UsprHeuristicM3Metric;
import treecmp.heuristics.spr.acc.UsprHeuristicM3AcceleratedMetric;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 1)
@Fork(1)
public class SprMatchingM3MetricsBenchmark {

    @Param({"10", "20", "30", "40", "50"})
    public int treeSize;

    private Tree unrootedT1;
    private Tree unrootedT2;

    private UsprHeuristicM3Metric classicSprMT;
    private UsprHeuristicM3AcceleratedMetric acceleratedSprMT;

    @Setup(Level.Trial)
    public void setup() {
        this.unrootedT1 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
        this.unrootedT2 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 67890L);

        this.classicSprMT = new UsprHeuristicM3Metric();
        this.acceleratedSprMT = new UsprHeuristicM3AcceleratedMetric();

        System.out.println("\n" + "=".repeat(50));
        System.out.println("WERYFIKACJA HEURYSTYKI USPR DLA ROZMIARU: " + treeSize);

        long startAcc = System.currentTimeMillis();
        double distAcc = acceleratedSprMT.getDistance(unrootedT1, unrootedT2);
        long timeAcc = System.currentTimeMillis() - startAcc;
        System.out.println("Accelerated uSPR MT: " + distAcc + " (w " + timeAcc + " ms)");

        if (treeSize <= 30) {
            long startClassic = System.currentTimeMillis();
            // Klonujemy t1, żeby klasyczna metryka nie zniszczyła go podczas rozgrzewki (warmup)
            double distClassic = classicSprMT.getDistance(new SimpleTree(unrootedT1), unrootedT2);
            long timeClassic = System.currentTimeMillis() - startClassic;
            System.out.println("Classic uSPR MT    : " + distClassic + " (w " + timeClassic + " ms)");

            if (Math.abs(distAcc - distClassic) < 0.0001) {
                System.out.println("STATUS: ZGODNOŚĆ POTWIERDZONA [OK]");
            } else {
                System.out.println("STATUS: ROZBIEŻNOŚĆ WYNIKÓW [BŁĄD!]");
            }
        }
        System.out.println("=".repeat(50) + "\n");
    }

    @Benchmark
    public double benchmarkClassicSprMT() {
        // Zabezpieczenie JMH - dajemy sklonowane drzewo, żeby każda z setek iteracji miała świeży graf!
        return classicSprMT.getDistance(new SimpleTree(unrootedT1), unrootedT2);
    }

    @Benchmark
    public double benchmarkAcceleratedSprMT() {
        return acceleratedSprMT.getDistance(unrootedT1, unrootedT2);
    }

    public static void main(String[] args) throws org.openjdk.jmh.runner.RunnerException {
        org.openjdk.jmh.runner.options.Options opt = new org.openjdk.jmh.runner.options.OptionsBuilder()
                .include(SprMatchingM3MetricsBenchmark.class.getSimpleName())
                .build();

        new org.openjdk.jmh.runner.Runner(opt).run();
    }
}