package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

import pal.tree.Tree;
import treecmp.heuristics.spr.SprHeuristicRFCMetric;
import treecmp.heuristics.spr.SprHeuristicRFCAcceleratedMetric;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 1)
@Fork(1)
public class SprMetricBenchmark {

    @Param({"10", "20", "30", "40", "50", "60", "70", "80", "90", "100"})
    public int treeSize;

    private Tree rootedT1;
    private Tree rootedT2;

    // Deklarujemy, ale NIE inicjalizujemy tutaj
    private SprHeuristicRFCMetric classicSpr;
    private SprHeuristicRFCAcceleratedMetric acceleratedSpr;

    @Setup(Level.Trial)
    public void setup() {
        // 1. Generowanie drzew (używając Twojej nowej, binarnej fabryki)
        this.rootedT1 = TestTreeFactory.randomRootedBinaryTree(treeSize, 12345L);
        this.rootedT2 = TestTreeFactory.randomRootedBinaryTree(treeSize, 67890L);

        // 2. Inicjalizacja metryk
        this.classicSpr = new SprHeuristicRFCMetric();
        this.acceleratedSpr = new SprHeuristicRFCAcceleratedMetric();

        // 3. WERYFIKACJA WYNIKÓW
        System.out.println("\n" + "=".repeat(40));
        System.out.println("WERYFIKACJA DLA ROZMIARU: " + treeSize);

        long startAcc = System.currentTimeMillis();
        double distAcc = acceleratedSpr.getDistance(rootedT1, rootedT2);
        long timeAcc = System.currentTimeMillis() - startAcc;

        System.out.println("Accelerated SPR: " + distAcc + " (obliczono w " + timeAcc + "ms)");

        // Uwaga: dla treeSize=100 klasyk może chwilę mielić, dlatego robimy to tylko raz tutaj
        if (treeSize <= 100) {
            long startClassic = System.currentTimeMillis();
            double distClassic = classicSpr.getDistance(rootedT1, rootedT2);
            long timeClassic = System.currentTimeMillis() - startClassic;
            System.out.println("Classic SPR    : " + distClassic + " (obliczono w " + timeClassic + "ms)");

            if (Math.abs(distAcc - distClassic) < 0.0001) {
                System.out.println("STATUS: WYNIKI IDENTYCZNE - OK");
            } else {
                System.out.println("!!! STATUS: RÓŻNE WYNIKI !!!");
            }
        } else {
            System.out.println("Classic SPR    : Pominięto weryfikację (zbyt duży rozmiar)");
        }
        System.out.println("=".repeat(40) + "\n");
    }

    @Benchmark
    public double benchmarkClassicSprRooted() {
        return classicSpr.getDistance(rootedT1, rootedT2);
    }

    @Benchmark
    public double benchmarkAcceleratedSprRooted() {
        return acceleratedSpr.getDistance(rootedT1, rootedT2);
    }

    public static void main(String[] args) throws org.openjdk.jmh.runner.RunnerException {
        org.openjdk.jmh.runner.options.Options opt = new org.openjdk.jmh.runner.options.OptionsBuilder()
                .include(SprMetricBenchmark.class.getSimpleName())
                .build();

        new org.openjdk.jmh.runner.Runner(opt).run();
    }
}