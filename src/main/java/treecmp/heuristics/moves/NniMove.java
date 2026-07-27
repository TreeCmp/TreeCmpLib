package treecmp.heuristics.moves;

import pal.tree.Node;

public class NniMove implements TreeMove {

    // Węzeł, który aktualnie "niesiemy" (odcięte poddrzewo)
    public final Node movingSubtree;

    // Węzeł, z którym się zamieniamy miejscami (sąsiad w grafie)
    public final Node swapPartner;

    // Opcjonalnie: Krawędź (węzeł-ojciec), wokół której dzieje się obrót,
    // ale dla metryki matchingowej najważniejsze jest "kto z kim się zamienił".

    public NniMove(Node movingSubtree, Node swapPartner) {
        this.movingSubtree = movingSubtree;
        this.swapPartner = swapPartner;
    }

    @Override
    public String getDescription() {
        return "Swap " + movingSubtree.getIdentifier().getName() +
                " with " + swapPartner.getIdentifier().getName();
    }

    @Override
    public int getNniEquivalentCost() {
        return 1; // 1 NNI to 1 NNI
    }
}