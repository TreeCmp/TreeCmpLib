package treecmp.heuristics.nni;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

public class NniClassicHeuristic extends HeuristicBaseMetric {

    private final Metric metric;
    private final boolean isRooted;
    private final String metricShortName;

    // Wzorzec kompozycji dla klasycznej heurystyki NNI
    public NniClassicHeuristic(Metric metric, boolean isRooted, String metricShortName) {
        super(isRooted);
        this.metric = metric;
        this.isRooted = isRooted;
        this.metricShortName = metricShortName;
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        // NniUtils przyjmuje flagę "unrooted" w konstruktorze
        return new NniUtils(!isRooted);
    }

    @Override
    protected Metric getMetric() {
        return this.metric;
    }

    @Override
    public String getName() {
        return "NNI_ClassicHeuristic_" + metricShortName;
    }
}