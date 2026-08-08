package treecmp.heuristics.ecr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

public class Ecr3ClassicHeuristic extends EcrClassicHeuristic {

    public Ecr3ClassicHeuristic(Metric metric, boolean isRooted, String metricShortName) {
        this(metric, null, isRooted, metricShortName);
    }

    public Ecr3ClassicHeuristic(Metric metric, Metric primaryMetric, boolean isRooted, String metricShortName) {
        super(metric, primaryMetric, isRooted, metricShortName);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SubtreeEcr3Utils(!isRooted);
    }

    @Override
    public String getName() {
        return "3sECR_ClassicHeuristic_" + metricShortName;
    }
}