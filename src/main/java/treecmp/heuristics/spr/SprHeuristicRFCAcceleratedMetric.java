package treecmp.heuristics.spr;

import pal.tree.Tree;
import treecmp.metrics.topological.RFClusterIncrementalMetric;

/**
 * Zoptymalizowana heurystyka SPR dla metryki Robinson-Foulds.
 * Zamiast generować fizyczne obiekty drzew dla całego otoczenia (co zajmuje pamięć i czas),
 * wykorzystuje IncrementalMetric oraz SprNeighborhoodWalker do błyskawicznego
 * przeszukania przestrzeni sąsiadów.
 */
public class SprHeuristicRFCAcceleratedMetric extends RFClusterIncrementalMetric {

    private final RFClusterIncrementalMetric incMetric;
    private final SprNeighborhoodWalker walker;

    public SprHeuristicRFCAcceleratedMetric() {
        // Wywołujemy pusty konstruktor z SprHeuristicBaseMetric (jeśli istnieje, super() wywoła się domyślnie)
        super();

        // Inicjalizujemy nasze narzędzia bezpośrednio tutaj
        this.incMetric = new RFClusterIncrementalMetric();
        this.walker = new SprNeighborhoodWalker();
    }

    /**
     * Główna metoda heurystyki.
     * Zwraca najmniejszą odległość RF, jaką można uzyskać wykonując jeden ruch SPR na drzewie t1,
     * w odniesieniu do drzewa t2.
     */
    @Override
    public double getDistance(Tree t1, Tree t2, int... indexes) {
        // KROK 1: Inicjalizacja stanu (Stateful Init) - to jest O(N) lub O(N^2) jednorazowo
        incMetric.initCalculationState(t1, t2);

        // Zmienna na wynik (musimy użyć tablicy lub Atomic, bo jesteśmy w lambdzie)
        // [0] -> Aktualne minimum
        final double[] minDistance = { Double.MAX_VALUE };

        // Opcjonalnie: możemy sprawdzić odległość dla samego t1 (bez ruchu)
        double initialDist = incMetric.getCurrentDistance();
        minDistance[0] = initialDist;

        // KROK 2: Uruchomienie Walkera
        // Walker "symuluje" całe otoczenie SPR poprzez serię ruchów NNI.
        // Dla każdego odwiedzonego wirtualnie sąsiada wołana jest lambda.
        walker.walk(t1, incMetric, (currentDist) -> {

            // To jest "Hot Loop" - wykonuje się tysiące razy.
            // Dzięki RFIncrementalMetric obliczenie currentDist trwa ułamki mikrosekund.
            if (currentDist < minDistance[0]) {
                minDistance[0] = currentDist;
            }
        });

        return minDistance[0];
    }

    @Override
    public String getName() {
        return "SPR_RFC_Accelerated";
    }
}