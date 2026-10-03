package treecmp.heuristics.ecr.acc;

import pal.tree.Node;
import treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR;
import treecmp.metrics.IncrementalMetric;
import java.util.List;

public interface Ecr3IncrementalMetric extends IncrementalMetric {
    double evaluate3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, TopologyTemplate3sECR newTopology);
    double commit3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, TopologyTemplate3sECR newTopology);
}