package treecmp.benchmarks.distance;

import org.openjdk.jmh.annotations.*;
import pal.tree.SimpleTree;
import treecmp.benchmarks.AbstractTreeCmpBenchmark;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;

import java.util.concurrent.TimeUnit;

/**
 * Klasa bazowa dla benchmarków pełnego przebiegu wspinaczki (Full-Run / Distance).
 * Hermetyzuje cykl życia heurystyk, tworzenie kopii ochronnych drzew dla każdej iteracji JMH
 * oraz bezpieczną obsługę błędów OOM.
 */
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public abstract class AbstractDistanceBenchmark extends AbstractTreeCmpBenchmark {

    protected HeuristicBaseMetric classicMetric;
    protected IncrementalHeuristicBaseMetric incrementalMetric;

    protected boolean classicOomReported = false;
    protected boolean incrOomReported = false;

    @Setup(Level.Trial)
    public void setup() {
        classicOomReported = false;
        incrOomReported = false;
        initMetricsAndTrees(metricName, treeSize);
    }

    /**
     * Inicjalizacja metryk i drzew charakterystyczna dla danego operatora (NNI, SPR, TBR, ECR2, ECR3).
     */
    protected abstract void initMetricsAndTrees(String metric, int size);

    @Benchmark
    public double benchmarkClassicFullRun() {
        if (classicMetric == null) return Double.NaN;
        try {
            // KLUCZOWE: Zawsze nowa instancja SimpleTree, by wspinaczka nie mutowała t1 między powtórzeniami
            return classicMetric.getDistance(new SimpleTree(t1), t2);
        } catch (OutOfMemoryError e) {
            if (!classicOomReported) {
                System.err.printf("%n[!] OOM w Classic Full-Run | Metryka: %s | N=%d [!]%n", metricName, treeSize);
                classicOomReported = true;
            }
            System.gc();
            return Double.NaN;
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    @Benchmark
    public double benchmarkIncrementalFullRun() {
        if (incrementalMetric == null) return Double.NaN;
        try {
            return incrementalMetric.getDistance(new SimpleTree(t1ForIncr), t2);
        } catch (OutOfMemoryError e) {
            if (!incrOomReported) {
                System.err.printf("%n[!] OOM w Incremental Full-Run | Metryka: %s | N=%d [!]%n", metricName, treeSize);
                incrOomReported = true;
            }
            System.gc();
            return Double.NaN;
        } catch (Throwable t) {
            return Double.NaN;
        }
    }
}