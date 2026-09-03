/*
package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.tbr.TbrClassicHeuristic;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS) // Czas w milisekundach
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class TbrDistanceBenchmark {

    @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    public String metricName;

    @Param({"10", "20", "30", "50", "70", "100"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Tree t1ForIncr;

    private HeuristicBaseMetric classicMetric;
    private IncrementalHeuristicBaseMetric incrementalMetric;

    private int classicProtectionLimit;

    private void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        boolean isRooted = false;

        // =========================================================================
        // WZORZEC STRATEGII: Dynamiczne dobieranie heurystyk dla TBR / uTBR
        // =========================================================================
        switch (metricName) {
            case "RF":
                isRooted = false;
                classicProtectionLimit = 50; // TBR to O(n^3), klasyk dławi się znacznie szybciej
                classicMetric     = new TbrClassicHeuristic(new RFMetric(), false, "RF");
                incrementalMetric = new UtbrIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicProtectionLimit = 50;
                classicMetric     = new TbrClassicHeuristic(new RFClusterMetric(), true, "RFC");
                incrementalMetric = new TbrIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicProtectionLimit = 20;
                classicMetric     = new TbrClassicHeuristic(new MatchingSplitMetric(), false, "MS");
                incrementalMetric = new UtbrIncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicProtectionLimit = 20;
                classicMetric     = new TbrClassicHeuristic(new MatchingClusterMetric(), true, "MC");
                incrementalMetric = new TbrIncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicProtectionLimit = 20;
                classicMetric     = new TbrClassicHeuristic(new MatchingPairMetric(), true, "MP");
                incrementalMetric = new TbrIncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicProtectionLimit = 15; // M3 + klasyczne uTBR jest ekstremalnie powolne
                classicMetric     = new TbrClassicHeuristic(new MatchingTripletMetric(), false, "M3");
                incrementalMetric = new UtbrIncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metricName);
        }

        if (isRooted) {
            t1 = TestTreeFactory.randomRootedBinaryTree(treeSize, 12345L);
            t2 = TestTreeFactory.randomRootedBinaryTree(treeSize, 67890L);
            t1ForIncr = TestTreeFactory.randomRootedBinaryTree(treeSize, 12345L);
        } else {
            t1 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
            t2 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 67890L);
            t1ForIncr = TestTreeFactory.randomUnrootedBinaryTree(treeSize, 12345L);
        }

        assignNumbers(t1); assignNumbers(t2); assignNumbers(t1ForIncr);

        System.out.println("\n" + "=".repeat(60));
        System.out.printf(" WERYFIKACJA PEŁNEGO PRZEBIEGU TBR/uTBR (%s) DLA ROZMIARU: %d%n", metricName, treeSize);
        System.out.println("-".repeat(60));

        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.getDistance(t1ForIncr, t2);
        long timeIncr = System.nanoTime() - startIncr;
        System.out.printf("Incremental Full-Run %-3s : %.2f (czas: %,d ms)%n", metricName, distIncr, timeIncr / 1_000_000);

        if (treeSize <= classicProtectionLimit) {
            try {
                long startClassic = System.nanoTime();
                // Klonujemy t1, żeby klasyk nie zniszczył topologii do kolejnych przebiegów testu
                double distClassic = classicMetric.getDistance(new SimpleTree(t1), t2);
                long timeClassic = System.nanoTime() - startClassic;
                System.out.printf("Classic Full-Run %-3s     : %.2f (czas: %,d ms)%n", metricName, distClassic, timeClassic / 1_000_000);

                if (distClassic == distIncr || Math.abs(distClassic - distIncr) < 1e-9) {
                    System.out.println("** STATUS: ZGODNOŚĆ POTWIERDZONA [OK] **");
                } else {
                    System.out.println("!! STATUS: ROZBIEŻNOŚĆ WYNIKÓW (Możliwe inne lokalne minimum) !!");
                }
            } catch (Throwable t) {
                System.out.println("Classic Full-Run         : [BŁĄD KLASYCZNEJ IMPLEMENTACJI]");
            }
        } else {
            System.out.printf("Classic Full-Run %-3s     : Pominięto (Limit bezpieczeństwa N<=%d)%n", metricName, classicProtectionLimit);
        }
        System.out.println("=".repeat(60) + "\n");
    }

    @Benchmark
    public double benchmarkClassicFullRun() {
        if (treeSize > classicProtectionLimit) return Double.NaN;
        try {
            // ZABEZPIECZENIE JMH: Dajemy sklonowane drzewo
            return classicMetric.getDistance(new SimpleTree(t1), t2);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    @Benchmark
    public double benchmarkIncrementalFullRun() {
        // Nasza logika inkrementalna bezpiecznie resetuje się i operuje na różnicach
        return incrementalMetric.getDistance(t1ForIncr, t2);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(TbrDistanceBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}*/
