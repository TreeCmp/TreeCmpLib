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

            if (size <= 120) {
                // N <= 120: Pełny zestaw Classic + Incremental dla WSZYSTKICH 6 metryk!
                // W ECR2 przy N=120 nawet MC Classic trwa ~2.3 s, a M3/MP/MS poniżej 1.7 s.
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));

            } else if (size <= 200) {
                // N = 200: Pełny Classic dla wszystkich 6 metryk.
                // Czasy Classic: RF (0.4s), MS (3.5s), RFC (5.9s), MP (7.5s), M3 (7.8s), MC (11.3s).
                // Wszystkie punkty Classic mieszczą się poniżej 12 sekund na operację.
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS", "MC", "MP", "M3"}, className, quickEstimate));

            } else if (size <= 300) {
                // N = 300: Classic dla RF (~1.1 s), MS (~11.6 s) oraz RFC (~19.0 s).
                // Incremental: RF, RFC, MS, MC (~101 ms), MP (~2.7 s) oraz M3 (~39 s).
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MS"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"MC", "MP", "M3"}, incrOnly, quickEstimate));

            } else if (size <= 500) {
                // N = 500: Classic tylko RF (~3.9 s).
                // Incremental dla RF, RFC, MS, MC, MP (~10.6 s).
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RFC", "MS", "MC", "MP"}, incrOnly, quickEstimate));

            } else if (size <= 800) {
                // N = 800: Classic tylko RF (~12.0 s).
                // Incremental dla RF, RFC, MS (~1.2 s, granica alokacji), MC oraz MP (~36 s).
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RFC", "MS", "MC", "MP"}, incrOnly, quickEstimate));

            } else if (size <= 1200) {
                // N = 1200: Classic RF (~31.7 s - dociąga linię Classic RF pod górną granicę).
                // Incremental dla RF, RFC, MC (~2.8 s).
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF"}, className, quickEstimate));
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RFC", "MC"}, incrOnly, quickEstimate));

            } else if (size <= 3000) {
                // N = 2000, 3000: Incremental RF, RFC oraz MC (dla N=3000 MC trwa ~29 s).
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC", "MC"}, incrOnly, quickEstimate));

            } else {
                // N >= 5000 do 120 000: Czysta skalowalność topologiczna RF i RFC Incremental.
                allResults.addAll(AbstractSingleStepBenchmark.runJmh(sizeStr,
                        new String[]{"RF", "RFC"}, incrOnly, quickEstimate));
            }
        }

        AbstractSingleStepBenchmark.exportToCsv("benchmark_single_step_ECR2.csv", allResults, "ECR2", "TimeMs");
    }
}