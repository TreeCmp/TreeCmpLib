package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

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
public class SprRFDistanceBenchmark {

    @Param({
            // Ukorzenione
            "CopheneticL2", "RMAST", "MC", "MP", "NodalL2Splitted", "Triplet",
            // Nieukorzenione
            "M3", "MPU", "MS", "NodalL2", "Quartet", "UMAST", "RF"
    })
    public String metricName;

    // Rozszerzamy zakres do 200, ponieważ wariant inkrementalny bez problemu sobie z tym poradzi
    @Param({"10", "20", "30", "50", "80", "120", "200"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Tree t1ForIncr;

    private HeuristicBaseMetric classicMetric;
    private IncrementalHeuristicBaseMetric incrementalMetric;

    private void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        boolean isRooted = false;

        switch (metricName) {
            case "CopheneticL2":
                isRooted = true;
                classicMetric = new SprHeuristicMetric(new CopheneticL2Metric(), new RFClusterMetric(), true, "CopheneticL2");
                break;
            case "RMAST":
                isRooted = true;
                classicMetric = new SprHeuristicMetric(new RMASTMetric(), new RFClusterMetric(), true, "RMAST");
                break;
            case "MC":
                isRooted = true;
                classicMetric = new SprHeuristicMetric(new MatchingClusterMetricO3(), new RFClusterMetric(), true, "MC");
                incrementalMetric = new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new SprHeuristicMetric(new MatchingPairMetric(), new RFClusterMetric(), true, "MP");
                incrementalMetric = new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), "MP");
                break;
            case "NodalL2Splitted":
                isRooted = true;
                classicMetric = new SprHeuristicMetric(new NodalL2SplittedMetric(), new RFClusterMetric(), true, "NodalL2Splitted");
                break;
            case "Triplet":
                isRooted = true;
                classicMetric = new SprHeuristicMetric(new TripletMetric(), new RFClusterMetric(), true, "Triplet");
                break;
            case "M3":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new MatchingTripletMetric(), new RFMetric(), "M3");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3");
                break;
            case "MPU":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new MatchingPairUnrootedMetric(), new RFMetric(), "MPU");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new MatchingSplitMetric(), new RFMetric(), "MS");
                incrementalMetric = new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), "MS");
                break;
            case "NodalL2":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new NodalL2Metric(), new RFMetric(), "NodalL2");
                break;
            case "Quartet":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new QuartetMetricLong(), new RFMetric(), "Quartet");
                break;
            case "UMAST":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new UMASTMetric(), new RFMetric(), "UMAST");
                break;
            case "RF":
                isRooted = false;
                classicMetric = new UsprHeuristicMetric(new RFMetric(), "RF");
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

        // Opcjonalny wydruk weryfikacyjny (wykonywany tylko w fazie Setup)
        if (incrementalMetric != null && classicMetric != null && treeSize <= 50) {
            double distIncr = incrementalMetric.getDistance(new SimpleTree(t1ForIncr), t2);
            double distClassic = classicMetric.getDistance(new SimpleTree(t1), t2);
            if (Math.abs(distClassic - distIncr) > 1e-6) {
                System.out.printf(" [Uwaga: Inne minimum lokalne SPR dla %s N=%d | Class=%.2f vs Incr=%.2f]%n",
                        metricName, treeSize, distClassic, distIncr);
            }
        }
    }

    @Benchmark
    public double benchmarkClassicFilteredRun() {
        // Czysty benchmark: klonujemy drzewo, by każda iteracja zaczynała pełną wspinaczkę od nowa
        return classicMetric.getDistance(new SimpleTree(t1), t2);
    }

    @Benchmark
    public double benchmarkIncrementalFilteredRun() {
        // Zapewnienie powtarzalności dla wariantu inkrementalnego (pełny cykl od zera)
        return incrementalMetric.getDistance(new SimpleTree(t1ForIncr), t2);
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = true;

        String[] treeSizes = SprRFDistanceBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            // =========================================================================
            // HARMONOGRAM DLA WARIANTU KLASYCZNEGO (CLASSIC)
            // =========================================================================
            if (size <= 20) {
                // N=10, N=20: Puszczamy absolutnie wszystkie 13 metryk (Classic daje radę)
                runJmh(sizeStr,
                        new String[]{"CopheneticL2", "RMAST", "MC", "MP", "NodalL2Splitted", "Triplet", "M3", "MPU", "MS", "NodalL2", "Quartet", "UMAST", "RF"},
                        "benchmarkClassicFilteredRun", quickEstimate);
            } else if (size <= 30) {
                // N=30: Odcinamy ekstremalnie ciężkie metryki sześcienne (Triplet, Quartet, M3)
                runJmh(sizeStr,
                        new String[]{"CopheneticL2", "RMAST", "MC", "MP", "NodalL2Splitted", "MPU", "MS", "NodalL2", "UMAST", "RF"},
                        "benchmarkClassicFilteredRun", quickEstimate);
            } else if (size <= 50) {
                // N=50: Zostawiamy tylko wybrane, średnio ciężkie i szybkie metryki
                runJmh(sizeStr,
                        new String[]{"RF", "MS", "MC", "MP", "CopheneticL2"},
                        "benchmarkClassicFilteredRun", quickEstimate);
            } else if (size <= 80) {
                // N=80: Pełna zbieżność SPR staje się rzezią, przetrwa tylko szybkie RF
                runJmh(sizeStr,
                        new String[]{"RF"},
                        "benchmarkClassicFilteredRun", quickEstimate);
            }
            // Powyżej N=80: CAŁKOWITY ZAKAZ uruchamiania wariantu klasycznego!

            // =========================================================================
            // HARMONOGRAM DLA WARIANTU INKREMENTALNEGO (INCREMENTAL)
            // =========================================================================
            // Wariant inkrementalny uruchamiamy dla absolutnie każdego zadeklarowanego rozmiaru
            // (aż do N=200), ale wyłącznie dla tych 5 metryk, dla których mamy dedykowane klasy Acc.
            runJmh(sizeStr,
                    new String[]{"RF", "MS", "MC", "MP", "M3"},
                    "benchmarkIncrementalFilteredRun", quickEstimate);
        }
    }

    private static void runJmh(String sizeStr, String[] metrics, String benchmarkMethod, boolean quickEstimate) throws Exception {
        ChainedOptionsBuilder builder = new OptionsBuilder()
                // Uruchamiamy tylko jedną, konkretną metodę (Classic albo Incremental)
                .include(SprRFDistanceBenchmark.class.getSimpleName() + "." + benchmarkMethod)
                .param("treeSize", sizeStr)
                .param("metricName", metrics)
                .jvmArgs("-Xms4g", "-Xmx16g");

        if (quickEstimate) {
            builder.warmupIterations(1)
                    .warmupTime(TimeValue.seconds(1))
                    .measurementIterations(2)
                    .measurementTime(TimeValue.seconds(1))
                    .forks(1)
                    .warmupForks(0);
        } else {
            builder.warmupIterations(5)
                    .warmupTime(TimeValue.seconds(2))
                    .measurementIterations(5)
                    .measurementTime(TimeValue.seconds(2))
                    .forks(2)
                    .warmupForks(1);
        }

        new Runner(builder.build()).run();
    }
}