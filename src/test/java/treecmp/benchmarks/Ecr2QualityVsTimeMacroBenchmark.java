package treecmp.benchmarks;

import pal.tree.Tree;
import treecmp.heuristics.ecr.Ecr2ClassicHeuristic;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Ecr2QualityVsTimeMacroBenchmark extends AbstractQualityMacroBenchmark {

    public static void main(String[] args) {
        new Ecr2QualityVsTimeMacroBenchmark().runBenchmark(
                args,
                "2-sECR QUALITY VS TIME MACRO-BENCHMARK (100 TREE PAIRS)",
                "benchmark_results_ECR2",
                new int[]{10, 20, 30, 50, 80}
        );
    }

    @Override
    protected void runEvaluationsForSize(int size, boolean rooted, List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName) {
        List<MetricSetup> metricsToTest = rooted ? getRootedMetrics() : getUnrootedMetrics();
        for (MetricSetup setup : metricsToTest) {
            forceCleanMemory();
            evaluateVariant(size, rooted, setup.name, "Classic (Pure)", setup.classicPure, trees, blacklist, history, csvFileName);
            forceCleanMemory();
            evaluateVariant(size, rooted, setup.name, "Increm. (Pure)", setup.incrementalPure, trees, blacklist, history, csvFileName);
            forceCleanMemory();
            evaluateVariant(size, rooted, setup.name, "Classic + RF (Tie)", setup.classicFiltered, trees, blacklist, history, csvFileName);
            forceCleanMemory();
            evaluateVariant(size, rooted, setup.name, "Increm. + RF (Tie)", setup.incrementalFiltered, trees, blacklist, history, csvFileName);

            System.out.println("-".repeat(160));
        }
    }

    private List<MetricSetup> getRootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();
        list.add(new MetricSetup("RFCluster",
                new Ecr2ClassicHeuristic(new RFClusterMetric(), true, "RFC"),
                new Ecr2IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFCinc"),
                null, null
        ));
        list.add(new MetricSetup("MC",
                new Ecr2ClassicHeuristic(new MatchingClusterMetric(), true, "MC_Pure"),
                new Ecr2IncrementalHeuristic(new MCIncrementalMetric(), "MCinc"),
                new Ecr2ClassicHeuristic(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                new Ecr2IncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MCinc_RF")
        ));
        list.add(new MetricSetup("MP",
                new Ecr2ClassicHeuristic(new MatchingPairMetric(), true, "MP_Pure"),
                new Ecr2IncrementalHeuristic(new MPIncrementalMetric(), "MPinc"),
                new Ecr2ClassicHeuristic(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                new Ecr2IncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MPinc_RF")
        ));
        return list;
    }

    private List<MetricSetup> getUnrootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();
        list.add(new MetricSetup("RF",
                new Ecr2ClassicHeuristic(new RFMetric(), false, "RF"),
                new Ecr2IncrementalHeuristic(new RFIncrementalMetric(), "RFinc"),
                null, null
        ));
        list.add(new MetricSetup("MS",
                new Ecr2ClassicHeuristic(new MatchingSplitMetric(), false, "MS_Pure"),
                new Ecr2IncrementalHeuristic(new MSIncrementalMetric(), "MSinc"),
                new Ecr2ClassicHeuristic(new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                new Ecr2IncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MSinc_RF")
        ));
        list.add(new MetricSetup("M3",
                new Ecr2ClassicHeuristic(new MatchingTripletMetric(), false, "M3_Pure"),
                new Ecr2IncrementalHeuristic(new M3IncrementalMetric(), "M3inc"),
                new Ecr2ClassicHeuristic(new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                new Ecr2IncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3inc_RF")
        ));
        return list;
    }
}