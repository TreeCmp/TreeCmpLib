package treecmp.heuristics.spr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.spr.acc.IncrementalUsprWalker;
import treecmp.metrics.topological.RFMetric;
import treecmp.metrics.topological.acc.RFIncrementalMetric;
import treecmp.util.TestTreeFactory;
import java.util.BitSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import pal.tree.Node;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ekstremalny Fuzz Test udowadniający w 100% poprawność akceleratora uSPR dla drzew nieukorzenionych.
 * Wykorzystuje produkcyjny IncrementalUsprWalker, porównując wirtualny dystans z fizyczną wyrocznią RFMetric.
 */
public class RFIncrementalMetricFuzzTest {

    private RFIncrementalMetric incrementalMetric;
    private RFMetric classicMetric;
    private UsprUtils usprUtils;
    private IncrementalUsprWalker walker;

    private static final int FUZZ_ITERATIONS = 50;
    private static final double DELTA = 0.000001;
    private int totalEvaluations = 0;

    @BeforeEach
    void setUp() {
        incrementalMetric = new RFIncrementalMetric();
        classicMetric = new RFMetric();
        usprUtils = new UsprUtils();
        walker = new IncrementalUsprWalker();
    }

    private Tree createCleanCopy(Tree original) {
        SimpleTree copy = new SimpleTree(original);
        copy.createNodeList();
        TreeUtils.computeParentPointers(copy.getRoot());
        return copy;
    }

    @Test
    void testFuzzUsprDFSAcceleration() {
        Random rng = new Random(123);
        totalEvaluations = 0;

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            int numLeaves = 10 + rng.nextInt(41);

            // RF korzysta z drzew unrooted
            Tree baseTree = createCleanCopy(TestTreeFactory.randomUnrootedBinaryTree(numLeaves, rng.nextLong()));
            Tree targetTree = createCleanCopy(TestTreeFactory.randomUnrootedBinaryTree(numLeaves, rng.nextLong()));

            incrementalMetric.initCalculationState(baseTree, targetTree);
            double initialDist = incrementalMetric.getCurrentDistance();

            // Uruchomienie produkcyjnego wędrowca uSPR
            walker.walk(baseTree, incrementalMetric, (fastDist, pruneNode, regraftNode) -> {
                // Pełna wyrocznia topologiczna
                Tree physicalTree = usprUtils.createUsprTree(baseTree, pruneNode, regraftNode);
                double classicDist = classicMetric.getDistance(physicalTree, targetTree);

                if (Math.abs(classicDist - fastDist) > DELTA) {
                    System.out.println("=================================================");
                    System.out.printf("MISMATCH at prune=%s, regraft=%s | Expected=%.1f, Actual=%.1f%n",
                            pruneNode.getNumber(), regraftNode.getNumber(), classicDist, fastDist);

                    // Diagnostyka bipartycji
                    Set<BitSet> physSplits = extractNormalizedSplits(physicalTree, baseTree);
                    Set<BitSet> targetSplitsSet = extractNormalizedSplits(targetTree, baseTree);

                    Set<BitSet> trueShared = new HashSet<>(physSplits);
                    trueShared.retainAll(targetSplitsSet);

                    System.out.println("Liczba splits w physicalTree: " + physSplits.size());
                    System.out.println("Liczba prawdziwych wspólnych (Oracle shared): " + trueShared.size());
                    System.out.println("Oczekiwany dystans (N=" + baseTree.getExternalNodeCount() + "): " +
                            ((physSplits.size() + targetSplitsSet.size() - 2.0 * trueShared.size()) / 2.0));

                    assertEquals(classicDist, fastDist, DELTA,
                            "Błąd w obliczeniach komplementarnych (Bipartitions) w uSPR!");
                }
                totalEvaluations++;
            });

            assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                    "Błąd wycofywania stosu (Undo Prune leak) w drzewie nr " + i);
        }
        System.out.println("RF uSPR DFS Fuzz Passed! Zweryfikowano bezbłędnie " + totalEvaluations +
                " kroków uSPR w pełnym cyklu odcięcie/wpięcie.");
    }

    private Set<BitSet> extractNormalizedSplits(Tree tree, Tree baseTree) {
        Set<BitSet> splits = new HashSet<>();
        Map<String, Integer> mapping = new HashMap<>();
        for (int i = 0; i < baseTree.getExternalNodeCount(); i++) {
            mapping.put(baseTree.getExternalNode(i).getIdentifier().getName(), i);
        }
        int N = baseTree.getExternalNodeCount();
        BitSet allMask = new BitSet(N);
        allMask.set(0, N);

        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            Node n = tree.getInternalNode(i);
            if (n.isRoot()) continue;
            BitSet bs = new BitSet(N);
            collectLeavesMapped(n, bs, mapping);
            if (bs.cardinality() > 1 && bs.cardinality() < N - 1) {
                if (bs.get(0)) {
                    BitSet inv = (BitSet) bs.clone();
                    inv.xor(allMask);
                    splits.add(inv);
                } else {
                    splits.add(bs);
                }
            }
        }
        return splits;
    }

    private void collectLeavesMapped(Node n, BitSet bs, Map<String, Integer> map) {
        if (n.isLeaf()) {
            Integer idx = map.get(n.getIdentifier().getName());
            if (idx != null) bs.set(idx);
        } else {
            for (int i = 0; i < n.getChildCount(); i++) {
                collectLeavesMapped(n.getChild(i), bs, map);
            }
        }
    }
}