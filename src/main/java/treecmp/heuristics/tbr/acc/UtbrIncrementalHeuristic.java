package treecmp.heuristics.tbr.acc;

import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.moves.TbrMove;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.IncrementalMetric;

/**
 * Uniwersalna, akcelerowana heurystyka (Steepest Descent) dla otoczenia uTBR.
 * Dedykowana dla drzew nieukorzenionych i obsługiwana przez UtbrNeighborhoodWalker.
 */
public class UtbrIncrementalHeuristic extends IncrementalHeuristicBaseMetric {

    private final UtbrNeighborhoodWalker walker;
    private final String metricShortName;

    // Uniwersalny konstruktor dla metryk nieukorzenionych (np. MS, uRF, MT)
    public UtbrIncrementalHeuristic(IncrementalMetric metric, String metricShortName) {
        super(false, metric); // FALSE = drzewa nieukorzenione
        this.walker = new UtbrNeighborhoodWalker();
        this.metricShortName = metricShortName;
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        walker.walk(currentTree, this.incMetric, (currentDist, pruneNode, rerootNode, targetNode) -> {
            checkImprovement(currentDist, new TbrMove(pruneNode, rerootNode, targetNode));
        });
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof TbrMove) {
            TbrMove tm = (TbrMove) move;

            // Używamy UTbrUtils, aby bezpiecznie przepiąć strukturę bez niszczenia
            // wewnętrznej trifurkacji korzenia, która występuje w drzewach nieukorzenionych.
            Tree newTree = new UTbrUtils().createUtbrTree(tree, tm.movingNode, tm.rerootNode, tm.targetNode);

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

                // Krytyczne przeliczanie rodziców, ratujące wewnętrzne wskaźniki PAL
                TreeUtils.computeParentPointers(currentTree.getRoot());

                this.incMetric.initCalculationState(currentTree, tree2);
                currentDist = this.incMetric.getCurrentDistance();
            }
        }
        // Zwraca łączną liczbę wykonanych kroków w dół w przestrzeni uTBR
        return (currentDist == 0) ? (double) totalSteps : Double.POSITIVE_INFINITY;
    }

    @Override
    public String getName() {
        return "uTBR_IncrementalHeuristic_" + metricShortName;
    }
}