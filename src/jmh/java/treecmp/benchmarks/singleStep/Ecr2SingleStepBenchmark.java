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
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristic;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class Ecr2SingleStepBenchmark {

    @Param({"RF", "RFC", "MS", "MC", "MP", "M3"})
    public String metricName;

    @Param({"10", "20", "30", "50", "80", "120", "200", "300", "500"})
    public int treeSize;

    private Tree t1;
    private Tree t2;
    private Tree t1ForIncr;

    private SubtreeEcr2Utils classicUtils;
    private Metric classicMetric;
    private IncrementalHeuristicBaseMetric incrementalMetric;

    private int classicProtectionLimit;

    private static void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        initMetricsAndTrees(metricName, treeSize);
    }

    private void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            case "RF":
                isRooted = false; classicProtectionLimit = 500;
                classicMetric = new RFMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true; classicProtectionLimit = 500;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false; classicProtectionLimit = 300;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true; classicProtectionLimit = 300;
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true; classicProtectionLimit = 300;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false; classicProtectionLimit = 300;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new M3IncrementalMetric(), "M3");
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
        classicUtils = new SubtreeEcr2Utils(!isRooted);
    }

    private double evaluateClassicBestDist() throws Exception {
        Tree[] neighbors = classicUtils.generateNeighbours(t1);
        double bestDist = Double.POSITIVE_INFINITY;
        for (Tree n : neighbors) {
            double d = classicMetric.getDistance(n, t2);
            if (d < bestDist) bestDist = d;
        }
        return bestDist;
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

        String[] treeSizes = Ecr2SingleStepBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            ChainedOptionsBuilder builder = new OptionsBuilder()
                    .include(Ecr2SingleStepBenchmark.class.getSimpleName())
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