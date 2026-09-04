package treecmp.benchmarks;

import pal.tree.Tree;
import treecmp.heuristics.spr.SprHeuristicMetric;
import treecmp.heuristics.spr.UsprHeuristicMetric;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SprQualityVsTimeMacroBenchmark extends AbstractQualityMacroBenchmark {

    public static void main(String[] args) {
        treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic.ENABLE_LOGGING = false;
        new SprQualityVsTimeMacroBenchmark().runBenchmark(
                args,
                "SPR QUALITY VS TIME MACRO-BENCHMARK (100 TREE PAIRS)",
                "benchmark_results_SPR",
                new int[]{10, 20, 30, 50, 80, 120, 200}
        );
    }

    @Override
    protected void runEvaluationsForSize(int size, boolean rooted, List<Tree> trees, Set<String> blacklist, Map<String, List<HistoryRecord>> history, String csvFileName) {
        List<MetricSetup> metricsToTest = rooted ? getRootedMetrics() : getUnrootedMetrics();
        for (MetricSetup setup : metricsToTest) {
            if (size < 200) {
                forceCleanMemory();
                evaluateVariant(size, rooted, setup.name, "Classic (Pure)", setup.classicPure, trees, blacklist, history, csvFileName);
            }
            forceCleanMemory();
            evaluateVariant(size, rooted, setup.name, "Increm. (Pure)", setup.incrementalPure, trees, blacklist, history, csvFileName);

            if (size < 200) {
                forceCleanMemory();
                evaluateVariant(size, rooted, setup.name, "Classic + RF (Tie)", setup.classicFiltered, trees, blacklist, history, csvFileName);
            }
            forceCleanMemory();
            evaluateVariant(size, rooted, setup.name, "Increm. + RF (Tie)", setup.incrementalFiltered, trees, blacklist, history, csvFileName);

            System.out.println("-".repeat(160));
        }
    }

    private List<MetricSetup> getRootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();
        list.add(new MetricSetup("RFCluster",
                new SprHeuristicMetric(new RFClusterMetric(), true, "RFC"),
                new SprIncrementalHeuristicMetric(new RFClusterIncrementalMetric(), "RFCinc"),
                null, null
        ));
        list.add(new MetricSetup("MC",
                new SprHeuristicMetric(new MatchingClusterMetric(), true, "MC_Pure"),
                new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), "MCinc"),
                new SprHeuristicMetric(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MCinc_RF")
        ));
        list.add(new MetricSetup("MP",
                new SprHeuristicMetric(new MatchingPairMetric(), true, "MP_Pure"),
                new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), "MPinc"),
                new SprHeuristicMetric(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MPinc_RF")
        ));
        return list;
    }

    private List<MetricSetup> getUnrootedMetrics() {
        List<MetricSetup> list = new ArrayList<>();
        list.add(new MetricSetup("RF",
                new UsprHeuristicMetric(new RFMetric(), "RF"),
                new UsprIncrementalHeuristicMetric(new RFIncrementalMetric(), "RFinc"),
                null, null
        ));
        list.add(new MetricSetup("MS",
                new UsprHeuristicMetric(new MatchingSplitMetric(), "MS_Pure"),
                new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), "MSinc"),
                new UsprHeuristicMetric(new MatchingSplitMetric(), new RFMetric(), "MS_RF"),
                new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), new RFIncrementalMetric(), "MSinc_RF")
        ));
        list.add(new MetricSetup("M3",
                new UsprHeuristicMetric(new MatchingTripletMetric(), "M3_Pure"),
                new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3inc"),
                new UsprHeuristicMetric(new MatchingTripletMetric(), new RFMetric(), "M3_RF"),
                new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3inc_RF")
        ));
        return list;
    }
}