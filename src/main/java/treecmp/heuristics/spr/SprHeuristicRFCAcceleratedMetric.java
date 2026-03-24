package treecmp.heuristics.spr;

import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.moves.SprMove;
import treecmp.heuristics.nni.NniUtils;
import treecmp.metrics.topological.RFClusterIncrementalMetric;

public class SprHeuristicRFCAcceleratedMetric extends IncrementalHeuristicBaseMetric {

    private final SprNeighborhoodWalker walker;
    private final NniUtils nniUtils;

    public SprHeuristicRFCAcceleratedMetric() {
        super(true, new RFClusterIncrementalMetric());
        this.walker = new SprNeighborhoodWalker();
        this.nniUtils = new NniUtils(true);
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        // Używamy this.incMetric z klasy bazowej
        walker.walk(currentTree, this.incMetric, (currentDist, movingNode, targetNode) -> {
            System.out.println("Move check: " + currentDist);
            checkImprovement(currentDist, new SprMove(movingNode, targetNode));
        });
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof SprMove) {
            SprMove sm = (SprMove) move;
            Tree newTree = new SprUtils().createSprTree(tree, sm.movingNode, sm.targetNode);
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

                // todo: sprawdzić czy to jest potrzebne
                TreeUtils.computeParentPointers(currentTree.getRoot());

                this.incMetric.initCalculationState(currentTree, tree2);
                currentDist = this.incMetric.getCurrentDistance();
            }
        }
        return (currentDist == 0) ? (double) totalSteps : Double.POSITIVE_INFINITY;
    }
}