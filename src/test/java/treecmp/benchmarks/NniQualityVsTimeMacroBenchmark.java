package treecmp.benchmarks;

import pal.tree.Tree;
import treecmp.heuristics.nni.NniClassicHeuristic;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NniQualityVsTimeMacroBenchmark extends AbstractQualityMacroBenchmark {

    public static void main(String[] args) {
        new NniQualityVsTimeMacroBenchmark().runBenchmark(
                args,
                "NNI QUALITY VS TIME MACRO-BENCHMARK (100 TREE PAIRS)",
                "benchmark_results_NNI",
                new int[]{10, 20, 30, 50, 80, 120, 200}
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
                new NniClassicHeuristic(new RFClusterMetric(), true, "RFC"),
                new NniIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFCinc"),
                null, null
        ));
        list.add(new MetricSetup("MC",
                new NniClassicHeuristic(new MatchingClusterMetric(), true, "MC_Pure"),
                new NniIncrementalHeuristic(new MCIncrementalMetric(), "MCinc"),
                new NniClassicHeuristic(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                new NniIncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MCinc_RF")
        ));
        list.add(new MetricSetup("MP",
                new NniClassicHeuristic(new MatchingPairMetric(), true, "MP_Pure"),
                new NniIncrementalHeuristic(new MPIncrementalMetric(), "MPinc"),
                new NniClassicHeuristic(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                new NniIncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MPinc_RF")
        ));
        return list;
    }

    private List<MetricSetup> getUnrootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();
        list.add(new MetricSetup("RF",
                new NniClassicHeuristic(new RFMetric(), false, "RF"),
                new NniIncrementalHeuristic(new RFIncrementalMetric(), "RFinc"),
                null, null
        ));
        list.add(new MetricSetup("MS",
                new NniClassicHeuristic(new MatchingSplitMetric(), false, "MS_Pure"),
                new NniIncrementalHeuristic(new MSIncrementalMetric(), "MSinc"),
                new NniClassicHeuristic(new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                new NniIncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MSinc_RF")
        ));
        list.add(new MetricSetup("M3",
                new NniClassicHeuristic(new MatchingTripletMetric(), false, "M3_Pure"),
                new NniIncrementalHeuristic(new M3IncrementalMetric(), "M3inc"),
                new NniClassicHeuristic(new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                new NniIncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3inc_RF")
        ));
        return list;
    }
}