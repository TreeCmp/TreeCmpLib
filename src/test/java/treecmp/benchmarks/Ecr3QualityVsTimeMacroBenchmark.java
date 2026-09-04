package treecmp.benchmarks;

import pal.tree.Tree;
import treecmp.heuristics.ecr.Ecr3ClassicHeuristic;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Ecr3QualityVsTimeMacroBenchmark extends AbstractQualityMacroBenchmark {

    public static void main(String[] args) {
        new Ecr3QualityVsTimeMacroBenchmark().runBenchmark(
                args,
                "3-sECR QUALITY VS TIME MACRO-BENCHMARK (100 TREE PAIRS)",
                "benchmark_results_ECR3",
                new int[]{10, 20, 30, 50}
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
                new Ecr3ClassicHeuristic(new RFClusterMetric(), true, "RFC"),
                new Ecr3IncrementalHeuristic(new RFClusterIncrementalMetric(), "RFCinc"),
                null, null
        ));
        list.add(new MetricSetup("MC",
                new Ecr3ClassicHeuristic(new MatchingClusterMetric(), true, "MC_Pure"),
                new Ecr3IncrementalHeuristic(new MCIncrementalMetric(), "MCinc"),
                new Ecr3ClassicHeuristic(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                new Ecr3IncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MCinc_RF")
        ));
        list.add(new MetricSetup("MP",
                new Ecr3ClassicHeuristic(new MatchingPairMetric(), true, "MP_Pure"),
                new Ecr3IncrementalHeuristic(new MPIncrementalMetric(), "MPinc"),
                new Ecr3ClassicHeuristic(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                new Ecr3IncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MPinc_RF")
        ));
        return list;
    }

    private List<MetricSetup> getUnrootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();
        list.add(new MetricSetup("RF",
                new Ecr3ClassicHeuristic(new RFMetric(), false, "RF"),
                new Ecr3IncrementalHeuristic(new RFIncrementalMetric(), "RFinc"),
                null, null
        ));
        list.add(new MetricSetup("MS",
                new Ecr3ClassicHeuristic(new MatchingSplitMetric(), false, "MS_Pure"),
                new Ecr3IncrementalHeuristic(new MSIncrementalMetric(), "MSinc"),
                new Ecr3ClassicHeuristic(new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                new Ecr3IncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MSinc_RF")
        ));
        list.add(new MetricSetup("M3",
                new Ecr3ClassicHeuristic(new MatchingTripletMetric(), false, "M3_Pure"),
                new Ecr3IncrementalHeuristic(new M3IncrementalMetric(), "M3inc"),
                new Ecr3ClassicHeuristic(new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                new Ecr3IncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3inc_RF")
        ));
        return list;
    }
}