package treecmp.metrics.topological.acc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.topological.MatchingTripletMetric;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MatchingTripletIncrementalMetricFuzzTest {

    private MatchingTripletIncrementalMetric incrementalMetric;
    private MatchingTripletMetric classicMetric;
    private UsprUtils usprUtils;
    private IdGroup testIdGroup;
    private int N;

    private static final double DELTA = 0.000001;
    private static final int FUZZ_ITERATIONS = 300;

    @BeforeEach
    void setUp() {
        incrementalMetric = new MatchingTripletIncrementalMetric();
        classicMetric = new MatchingTripletMetric();
        usprUtils = new UsprUtils();
    }

    @Test
    void testFuzzRandomTrajectoriesAndRollbacks() {
        Tree baseTree = TestTreeFactory.tenLeavesUnrootedTree1();
        Tree targetTree = TestTreeFactory.tenLeavesUnrootedTree2();
        testIdGroup = TreeUtils.getLeafIdGroup(baseTree);
        N = baseTree.getExternalNodeCount();

        incrementalMetric.initCalculationState(baseTree, targetTree);
        Tree currentTestTree = baseTree;

        Random random = new Random(42);
        Stack<Tree> treeHistory = new Stack<>();

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            List<NniMove> validMoves = new ArrayList<>();
            Node[] allNodes = TreeCmpUtils.getAllNodes(currentTestTree);

            for (Node node : allNodes) {
                if (!node.isRoot() && !node.isLeaf()) {
                    Node parent = node.getParent();
                    if (parent != null) {
                        Node sibling = getSibling(node);
                        if (sibling != null && node.getChildCount() >= 2) {
                            Node c1 = node.getChild(0);
                            Node c2 = node.getChild(1);

                            Tree test1 = applyNniToTree(currentTestTree, c1, sibling);
                            if (test1 != null && test1.getExternalNodeCount() == currentTestTree.getExternalNodeCount()) {
                                validMoves.add(new NniMove(c1, sibling));
                            }

                            Tree test2 = applyNniToTree(currentTestTree, c2, sibling);
                            if (test2 != null && test2.getExternalNodeCount() == currentTestTree.getExternalNodeCount()) {
                                validMoves.add(new NniMove(c2, sibling));
                            }
                        }
                    }
                }
            }

            if (validMoves.isEmpty()) break;

            NniMove move = validMoves.get(random.nextInt(validMoves.size()));
            treeHistory.push(currentTestTree);

            double actualDistance = incrementalMetric.applyNni(move);

            Tree expectedNewTree = applyNniToTree(currentTestTree, move.movingSubtree, move.swapPartner);
            double expectedDistance = classicMetric.getDistance(expectedNewTree, createCleanCopy(targetTree));

            assertEquals(expectedDistance, actualDistance, DELTA,
                    "Fuzz mismatch at iteration " + i + "! Incremental MT logic drifted from baseline.");

            currentTestTree = expectedNewTree;

            if (random.nextDouble() < 0.3) {
                incrementalMetric.undoNni(move);
                currentTestTree = treeHistory.pop();

                double expectedUndoDist = classicMetric.getDistance(createCleanCopy(currentTestTree), createCleanCopy(targetTree));
                assertEquals(expectedUndoDist, incrementalMetric.getCurrentDistance(), DELTA,
                        "Undo mismatch at iteration " + i + "! History stack corruption detected in MT.");
            }
        }
    }

    private Tree createCleanCopy(Tree original) {
        SimpleTree copy = new SimpleTree(original);
        copy.createNodeList();
        TreeUtils.computeParentPointers(copy.getRoot());
        for (int i = 0; i < copy.getInternalNodeCount(); i++) copy.getInternalNode(i).setNumber(i);
        for (int i = 0; i < copy.getExternalNodeCount(); i++) copy.getExternalNode(i).setNumber(i);
        return copy;
    }

    private Tree applyNniToTree(Tree tree, Node moving, Node partner) {
        Tree safeCopy = createCleanCopy(tree);
        Node virtMoving = getMappedNode(safeCopy, moving);
        Node virtPartner = getMappedNode(safeCopy, partner);

        Tree tNew = null;
        try {
            if (virtMoving != null && virtPartner != null) {
                Tree rawNew = usprUtils.createUsprTree(safeCopy, virtMoving, virtPartner);
                if (rawNew != null) tNew = createCleanCopy(rawNew);
            }
        } catch (Exception ignored) {}
        return tNew;
    }

    private Node getMappedNode(Tree destTree, Node srcNode) {
        if (srcNode.isLeaf()) {
            return TreeUtils.getNodeByName(destTree, srcNode.getIdentifier().getName());
        }
        Signature targetSig = new Signature(srcNode, N, testIdGroup);
        for (int i = 0; i < destTree.getInternalNodeCount(); i++) {
            Signature sig = new Signature(destTree.getInternalNode(i), N, testIdGroup);
            if (sig.equals(targetSig)) return destTree.getInternalNode(i);
        }
        return null;
    }

    private static class Signature {
        BitSet[] clusters = new BitSet[3];
        public Signature(Node n, int N, IdGroup idGroup) {
            for (int i = 0; i < 3; i++) clusters[i] = new BitSet();
            int idx = 0;
            if (n.getParent() != null) {
                clusters[idx++] = getLeavesExcluding(n, idGroup, N);
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                if (idx < 3) clusters[idx++] = getLeaves(n.getChild(i), idGroup);
            }
            Arrays.sort(clusters, (a, b) -> a.toString().compareTo(b.toString()));
        }

        public boolean equals(Signature other) {
            for (int i = 0; i < 3; i++) {
                if (!this.clusters[i].equals(other.clusters[i])) return false;
            }
            return true;
        }
    }

    private static BitSet getLeaves(Node n, IdGroup idGroup) {
        BitSet bs = new BitSet();
        populate(n, bs, idGroup);
        return bs;
    }

    private static void populate(Node n, BitSet bs, IdGroup idGroup) {
        if (n.isLeaf()) {
            bs.set(idGroup.whichIdNumber(n.getIdentifier().getName()));
        } else {
            for (int i = 0; i < n.getChildCount(); i++) populate(n.getChild(i), bs, idGroup);
        }
    }

    private static BitSet getLeavesExcluding(Node exclude, IdGroup idGroup, int N) {
        BitSet bs = getLeaves(exclude, idGroup);
        BitSet comp = new BitSet(N);
        comp.set(0, N);
        comp.andNot(bs);
        return comp;
    }

    private Node getSibling(Node node) {
        Node parent = node.getParent();
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) != node) {
                return parent.getChild(i);
            }
        }
        return null;
    }
}