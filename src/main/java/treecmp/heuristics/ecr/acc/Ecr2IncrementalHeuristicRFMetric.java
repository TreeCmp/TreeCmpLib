package treecmp.heuristics.ecr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.Ecr2Move;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR;
import treecmp.metrics.topological.acc.RFIncrementalMetric;

import java.util.Arrays;

public class Ecr2IncrementalHeuristicRFMetric extends IncrementalHeuristicBaseMetric {

    private final SubtreeEcr2Utils ecr2Utils;

    public Ecr2IncrementalHeuristicRFMetric() {
        super(false, new RFIncrementalMetric());
        this.ecr2Utils = new SubtreeEcr2Utils(true);
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        this.bestDist = this.incMetric.getCurrentDistance();
        int intNum = currentTree.getInternalNodeCount();

        for (int i = 0; i < intNum; i++) {
            Node node = currentTree.getInternalNode(i);

            // --- 1. Klaster CHAIN ---
            if (node != currentTree.getRoot()) {
                Node c = node.getParent();
                if (c != null && !c.isLeaf()) {
                    Node p = c.getParent();
                    if (p != null && !p.isLeaf()) {
                        Node[] boundarySubtrees = new Node[4];
                        boundarySubtrees[0] = getOtherChild(p, c);
                        boundarySubtrees[1] = getOtherChild(c, node);
                        boundarySubtrees[2] = node.getChild(0);
                        boundarySubtrees[3] = node.getChild(1);

                        evaluateEcr2Cluster(p, c, node, boundarySubtrees, false);
                    }
                }
            }

            // --- 2. Klaster FORK ---
            java.util.List<Node> internalChildren = new java.util.ArrayList<>();
            for (int j = 0; j < node.getChildCount(); j++) {
                Node child = node.getChild(j);
                if (!child.isLeaf()) internalChildren.add(child);
            }

            if (internalChildren.size() >= 2) {
                for (int a = 0; a < internalChildren.size(); a++) {
                    for (int b = a + 1; b < internalChildren.size(); b++) {
                        Node m1 = internalChildren.get(a);
                        Node m2 = internalChildren.get(b);

                        Node[] boundarySubtrees = new Node[4];
                        boundarySubtrees[0] = m1.getChild(0);
                        boundarySubtrees[1] = m1.getChild(1);
                        boundarySubtrees[2] = m2.getChild(0);
                        boundarySubtrees[3] = m2.getChild(1);

                        evaluateEcr2Cluster(node, m1, m2, boundarySubtrees, true);
                    }
                }
            }
        }
    }

    private void evaluateEcr2Cluster(Node top, Node m1, Node m2, Node[] boundarySubtrees, boolean isFork) {
        for (TopologyTemplate2sECR template : SubtreeEcr2Utils.getTemplates()) {
            if (template.isFork == isFork && Arrays.equals(template.indices, new int[]{0, 1, 2, 3})) {
                continue;
            }

            double dist = this.incMetric.evaluate2sEcrMove(top, m1, m2, boundarySubtrees, template);

            if (dist < this.bestDist) {
                this.bestDist = dist;
                this.improved = true;
                this.bestMove = new Ecr2Move(top, m1, m2, boundarySubtrees, template);
            }
        }
    }

    private Node getOtherChild(Node parent, Node exclude) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            Node child = parent.getChild(i);
            if (child != exclude) return child;
        }
        return null;
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof Ecr2Move) {
            return ecr2Utils.applyPhysicalMove(tree, (Ecr2Move) move);
        }
        return tree;
    }

    @Override
    protected double commitMoveToMetric(TreeMove move) {
        if (move instanceof Ecr2Move) {
            Ecr2Move ecr2Move = (Ecr2Move) move;
            return this.incMetric.commit2sEcrMove(
                    ecr2Move.top, ecr2Move.m1, ecr2Move.m2, ecr2Move.boundarySubtrees, ecr2Move.template
            );
        }
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
        return "2sECR_IncrementalHeuristic_RF";
    }
}