package treecmp.heuristics.moves;

import pal.tree.Node;
import treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR;
import java.util.Arrays;

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
        String clusterType = template.isFork ? "Fork" : "Chain";
        return String.format("2-sECR [%s] applying permutation: %s (NNI cost: %d)",
                clusterType, Arrays.toString(template.indices), getNniEquivalentCost());
    }

    @Override
    public int getNniEquivalentCost() {
        // Jeśli szablon ma pole exactNniCost, zwracamy je bezpośrednio:
        // return template.exactNniCost;

        // Alternatywnie: wyliczenie deterministyczne na podstawie permutacji indeksów
        // W 2-sECR brak zmiany to 0, prosta rotacja to 1 NNI, pełna przebudowa to 2 NNI.
        int diffCount = 0;
        for (int i = 0; i < template.indices.length; i++) {
            if (template.indices[i] != i) diffCount++;
        }
        return (diffCount <= 2) ? 1 : 2;
    }
}