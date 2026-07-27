package treecmp.heuristics.ecr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.Ecr3Move;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR;
import treecmp.metrics.IncrementalMetric;

import java.util.List;
import java.util.ArrayList;

public class Ecr3IncrementalHeuristic extends IncrementalHeuristicBaseMetric {

    private final SubtreeEcr3Utils ecr3Utils;
    private final String metricShortName;
    protected IncrementalMetric primaryMetric;

    protected List<TreeMove> tiedMoves = new ArrayList<>();
    protected double currentPrimaryBestDist;

    // 1. Podstawowy konstruktor
    public Ecr3IncrementalHeuristic(IncrementalMetric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    // 2. Rozszerzony konstruktor (Tie-Breaker)
    public Ecr3IncrementalHeuristic(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(metric.isRooted(), metric);
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
        this.ecr3Utils = new SubtreeEcr3Utils(!metric.isRooted());
    }

    private void checkImprovementWithTies(double currentDist, TreeMove move) {
        if (currentDist < this.currentPrimaryBestDist) {
            this.currentPrimaryBestDist = currentDist;
            this.tiedMoves.clear();
            this.tiedMoves.add(move);
        } else if (currentDist == this.currentPrimaryBestDist && currentDist != Double.POSITIVE_INFINITY) {
            this.tiedMoves.add(move);
        }
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;

        this.tiedMoves.clear();
        this.currentPrimaryBestDist = Double.POSITIVE_INFINITY;
        int intNum = currentTree.getInternalNodeCount();

        for (int i = 0; i < intNum; i++) {
            Node rootOfCluster = currentTree.getInternalNode(i);
            List<List<Node>> clusters = ecr3Utils.getClusters(rootOfCluster, 4);

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

    @Override protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        return ecr3Utils.applyPhysicalMove(tree, (Ecr3Move) move);
    }

    @Override protected double commitMoveToMetric(TreeMove move) {
        Ecr3Move m = (Ecr3Move) move;
        return this.incMetric.commit3sEcrMove(m.cluster, m.boundarySubtrees, m.template);
    }

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        Tree currentTree = tree1;
        this.improved = true;
        int totalSteps = 0;
        int maxSteps = 1000;

        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;

        activeMetric.initCalculationState(currentTree, tree2);
        if (primaryMetric != null) {
            this.incMetric.initCalculationState(currentTree, tree2);
        }

        double currentDist = activeMetric.getCurrentDistance();

        while (this.improved && currentDist > 0 && totalSteps < maxSteps) {
            this.improved = false;

            searchNeighborhood(currentTree);

            if (!this.tiedMoves.isEmpty() && this.currentPrimaryBestDist < currentDist) {
                TreeMove bestMove = null;

                if (primaryMetric == null || tiedMoves.size() == 1) {
                    bestMove = tiedMoves.get(0);
                }
                else {
                    double bestHeavyDist = Double.POSITIVE_INFINITY;
                    for (TreeMove tm : tiedMoves) {
                        Ecr3Move em = (Ecr3Move) tm;
                        double heavyDist = this.incMetric.evaluate3sEcrMove(em.cluster, em.boundarySubtrees, em.template);
                        if (heavyDist < bestHeavyDist) {
                            bestHeavyDist = heavyDist;
                            bestMove = em;
                        }
                    }
                }

                if (bestMove != null) {
                    Ecr3Move finalMove = (Ecr3Move) bestMove;

                    currentDist = activeMetric.commit3sEcrMove(finalMove.cluster, finalMove.boundarySubtrees, finalMove.template);
                    activeMetric.commit();

                    if (primaryMetric != null) {
                        this.incMetric.commit3sEcrMove(finalMove.cluster, finalMove.boundarySubtrees, finalMove.template);
                        this.incMetric.commit();
                    }

                    currentTree = applyPhysicalMove(currentTree, finalMove);
                    totalSteps++;
                    this.improved = true;
                }
            }
        }
        return (currentDist == 0) ? (double) totalSteps : Double.POSITIVE_INFINITY;
    }

    @Override public String getName() { return "3sECR_IncrementalHeuristic_" + metricShortName; }
}