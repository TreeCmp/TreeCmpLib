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
import java.util.TreeSet;

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

/*    @Test
    public void testEveryMoveMatchesClassicOracleWithDebbug() {
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

                    // --- DIAGNOSTYKA ROZBICIA NA SKŁADOWE T1 I T2 ---
                    System.out.println("--- SZCZEGÓŁY METRYKI INKREMENTALNEJ ---");
                    incMetric.debugPrintMove(prune, reroot, target);

                    assertEquals(expected, dist, 1e-6,
                            String.format("Mismatch at move: P=%s, R=%s, T=%s",
                                    formatNode(prune), formatNode(reroot), formatNode(target)));
                }
            }
        });
    }*/

    @Test
    public void debugSpecificMismatch() {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(10, 42L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(10, 84L);
        ((SimpleTree) t1).createNodeList();
        ((SimpleTree) t2).createNodeList();

        TbrUtils utils = new TbrUtils();
        RFClusterMetric classicMetric = new RFClusterMetric();
        RFClusterIncrementalMetric incMetric = new RFClusterIncrementalMetric();
        incMetric.initCalculationState(t1, t2);

        // 1. Znalezienie węzłów P=#2, R=L8, T=#8
        Node pNode = null, rNode = null, tNode = null;
        for (int i = 0; i < t1.getInternalNodeCount(); i++) {
            Node n = t1.getInternalNode(i);
            if (formatNode(n).equals("#2")) pNode = n;
            if (formatNode(n).equals("#8")) tNode = n;
        }
        for (int i = 0; i < t1.getExternalNodeCount(); i++) {
            Node n = t1.getExternalNode(i);
            if (formatNode(n).equals("L8")) rNode = n;
        }

        System.out.println("=================================================================");
        System.out.printf("DIAGNOSTYKA DLA RUCHU: P=%s, R=%s, T=%s%n",
                formatNode(pNode), formatNode(rNode), formatNode(tNode));
        System.out.println("=================================================================");

        // 2. Wyrocznia: Drzewo fizyczne
        Tree physical = utils.createTbrTree(t1, pNode, rNode, tNode);
        ((SimpleTree) physical).createNodeList();
        double expectedDist = classicMetric.getDistance(physical, t2);

        incMetric.setPrunedState(pNode, null);
        double actualDist = incMetric.computeExactDistance(pNode, rNode, tNode);

        System.out.printf("DYSTANS: Expected=%.1f | Actual=%.1f%n", expectedDist, actualDist);
        System.out.println("DRZEWO FIZYCZNE T':  " + physical);
        System.out.println("DRZEWO DOCELOWE T2:  " + t2);
        System.out.println("-----------------------------------------------------------------");

        Set<Set<String>> targetClusters = extractNamedClusters(t2);
        Set<Set<String>> physicalClusters = extractNamedClusters(physical);

        System.out.println("KLASTY W FIZYCZNYM DRZEWIE (Rozmiar: " + physicalClusters.size() + "):");
        for (Set<String> c : physicalClusters) {
            boolean inTarget = targetClusters.contains(c);
            System.out.printf("   [%s] %s%n", inTarget ? "WSPÓLNY Z T2" : "unikalny", c);
        }
        System.out.println("-----------------------------------------------------------------");

        System.out.println("KLASTY W DOCELOWYM DRZEWIE T2 (Rozmiar: " + targetClusters.size() + "):");
        for (Set<String> c : targetClusters) {
            System.out.println("   -> " + c);
        }
        System.out.println("=================================================================");
    }

    private static Set<Set<String>> extractNamedClusters(Tree tree) {
        Set<Set<String>> clusters = new HashSet<>();
        int N = tree.getExternalNodeCount();
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            Node n = tree.getInternalNode(i);
            if (n.isRoot()) continue;
            Set<String> leafNames = new TreeSet<>();
            collectLeafNames(n, leafNames);
            if (leafNames.size() > 1 && leafNames.size() < N) {
                clusters.add(leafNames);
            }
        }
        return clusters;
    }

    private static void collectLeafNames(Node n, Set<String> names) {
        if (n.isLeaf()) {
            names.add(n.getIdentifier().getName());
        } else {
            for (int i = 0; i < n.getChildCount(); i++) {
                collectLeafNames(n.getChild(i), names);
            }
        }
    }
}