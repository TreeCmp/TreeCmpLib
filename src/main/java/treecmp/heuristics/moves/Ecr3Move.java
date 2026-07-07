package treecmp.heuristics.moves;

import pal.tree.Node;
import treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR;

import java.util.List;

public class Ecr3Move implements TreeMove {
    public final List<Node> cluster;
    public final Node[] boundarySubtrees;
    public final TopologyTemplate3sECR template;

    public Ecr3Move(List<Node> cluster, Node[] boundarySubtrees, TopologyTemplate3sECR template) {
        this.cluster = cluster;
        this.boundarySubtrees = boundarySubtrees;
        this.template = template;
    }

    @Override
    public String getDescription() {
        return "3-sECR: Resolving a 4-node cluster into a new binary topology (1 of 105)";
    }
}