package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.results.RunResult;
import pal.tree.SimpleTree;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.nni.NniUtils;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;

public class NniSingleStepBenchmark extends AbstractSingleStepBenchmark {

    private TreeNeighborhoodUtils classicUtils;

    @Override
    protected void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            case "RF":
                isRooted = false;
                classicMetric = new RFMetric();
                incrementalMetric = new NniIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new NniIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new NniIncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new NniIncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new NniIncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new NniIncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Unknown metric: " + metric);
        }

        // Wspólna metoda pobierająca drzewo z datasetu lub generująca losowe (w klasie bazowej)
        loadOrGenerateTrees(size, isRooted);

        classicUtils = new NniUtils(!isRooted);
    }

    @Override
    protected double evaluateClassicBestDist() throws Exception {
        final double[] bestDist = {Double.POSITIVE_INFINITY};

        classicUtils.forEachNeighbour(t1, neighbor -> {
            try {
                if (neighbor instanceof SimpleTree) {
                    ((SimpleTree) neighbor).createNodeList();
                }
                double d = classicMetric.getDistance(neighbor, t2);
                if (d < bestDist[0]) {
                    bestDist[0] = d;
                }
            } catch (Exception e) {
                throw new RuntimeException("Błąd podczas ewaluacji dystansu w sąsiedztwie NNI", e);
            }
        });

        return bestDist[0];
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();

        String[] treeSizes = NniSingleStepBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        List<org.openjdk.jmh.results.RunResult> allResults = new ArrayList<>();
        String className = NniSingleStepBenchmark.class.getSimpleName();
        String incrOnly = className + ".benchmarkIncrementalSingleStep";

        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            if (size <= 120) {
                // Classic + Incremental dla wszystkich 6 metryk (~2.5 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));

            } else if (size <= 300) {
                // Classic dla RF, RFC; Incremental dla wszystkich metryk (M3 powraca do matrycy) (~2 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"MS", "MC", "MP", "M3"}, incrOnly, quickEstimate));

            } else if (size <= 500) {
                // Classic RF; Incremental dla reszty (~3 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RFC", "MS", "MC", "MP", "M3"}, incrOnly, quickEstimate));

            } else if (size <= 800) {
                // OSTATNI KROK DLA MS: N=800 wymaga 4.1 GB RAM. Dalsze rozmiary rzucą OOM (~1.5 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, incrOnly, quickEstimate));

            } else if (size <= 1200) {
                // BEZ MS (wymaga 14 GB RAM -> OOM). MP finiszuje na 1200 (48s). MC i M3 idą lekko (~2 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MC", "MP", "M3"}, incrOnly, quickEstimate));

            } else if (size <= 3000) {
                // N = 2000, 3000: BEZ MP (churn >100 GB) i BEZ MS. MC (~2-5s), M3 (~20-50s), RF/RFC (<10ms) (~2.5 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MC", "M3"}, incrOnly, quickEstimate));

            } else {
                // Czysta skalowalność topologiczna aż do N = 120 000 (~3 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC"}, incrOnly, quickEstimate));
            }
        }

        AbstractSingleStepBenchmark.exportToCsv("benchmark_single_step_NNI.csv", allResults, "NNI");
    }
}