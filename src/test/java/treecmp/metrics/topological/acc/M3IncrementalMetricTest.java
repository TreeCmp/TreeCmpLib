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
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class M3IncrementalMetricTest {

    private M3IncrementalMetric incrementalMetric;
    private IdGroup testIdGroup;
    private int N;

    private static final double DELTA = 0.000001;

    private Tree baseTree;
    private Tree targetTree;

    @BeforeEach
    void setUp() {
        incrementalMetric = new M3IncrementalMetric();

        baseTree = TestTreeFactory.tenLeavesUnrootedTree1(); // UNROOTED
        targetTree = TestTreeFactory.tenLeavesUnrootedTree2();
        testIdGroup = TreeUtils.getLeafIdGroup(baseTree);
        N = baseTree.getExternalNodeCount();
    }

    /**
     * Wzorujemy się na sukcesie FuzzTestu:
     * Używamy świeżej instancji Twojej nowej metryki jako nieomylnej Wyroczni (Oracle).
     */
    private double getOracleDistance(Tree currentTree, Tree target) {
        M3IncrementalMetric oracle = new M3IncrementalMetric();
        oracle.initCalculationState(currentTree, target);
        return oracle.getCurrentDistance();
    }

    @Test
    void testInitialDistanceConsistency() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double expectedOracleDist = getOracleDistance(baseTree, targetTree);

        assertEquals(expectedOracleDist, incrementalMetric.getCurrentDistance(), DELTA,
                "Initial MT distance must be identical between incremental and oracle metrics!");
    }

    @Test
    void testSingleNniMoveAndUndoConsistency() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        Tree freshTree = TestTreeFactory.tenLeavesUnrootedTree1();
        NniMove move = getDeterministicValidMoves(freshTree).get(0);

        double actualDistAfterMove = incrementalMetric.applyNni(move);

        Tree expectedNewTree = applyNniToTreeForOracle(freshTree, move);
        double expectedDistAfterMove = getOracleDistance(expectedNewTree, targetTree);

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

        Tree virtualTree1 = applyNniToTreeForOracle(freshTreeA, pathA_step1);
        NniMove pathA_step2 = getDeterministicValidMoves(virtualTree1).get(0);
        incrementalMetric.applyNni(pathA_step2);

        incrementalMetric.undoNni(pathA_step2);
        assertEquals(distAfterPathA, incrementalMetric.getCurrentDistance(), DELTA, "Nested undo level 1 failed!");

        incrementalMetric.undoNni(pathA_step1);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA, "Nested undo level 2 failed!");

        double distAfterPathB = incrementalMetric.applyNni(pathB_step1);

        Tree expectedTreeB = applyNniToTreeForOracle(freshTreeB, pathB_step1);
        double expectedOracleB = getOracleDistance(expectedTreeB, targetTree);

        assertEquals(expectedOracleB, distAfterPathB, DELTA,
                "Cross-contamination detected! Switching trajectories broke the NCV tracking matrix.");
    }

    @Test
    void testProtectionAgainstPalIdOverlap() {
        Tree dirtyTree = TestTreeFactory.tenLeavesUnrootedTree1();

        // Symulacja błędu - zamazujemy natywną strukturę PAL
        for (int i = 0; i < dirtyTree.getInternalNodeCount(); i++) dirtyTree.getInternalNode(i).setNumber(0);
        for (int i = 0; i < dirtyTree.getExternalNodeCount(); i++) dirtyTree.getExternalNode(i).setNumber(0);

        incrementalMetric.initCalculationState(dirtyTree, targetTree);
        double incDist = incrementalMetric.getCurrentDistance();

        // Wyroczni (też M3IncrementalMetric) dajemy czyste drzewo dla referencji
        Tree cleanTree = createCleanCopyForOracle(TestTreeFactory.tenLeavesUnrootedTree1());
        double expectedDist = getOracleDistance(cleanTree, targetTree);

        assertEquals(expectedDist, incDist, DELTA,
                "[REGRESSION BUG] Metryka uległa korupcji! Brak izolacji przed zniszczonymi ID biblioteki PAL!");
    }

    @Test
    void testPhantomRootTripletConservation() {
        Tree baseUnrooted = TestTreeFactory.tenLeavesUnrootedTree1();
        incrementalMetric.initCalculationState(baseUnrooted, targetTree);

        List<NniMove> moves = getDeterministicValidMoves(baseUnrooted);
        assertTrue(moves.size() > 0, "Brak ruchów NNI do przetestowania!");

        NniMove rootAdjacentMove = moves.get(0);

        double actualDist = incrementalMetric.applyNni(rootAdjacentMove);

        Tree physicalNeighbor = applyNniToTreeForOracle(baseUnrooted, rootAdjacentMove);
        double expectedDist = getOracleDistance(physicalNeighbor, targetTree);

        assertEquals(expectedDist, actualDist, DELTA,
                "[REGRESSION BUG] Zgubiono triplety w 'newIntersection'! Metryka nie radzi sobie z widmowym korzeniem drzewa.");
    }

    @Test
    void testLapDualVariablePermutationStability() {
        incrementalMetric.initCalculationState(baseTree, targetTree);

        List<NniMove> initialMoves = getDeterministicValidMoves(baseTree);
        NniMove move1 = initialMoves.get(0);

        incrementalMetric.applyNni(move1);
        Tree step1Tree = applyNniToTreeForOracle(baseTree, move1);

        List<NniMove> nextMoves = getDeterministicValidMoves(step1Tree);
        NniMove move2 = nextMoves.get(1);

        double actualDist = incrementalMetric.applyNni(move2);

        Tree step2Tree = applyNniToTreeForOracle(step1Tree, move2);
        double expectedDist = getOracleDistance(step2Tree, targetTree);

        assertEquals(expectedDist, actualDist, DELTA,
                "[REGRESSION BUG] Awaria Signature Mapping! Zmienne dualne LAP (u, v) zostały błędnie skojarzone z wierszami po permutacji topologii.");
    }

    // =======================================================================================
    // INTERNAL UTILITIES DLA WYROCZNI
    // =======================================================================================

    private Tree createCleanCopyForOracle(Tree original) {
        SimpleTree copy = new SimpleTree(original);
        copy.createNodeList();
        TreeUtils.computeParentPointers(copy.getRoot());
        return copy;
    }

    private Tree applyNniToTreeForOracle(Tree tree, NniMove move) {
        Tree safeCopy = createCleanCopyForOracle(tree);
        Node virtMoving = getMappedNode(safeCopy, move.movingSubtree);
        Node virtPartner = getMappedNode(safeCopy, move.swapPartner);

        if (virtMoving != null && virtPartner != null) {
            Node p1 = virtMoving.getParent();
            Node p2 = virtPartner.getParent();
            if (p1 != null && p2 != null && p1 != p2) {
                int idx1 = -1, idx2 = -1;
                for (int i = 0; i < p1.getChildCount(); i++) if (p1.getChild(i) == virtMoving) idx1 = i;
                for (int i = 0; i < p2.getChildCount(); i++) if (p2.getChild(i) == virtPartner) idx2 = i;

                if (idx1 != -1 && idx2 != -1) {
                    p1.setChild(idx1, virtPartner); virtPartner.setParent(p1);
                    p2.setChild(idx2, virtMoving); virtMoving.setParent(p2);
                    return createCleanCopyForOracle(safeCopy);
                }
            }
        }
        return safeCopy;
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

                        Tree test1 = applyNniToTreeForOracle(tree, new NniMove(c1, sibling));
                        if (test1 != null && test1.getExternalNodeCount() == tree.getExternalNodeCount()) {
                            validMoves.add(new NniMove(c1, sibling));
                        }

                        Tree test2 = applyNniToTreeForOracle(tree, new NniMove(c2, sibling));
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
        if (srcNode.isLeaf()) return TreeUtils.getNodeByName(destTree, srcNode.getIdentifier().getName());
        Signature targetSig = new Signature(srcNode, N, testIdGroup);
        for (int i = 0; i < destTree.getInternalNodeCount(); i++) {
            Signature sig = new Signature(destTree.getInternalNode(i), N, testIdGroup);
            if (sig.equals(targetSig)) return destTree.getInternalNode(i);
        }
        return null;
    }

    private static class Signature {
        String hash;
        public Signature(Node n, int N, IdGroup idGroup) {
            List<BitSet> parts = new ArrayList<>();
            for (int i = 0; i < n.getChildCount(); i++) {
                parts.add(getLeaves(n.getChild(i), idGroup));
            }
            if (n.getParent() != null) {
                BitSet parentPart = new BitSet(N);
                parentPart.set(0, N);
                for (int i = 0; i < n.getChildCount(); i++) {
                    parentPart.andNot(parts.get(i));
                }
                if (!parentPart.isEmpty()) {
                    parts.add(parentPart);
                }
            }
            String[] strParts = new String[parts.size()];
            for (int i = 0; i < parts.size(); i++) {
                strParts[i] = parts.get(i).toString();
            }
            Arrays.sort(strParts);
            this.hash = Arrays.toString(strParts);
        }
        @Override
        public int hashCode() { return hash.hashCode(); }
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Signature)) return false;
            return this.hash.equals(((Signature)obj).hash);
        }
        @Override
        public String toString() { return hash; }
    }

    private static BitSet getLeaves(Node n, IdGroup idGroup) {
        BitSet bs = new BitSet(); populate(n, bs, idGroup); return bs;
    }

    private static void populate(Node n, BitSet bs, IdGroup idGroup) {
        if (n.isLeaf()) bs.set(idGroup.whichIdNumber(n.getIdentifier().getName()));
        else for (int i = 0; i < n.getChildCount(); i++) populate(n.getChild(i), bs, idGroup);
    }

    private static BitSet getLeavesExcluding(Node exclude, IdGroup idGroup, int N) {
        BitSet bs = getLeaves(exclude, idGroup);
        BitSet comp = new BitSet(N); comp.set(0, N); comp.andNot(bs); return comp;
    }

    private Node getSibling(Node node) {
        Node parent = node.getParent();
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) if (parent.getChild(i) != node) return parent.getChild(i);
        return null;
    }
}