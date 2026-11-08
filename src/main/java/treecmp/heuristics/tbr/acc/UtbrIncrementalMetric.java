package treecmp.heuristics.tbr.acc;

import pal.tree.Node;
import treecmp.metrics.IncrementalMetric;

/**
 * Kontrakt przyrostowy dla otoczenia 2D-DFS uTBR (drzewa nieukorzenione: MS, M3, RF).
 */
public interface UtbrIncrementalMetric extends IncrementalMetric {
    void setPrunedState(Node pruneNode, Node wanderingSource);
    void revertPrunedState(Node pruneNode, Node wanderingSource);

    void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource);
    void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource);
    void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource);

    void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode);
    void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode);

    // Przeciążenia domyślne (gdy rerootNode == pruneNode)
    default void setTargetRoot(Node pruneNode, Node wanderingSource) {
        setTargetRoot(pruneNode, pruneNode, wanderingSource);
    }

    default void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        moveTargetDown(parentTarget, childTarget, pruneNode, pruneNode, wanderingSource);
    }

    default void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        moveTargetUp(parentTarget, childTarget, pruneNode, pruneNode, wanderingSource);
    }
}