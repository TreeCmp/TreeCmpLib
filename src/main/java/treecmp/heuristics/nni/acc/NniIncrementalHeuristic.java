package treecmp.heuristics.nni.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.nni.NniUtils;
import treecmp.metrics.IncrementalMetric;

public class NniIncrementalHeuristic extends IncrementalHeuristicBaseMetric {

    private final NniUtils nniUtils;
    private final String metricShortName;

    // WZORZEC KOMPOZYCJI: Wstrzykujemy dowolną metrykę inkrementalną
    public NniIncrementalHeuristic(IncrementalMetric metric, String metricShortName) {
        super(metric.isRooted(), metric);
        this.metricShortName = metricShortName;
        // Automatyczne ustawienie flagi unrooted na podstawie metryki!
        this.nniUtils = new NniUtils(!metric.isRooted());
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        this.bestDist = this.incMetric.getCurrentDistance();
        exploreNniRecursive(currentTree.getRoot());
    }

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

        this.incMetric.initCalculationState(currentTree, tree2);
        double currentDist = this.incMetric.getCurrentDistance();

        while (this.improved && currentDist > 0) {
            this.improved = false;
            this.bestDist = currentDist;
            this.bestMove = null;

            searchNeighborhood(currentTree);

            if (this.improved && this.bestMove != null) {
                currentDist = commitMoveToMetric(this.bestMove);
                this.incMetric.commit();
                currentTree = applyPhysicalMove(currentTree, this.bestMove);
                totalSteps++;
            }
        }
        return (currentDist == 0) ? (double) totalSteps : Double.POSITIVE_INFINITY;
    }

    @Override
    public String getName() {
        return "NNI_IncrementalHeuristic_" + metricShortName;
    }
}