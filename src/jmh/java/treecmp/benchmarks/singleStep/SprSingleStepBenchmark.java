package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

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
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class SprSingleStepBenchmark {

    @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    public String metricName;

    @Param({"10", "20", "30", "50", "80"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Tree t1ForIncr;

    private TreeNeighborhoodUtils classicUtils;
    private Metric classicMetric;
    private IncrementalHeuristicBaseMetric incrementalMetric;

    private int classicProtectionLimit;

    private static void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @Setup(Level.Trial)
    public void setup() throws Exception {
        initMetricsAndTrees(metricName, treeSize);

        // Cicha weryfikacja w tle dla procesów JMH
        double distIncr = incrementalMetric.evaluateSingleStep(t1ForIncr, t2);
        if (treeSize <= classicProtectionLimit) {
            double bestClassicDist = evaluateClassicBestDist();
            boolean isMatch = (bestClassicDist == distIncr || Math.abs(bestClassicDist - distIncr) < 1e-9);
            if (!isMatch) {
                throw new IllegalStateException(String.format(
                        "Mismatch in SPR/uSPR (%s) for size %d! Classic=%.4f vs Incr=%.4f",
                        metricName, treeSize, bestClassicDist, distIncr
                ));
            }
        }
    }

    private void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
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
                isRooted = true; classicProtectionLimit = 300;
                treecmp.config.IOSettings.getIOSettings().setOptMsMcByRf(true);
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true; classicProtectionLimit = 120;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false; classicProtectionLimit = 120;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Unknown metric: " + metric);
        }

        if (isRooted) {
            t1 = TestTreeFactory.randomRootedBinaryTree(size, 12345L);
            t2 = TestTreeFactory.randomRootedBinaryTree(size, 67890L);
            t1ForIncr = TestTreeFactory.randomRootedBinaryTree(size, 12345L);
        } else {
            t1 = TestTreeFactory.randomUnrootedBinaryTree(size, 12345L);
            t2 = TestTreeFactory.randomUnrootedBinaryTree(size, 67890L);
            t1ForIncr = TestTreeFactory.randomUnrootedBinaryTree(size, 12345L);
        }

        assignNumbers(t1); assignNumbers(t2); assignNumbers(t1ForIncr);
        classicUtils = isRooted ? new SprUtils() : new UsprUtils();
    }

    private double evaluateClassicBestDist() {
        final double[] bestDist = {Double.POSITIVE_INFINITY};

        if (classicUtils instanceof SprUtils) {
            ((SprUtils) classicUtils).forEachSprTree(t1, neighbor -> {
                double d = 0;
                try {
                    if (neighbor instanceof SimpleTree) {
                        ((SimpleTree) neighbor).createNodeList();
                    }
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
                    if (neighbor instanceof SimpleTree) {
                        ((SimpleTree) neighbor).createNodeList();
                    }
                    d = classicMetric.getDistance(neighbor, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }
                if (d < bestDist[0]) bestDist[0] = d;
            });
        }
        return bestDist[0];
    }

    @Benchmark
    public double benchmarkClassicSingleStep() {
        if (treeSize > classicProtectionLimit) return Double.NaN;
        try {
            return evaluateClassicBestDist();
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    @Benchmark
    public double benchmarkIncrementalSingleStep() {
        return incrementalMetric.evaluateSingleStep(t1ForIncr, t2);
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = true;

        String[] treeSizes = SprSingleStepBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            ChainedOptionsBuilder builder = new OptionsBuilder()
                    .include(SprSingleStepBenchmark.class.getSimpleName())
                    .param("treeSize", sizeStr)
                    .addProfiler("stack");

            if (quickEstimate) {
                builder.warmupIterations(1)
                        .warmupTime(TimeValue.seconds(1))
                        .measurementIterations(1)
                        .measurementTime(TimeValue.seconds(1))
                        .timeout(TimeValue.seconds(5))
                        .forks(1)
                        .warmupForks(0);
            } else {
                builder.warmupIterations(5)
                        .warmupTime(TimeValue.seconds(2))
                        .measurementIterations(5)
                        .measurementTime(TimeValue.seconds(2))
                        .timeout(TimeValue.seconds(30))
                        .forks(2)
                        .warmupForks(1);
            }

            Options opt = builder.build();
            new Runner(opt).run();
        }
    }
}