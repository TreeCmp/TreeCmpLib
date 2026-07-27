package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.spr.SprHeuristicMetric;
import treecmp.heuristics.spr.UsprHeuristicMetric;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SprRFDistanceBenchmark {

    @Param({
            // Ukorzenione
            "CopheneticL2", "RMAST", "MC", "MP", "NodalL2Splitted", "Triplet",
            // Nieukorzenione
            "M3", "MPU", "MS", "NodalL2", "Quartet", "UMAST", "RF"
    })
    public String metricName;

    //@Param({"10", "20", "30", "50", "80"})
    @Param({"10", "20", "30", "50", "80"})
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
        classicProtectionLimit = 100;

        switch (metricName) {
            // =========================================================
            // METRYKI UKORZENIONE
            // Klasyk używa filtra RFC, Inkremental używa oryginału O(1)
            // =========================================================
            case "CopheneticL2":
                isRooted = true;
                // Zaktualizowano konstruktor: dodano parametr isRooted (true)
                classicMetric     = new SprHeuristicMetric(new CopheneticL2Metric(), new RFClusterMetric(), true, "CopheneticL2");
                //incrementalMetric = new SprIncrementalHeuristicMetric(new CopheneticL2IncrementalMetric(), "CopheneticL2");
                break;
            case "RMAST":
                isRooted = true;
                classicMetric     = new SprHeuristicMetric(new RMASTMetric(), new RFClusterMetric(), true, "RMAST");
                //incrementalMetric = new SprIncrementalHeuristicMetric(new RMASTIncrementalMetric(), "RMAST");
                break;
            case "MC":
                isRooted = true;
                classicMetric     = new SprHeuristicMetric(new MatchingClusterMetricO3(), new RFClusterMetric(), true, "MC");
                incrementalMetric = new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric     = new SprHeuristicMetric(new MatchingPairMetric(), new RFClusterMetric(), true, "MP");
                incrementalMetric = new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), "MP");
                break;
            case "NodalL2Splitted":
                isRooted = true;
                classicMetric     = new SprHeuristicMetric(new NodalL2SplittedMetric(), new RFClusterMetric(), true, "NodalL2Splitted");
                //incrementalMetric = new SprIncrementalHeuristicMetric(new NodalL2SplittedIncrementalMetric(), "NodalL2Splitted");
                break;
            case "Triplet":
                isRooted = true;
                classicMetric     = new SprHeuristicMetric(new TripletMetric(), new RFClusterMetric(), true, "Triplet");
                //incrementalMetric = new SprIncrementalHeuristicMetric(new TripletIncrementalMetric(), "Triplet");
                break;

            // =========================================================
            // METRYKI NIEUKORZENIONE
            // Klasyk używa filtra RF, Inkremental używa oryginału O(1)
            // =========================================================
            case "M3":
                isRooted = false;
                classicMetric     = new UsprHeuristicMetric(new MatchingTripletMetric(), new RFMetric(), "M3");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3");
                break;
            case "MPU":
                isRooted = false;
                classicMetric     = new UsprHeuristicMetric(new MatchingPairUnrootedMetric(), new RFMetric(), "MPU");
                //incrementalMetric = new UsprIncrementalHeuristicMetric(new MPUIncrementalMetric(), "MPU");
                break;
            case "MS":
                isRooted = false;
                classicMetric     = new UsprHeuristicMetric(new MatchingSplitMetric(), new RFMetric(), "MS");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), "MS");
                break;
            case "NodalL2":
                isRooted = false;
                classicMetric     = new UsprHeuristicMetric(new NodalL2Metric(), new RFMetric(), "NodalL2");
                //incrementalMetric = new UsprIncrementalHeuristicMetric(new NodalL2IncrementalMetric(), "NodalL2");
                break;
            case "Quartet":
                isRooted = false;
                classicMetric     = new UsprHeuristicMetric(new QuartetMetricLong(), new RFMetric(), "Quartet");
                //incrementalMetric = new UsprIncrementalHeuristicMetric(new QuartetIncrementalMetricLong(), "Quartet");
                break;
            case "UMAST":
                isRooted = false;
                classicMetric     = new UsprHeuristicMetric(new UMASTMetric(), new RFMetric(), "UMAST");
                //incrementalMetric = new UsprIncrementalHeuristicMetric(new UMASTIncrementalMetric(), "UMAST");
                break;
            case "RF":
                isRooted = false;
                classicMetric     = new UsprHeuristicMetric(new RFMetric(), "RF");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new RFIncrementalMetric(), "RF");
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

        System.out.println("\n" + "=".repeat(70));
        System.out.printf(" WERYFIKACJA HEURYSTYKI AKCELEROWANEJ RF (%s) - ROZMIAR: %d%n", metricName, treeSize);
        System.out.println("-".repeat(70));

        if (incrementalMetric != null) {
            long startIncr = System.nanoTime();
            double distIncr = incrementalMetric.getDistance(t1ForIncr, t2);
            long timeIncr = System.nanoTime() - startIncr;
            System.out.printf("Incremental (natywny O(1)) %-11s : %.2f (czas: %,d ms)%n", metricName, distIncr, timeIncr / 1_000_000);

            if (treeSize <= classicProtectionLimit) {
                try {
                    long startClassic = System.nanoTime();
                    double distClassic = classicMetric.getDistance(new SimpleTree(t1), t2);
                    long timeClassic = System.nanoTime() - startClassic;
                    System.out.printf("Classic + RF Filter %-18s : %.2f (czas: %,d ms)%n", metricName, distClassic, timeClassic / 1_000_000);

                    if (Math.abs(distClassic - distIncr) < 1e-6) {
                        System.out.println("** STATUS: ZGODNOŚĆ POTWIERDZONA [OK] **");
                    } else {
                        System.out.println("!! STATUS: ROZBIEŻNOŚĆ WYNIKÓW (Inne lokalne minimum) !!");
                    }
                } catch (Throwable t) {
                    System.out.println("Classic Full-Run : [BŁĄD KLASYCZNEJ IMPLEMENTACJI]");
                }
            }
        } else {
            System.out.println("Brak implementacji Inkrementalnej dla: " + metricName);
        }
        System.out.println("=".repeat(70) + "\n");
    }

    @Benchmark
    public double benchmarkClassicFilteredRun() {
        if (treeSize > classicProtectionLimit || classicMetric == null) return Double.NaN;
        try {
            return classicMetric.getDistance(new SimpleTree(t1), t2);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    @Benchmark
    public double benchmarkIncrementalFilteredRun() {
        if (incrementalMetric == null) return Double.NaN;
        return incrementalMetric.getDistance(t1ForIncr, t2);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SprRFDistanceBenchmark.class.getSimpleName())
                .addProfiler("stack")
                // .addProfiler("gc")
                .build();
        new Runner(opt).run();
    }
}