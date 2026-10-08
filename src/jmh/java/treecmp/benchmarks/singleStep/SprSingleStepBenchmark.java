package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.*;
import pal.tree.SimpleTree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;

public class SprSingleStepBenchmark extends AbstractSingleStepBenchmark {

    private TreeNeighborhoodUtils classicUtils;

    @Override
    @Setup(Level.Trial)
    public void setup() {
        super.setup();

        // Lekka weryfikacja poprawności TYLKO dla małych drzew (N <= 30),
        // aby nie blokować metody setup() przed startem benchmarku
        if (treeSize <= 30) {
            try {
                double distIncr = incrementalMetric.evaluateSingleStep(t1ForIncr, t2);
                double bestClassicDist = evaluateClassicBestDist();
                boolean isMatch = (bestClassicDist == distIncr || Math.abs(bestClassicDist - distIncr) < 1e-9);
                if (!isMatch) {
                    throw new IllegalStateException(String.format(
                            "Mismatch in SPR/uSPR (%s) for size %d! Classic=%.4f vs Incr=%.4f",
                            metricName, treeSize, bestClassicDist, distIncr
                    ));
                }
            } catch (Exception e) {
                throw new RuntimeException("Błąd podczas walidacji SPR w setup(): " + e.getMessage(), e);
            }
        }
    }

    @Override
    protected void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            case "RF":
                isRooted = false;
                classicMetric = new RFMetric();
                incrementalMetric = new UsprIncrementalHeuristicMetric(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                treecmp.config.IOSettings.getIOSettings().setOptMsMcByRf(true);
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Unknown metric: " + metric);
        }

        // Korzysta ze wspólnej metody ładowania/generowania drzew z AbstractSingleStepBenchmark
        loadOrGenerateTrees(size, isRooted);

        classicUtils = isRooted ? new SprUtils() : new UsprUtils();
    }

    @Override
    protected double evaluateClassicBestDist() {
        final double[] bestDist = {Double.POSITIVE_INFINITY};

        if (classicUtils instanceof SprUtils) {
            ((SprUtils) classicUtils).forEachSprTree(t1, neighbor -> {
                double d;
                try {
                    if (neighbor instanceof SimpleTree) {
                        ((SimpleTree) neighbor).createNodeList();
                    }
                    d = classicMetric.getDistance(neighbor, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }
                if (d < bestDist[0]) {
                    bestDist[0] = d;
                }
            });
        } else if (classicUtils instanceof UsprUtils) {
            ((UsprUtils) classicUtils).forEachUsprTree(t1, neighbor -> {
                double d;
                try {
                    if (neighbor instanceof SimpleTree) {
                        ((SimpleTree) neighbor).createNodeList();
                    }
                    d = classicMetric.getDistance(neighbor, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }
                if (d < bestDist[0]) {
                    bestDist[0] = d;
                }
            });
        }
        return bestDist[0];
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();
        String[] treeSizes = SprSingleStepBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        List<org.openjdk.jmh.results.RunResult> allResults = new ArrayList<>();
        String className = SprSingleStepBenchmark.class.getSimpleName();

        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            if (size <= 80) {
                // N <= 80: Pełny zestaw Classic + Incremental dla 6 metryk
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));
            } else if (size <= 120) {
                // N = 120: Dołączamy MS Classic (~21.5s) i M3 Classic (~51.6s).
                // Wszystkie 6 metryk liczone w wariancie Classic i Incr!
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));
            } else if (size <= 200) {
                // N = 200: Classic dla RFC (~16.6s), RF (~17.4s) oraz MP (~33.3s)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MP"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"MS", "MC", "M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 300) {
                // N = 300: Pełny zestaw 6 metryk Incremental
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 500) {
                // N = 500: Incr dla RF, RFC, MC, MP, M3
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MC", "MP", "M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 800) {
                // N = 800: Incr dla RF, RFC, MP, M3
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RF", "RFC", "MP", "M3"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 1200) {
                // N = 1200: Incr dla RFC (~7.5s) oraz MP (~80s)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RFC", "MP"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            } else if (size <= 3000) {
                // N = 2000 i 3000: Rozszerzenie RFC Incr (N=2000 ~29s, N=3000 ~83s)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(
                        sizeStr, new String[]{"RFC"}, className + ".benchmarkIncrementalSingleStep", quickEstimate));
            }
        }

        AbstractSingleStepBenchmark.exportToCsv("benchmark_single_step_SPR.csv", allResults, "SPR", "TimeMs");
    }
}