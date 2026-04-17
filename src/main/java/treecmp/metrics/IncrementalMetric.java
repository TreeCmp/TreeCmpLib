package treecmp.metrics;

import treecmp.heuristics.moves.NniMove;
import pal.tree.Node;
import pal.tree.Tree;

public interface IncrementalMetric extends Metric {

    void initCalculationState(Tree baseTree, Tree targetTree);

    double applyNni(NniMove move);
    void undoNni(NniMove move);

    double getCurrentDistance();
    void commit();

    // ==========================================
    // METODY DEDYKOWANE DLA HEURYSTYKI SPR
    // ==========================================

    /**
     * Wirtualnie odcina poddrzewo. Metryka aktualizuje swój stan
     * (np. czyszcząc ścieżki do korzenia z wędrujących elementów).
     */
    void applySprPrune(Node pruneNode);

    /**
     * Cofa wirtualne odcięcie poddrzewa, przywracając stan sprzed applySprPrune.
     */
    void undoSprPrune(Node pruneNode);

    /**
     * Zwraca dokładny dystans SPR dla wirtualnego wpięcia odciętego poddrzewa w nowy cel.
     * Metryka ocenia wpięcie na podstawie swojego obecnego, zaktualizowanego stanu.
     */
    double evaluateSprRegraft(Node pruneNode, Node targetNode);

    /**
     * Tymczasowo aktualizuje stan węzła podczas schodzenia w dół drzewa w poszukiwaniu miejsca wpięcia.
     */
    void applySprRegraftStep(Node pruneNode, Node currentNode);

    /**
     * Cofa ostatni krok applySprRegraftStep.
     */
    void undoSprRegraftStep();
}