package treecmp.heuristics.spr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

public class SprHeuristicMetric extends HeuristicBaseMetric {
    private final Metric metric;
    private final String metricShortName;

    public SprHeuristicMetric(Metric metric, String metricShortName) {
        super(true); // Drzewa ukorzenione
        this.metric = metric;
        this.metricShortName = metricShortName;
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SprUtils(); // Twoja klasa generująca klasyczne sąsiedztwo SPR
    }

    @Override
    protected Metric getMetric() {
        return this.metric;
    }

    @Override
    public String getName() {
        return "SPR_ClassicHeuristic_" + metricShortName;
    }
}