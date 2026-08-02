package treecmp.heuristics.ecr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.moves.Ecr2Move;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR;
import treecmp.metrics.IncrementalMetric;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class Ecr2IncrementalHeuristic extends EcrIncrementalHeuristic {

    private final SubtreeEcr2Utils ecr2Utils;

    public Ecr2IncrementalHeuristic(IncrementalMetric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    public Ecr2IncrementalHeuristic(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(metric, primaryMetric, metricShortName);
        this.ecr2Utils = new SubtreeEcr2Utils(!metric.isRooted());
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;
        this.tiedMoves.clear();
        this.bestDist = Double.POSITIVE_INFINITY;

        int intNum = currentTree.getInternalNodeCount();
        for (int i = 0; i < intNum; i++) {
            Node node = currentTree.getInternalNode(i);

            if (node != currentTree.getRoot()) {
                Node c = node.getParent();
                if (c != null && !c.isLeaf()) {
                    Node p = c.getParent();
                    if (p != null && !p.isLeaf()) {
                        Node[] bounds = new Node[]{ getOtherChild(p, c), getOtherChild(c, node), node.getChild(0), node.getChild(1) };
                        evaluateEcr2Cluster(p, c, node, bounds, false, activeMetric);
                    }
                }
            }

            List<Node> intChildren = new ArrayList<>();
            for (int j = 0; j < node.getChildCount(); j++) if (!node.getChild(j).isLeaf()) intChildren.add(node.getChild(j));
            if (intChildren.size() >= 2) {
                for (int a = 0; a < intChildren.size(); a++) {
                    for (int b = a + 1; b < intChildren.size(); b++) {
                        Node m1 = intChildren.get(a); Node m2 = intChildren.get(b);
                        Node[] bounds = new Node[]{m1.getChild(0), m1.getChild(1), m2.getChild(0), m2.getChild(1)};
                        evaluateEcr2Cluster(node, m1, m2, bounds, true, activeMetric);
                    }
                }
            }
        }
    }

    private void evaluateEcr2Cluster(Node top, Node m1, Node m2, Node[] bounds, boolean isFork, IncrementalMetric activeMetric) {
        for (TopologyTemplate2sECR template : SubtreeEcr2Utils.getTemplates()) {
            if (template.isFork == isFork && Arrays.equals(template.indices, new int[]{0, 1, 2, 3})) continue;
            double dist = activeMetric.evaluate2sEcrMove(top, m1, m2, bounds, template);
            checkImprovementWithTies(dist, new Ecr2Move(top, m1, m2, bounds, template));
        }
    }

    private Node getOtherChild(Node parent, Node exclude) {
        for (int i = 0; i < parent.getChildCount(); i++) if (parent.getChild(i) != exclude) return parent.getChild(i);
        return null;
    }

    @Override
    protected double evaluateMoveOnMetric(IncrementalMetric metric, TreeMove move) {
        Ecr2Move m = (Ecr2Move) move;
        return metric.evaluate2sEcrMove(m.top, m.m1, m.m2, m.boundarySubtrees, m.template);
    }

    @Override
    protected double commitMoveOnMetric(IncrementalMetric metric, TreeMove move) {
        Ecr2Move m = (Ecr2Move) move;
        return metric.commit2sEcrMove(m.top, m.m1, m.m2, m.boundarySubtrees, m.template);
    }

    @Override protected double commitMoveToMetric(TreeMove move) { return commitMoveOnMetric(this.incMetric, move); }
    @Override protected Tree applyPhysicalMove(Tree tree, TreeMove move) { return ecr2Utils.applyPhysicalMove(tree, (Ecr2Move) move); }
    @Override public String getName() { return "2sECR_IncrementalHeuristic_" + metricShortName; }
}