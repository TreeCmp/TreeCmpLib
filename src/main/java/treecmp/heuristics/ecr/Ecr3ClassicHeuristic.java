package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

public class Ecr3ClassicHeuristic extends HeuristicBaseMetric {

    private final Metric metric;
    private final boolean isRooted;
    private final String metricShortName;

    public Ecr3ClassicHeuristic(Metric metric, boolean isRooted, String metricShortName) {
        super(isRooted);
        this.metric = metric;
        this.isRooted = isRooted;
        this.metricShortName = metricShortName;
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SubtreeEcr3Utils(!isRooted);
    }

    @Override
    protected Metric getMetric() {
        return this.metric;
    }

    @Override
    public String getName() {
        return "3sECR_ClassicHeuristic_" + metricShortName;
    }
}