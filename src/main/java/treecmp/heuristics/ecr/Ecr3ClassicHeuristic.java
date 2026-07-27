package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

public class Ecr3ClassicHeuristic extends HeuristicBaseMetric {

    private final Metric metric;
    private final Metric primaryMetric; // Dodane: wsparcie dla szybkiego filtra RF
    private final boolean isRooted;
    private final String metricShortName;

    // 1. Podstawowy konstruktor
    public Ecr3ClassicHeuristic(Metric metric, boolean isRooted, String metricShortName) {
        this(metric, null, isRooted, metricShortName);
    }

    // 2. Rozszerzony konstruktor (Tie-Breaker)
    public Ecr3ClassicHeuristic(Metric metric, Metric primaryMetric, boolean isRooted, String metricShortName) {
        super(isRooted);
        this.metric = metric;
        this.primaryMetric = primaryMetric;
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
    protected Metric getPrimaryMetric() {
        return this.primaryMetric != null ? this.primaryMetric : super.getPrimaryMetric();
    }

    @Override
    public String getName() {
        return "3sECR_ClassicHeuristic_" + metricShortName;
    }
}