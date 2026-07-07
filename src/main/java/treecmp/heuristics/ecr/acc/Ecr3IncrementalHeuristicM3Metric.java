package treecmp.heuristics.ecr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.Ecr3Move;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR;
import treecmp.metrics.topological.acc.M3IncrementalMetric;

import java.util.List;

public class Ecr3IncrementalHeuristicM3Metric extends IncrementalHeuristicBaseMetric {

    private final SubtreeEcr3Utils ecr3Utils;

    public Ecr3IncrementalHeuristicM3Metric() {
        super(false, new M3IncrementalMetric());
        this.ecr3Utils = new SubtreeEcr3Utils(true);
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        this.bestDist = this.incMetric.getCurrentDistance();
        int intNum = currentTree.getInternalNodeCount();

        for (int i = 0; i < intNum; i++) {
            Node rootOfCluster = currentTree.getInternalNode(i);

            if (rootOfCluster == currentTree.getRoot()) continue;

            List<List<Node>> clusters = ecr3Utils.getClusters(rootOfCluster, 4);

            for (List<Node> cluster : clusters) {
                List<Node> subtreesList = ecr3Utils.getBoundarySubtrees(cluster);
                if (subtreesList.size() != 5) continue;

                Node[] s = subtreesList.toArray(new Node[0]);
                TopologyTemplate3sECR originalSignature = ecr3Utils.extractSignature(rootOfCluster, cluster, subtreesList);

                for (TopologyTemplate3sECR template : SubtreeEcr3Utils.getTemplates()) {
                    if (template.isIsomorphic(originalSignature)) continue;

                    double dist = this.incMetric.evaluate3sEcrMove(cluster, s, template);
                    if (dist < this.bestDist) {
                        this.bestDist = dist;
                        this.improved = true;
                        this.bestMove = new Ecr3Move(cluster, s, template);
                    }
                }
            }
        }
    }

    @Override protected Tree applyPhysicalMove(Tree tree, TreeMove move) { return ecr3Utils.applyPhysicalMove(tree, (Ecr3Move) move); }

    @Override protected double commitMoveToMetric(TreeMove move) {
        Ecr3Move m = (Ecr3Move) move;
        return this.incMetric.commit3sEcrMove(m.cluster, m.boundarySubtrees, m.template);
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

    @Override public String getName() { return "3sECR_IncrementalHeuristic_M3"; }
}