package treecmp.heuristics.ecr.acc;

import pal.tree.Node;
import treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR;
import treecmp.metrics.IncrementalMetric;

public interface Ecr2IncrementalMetric extends IncrementalMetric {
    double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, TopologyTemplate2sECR newTopology);
    double commit2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, TopologyTemplate2sECR newTopology);
}