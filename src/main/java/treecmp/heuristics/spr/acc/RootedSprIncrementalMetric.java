package treecmp.heuristics.spr.acc;

import pal.tree.Node;
import treecmp.metrics.IncrementalMetric;

public interface RootedSprIncrementalMetric extends IncrementalMetric {
    void setPrunedState(Node pruneNode, Node wanderingSource);
    void revertPrunedState(Node pruneNode, Node wanderingSource);
    void setTargetRoot(Node pruneNode, Node wanderingSource);
    void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource);
    void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource);
}