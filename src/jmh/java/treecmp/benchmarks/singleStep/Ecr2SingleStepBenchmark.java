package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.results.RunResult;
import pal.tree.SimpleTree;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;

public class Ecr2SingleStepBenchmark extends AbstractSingleStepBenchmark {

    private SubtreeEcr2Utils classicUtils;

    @Override
    protected void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            case "RF":
                isRooted = false;
                classicMetric = new RFMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new Ecr2IncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Unknown metric: " + metric);
        }

        loadOrGenerateTrees(size, isRooted);
        classicUtils = new SubtreeEcr2Utils(!isRooted);
    }

    @Override
    protected double evaluateClassicBestDist() throws Exception {
        final double[] bestDist = { Double.POSITIVE_INFINITY };

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
                throw new RuntimeException("Błąd podczas ewaluacji dystansu w sąsiedztwie ECR2", e);
            }
        });

        return bestDist[0];
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();

        String[] treeSizes = Ecr2SingleStepBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        List<RunResult> allResults = new ArrayList<>();
        String className = Ecr2SingleStepBenchmark.class.getSimpleName();
        String incrOnly = className + ".benchmarkIncrementalSingleStep";

        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            if (size <= 80) {
                // N <= 80: Pełny zestaw Classic + Incremental dla wszystkich 6 metryk
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));

            } else if (size <= 120) {
                // POLUZOWANIE: Dodano Classic MS i MC dla N=120 (trwają ~6-8s w ECR2)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"MP", "M3"}, incrOnly, quickEstimate));

            } else if (size <= 200) {
                // N = 200: Classic dla RF i RFC. Inkrementalny dla wszystkich 6 metryk.
                // OSTATNI KROK DLA M3: N=200 zamyka krzywą M3 (trwa ~29s)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"MS", "MC", "MP", "M3"}, incrOnly, quickEstimate));

            } else if (size <= 500) {
                // N = 300, 500: Classic tylko RF.
                // Inkrementalny dla RF, RFC, MS, MC, MP (BEZ M3).
                // OSTATNI KROK DLA MP: N=500 zamyka krzywą MP (trwa ~33s)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RFC", "MS", "MC", "MP"}, incrOnly, quickEstimate));

            } else if (size <= 800) {
                // POLUZOWANIE: Classic RF pociągnięty do N=800 (~40s).
                // Inkrementalny dla RF, RFC, MS, MC (BEZ M3, BEZ MP).
                // OSTATNI KROK DLA MS: N=800 to granica alokacji przed OOM przy N=1200
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RFC", "MS", "MC"}, incrOnly, quickEstimate));

            } else if (size <= 2000) {
                // N = 1200, 2000: BEZ MS (OOM). Zostaje MC (~15-45s) oraz RF, RFC.
                // OSTATNI KROK DLA MC: N=2000 zamyka metryki dopasowaniowe
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MC"}, incrOnly, quickEstimate));

            } else {
                // N >= 3000 aż do 120 000: Czysta skalowalność topologiczna RF i RFC (w ECR2 <15-50s)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC"}, incrOnly, quickEstimate));
            }
        }

        AbstractSingleStepBenchmark.exportToCsv("benchmark_single_step_ECR2.csv", allResults, "ECR2");
    }
}