package treecmp.heuristics.tbr.acc;

import pal.tree.Node;
import treecmp.metrics.IncrementalMetric;

public interface RootedTbrMetric extends IncrementalMetric {
    void setPrunedState(Node pruneNode, Node wanderingSource);
    void revertPrunedState(Node pruneNode, Node wanderingSource);
    void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource);
    void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource);
    void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource);
    void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode);
    void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode);
}