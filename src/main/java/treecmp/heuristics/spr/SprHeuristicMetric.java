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

    // 1. Podstawowy konstruktor (dla pojedynczych metryk, np. RFC, TT, MC)
    public SprHeuristicMetric(Metric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    // 2. Rozszerzony konstruktor z filtrem (np. jeśli chcemy użyć RFC jako filtra dla innej metryki)
    public SprHeuristicMetric(Metric metric, Metric primaryMetric, String metricShortName) {
        super(true); // Klasyczne otoczenie SPR z definicji operuje na drzewach ukorzenionych (rooted)
        this.metric = metric;
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SprUtils();
    }

    @Override
    protected Metric getMetric() {
        return this.metric;
    }

    @Override
    protected Metric getPrimaryMetric() {
        // Jeśli wstrzyknięto szybki filtr (primaryMetric), użyj go.
        // W przeciwnym razie zachowaj domyślne zachowanie klasy bazowej.
        return this.primaryMetric != null ? this.primaryMetric : super.getPrimaryMetric();
    }

    @Override
    public String getName() {
        return "SPR_ClassicHeuristic_" + metricShortName;
    }
}