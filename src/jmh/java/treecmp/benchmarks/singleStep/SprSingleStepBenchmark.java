package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS) // Mikrosekundy dla SPR 1-Step
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SprSingleStepBenchmark {

    // Możesz odkomentować pełną listę, gdy będziesz chciał przetestować wszystkie metryki naraz
    // @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    @Param({"MC"})
    public String metricName;

    @Param({"10", "20", "30", "50", "80", "120", "200", "320"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Tree t1ForIncr;

    private TreeNeighborhoodUtils classicUtils;
    private Metric classicMetric;
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
        // WZORZEC STRATEGII (Kompozycja):
        // Wstrzykujemy silnik inkrementalny do uniwersalnej heurystyki SPR/uSPR.
        // Limity ustawione na 100, ponieważ mamy pamięć O(1) dzięki Leniwym Generatorom!
        // =========================================================================
        switch (metricName) {
            case "RF":
                isRooted = false; classicProtectionLimit = 500;
                classicMetric = new RFMetric();
                incrementalMetric = new UsprIncrementalHeuristicMetric(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true; classicProtectionLimit = 500;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false; classicProtectionLimit = 500;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true; classicProtectionLimit = 500;
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true; classicProtectionLimit = 500;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false; classicProtectionLimit = 500;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3");
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

        // Wybór klasycznego generatora (Rooted vs Unrooted)
        classicUtils = isRooted ? new SprUtils() : new UsprUtils();

        System.out.println("\n" + "=".repeat(60));
        System.out.printf(" WERYFIKACJA 1-STEP SPR/uSPR (%s) DLA ROZMIARU: %d%n", metricName, treeSize);
        System.out.println("-".repeat(60));

        long startIncr = System.nanoTime();
        double distIncr = incrementalMetric.evaluateSingleStep(t1ForIncr, t2);
        long timeIncr = System.nanoTime() - startIncr;
        System.out.printf("Incremental 1-Step %-3s : %.2f (czas: %,d ns)%n", metricName, distIncr, timeIncr);

        if (treeSize <= classicProtectionLimit) {
            try {
                long startClassic = System.nanoTime();

                // Tablica 1-elementowa pozwala na mutację wewnątrz Lambdy (Consumer)
                final double[] bestClassicDist = {Double.POSITIVE_INFINITY};

                if (classicUtils instanceof SprUtils) {
                    ((SprUtils) classicUtils).forEachSprTree(t1, neighbor -> {
                        double d = 0;
                        try {
                            d = classicMetric.getDistance(neighbor, t2);
                        } catch (TreeCmpException e) {
                            throw new RuntimeException(e);
                        }
                        if (d < bestClassicDist[0]) bestClassicDist[0] = d;
                    });
                } else if (classicUtils instanceof UsprUtils) {
                    ((UsprUtils) classicUtils).forEachUsprTree(t1, neighbor -> {
                        double d = 0;
                        try {
                            d = classicMetric.getDistance(neighbor, t2);
                        } catch (TreeCmpException e) {
                            throw new RuntimeException(e);
                        }
                        if (d < bestClassicDist[0]) bestClassicDist[0] = d;
                    });
                }

                long timeClassic = System.nanoTime() - startClassic;
                System.out.printf("Classic 1-Step %-3s     : %.2f (czas: %,d ns)%n", metricName, bestClassicDist[0], timeClassic);

                if (bestClassicDist[0] == distIncr || Math.abs(bestClassicDist[0] - distIncr) < 1e-9) {
                    System.out.println("** STATUS: ZGODNOŚĆ POTWIERDZONA [OK] **");
                } else {
                    System.out.println("!! STATUS: ROZBIEŻNOŚĆ WYNIKÓW [BŁĄD!] !!");
                }
            } catch (Throwable t) {
                System.out.println("Classic 1-Step         : [BŁĄD KLASYCZNEJ IMPLEMENTACJI]");
                t.printStackTrace();
            }
        } else {
            System.out.printf("Classic 1-Step %-3s     : Pominięto (Limit bezpieczeństwa N<=%d)%n", metricName, classicProtectionLimit);
        }
        System.out.println("=".repeat(60) + "\n");
    }

    @Benchmark
    public double benchmarkClassicSingleStep() {
        if (treeSize > classicProtectionLimit) return Double.NaN;
        try {
            final double[] bestDist = {Double.POSITIVE_INFINITY};

            if (classicUtils instanceof SprUtils) {
                ((SprUtils) classicUtils).forEachSprTree(t1, neighbor -> {
                    double d = 0;
                    try {
                        d = classicMetric.getDistance(neighbor, t2);
                    } catch (TreeCmpException e) {
                        throw new RuntimeException(e);
                    }
                    if (d < bestDist[0]) bestDist[0] = d;
                });
            } else if (classicUtils instanceof UsprUtils) {
                ((UsprUtils) classicUtils).forEachUsprTree(t1, neighbor -> {
                    double d = 0;
                    try {
                        d = classicMetric.getDistance(neighbor, t2);
                    } catch (TreeCmpException e) {
                        throw new RuntimeException(e);
                    }
                    if (d < bestDist[0]) bestDist[0] = d;
                });
            }
            return bestDist[0];
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    @Benchmark
    public double benchmarkIncrementalSingleStep() {
        return incrementalMetric.evaluateSingleStep(t1ForIncr, t2);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SprSingleStepBenchmark.class.getSimpleName())
                .addProfiler("stack")
                // .addProfiler("gc")
                .build();
        new Runner(opt).run();
    }
}