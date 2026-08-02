package treecmp.heuristics.ecr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR;
import treecmp.heuristics.moves.Ecr3Move;
import treecmp.heuristics.moves.TreeMove;
import treecmp.metrics.IncrementalMetric;

import java.util.List;

public class Ecr3IncrementalHeuristic extends EcrIncrementalHeuristic {

    private final SubtreeEcr3Utils ecr3Utils;
    private final String metricShortName;

    // 1. Konstruktor podstawowy (bez filtra)
    public Ecr3IncrementalHeuristic(IncrementalMetric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    // 2. Konstruktor z filtrem (np. RF tie-breaker)
    public Ecr3IncrementalHeuristic(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(metric, primaryMetric, metricShortName);
        this.metricShortName = metricShortName;
        this.ecr3Utils = new SubtreeEcr3Utils(!metric.isRooted());
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        IncrementalMetric activeMetric = (this.primaryMetric != null) ? this.primaryMetric : this.incMetric;

        this.tiedMoves.clear();
        this.bestDist = Double.POSITIVE_INFINITY;
        int intNum = currentTree.getInternalNodeCount();

        for (int i = 0; i < intNum; i++) {
            Node rootOfCluster = currentTree.getInternalNode(i);

            // W PAL każdy węzeł ma 2 dzieci w dół. Aby uzyskać 5 poddrzew brzegowych,
            // klaster 3-sECR musi zawsze składać się z 4 węzłów wewnętrznych (size = 4).
            int targetClusterSize = 4;
            List<List<Node>> clusters = ecr3Utils.getClusters(rootOfCluster, targetClusterSize);

            for (List<Node> cluster : clusters) {
                List<Node> subtreesList = ecr3Utils.getBoundarySubtrees(cluster);
                if (subtreesList.size() != 5) continue;

                Node[] s = subtreesList.toArray(new Node[0]);
                TopologyTemplate3sECR originalSignature = ecr3Utils.extractSignature(rootOfCluster, cluster, subtreesList);

                evaluateEcr3Cluster(cluster, s, originalSignature, activeMetric);
            }
        }
    }

    private void evaluateEcr3Cluster(List<Node> cluster, Node[] boundarySubtrees, TopologyTemplate3sECR originalSignature, IncrementalMetric activeMetric) {
        for (TopologyTemplate3sECR template : SubtreeEcr3Utils.getTemplates()) {
            if (template.isIsomorphic(originalSignature)) continue;

            double dist = activeMetric.evaluate3sEcrMove(cluster, boundarySubtrees, template);
            checkImprovementWithTies(dist, new Ecr3Move(cluster, boundarySubtrees, template));
        }
    }

    @Override
    protected double evaluateMoveOnMetric(IncrementalMetric activeMetric, TreeMove move) {
        Ecr3Move m = (Ecr3Move) move;
        return activeMetric.evaluate3sEcrMove(m.cluster, m.boundarySubtrees, m.template);
    }

    @Override
    protected double commitMoveOnMetric(IncrementalMetric activeMetric, TreeMove move) {
        Ecr3Move m = (Ecr3Move) move;
        return activeMetric.commit3sEcrMove(m.cluster, m.boundarySubtrees, m.template);
    }

    @Override
    protected double commitMoveToMetric(TreeMove move) {
        return commitMoveOnMetric(this.incMetric, move);
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        return ecr3Utils.applyPhysicalMove(tree, (Ecr3Move) move);
    }

    @Override
    public String getName() {
        return "3sECR_IncrementalHeuristic_" + metricShortName;
    }
}