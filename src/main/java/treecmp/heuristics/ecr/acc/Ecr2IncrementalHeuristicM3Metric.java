package treecmp.heuristics.ecr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.Ecr2Move;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR;
import treecmp.metrics.topological.acc.M3IncrementalMetric;

import java.util.Arrays;

public class Ecr2IncrementalHeuristicM3Metric extends IncrementalHeuristicBaseMetric {

    private final SubtreeEcr2Utils ecr2Utils;

    public Ecr2IncrementalHeuristicM3Metric() {
        super(false, new M3IncrementalMetric());
        this.ecr2Utils = new SubtreeEcr2Utils(true);
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        this.bestDist = this.incMetric.getCurrentDistance();
        int intNum = currentTree.getInternalNodeCount();

        for (int i = 0; i < intNum; i++) {
            Node node = currentTree.getInternalNode(i);

            if (node != currentTree.getRoot()) {
                Node c = node.getParent();
                if (c != null && !c.isLeaf()) {
                    Node p = c.getParent();
                    if (p != null && !p.isLeaf()) {
                        Node[] bounds = new Node[]{ getOtherChild(p, c), getOtherChild(c, node), node.getChild(0), node.getChild(1) };
                        evaluateEcr2Cluster(p, c, node, bounds, false);
                    }
                }
            }

            java.util.List<Node> intChildren = new java.util.ArrayList<>();
            for (int j = 0; j < node.getChildCount(); j++) if (!node.getChild(j).isLeaf()) intChildren.add(node.getChild(j));
            if (intChildren.size() >= 2) {
                for (int a = 0; a < intChildren.size(); a++) {
                    for (int b = a + 1; b < intChildren.size(); b++) {
                        Node m1 = intChildren.get(a); Node m2 = intChildren.get(b);
                        Node[] bounds = new Node[]{m1.getChild(0), m1.getChild(1), m2.getChild(0), m2.getChild(1)};
                        evaluateEcr2Cluster(node, m1, m2, bounds, true);
                    }
                }
            }
        }
    }

    private void evaluateEcr2Cluster(Node top, Node m1, Node m2, Node[] bounds, boolean isFork) {
        for (TopologyTemplate2sECR template : SubtreeEcr2Utils.getTemplates()) {
            if (template.isFork == isFork && Arrays.equals(template.indices, new int[]{0, 1, 2, 3})) continue;
            double dist = this.incMetric.evaluate2sEcrMove(top, m1, m2, bounds, template);
            if (dist < this.bestDist) {
                this.bestDist = dist;
                this.improved = true;
                this.bestMove = new Ecr2Move(top, m1, m2, bounds, template);
            }
        }
    }

    private Node getOtherChild(Node parent, Node exclude) {
        for (int i = 0; i < parent.getChildCount(); i++) if (parent.getChild(i) != exclude) return parent.getChild(i);
        return null;
    }

    @Override protected Tree applyPhysicalMove(Tree tree, TreeMove move) { return ecr2Utils.applyPhysicalMove(tree, (Ecr2Move) move); }

    @Override protected double commitMoveToMetric(TreeMove move) {
        Ecr2Move m = (Ecr2Move) move;
        return this.incMetric.commit2sEcrMove(m.top, m.m1, m.m2, m.boundarySubtrees, m.template);
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
        // POPRAWKA: Jeśli algorytm utknie (currentDist > 0), zwracamy Nieskończoność (brak drogi)
        return (currentDist == 0) ? (double) totalSteps : Double.POSITIVE_INFINITY;
    }

    @Override public String getName() { return "2sECR_IncrementalHeuristic_M3"; }
}