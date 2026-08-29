package treecmp.heuristics.vnd;

import java.util.HashMap;
import java.util.Map;

public class VndTimeProfiler {

    public static final ThreadLocal<VndTimeProfiler> INSTANCE = ThreadLocal.withInitial(VndTimeProfiler::new);

    // Klucz: np. "NNI_Success", "SPR_Failure"
    // Wartość: Skumulowany czas w nanosekundach
    private final Map<String, Long> timeStats = new HashMap<>();

    public void recordTime(String neighborhood, boolean success, long timeNs) {
        String key = neighborhood + (success ? "_Success" : "_Failure");
        timeStats.put(key, timeStats.getOrDefault(key, 0L) + timeNs);
    }

    public void clear() {
        timeStats.clear();
    }

    public Map<String, Long> getStats() {
        return new HashMap<>(timeStats);
    }
}