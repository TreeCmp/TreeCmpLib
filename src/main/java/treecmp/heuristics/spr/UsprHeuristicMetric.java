package treecmp.heuristics.spr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

public class UsprHeuristicMetric extends HeuristicBaseMetric {
    private final Metric metric;
    private final String metricShortName;

    public UsprHeuristicMetric(Metric metric, String metricShortName) {
        super(false); // Drzewa nieukorzenione
        this.metric = metric;
        this.metricShortName = metricShortName;
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new UsprUtils(); // Twoja klasa generująca klasyczne sąsiedztwo uSPR
    }

    @Override
    protected Metric getMetric() {
        return this.metric;
    }

    @Override
    public String getName() {
        return "uSPR_ClassicHeuristic_" + metricShortName;
    }
}