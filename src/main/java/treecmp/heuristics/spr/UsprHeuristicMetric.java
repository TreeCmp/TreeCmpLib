package treecmp.heuristics.spr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

public class UsprHeuristicMetric extends HeuristicBaseMetric {

    private final Metric metric;
    private final Metric primaryMetric; // Szybki filtr (np. RF)
    private final String metricShortName;

    public UsprHeuristicMetric(Metric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    public UsprHeuristicMetric(Metric metric, Metric primaryMetric, String metricShortName) {
        super(false); // Drzewa nieukorzenione
        this.metric = metric;
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new UsprUtils();
    }

    @Override
    protected Metric getMetric() {
        return this.metric;
    }

    @Override
    protected Metric getPrimaryMetric() {
        return this.primaryMetric != null ? this.primaryMetric : super.getPrimaryMetric();
    }

    @Override
    public String getName() {
        if (this.primaryMetric != null) {
            return "uSPR_ClassicHeuristic_" + primaryMetric.getCommandLineName() + "_" + metricShortName;
        }
        return "uSPR_ClassicHeuristic_" + metricShortName;
    }
}