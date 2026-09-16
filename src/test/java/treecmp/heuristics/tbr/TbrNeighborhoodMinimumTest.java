package treecmp.heuristics.tbr;

import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TbrNeighborhoodMinimumTest {

    private static final double EPSILON = 1e-9;

    @Test
    public void testRfClusterFindsGlobalMinimumInTbrNeighborhood() {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(8, 12345L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(8, 67890L);
        assignNumbers(t1);
        assignNumbers(t2);

        RFClusterMetric classicRfc = new RFClusterMetric();
        TbrUtils utils = new TbrUtils();

        // 1. Bruteforce: obliczamy prawdziwe minimum w całym otoczeniu klasyczną wyrocznią
        double bestClassicDist = classicRfc.getDistance(t1, t2);
        List<Node> allNodes = getAllNodes(t1);

        for (Node prune : allNodes) {
            if (prune.isRoot() || prune.getParent() == null) continue;

            List<Node> rerootNodes = new ArrayList<>();
            collectSubtree(prune, rerootNodes);

            List<Node> targetNodes = new ArrayList<>();
            collectOutside(t1.getRoot(), prune, targetNodes);

            for (Node reroot : rerootNodes) {
                for (Node target : targetNodes) {
                    if (reroot == prune && target == prune.getParent()) continue;
                    if (utils.isValidTbrMove(prune, reroot, target)) {
                        Tree neighbor = utils.createTbrTree(t1, prune, reroot, target);
                        if (neighbor != null) {
                            assignNumbers(neighbor);
                            double d = classicRfc.getDistance(neighbor, t2);
                            if (d < bestClassicDist) {
                                bestClassicDist = d;
                            }
                        }
                    }
                }
            }
        }

        // 2. Inkrementacja: sprawdzamy minimum znalezione w jednym kroku Steepest Descent
        RFClusterIncrementalMetric incMetric = new RFClusterIncrementalMetric();
        TbrIncrementalHeuristic heuristic = new TbrIncrementalHeuristic(incMetric, "RFC");

        double bestIncrDist = heuristic.evaluateSingleStep(new SimpleTree(t1), t2);

        // 3. Asercja: minimum wyznaczone inkrementalnie musi być identyczne z wyrocznią
        assertTrue(bestIncrDist < classicRfc.getDistance(t1, t2),
                "Heurystyka TBR musi znaleźć ruch poprawiający (zejście w dół)!");
        assertEquals(bestClassicDist, bestIncrDist, EPSILON,
                String.format("Rozbieżność minimum otoczenia TBR! Classic=%.2f vs Incr=%.2f",
                        bestClassicDist, bestIncrDist));
    }

    private static void assignNumbers(Tree t) {
        if (t instanceof SimpleTree) {
            ((SimpleTree) t).createNodeList();
        }
    }

    private static List<Node> getAllNodes(Tree tree) {
        List<Node> list = new ArrayList<>();
        collectSubtree(tree.getRoot(), list);
        return list;
    }

    private static void collectSubtree(Node n, List<Node> list) {
        list.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collectSubtree(n.getChild(i), list);
    }

    private static void collectOutside(Node curr, Node exclude, List<Node> list) {
        if (curr == exclude) return;
        list.add(curr);
        for (int i = 0; i < curr.getChildCount(); i++) collectOutside(curr.getChild(i), exclude, list);
    }
}