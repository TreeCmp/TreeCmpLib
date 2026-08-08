package treecmp.heuristics.ecr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

import java.util.List;

public class Ecr2ClassicHeuristic extends EcrClassicHeuristic {

    public Ecr2ClassicHeuristic(Metric metric, boolean isRooted, String metricShortName) {
        this(metric, null, isRooted, metricShortName);
    }

    public Ecr2ClassicHeuristic(Metric metric, Metric primaryMetric, boolean isRooted, String metricShortName) {
        super(metric, primaryMetric, isRooted, metricShortName);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SubtreeEcr2Utils(!isRooted);
    }

    @Override
    public String getName() {
        return "2sECR_ClassicHeuristic_" + metricShortName;
    }
}