package treecmp.heuristics.moves;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Ecr2Move implements TreeMove {
    public final Node top;
    public final Node m1;
    public final Node m2;
    public final Node[] boundarySubtrees;
    public final TopologyTemplate2sECR template;

    public Ecr2Move(Node top, Node m1, Node m2, Node[] boundarySubtrees, TopologyTemplate2sECR template) {
        this.top = top;
        this.m1 = m1;
        this.m2 = m2;
        this.boundarySubtrees = boundarySubtrees;
        this.template = template;
    }

    @Override
    public String getDescription() {
        String clusterType = template.isFork ? "Fork" : "Chain"; //[cite: 38]
        return String.format("2-sECR [%s] applying permutation: %s (NNI cost: %d)",
                clusterType, Arrays.toString(template.indices), getNniEquivalentCost()); //[cite: 38]
    }

    @Override
    public int getNniEquivalentCost() {
        return calculateCost(this.template);
    }

    @Override
    public List<Tree> getNniTrajectory(Tree startTree) {
        List<Tree> trajectory = new ArrayList<>();
        boolean isOriginalFork = (m2.getParent() == top); //[cite: 38]

        // 1. Jeśli ruch 2sECR ma koszt 2 NNI, generujemy drzewo z szablonu pośredniego (koszt 1 NNI)
        if (getNniEquivalentCost() == 2) {
            TopologyTemplate2sECR step1Template = findStep1Template();
            if (step1Template != null) {
                Tree step1Tree = SubtreeEcr2Utils.createEcrTree(
                        startTree, top, m1, m2, boundarySubtrees, step1Template, isOriginalFork
                ); //[cite: 38]
                if (step1Tree != null) {
                    trajectory.add(step1Tree);
                }
            }
        }

        // 2. Na końcu zawsze umieszczamy ostateczne drzewo docelowe
        Tree finalTree = SubtreeEcr2Utils.createEcrTree(
                startTree, top, m1, m2, boundarySubtrees, template, isOriginalFork
        ); //[cite: 38]
        if (finalTree != null) {
            trajectory.add(finalTree);
        }

        return trajectory;
    }

    private TopologyTemplate2sECR findStep1Template() {
        TopologyTemplate2sECR bestMatch = null;
        int maxAgreement = -1;

        for (TopologyTemplate2sECR candidate : SubtreeEcr2Utils.getTemplates()) { //[cite: 38]
            // Wybieramy szablon o koszcie 1 NNI tego samego typu strukturalnego (Fork/Chain)
            if (candidate.isFork == template.isFork && calculateCost(candidate) == 1) { //[cite: 38]
                int agreement = 0;
                for (int i = 0; i < 4; i++) {
                    if (candidate.indices[i] == template.indices[i]) { //[cite: 38]
                        agreement++;
                    }
                }
                if (agreement > maxAgreement) {
                    maxAgreement = agreement;
                    bestMatch = candidate;
                }
            }
        }
        return bestMatch;
    }

    private static int calculateCost(TopologyTemplate2sECR t) {
        int diffCount = 0;
        for (int i = 0; i < t.indices.length; i++) {
            if (t.indices[i] != i) diffCount++; //[cite: 38]
        }
        return (diffCount == 0) ? 0 : ((diffCount == 2) ? 1 : 2);
    }
}