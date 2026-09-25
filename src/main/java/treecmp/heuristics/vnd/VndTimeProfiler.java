package treecmp.heuristics.vnd;

import java.util.LinkedHashMap;
import java.util.Map;

public class VndTimeProfiler {

    public static final ThreadLocal<VndTimeProfiler> INSTANCE = ThreadLocal.withInitial(VndTimeProfiler::new);

    // Skumulowane czasy CPU (nanosekundy)
    private final Map<String, Long> timeStats = new AliasAwareStatsMap();

    // Skumulowany wkład w sukces (liczba wykonanych kroków NNI)
    private final Map<String, Long> nniCostStats = new AliasAwareStatsMap();

    /**
     * Rejestruje wkład operatora w sukces (liczbę wykonanych kroków NNI w danej poprawie).
     */
    public void recordNniCost(String neighborhood, long nniCost) {
        if (nniCost <= 0) return;
        String canon = toCanonicalName(neighborhood);
        String key = canon + "_Success";
        nniCostStats.put(key, nniCostStats.getOrDefault(key, 0L) + nniCost);
    }

    public void recordStepCost(String neighborhood, int stepNniCost) {
        recordNniCost(neighborhood, (long) stepNniCost);
    }

    /**
     * Rejestruje czas CPU (zachowane dla pełnej kompatybilności wstecznej).
     */
    public void recordTime(String neighborhood, boolean success, long timeNs) {
        String canon = toCanonicalName(neighborhood);
        String key = canon + (success ? "_Success" : "_Failure");
        timeStats.put(key, timeStats.getOrDefault(key, 0L) + timeNs);
    }

    public void clear() {
        timeStats.clear();
        nniCostStats.clear();
    }

    /**
     * Zwraca statystyki kroków NNI, jeśli zostały zarejestrowane.
     * W przeciwnym razie powraca do statystyk czasu (dla wariantów klasycznych).
     */
    public Map<String, Long> getStats() {
        if (!nniCostStats.isEmpty()) {
            return new AliasAwareStatsMap(nniCostStats);
        }
        return new AliasAwareStatsMap(timeStats);
    }

    public Map<String, Long> getNniCostStats() {
        return new AliasAwareStatsMap(nniCostStats);
    }

    public Map<String, Long> getTimeStats() {
        return new AliasAwareStatsMap(timeStats);
    }

    private static String toCanonicalName(String name) {
        if (name == null) return "Unknown";
        String lower = name.toLowerCase();
        if (lower.contains("nni")) return "NNI";
        if (lower.contains("ecr2") || lower.contains("2secr")) return "ecr2";
        if (lower.contains("ecr3") || lower.contains("3secr")) return "ecr3";
        if (lower.contains("spr")) return "SPR";
        if (lower.contains("tbr")) return "TBR";
        return name;
    }

    /**
     * Specjalizowana mapa, która zachowuje pojedyncze wpisy podczas iteracji (brak podwajania sum),
     * ale obsługuje dowolne wielkości liter i aliasy przy zapytaniach get() i getOrDefault().
     */
    public static class AliasAwareStatsMap extends LinkedHashMap<String, Long> {

        public AliasAwareStatsMap() {
            super();
        }

        public AliasAwareStatsMap(Map<String, Long> m) {
            super(m);
        }

        @Override
        public Long get(Object key) {
            if (key == null) return super.get(null);
            Long val = super.get(key);
            if (val != null) return val;

            String alias = resolveAlias(key.toString());
            if (alias != null) {
                return super.get(alias);
            }
            return null;
        }

        @Override
        public Long getOrDefault(Object key, Long defaultValue) {
            Long val = get(key);
            return (val != null) ? val : defaultValue;
        }

        private String resolveAlias(String rawKey) {
            String lower = rawKey.toLowerCase();
            boolean isFailure = lower.endsWith("_failure");
            String suffix = isFailure ? "_Failure" : "_Success";

            if (lower.contains("nni")) return "NNI" + suffix;
            if (lower.contains("ecr2") || lower.contains("2secr")) return "ecr2" + suffix;
            if (lower.contains("ecr3") || lower.contains("3secr")) return "ecr3" + suffix;
            if (lower.contains("spr")) return "SPR" + suffix;
            if (lower.contains("tbr")) return "TBR" + suffix;

            return null;
        }
    }
}