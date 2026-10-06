package treecmp.benchmarks.singleStep;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.results.RunResult;
import pal.tree.SimpleTree;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;

public class Ecr3SingleStepBenchmark extends AbstractSingleStepBenchmark {

    private SubtreeEcr3Utils classicUtils;

    @Override
    protected void initMetricsAndTrees(String metric, int size) {
        boolean isRooted = false;

        switch (metric) {
            case "RF":
                isRooted = false;
                classicMetric = new RFMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new Ecr3IncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Unknown metric: " + metric);
        }

        // Wspólna metoda pobierająca drzewo z datasetu lub generująca losowe
        loadOrGenerateTrees(size, isRooted);
        classicUtils = new SubtreeEcr3Utils(!isRooted);
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
                throw new RuntimeException("Błąd podczas ewaluacji dystansu w sąsiedztwie ECR3", e);
            }
        });

        return bestDist[0];
    }

    public static void main(String[] args) throws Exception {
        boolean quickEstimate = isQuickEstimate();

        String[] treeSizes = Ecr3SingleStepBenchmark.class
                .getField("treeSize")
                .getAnnotation(Param.class)
                .value();

        List<org.openjdk.jmh.results.RunResult> allResults = new ArrayList<>();
        String className = Ecr3SingleStepBenchmark.class.getSimpleName();
        String incrOnly = className + ".benchmarkIncrementalSingleStep";

        for (String sizeStr : treeSizes) {
            int size = Integer.parseInt(sizeStr);

            if (size <= 50) {
                // N <= 50: Pełny zestaw Classic + Incremental dla wszystkich 6 metryk (~4.5 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));

            } else if (size <= 80) {
                // N = 80: Classic tylko RF/RFC; Incremental dla wszystkich 6 metryk (~2.5 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"MS", "MC", "MP", "M3"}, incrOnly, quickEstimate));

            } else if (size <= 120) {
                // N = 120: Classic RF; Incremental dla wszystkich 6 metryk.
                // OSTATNI KROK DLA M3: M3 na N=120 trwa ~37s, powyżej dławi CPU (~1.5 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RFC", "MS", "MC", "MP", "M3"}, incrOnly, quickEstimate));

            } else if (size <= 200) {
                // N = 200: Klasyczne całkowicie wyłączone.
                // Incremental: RF, RFC, MS, MC, MP (BEZ M3).
                // OSTATNI KROK DLA MP: MP trwa ~35s, przy N=300 eksploduje do 105s (~1.5 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP"}, incrOnly, quickEstimate));

            } else if (size <= 800) {
                // N = 300, 500, 800: Incremental dla RF, RFC, MS, MC (BEZ M3, BEZ MP).
                // OSTATNI KROK DLA MC (trwa 62s) ORAZ MS (trwa 36s - granica OOM) (~5 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC"}, incrOnly, quickEstimate));

            } else if (size <= 30000) {
                // N = 1200 .. 30 000: Skalowalność topologiczna RF i RFC w otoczeniu ECR3.
                // OSTATNI KROK DLA ECR3: N=30 000 (powyżej pojedynczy krok RF trwa >5 min) (~6.5 min)
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC"}, incrOnly, quickEstimate));

            } else {
                // N > 30 000 w ECR3 pomijamy celowo – kombinatoryka promienia 3 przekracza sensowny czas
                break;
            }
        }

        AbstractSingleStepBenchmark.exportToCsv("benchmark_single_step_ECR3.csv", allResults, "ECR3");
    }
}