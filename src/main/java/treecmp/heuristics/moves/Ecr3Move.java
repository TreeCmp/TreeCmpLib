package treecmp.heuristics.moves;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR;

import java.util.ArrayList;
import java.util.Collections;
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
        return String.format("3-sECR: Resolving cluster into new binary topology (exact NNI cost: %d)",
                getNniEquivalentCost());
    }

    @Override
    public int getNniEquivalentCost() {
        return template.nniCost; //[cite: 39]
    }

    @Override
    public List<Tree> getNniTrajectory(Tree startTree) {
        SubtreeEcr3Utils utils = new SubtreeEcr3Utils(false);
        List<Tree> trajectory = new ArrayList<>();

        // 1. Budujemy drzewa pośrednie (NNI_Substep_1, NNI_Substep_2) bezpośrednio z szablonu ECR
        List<TopologyTemplate3sECR> stepTemplates = template.nniTrajectoryTemplates; //[cite: 39]
        if (stepTemplates != null && !stepTemplates.isEmpty()) {
            for (TopologyTemplate3sECR stepTemplate : stepTemplates) {
                Tree stepTree = utils.createEcr3Tree(startTree, cluster, boundarySubtrees, stepTemplate); //[cite: 39]
                if (stepTree != null) {
                    trajectory.add(stepTree);
                }
            }
        }

        // 2. Na końcu zawsze umieszczamy ostateczne drzewo docelowe
        Tree finalTree = utils.createEcr3Tree(startTree, cluster, boundarySubtrees, template); //[cite: 39]
        if (finalTree != null) {
            trajectory.add(finalTree);
        }

        return trajectory;
    }
}