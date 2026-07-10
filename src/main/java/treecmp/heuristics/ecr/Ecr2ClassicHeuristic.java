package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

public class Ecr2ClassicHeuristic extends HeuristicBaseMetric {

    private final Metric metric;
    private final boolean isRooted;
    private final String metricShortName;

    // Kompozycja: Podajemy instancję metryki, flagę ukorzenienia i nazwę
    public Ecr2ClassicHeuristic(Metric metric, boolean isRooted, String metricShortName) {
        super(isRooted);
        this.metric = metric;
        this.isRooted = isRooted;
        this.metricShortName = metricShortName;
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        // SubtreeEcr2Utils przyjmuje flagę "unrooted" (dlatego odwracamy isRooted)
        return new SubtreeEcr2Utils(!isRooted);
    }

    @Override
    protected Metric getMetric() {
        return this.metric;
    }

    @Override
    public String getName() {
        return "2sECR_ClassicHeuristic_" + metricShortName;
    }
}