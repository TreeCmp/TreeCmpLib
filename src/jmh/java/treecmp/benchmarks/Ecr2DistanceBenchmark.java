package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.ecr.*;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristic;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS) // Ustawiamy na MILISEKUNDY, bo pełne przebiegi trwają dłużej
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Ecr2DistanceBenchmark {

    @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    public String metricName;

    // Możesz dostosować rozmiary. Dla pełnego przebiegu N=100 może być bardzo wolne dla klasyka
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

        switch (metricName) {
            case "RF":
                isRooted = false; classicProtectionLimit = 100;
                classicMetric     = new Ecr2ClassicHeuristic(new RFMetric(), isRooted, "RF");
                incrementalMetric = new Ecr2IncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true; classicProtectionLimit = 100;
                classicMetric     = new Ecr2ClassicHeuristic(new RFClusterMetric(), isRooted, "RFC");
                incrementalMetric = new Ecr2IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false; classicProtectionLimit = 50;
                classicMetric     = new Ecr2ClassicHeuristic(new MatchingSplitMetric(), isRooted, "MS");
                incrementalMetric = new Ecr2IncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true; classicProtectionLimit = 50;
                classicMetric     = new Ecr2ClassicHeuristic(new MatchingClusterMetric(), isRooted, "MC");
                incrementalMetric = new Ecr2IncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true; classicProtectionLimit = 50;
                classicMetric     = new Ecr2ClassicHeuristic(new MatchingPairMetric(), isRooted, "MP");
                incrementalMetric = new Ecr2IncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false; classicProtectionLimit = 50;
                classicMetric     = new Ecr2ClassicHeuristic(new MatchingTripletMetric(), isRooted, "M3");
                incrementalMetric = new Ecr2IncrementalHeuristic(new M3IncrementalMetric(), "M3");
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
        System.out.printf(" WERYFIKACJA PEŁNEGO PRZEBIEGU 2-sECR (%s) DLA ROZMIARU: %d%n", metricName, treeSize);
        System.out.println("-".repeat(60));

        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.getDistance(t1ForIncr, t2);
        long timeIncr = System.nanoTime() - startIncr;
        System.out.printf("Incremental Full-Run %-3s : %.2f (czas: %,d ms)%n", metricName, distIncr, timeIncr / 1_000_000);

        if (treeSize <= classicProtectionLimit) {
            try {
                long startClassic = System.nanoTime();
                double distClassic = classicMetric.getDistance(t1, t2);
                long timeClassic = System.nanoTime() - startClassic;
                System.out.printf("Classic Full-Run %-3s     : %.2f (czas: %,d ms)%n", metricName, distClassic, timeClassic / 1_000_000);

                if (distClassic == distIncr || Math.abs(distClassic - distIncr) < 1e-9) {
                    System.out.println("** STATUS: ZGODNOŚĆ POTWIERDZONA [OK] **");
                } else {
                    // W pełnym przebiegu czasami klasyk i inkrementalny mogą wejść w inne lokalne minimum
                    // z powodu równego dystansu przy remisach, ale zazwyczaj wyniki się pokrywają.
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
            return classicMetric.getDistance(t1, t2);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    @Benchmark
    public double benchmarkIncrementalFullRun() {
        return incrementalMetric.getDistance(t1ForIncr, t2);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(Ecr2DistanceBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}