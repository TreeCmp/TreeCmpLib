package treecmp.heuristics.nni;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.metrics.topological.RFIncrementalMetric;

public class NniIncrementalHeuristicRFMetric extends IncrementalHeuristicBaseMetric {

    private final NniUtils nniUtils;

    public NniIncrementalHeuristicRFMetric() {
        // false (Unrooted) -> SPLIT (RFIncrementalMetric)
        super(false, new RFIncrementalMetric());

        // Skoro to drzewa nieukorzenione (unrooted), NniUtils musi przyjąć false!
        this.nniUtils = new NniUtils(false);
    }

    // USUNIĘTO: getIncrementalMetric() oraz getNniUtils()

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        // Używamy this.incMetric z klasy bazowej
        this.bestDist = this.incMetric.getCurrentDistance();
        exploreNniRecursive(currentTree.getRoot());
    }

    // USUNIĘTO parametr incMetric, używamy bezpośrednio this.incMetric
    private void exploreNniRecursive(Node parent) {
        if (parent.isLeaf()) return;

        for (int i = 0; i < parent.getChildCount(); i++) {
            Node child = parent.getChild(i);
            if (child.isLeaf()) continue;

            Node sibling = findSibling(parent, child);
            if (sibling != null && child.getChildCount() >= 2) {
                Node g1 = child.getChild(0);
                Node g2 = child.getChild(1);

                // --- SĄSIAD 1 ---
                NniMove move1 = new NniMove(g1, sibling);
                double dist1 = this.incMetric.applyNni(move1);
                checkImprovement(dist1, move1);
                this.incMetric.undoNni(move1);

                // --- SĄSIAD 2 ---
                NniMove move2 = new NniMove(g2, sibling);
                double dist2 = this.incMetric.applyNni(move2);
                checkImprovement(dist2, move2);
                this.incMetric.undoNni(move2);
            }

            exploreNniRecursive(child);
        }
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof NniMove) {
            return nniUtils.applyPhysicalMove(tree, (NniMove) move);
        }
        return tree;
    }

    @Override
    protected double commitMoveToMetric(TreeMove move) {
        if (move instanceof NniMove) {
            return this.incMetric.applyNni((NniMove) move);
        }
        return this.incMetric.getCurrentDistance();
    }

    private Node findSibling(Node parent, Node child) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            Node n = parent.getChild(i);
            if (n != child) return n;
        }
        return null;
    }

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        Tree currentTree = tree1;
        this.improved = true;
        int totalSteps = 0;

        // Inicjalizacja metryki na start
        this.incMetric.initCalculationState(currentTree, tree2);
        double currentDist = this.incMetric.getCurrentDistance();

        while (this.improved && currentDist > 0) {
            this.improved = false;
            this.bestDist = currentDist;
            this.bestMove = null;

            // Szukamy najlepszego sąsiada
            searchNeighborhood(currentTree);

            if (this.improved && this.bestMove != null) {
                // 1. Zmiana w metryce "w locie" (bez twardego resetu, bo to szybkie NNI)
                currentDist = commitMoveToMetric(this.bestMove);
                this.incMetric.commit();

                // 2. Fizyczna zmiana struktury drzewa w PAL
                currentTree = applyPhysicalMove(currentTree, this.bestMove);
                totalSteps++;
            }
        }
        return (currentDist == 0) ? (double) totalSteps : Double.POSITIVE_INFINITY;
    }

    @Override
    public String getName() {
        return "NNI_Heuristic_RF";
    }
}