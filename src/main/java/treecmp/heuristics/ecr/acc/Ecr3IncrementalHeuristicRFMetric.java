package treecmp.heuristics.ecr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.Ecr3Move;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR;
import treecmp.metrics.topological.acc.RFIncrementalMetric;

import java.util.List;

public class Ecr3IncrementalHeuristicRFMetric extends IncrementalHeuristicBaseMetric {

    private final SubtreeEcr3Utils ecr3Utils;

    public Ecr3IncrementalHeuristicRFMetric() {
        super(false, new RFIncrementalMetric());
        // Zgodnie z RFMetric na splitach działamy na drzewach nieukorzenionych
        this.ecr3Utils = new SubtreeEcr3Utils(true);
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        this.bestDist = this.incMetric.getCurrentDistance();
        int intNum = currentTree.getInternalNodeCount();

        // Wyłapujemy wszystkie punkty startowe (najwyższe węzły w klastrach)
        for (int i = 0; i < intNum; i++) {
            Node rootOfCluster = currentTree.getInternalNode(i);

            // Pobieramy wszystkie klastry 4-węzłowe schodzące w dół
            List<List<Node>> clusters = ecr3Utils.getClusters(rootOfCluster, 4);

            for (List<Node> cluster : clusters) {
                // Musi być dokładnie 5 poddrzew na granicy (warunek rygorystycznego 3-sECR)
                List<Node> subtreesList = ecr3Utils.getBoundarySubtrees(cluster);
                if (subtreesList.size() != 5) continue;

                Node[] s = subtreesList.toArray(new Node[0]);
                TopologyTemplate3sECR originalSignature = ecr3Utils.extractSignature(rootOfCluster, cluster, subtreesList);

                evaluateEcr3Cluster(cluster, s, originalSignature);
            }
        }
    }

    private void evaluateEcr3Cluster(List<Node> cluster, Node[] boundarySubtrees, TopologyTemplate3sECR originalSignature) {
        for (TopologyTemplate3sECR template : SubtreeEcr3Utils.getTemplates()) {

            // Pomijamy oryginalną topologię
            if (template.isIsomorphic(originalSignature)) {
                continue;
            }

            // Sondowanie w czasie stałym
            double dist = this.incMetric.evaluate3sEcrMove(cluster, boundarySubtrees, template);

            if (dist < this.bestDist) {
                this.bestDist = dist;
                this.improved = true;
                this.bestMove = new Ecr3Move(cluster, boundarySubtrees, template);
            }
        }
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof Ecr3Move) {
            return ecr3Utils.applyPhysicalMove(tree, (Ecr3Move) move);
        }
        return tree;
    }

    @Override
    protected double commitMoveToMetric(TreeMove move) {
        if (move instanceof Ecr3Move) {
            Ecr3Move m = (Ecr3Move) move;
            return this.incMetric.commit3sEcrMove(m.cluster, m.boundarySubtrees, m.template);
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
        return "3sECR_IncrementalHeuristic_RF";
    }
}