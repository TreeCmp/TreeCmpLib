package treecmp.heuristics.spr.acc;

import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.moves.SprMove;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.MCIncrementalMetric;
import treecmp.metrics.topological.acc.MPIncrementalMetric;
import treecmp.metrics.topological.acc.MSIncrementalMetric;

public class SprIncrementalHeuristicMetric extends IncrementalHeuristicBaseMetric {

    protected final ClassicSprWalker standardWalker;
    private final IncrementalSprWalker rootedWalker;
    protected final SprUtils sprUtils;
    private final String metricShortName;

    public SprIncrementalHeuristicMetric(IncrementalMetric metric, String metricShortName) {
        super(metric.isRooted(), metric);
        this.metricShortName = metricShortName;
        this.standardWalker = new ClassicSprWalker();
        this.rootedWalker = new IncrementalSprWalker();
        this.sprUtils = new SprUtils();
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        // Dodajemy MPIncrementalMetric do warunku korzystającego z Continuous Walkera!
        if (this.incMetric instanceof MSIncrementalMetric ||
                this.incMetric instanceof MPIncrementalMetric ||
                this.incMetric instanceof MCIncrementalMetric) {
            rootedWalker.walk(currentTree, (IncrementalSprWalker.RootedMetric) this.incMetric, (currentDist, movingNode, targetNode) -> {
                checkImprovement(currentDist, new SprMove(movingNode, targetNode));
            });
        } else {
            standardWalker.walk(currentTree, this.incMetric, (currentDist, movingNode, targetNode) -> {
                checkImprovement(currentDist, new SprMove(movingNode, targetNode));
            });
        }
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof SprMove) {
            SprMove sprMove = (SprMove) move;
            Tree newTree = sprUtils.applyPhysicalSprMove(tree, sprMove);
            if (newTree != null) {
                // POPRAWKA: Prawidłowe wywołania z incMetric oraz TreeCmpUtils
                if (this.incMetric.isRooted()) {
                    newTree.getRoot().setBranchLength(0.0);
                } else {
                    TreeCmpUtils.unrootTreeIfNeeded(newTree);
                }
                newTree.createNodeList();
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

    @Override public boolean isRooted() { return this.incMetric.isRooted(); }
    @Override public String getName() { return "Heur. SPR " + this.metricShortName; }
    @Override public String getCommandLineName() { return "hspr_" + this.incMetric.getCommandLineName(); }
}