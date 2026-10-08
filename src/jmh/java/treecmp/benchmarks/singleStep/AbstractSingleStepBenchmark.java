package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Level;
import treecmp.benchmarks.AbstractTreeCmpBenchmark;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;

import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.MICROSECONDS)
public abstract class AbstractSingleStepBenchmark extends AbstractTreeCmpBenchmark {

    protected Metric classicMetric;
    protected IncrementalHeuristicBaseMetric incrementalMetric;

    @Setup(Level.Trial)
    public void setup() {
        initMetricsAndTrees(metricName, treeSize);
    }

    protected abstract void initMetricsAndTrees(String metric, int size);
    protected abstract double evaluateClassicBestDist() throws Exception;

    @Benchmark
    public double benchmarkClassicSingleStep() {
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
}