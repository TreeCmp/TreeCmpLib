package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

public class Ecr2ClassicHeuristic extends HeuristicBaseMetric {

    private final Metric metric;
    private final Metric primaryMetric; // Dodane: wsparcie dla opcjonalnego szybkiego filtra
    private final boolean isRooted;
    private final String metricShortName;

    // 1. Podstawowy konstruktor (bez filtra)
    public Ecr2ClassicHeuristic(Metric metric, boolean isRooted, String metricShortName) {
        this(metric, null, isRooted, metricShortName);
    }

    // 2. Rozszerzony konstruktor (z filtrem RF do rozwiązywania remisów)
    public Ecr2ClassicHeuristic(Metric metric, Metric primaryMetric, boolean isRooted, String metricShortName) {
        super(isRooted);
        this.metric = metric;
        this.primaryMetric = primaryMetric;
        this.isRooted = isRooted;
        this.metricShortName = metricShortName;
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SubtreeEcr2Utils(!isRooted);
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
        return "2sECR_ClassicHeuristic_" + metricShortName;
    }
}