package treecmp.metrics;

import treecmp.heuristics.moves.NniMove;
import pal.tree.Tree;

/**
 * Interface for metrics supporting incremental distance computation.
 * Allows for rapid result updates after performing an NNI move (or SPR decomposed into NNI),
 * without the need to recalculate the entire metric from scratch.
 */
public interface IncrementalMetric extends Metric {

    /**
     * Initializes the calculation state for a pair of trees.
     * This method performs a full calculation (computationally expensive) and prepares
     * auxiliary structures (e.g., matching matrix) for subsequent fast updates.
     *
     * @param baseTree   The tree to be modified by moves (e.g., the neighborhood center).
     * @param targetTree The reference tree (target).
     */
    void initCalculationState(Tree baseTree, Tree targetTree);

    /**
     * Updates the metric state and returns the new distance after performing a virtual NNI move.
     * This method is expected to run in O(n) time or similar complexity.
     *
     * @param move The NNI move (subtree swap) being simulated.
     * @return The new distance value after the move is applied.
     */
    double applyNni(NniMove move);

    /**
     * Reverts the last performed NNI move, restoring the metric state to the previous one.
     * Used during neighborhood traversal (backtracking).
     *
     * @param move The same move that was previously applied.
     */
    void undoNni(NniMove move);

    /**
     * Returns the current distance value stored in the metric state.
     *
     * @return The current distance.
     */
    double getCurrentDistance();
}