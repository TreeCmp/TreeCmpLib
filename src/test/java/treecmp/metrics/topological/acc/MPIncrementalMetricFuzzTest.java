package treecmp.metrics.topological.acc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.Tree;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.moves.Ecr2Move;
import treecmp.heuristics.moves.Ecr3Move;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.topological.MatchingPairMetric;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fuzz / Chaos testing for the MatchingPairIncrementalMetric.
 * Fires strict structural perturbations to ensure the incremental optimization
 * NEVER drifts from the pure baseline MatchingPairMetric.
 */
public class MPIncrementalMetricFuzzTest {

    private MPIncrementalMetric incrementalMetric;
    private MatchingPairMetric classicMetric;
    private SprUtils sprUtils;

    private static final double DELTA = 0.000001;
    private static final int FUZZ_ITERATIONS = 50;

    @BeforeEach
    void setUp() {
        incrementalMetric = new MPIncrementalMetric();
        classicMetric = new MatchingPairMetric();
        sprUtils = new SprUtils();
    }

    @Test
    void testFuzzRandomNniTrajectories() {
        Tree currentTestTree = TestTreeFactory.tenLeavesRootedTree1();
        Tree targetTree = TestTreeFactory.tenLeavesRootedTree2();
        incrementalMetric.initCalculationState(currentTestTree, targetTree);

        Random random = new Random(42);

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            List<NniMove> validMoves = new ArrayList<>();
            Node[] allNodes = TreeCmpUtils.getAllNodes(currentTestTree);

            for (Node node : allNodes) {
                if (!node.isRoot() && !node.isLeaf()) {
                    Node parent = node.getParent();
                    if (parent != null) {
                        Node uncle = getSibling(node);
                        if (uncle != null && node.getChildCount() >= 2) {
                            validMoves.add(new NniMove(node.getChild(0), uncle));
                            validMoves.add(new NniMove(node.getChild(1), uncle));
                        }
                    }
                }
            }

            if (validMoves.isEmpty()) break;
            NniMove move = validMoves.get(random.nextInt(validMoves.size()));

            Tree expectedNewTree = sprUtils.createSprTree(currentTestTree, getSibling(move.movingSubtree), move.swapPartner);
            double expectedDistance = classicMetric.getDistance(expectedNewTree, targetTree);
            double actualDistance = incrementalMetric.applyNni(move);

            assertEquals(expectedDistance, actualDistance, DELTA, "NNI Fuzz mismatch at iteration " + i);
            incrementalMetric.undoNni(move);
        }
    }

    @Test
    void testFuzz2sEcrTrajectories() {
        Tree currentTestTree = TestTreeFactory.tenLeavesRootedTree1();
        Tree targetTree = TestTreeFactory.tenLeavesRootedTree2();

        incrementalMetric.initCalculationState(currentTestTree, targetTree);
        Random random = new Random(123);
        SubtreeEcr2Utils ecr2Utils = new SubtreeEcr2Utils(false);

        for (int iter = 0; iter < FUZZ_ITERATIONS; iter++) {
            List<Ecr2Move> validMoves = new ArrayList<>();
            for (int i = 0; i < currentTestTree.getInternalNodeCount(); i++) {
                Node node = currentTestTree.getInternalNode(i);
                if (node != currentTestTree.getRoot()) {
                    Node c = node.getParent();
                    if (c != null && !c.isLeaf()) {
                        Node p = c.getParent();
                        if (p != null && !p.isLeaf()) {
                            Node[] bounds = new Node[]{getOtherChild(p, c), getOtherChild(c, node), node.getChild(0), node.getChild(1)};
                            for (SubtreeEcr2Utils.TopologyTemplate2sECR temp : SubtreeEcr2Utils.getTemplates()) {
                                if (!temp.isFork && !Arrays.equals(temp.indices, new int[]{0,1,2,3}))
                                    validMoves.add(new Ecr2Move(p, c, node, bounds, temp));
                            }
                        }
                    }
                }

                List<Node> intChildren = new ArrayList<>();
                for (int j = 0; j < node.getChildCount(); j++) if (!node.getChild(j).isLeaf()) intChildren.add(node.getChild(j));
                if (intChildren.size() >= 2) {
                    Node m1 = intChildren.get(0); Node m2 = intChildren.get(1);
                    Node[] bounds = new Node[]{m1.getChild(0), m1.getChild(1), m2.getChild(0), m2.getChild(1)};
                    for (SubtreeEcr2Utils.TopologyTemplate2sECR temp : SubtreeEcr2Utils.getTemplates()) {
                        if (temp.isFork && !Arrays.equals(temp.indices, new int[]{0,1,2,3}))
                            validMoves.add(new Ecr2Move(node, m1, m2, bounds, temp));
                    }
                }
            }

            if (validMoves.isEmpty()) break;
            Ecr2Move move = validMoves.get(random.nextInt(validMoves.size()));

            double evalDist = incrementalMetric.evaluate2sEcrMove(move.top, move.m1, move.m2, move.boundarySubtrees, move.template);
            double commitDist = incrementalMetric.commit2sEcrMove(move.top, move.m1, move.m2, move.boundarySubtrees, move.template);

            // Modyfikacja fizyczna ujednolicająca drzewo w miejscu
            ecr2Utils.applyPhysicalMove(currentTestTree, move);
            double classicDist = classicMetric.getDistance(currentTestTree, targetTree);

            assertEquals(classicDist, evalDist, DELTA, "2-sECR Evaluate mismatch!");
            assertEquals(classicDist, commitDist, DELTA, "2-sECR Commit mismatch!");
        }
    }

    @Test
    void testFuzz3sEcrTrajectories() {
        Tree currentTestTree = TestTreeFactory.tenLeavesRootedTree1();
        Tree targetTree = TestTreeFactory.tenLeavesRootedTree2();

        incrementalMetric.initCalculationState(currentTestTree, targetTree);
        Random random = new Random(777);
        SubtreeEcr3Utils ecr3Utils = new SubtreeEcr3Utils(false);

        for (int iter = 0; iter < FUZZ_ITERATIONS; iter++) {
            List<Ecr3Move> validMoves = new ArrayList<>();
            for (int i = 0; i < currentTestTree.getInternalNodeCount(); i++) {
                Node rootOfCluster = currentTestTree.getInternalNode(i);
                for (List<Node> cluster : ecr3Utils.getClusters(rootOfCluster, 4)) {
                    List<Node> subtreesList = ecr3Utils.getBoundarySubtrees(cluster);
                    if (subtreesList.size() == 5) {
                        Node[] bounds = subtreesList.toArray(new Node[0]);
                        SubtreeEcr3Utils.TopologyTemplate3sECR origSig = ecr3Utils.extractSignature(rootOfCluster, cluster, subtreesList);
                        for (SubtreeEcr3Utils.TopologyTemplate3sECR temp : SubtreeEcr3Utils.getTemplates()) {
                            if (!temp.isIsomorphic(origSig)) validMoves.add(new Ecr3Move(cluster, bounds, temp));
                        }
                    }
                }
            }

            if (validMoves.isEmpty()) break;
            Ecr3Move move = validMoves.get(random.nextInt(validMoves.size()));

            double evalDist = incrementalMetric.evaluate3sEcrMove(move.cluster, move.boundarySubtrees, move.template);
            double commitDist = incrementalMetric.commit3sEcrMove(move.cluster, move.boundarySubtrees, move.template);

            ecr3Utils.applyPhysicalMove(currentTestTree, move);
            double classicDist = classicMetric.getDistance(currentTestTree, targetTree);

            assertEquals(classicDist, evalDist, DELTA, "3-sECR Evaluate mismatch!");
            assertEquals(classicDist, commitDist, DELTA, "3-sECR Commit mismatch!");
        }
    }

    private Node getSibling(Node node) {
        Node parent = node.getParent();
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) != node) return parent.getChild(i);
        }
        return null;
    }

    private Node getOtherChild(Node parent, Node exclude) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) != exclude) return parent.getChild(i);
        }
        return null;
    }
}