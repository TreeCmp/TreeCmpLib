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
        // Określamy, z jakiego typu gwiazdy (oryginalnego klastra) zrobiliśmy rozplecenie
        String clusterType = template.isFork ? "Fork" : "Chain";

        // Zwracamy czytelny opis, np.: "2-sECR [Chain] applying permutation: [0, 2, 1, 3]"
        return String.format("2-sECR [%s] applying permutation: %s",
                clusterType, Arrays.toString(template.indices));
    }
}