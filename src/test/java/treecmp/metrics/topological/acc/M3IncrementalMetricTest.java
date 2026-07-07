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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MatchingTripletIncrementalMetricTest {

    private MatchingTripletIncrementalMetric incrementalMetric;
    private MatchingTripletMetric classicMetric;
    private UsprUtils usprUtils;
    private IdGroup testIdGroup;
    private int N;

    private static final double DELTA = 0.000001;

    private Tree baseTree;
    private Tree targetTree;

    @BeforeEach
    void setUp() {
        incrementalMetric = new MatchingTripletIncrementalMetric();
        classicMetric = new MatchingTripletMetric();
        usprUtils = new UsprUtils();

        baseTree = TestTreeFactory.tenLeavesUnrootedTree1();
        targetTree = TestTreeFactory.tenLeavesUnrootedTree2();
        testIdGroup = TreeUtils.getLeafIdGroup(baseTree);
        N = baseTree.getExternalNodeCount();
    }

    @Test
    void testInitialDistanceConsistency() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double expectedClassicDist = classicMetric.getDistance(createCleanCopy(baseTree), createCleanCopy(targetTree));

        assertEquals(expectedClassicDist, incrementalMetric.getCurrentDistance(), DELTA,
                "Initial MT distance must be perfectly identical between incremental and classic metrics!");
    }

    @Test
    void testSingleNniMoveAndUndoConsistency() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        Tree freshTree = TestTreeFactory.tenLeavesUnrootedTree1();
        NniMove move = getDeterministicValidMoves(freshTree).get(0);

        double actualDistAfterMove = incrementalMetric.applyNni(move);

        Tree expectedNewTree = applyNniToTree(freshTree, move.movingSubtree, move.swapPartner);
        double expectedDistAfterMove = classicMetric.getDistance(expectedNewTree, createCleanCopy(targetTree));

        assertEquals(expectedDistAfterMove, actualDistAfterMove, DELTA,
                "The target-matching NNI move failed to correctly update the NCV assignments!");

        incrementalMetric.undoNni(move);

        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "The rollback operation (undo) failed to restore the exact original MT distance!");
    }

    @Test
    void testMultipleSequentialNniMovesMaintainStateConsistency() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        List<NniMove> moves = getDeterministicValidMoves(baseTree);
        assertTrue(moves.size() >= 3, "Test tree does not support enough valid NNI structural moves!");

        NniMove move1 = moves.get(0);
        NniMove move2 = moves.get(1);
        NniMove move3 = moves.get(2);

        double dist1 = incrementalMetric.applyNni(move1);
        double dist2 = incrementalMetric.applyNni(move2);
        incrementalMetric.applyNni(move3);

        incrementalMetric.undoNni(move3);
        assertEquals(dist2, incrementalMetric.getCurrentDistance(), DELTA,
                "Undoing move3 must return the metric state precisely to the post-move2 state!");

        incrementalMetric.undoNni(move2);
        assertEquals(dist1, incrementalMetric.getCurrentDistance(), DELTA,
                "Undoing move2 must return the metric state precisely to the post-move1 state!");

        incrementalMetric.undoNni(move1);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "After wiping the entire move history stack, the MT distance must equal the initial baseline!");
    }

    @Test
    void testComplexBranchingTrajectoryWithNestedUndos() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        Tree freshTreeA = TestTreeFactory.tenLeavesUnrootedTree1();
        NniMove pathA_step1 = getDeterministicValidMoves(freshTreeA).get(0);

        Tree freshTreeB = TestTreeFactory.tenLeavesUnrootedTree1();
        NniMove pathB_step1 = getDeterministicValidMoves(freshTreeB).get(1);

        double distAfterPathA = incrementalMetric.applyNni(pathA_step1);

        Tree virtualTree1 = applyNniToTree(freshTreeA, pathA_step1.movingSubtree, pathA_step1.swapPartner);
        NniMove pathA_step2 = getDeterministicValidMoves(virtualTree1).get(0);
        incrementalMetric.applyNni(pathA_step2);

        incrementalMetric.undoNni(pathA_step2);
        assertEquals(distAfterPathA, incrementalMetric.getCurrentDistance(), DELTA, "Nested undo level 1 failed!");

        incrementalMetric.undoNni(pathA_step1);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA, "Nested undo level 2 failed!");

        double distAfterPathB = incrementalMetric.applyNni(pathB_step1);

        Tree expectedTreeB = applyNniToTree(freshTreeB, pathB_step1.movingSubtree, pathB_step1.swapPartner);
        double expectedClassicB = classicMetric.getDistance(expectedTreeB, createCleanCopy(targetTree));

        assertEquals(expectedClassicB, distAfterPathB, DELTA,
                "Cross-contamination detected! Switching trajectories broke the NCV tracking matrix.");
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

    private List<NniMove> getDeterministicValidMoves(Tree tree) {
        List<NniMove> validMoves = new ArrayList<>();
        Node[] allNodes = TreeCmpUtils.getAllNodes(tree);

        for (Node node : allNodes) {
            if (!node.isRoot() && !node.isLeaf()) {
                Node parent = node.getParent();
                if (parent != null) {
                    Node sibling = getSibling(node);
                    if (sibling != null && node.getChildCount() >= 2) {
                        Node c1 = node.getChild(0);
                        Node c2 = node.getChild(1);

                        Tree test1 = applyNniToTree(tree, c1, sibling);
                        if (test1 != null && test1.getExternalNodeCount() == tree.getExternalNodeCount()) {
                            validMoves.add(new NniMove(c1, sibling));
                        }

                        Tree test2 = applyNniToTree(tree, c2, sibling);
                        if (test2 != null && test2.getExternalNodeCount() == tree.getExternalNodeCount()) {
                            validMoves.add(new NniMove(c2, sibling));
                        }
                    }
                }
            }
        }
        return validMoves;
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