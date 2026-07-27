package treecmp.heuristics.moves;

public interface TreeMove {
    String getDescription();

    /**
     * Zwraca koszt danego ruchu wyrażony jako ekwiwalent liczby prostych kroków NNI.
     */
    int getNniEquivalentCost();
}
