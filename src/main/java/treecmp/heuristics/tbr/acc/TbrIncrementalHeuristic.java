package treecmp.heuristics.tbr.acc;

import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.moves.TbrMove;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.metrics.IncrementalMetric;

/**
 * Uniwersalna, akcelerowana heurystyka (Steepest Descent) dla otoczenia TBR.
 * Używa szybkiego przeliczania metryk inkrementalnych.
 */
public class TbrIncrementalHeuristic extends IncrementalHeuristicBaseMetric {

    private final TbrNeighborhoodWalker walker;
    private final String metricShortName;

    // Uniwersalny konstruktor przyjmujący dowolną metrykę inkrementalną!
    public TbrIncrementalHeuristic(IncrementalMetric metric, String metricShortName) {
        super(true, metric); // true dla drzew ukorzenionych (TBR)
        this.walker = new TbrNeighborhoodWalker();
        this.metricShortName = metricShortName;
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        // Używamy this.incMetric z klasy bazowej.
        // Wizytator TBR przyjmuje 3 parametry węzłowe: odcięty korzeń, nowy wirtualny korzeń i cel
        walker.walk(currentTree, this.incMetric, (currentDist, pruneNode, rerootNode, targetNode) -> {
            // Rejestrujemy ruch TBR zawierający informację o przekorzenieniu
            checkImprovement(currentDist, new TbrMove(pruneNode, rerootNode, targetNode));
        });
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof TbrMove) {
            TbrMove tm = (TbrMove) move;

            // Fizycznie tworzy drzewo po rTBR: odcina pruneNode, zmienia korzeń na rerootNode i wpina w targetNode.
            Tree newTree = new TbrUtils().createTbrTree(tree, tm.movingNode, tm.rerootNode, tm.targetNode);

            if (newTree != null) {
                if (newTree instanceof pal.tree.SimpleTree) {
                    ((pal.tree.SimpleTree) newTree).createNodeList();
                }
                return newTree;
            }
        }
        return tree;
    }

    @Override
    protected double commitMoveToMetric(TreeMove move) {
        return this.incMetric.getCurrentDistance();
    }

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        Tree currentTree = tree1;
        this.improved = true;
        int totalSteps = 0;

        this.incMetric.initCalculationState(currentTree, tree2);
        double currentDist = this.incMetric.getCurrentDistance();

        while (this.improved && currentDist > 0) {
            this.improved = false;
            this.bestDist = currentDist;
            this.bestMove = null;

            searchNeighborhood(currentTree);

            if (this.improved && this.bestMove != null) {
                currentTree = applyPhysicalMove(currentTree, this.bestMove);
                totalSteps++;

                // Krytyczne ręczne przeliczanie rodziców (zabezpieczenie przed biblioteką PAL)
                TreeUtils.computeParentPointers(currentTree.getRoot());

                this.incMetric.initCalculationState(currentTree, tree2);
                currentDist = this.incMetric.getCurrentDistance();
            }
        }
        return (currentDist == 0) ? (double) totalSteps : Double.POSITIVE_INFINITY;
    }

    @Override
    public String getName() {
        return "TBR_IncrementalHeuristic_" + metricShortName;
    }
}