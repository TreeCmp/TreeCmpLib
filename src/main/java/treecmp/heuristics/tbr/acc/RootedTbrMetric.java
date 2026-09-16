package treecmp.heuristics.tbr.acc;

import pal.tree.Node;

/**
 * Kontrakt dla metryk przyrostowych wspierających przeszukiwanie
 * otoczenia TBR/rTBR za pomocą dwuwymiarowego DFS 1-NNI (2D-DFS).
 */
public interface RootedTbrMetric {

    /**
     * Bisekcja: odcięcie poddrzewa pruneNode od reszty drzewa.
     */
    void setPrunedState(Node pruneNode, Node wanderingSource);

    /**
     * Wycofanie bisekcji ze stosu delty.
     */
    void revertPrunedState(Node pruneNode, Node wanderingSource);

    /**
     * Ustalenie punktu bazowego wpięcia w korzeniu poddrzewa docelowego.
     */
    void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource);

    /**
     * Przesunięcie punktu wpięcia o 1 krawędź w dół w drzewie docelowym (1-NNI).
     */
    void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource);

    /**
     * Wycofanie przesunięcia wpięcia o 1 krawędź w górę.
     */
    void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource);

    /**
     * Przekorzenienie poddrzewa odciętego o 1 krawędź w dół (1-NNI).
     */
    void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode);

    /**
     * Wycofanie przekorzenienia o 1 krawędź w górę.
     */
    void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode);

    /**
     * Zwraca aktualny dystans po wykonanych operacjach atomowych.
     */
    double getCurrentDistance();
}