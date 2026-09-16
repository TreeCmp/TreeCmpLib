package treecmp.heuristics.tbr;

import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.tbr.acc.IncrementalTbrWalker;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RFClusterWalkerParityTest {

    private static String formatNode(Node n) {
        if (n == null) return "null";
        String name = n.getIdentifier() != null ? n.getIdentifier().getName() : "";
        return name.isEmpty() ? "#" + n.getNumber() : name;
    }

    private static Set<BitSet> extractPhysicalClusters(Tree tree) {
        Set<BitSet> clusters = new HashSet<>();
        int N = tree.getExternalNodeCount();
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            Node n = tree.getInternalNode(i);
            if (n.isRoot()) continue;
            BitSet bs = new BitSet(N);
            collectLeaves(n, bs);
            if (bs.cardinality() > 1 && bs.cardinality() < N) {
                clusters.add(bs);
            }
        }
        return clusters;
    }

    private static void collectLeaves(Node n, BitSet bs) {
        if (n.isLeaf()) {
            bs.set(n.getNumber());
        } else {
            for (int i = 0; i < n.getChildCount(); i++) collectLeaves(n.getChild(i), bs);
        }
    }

    @Test
    public void testEveryMoveMatchesClassicOracle() {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(10, 42L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(10, 84L);
        ((SimpleTree) t1).createNodeList();
        ((SimpleTree) t2).createNodeList();

        RFClusterIncrementalMetric incMetric = new RFClusterIncrementalMetric();
        RFClusterMetric classicMetric = new RFClusterMetric();
        TbrUtils utils = new TbrUtils();

        incMetric.initCalculationState(t1, t2);

        IncrementalTbrWalker walker = new IncrementalTbrWalker();
        walker.walk(t1, incMetric, (dist, prune, reroot, target) -> {
            Tree physical = utils.createTbrTree(t1, prune, reroot, target);
            if (physical != null) {
                ((SimpleTree) physical).createNodeList();
                double expected = classicMetric.getDistance(physical, t2);
                if (Math.abs(expected - dist) > 1e-6) {
                    System.out.println("=================================================");
                    System.out.printf("MISMATCH at P=%s, R=%s, T=%s | Expected=%.1f, Actual=%.1f%n",
                            formatNode(prune), formatNode(reroot), formatNode(target), expected, dist);
                    Set<BitSet> physicalClusters = extractPhysicalClusters(physical);
                    System.out.println("Liczba klastrow w fizycznym drzewie: " + physicalClusters.size());
                    assertEquals(expected, dist, 1e-6,
                            String.format("Mismatch at move: P=%s, R=%s, T=%s",
                                    formatNode(prune), formatNode(reroot), formatNode(target)));
                }
            }
        });
    }
}