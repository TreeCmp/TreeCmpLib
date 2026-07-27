package treecmp.heuristics.spr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

/**
 * Uniwersalna klasyczna heurystyka (Steepest Descent) dla otoczenia SPR.
 * Zastępuje dziesiątki dedykowanych klas dla poszczególnych metryk.
 */
public class SprHeuristicMetric extends HeuristicBaseMetric {

    private final Metric metric;
    private final Metric primaryMetric; // Opcjonalny szybki filtr
    private final String metricShortName;
    private final boolean forceUspr;

    public SprHeuristicMetric(Metric metric, boolean isRooted, String metricShortName) {
        super(isRooted);
        this.metric = metric;
        this.primaryMetric = null;
        this.metricShortName = metricShortName;
        this.forceUspr = !isRooted;
    }

    public SprHeuristicMetric(Metric metric, Metric primaryMetric, boolean isRooted, String metricShortName) {
        super(isRooted);
        this.metric = metric;
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
        this.forceUspr = !isRooted;
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        // Zabezpieczenie: UsprUtils odpali się tylko, jeśli w nowym konstruktorze
        // wyraźnie ustawimy isRooted = false (np. w benchmarkach). 
        // W przeciwnym razie bezpiecznie działa stary SprUtils.
        return this.forceUspr ? new UsprUtils() : new SprUtils();
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
        return "SPR_ClassicHeuristic_" + metricShortName;
    }
}