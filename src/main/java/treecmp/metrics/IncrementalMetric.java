package treecmp.metrics;

import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR;
import treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR;
import pal.tree.Node;
import pal.tree.Tree;

import java.util.List;

/**
 * Interface defining the contract for incremental metric state evaluation.
 * Allows heuristic walkers to evaluate topological perturbations in O(1) or O(n)
 * without performing heavy tree cloning or from-scratch re-evaluation.
 */
public interface IncrementalMetric extends Metric {

    void initCalculationState(Tree baseTree, Tree targetTree);

    double applyNni(NniMove move);
    void undoNni(NniMove move);

    double getCurrentDistance();
    void commit();

    // ==========================================
    // METHODS DEDICATED FOR SPR HEURISTICS
    // ==========================================

    void applySprPrune(Node pruneNode);
    void undoSprPrune(Node pruneNode);
    double evaluateSprRegraft(Node pruneNode, Node targetNode);
    void applySprRegraftStep(Node pruneNode, Node currentNode);
    void undoSprRegraftStep();

    // ==========================================
    // METHODS DEDICATED FOR 2-sECR HEURISTICS
    // ==========================================

    /**
     * Atomically evaluates a 2-sECR move (simultaneous contraction of 2 adjacent edges).
     * The metric engine should invalidate 3 internal descriptors (top, m1, m2)
     * and calculate the cost of substituting them with the provided new topology pattern.
     * * @param top The highest node of the 3-node backbone cluster.
     * @param m1 The first internal descendant of the cluster.
     * @param m2 The second internal descendant of the cluster.
     * @param boundarySubtrees Array of 4 subtrees connected to the cluster boundary.
     * @param newTopology The pre-calculated structural template (1 out of 14 valid).
     * @return The resulting metric distance after the topological perturbation.
     */
    double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, TopologyTemplate2sECR newTopology);

    /**
     * Permanently commits the 2-sECR move to the internal metric state.
     */
    double commit2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, TopologyTemplate2sECR newTopology);

    // ==========================================
    // METHODS DEDICATED FOR 3-sECR HEURISTICS
    // ==========================================

    /**
     * Atomically evaluates a 3-sECR move (simultaneous contraction of 3 adjacent edges).
     * The metric engine should invalidate 4 internal descriptors within the cluster
     * and calculate the cost of resolving the degree-6 star into the new topology.
     * * @param cluster List of 4 connected internal nodes forming the contracted backbone.
     * @param boundarySubtrees Array of 5 subtrees attached to the cluster boundary.
     * @param newTopology The pre-calculated structural template (1 out of 104 valid).
     * @return The resulting metric distance.
     */
    double evaluate3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, TopologyTemplate3sECR newTopology);

    /**
     * Permanently commits the 3-sECR move to the internal metric state.
     */
    double commit3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, TopologyTemplate3sECR newTopology);
}