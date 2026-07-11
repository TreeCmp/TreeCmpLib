package treecmp.heuristics.nni;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

public class NniClassicHeuristic extends HeuristicBaseMetric {

    private final Metric metric;
    private final Metric primaryMetric; // Dodane: wsparcie dla opcjonalnego szybkiego filtra
    private final boolean isRooted;
    private final String metricShortName;

    // 1. Podstawowy konstruktor (dla zwykłych metryk np. RF, RFC, M3)
    public NniClassicHeuristic(Metric metric, boolean isRooted, String metricShortName) {
        this(metric, null, isRooted, metricShortName); // Delegacja do pełnego konstruktora
    }

    // 2. Rozszerzony konstruktor z filtrem (dla metryk łączonych np. UMAST + RF)
    public NniClassicHeuristic(Metric metric, Metric primaryMetric, boolean isRooted, String metricShortName) {
        super(isRooted);
        this.metric = metric;
        this.primaryMetric = primaryMetric;
        this.isRooted = isRooted;
        this.metricShortName = metricShortName;
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new NniUtils(!isRooted);
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
        return "NNI_ClassicHeuristic_" + metricShortName;
    }
}