/*
package treecmp.heuristics.tbr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.metrics.Metric;

*/
/**
 * Zunifikowana klasyczna heurystyka dla ruchów TBR (Rooted) oraz uTBR (Unrooted).
 * Eliminuje potrzebę tworzenia dedykowanych klas dla każdej metryki.
 *//*

public class TbrClassicHeuristic extends HeuristicBaseMetric {

    private final Metric metric;
    private final Metric primaryMetric; // Opcjonalny szybki filtr
    private final boolean isRooted;
    private final String metricShortName;

    // 1. Podstawowy konstruktor (dla pojedynczych metryk, np. RF, RFC)
    public TbrClassicHeuristic(Metric metric, boolean isRooted, String metricShortName) {
        this(metric, null, isRooted, metricShortName);
    }

    // 2. Rozszerzony konstruktor (z możliwością dodania szybkiego filtra)
    public TbrClassicHeuristic(Metric metric, Metric primaryMetric, boolean isRooted, String metricShortName) {
        super(isRooted);
        this.metric = metric;
        this.primaryMetric = primaryMetric;
        this.isRooted = isRooted;
        this.metricShortName = metricShortName;
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        // Kluczowa logika: Automatyczny wybór odpowiedniego generatora!
        // Jeśli drzewo jest ukorzenione -> TbrUtils
        // Jeśli drzewo jest nieukorzenione -> UTbrUtils
        return isRooted ? new TbrUtils() : new UTbrUtils();
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
        String prefix = isRooted ? "TBR" : "uTBR";
        return prefix + "_ClassicHeuristic_" + metricShortName;
    }
}*/
