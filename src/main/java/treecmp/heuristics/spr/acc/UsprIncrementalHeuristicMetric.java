package treecmp.heuristics.spr.acc;

import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.moves.SprMove;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.IncrementalMetric;

public class UsprIncrementalHeuristicMetric extends IncrementalHeuristicBaseMetric {

    protected final UsprNeighborhoodWalker walker;
    protected final UsprUtils usprUtils;
    private final String metricShortName;

    // WZORZEC KOMPOZYCJI: Uniwersalny wstrzykiwacz metryki
    public UsprIncrementalHeuristicMetric(IncrementalMetric metric, String metricShortName) {
        super(metric.isRooted(), metric);
        this.metricShortName = metricShortName;
        this.walker = new UsprNeighborhoodWalker();
        this.usprUtils = new UsprUtils();
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        walker.walk(currentTree, this.incMetric, (currentDist, movingNode, targetNode) -> {
            checkImprovement(currentDist, new SprMove(movingNode, targetNode));
        });
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof SprMove) {
            SprMove sm = (SprMove) move;
            Tree newTree = usprUtils.createUsprTree(tree, sm.movingNode, sm.targetNode);
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

                TreeUtils.computeParentPointers(currentTree.getRoot());

                this.incMetric.initCalculationState(currentTree, tree2);
                currentDist = this.incMetric.getCurrentDistance();
            }
        }
        return (currentDist == 0) ? (double) totalSteps : Double.POSITIVE_INFINITY;
    }

    @Override
    public String getName() {
        return "uSPR_IncrementalHeuristic_" + metricShortName;
    }
}