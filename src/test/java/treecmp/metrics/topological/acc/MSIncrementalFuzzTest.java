package treecmp.metrics.topological.acc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.moves.Ecr2Move;
import treecmp.heuristics.moves.Ecr3Move;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.topological.MatchingSplitMetric;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MSIncrementalFuzzTest {

    private MSIncrementalMetric incrementalMetric;
    private MatchingSplitMetric classicMetric;
    private SprUtils sprUtils;

    private static final double DELTA = 0.000001;
    private static final int ECR_FUZZ_ITERATIONS = 50;

    @BeforeEach
    void setUp() {
        incrementalMetric = new MSIncrementalMetric();
        classicMetric = new MatchingSplitMetric();
        sprUtils = new SprUtils();
    }

    @Test
    void testFuzzThousandsOfRandomUnrootedNniMoves() throws Exception {
        int numberOfRandomTrees = 50;
        Random sizeRng = new Random(42);
        int totalNniEvaluations = 0;

        for (int i = 0; i < numberOfRandomTrees; i++) {
            int numLeaves = 10 + sizeRng.nextInt(41);
            Tree baseTree = TestTreeFactory.randomRootedBinaryTree(numLeaves, sizeRng.nextLong());
            Tree targetTree = TestTreeFactory.randomRootedBinaryTree(numLeaves, sizeRng.nextLong());

            incrementalMetric.initCalculationState(baseTree, targetTree);
            double initialClassicDist = classicMetric.getDistance(baseTree, targetTree);

            assertEquals(initialClassicDist, incrementalMetric.getCurrentDistance(), DELTA, "Base initialization mismatch");

            List<NniMove> validMoves = getAllValidNniMoves(baseTree);
            for (NniMove move : validMoves) {
                Node siblingOfMoving = getSibling(move.movingSubtree);
                Tree physicalNeighbor = sprUtils.createSprTree(baseTree, siblingOfMoving, move.swapPartner);
                if (physicalNeighbor == null) continue;

                double expectedDist = classicMetric.getDistance(physicalNeighbor, targetTree);
                double actualDist = incrementalMetric.applyNni(move);

                assertEquals(expectedDist, actualDist, DELTA, "NNI mismatch!");
                incrementalMetric.undoNni(move);
                assertEquals(initialClassicDist, incrementalMetric.getCurrentDistance(), DELTA);
                totalNniEvaluations++;
            }
        }
        System.out.println("MS NNI Fuzz Test Passed! Successfully executed " + totalNniEvaluations + " evaluations.");
    }

    @Test
    @Disabled
    void testFuzz2sEcrTrajectories() {
        Tree currentTestTree = TestTreeFactory.tenLeavesRootedTree1();
        Tree targetTree = TestTreeFactory.tenLeavesRootedTree2();

        incrementalMetric.initCalculationState(currentTestTree, targetTree);
        Random random = new Random(123);
        SubtreeEcr2Utils ecr2Utils = new SubtreeEcr2Utils(true); // true = unrooted for MS

        for (int iter = 0; iter < ECR_FUZZ_ITERATIONS; iter++) {
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

            ecr2Utils.applyPhysicalMove(currentTestTree, move);
            double classicDist = classicMetric.getDistance(currentTestTree, targetTree);

            assertEquals(classicDist, evalDist, DELTA, "MS 2-sECR Evaluate mismatch!");
            assertEquals(classicDist, commitDist, DELTA, "MS 2-sECR Commit mismatch!");
        }
    }

    @Test
    @Disabled
    void testFuzz3sEcrTrajectories() {
        Tree currentTestTree = TestTreeFactory.tenLeavesRootedTree1();
        Tree targetTree = TestTreeFactory.tenLeavesRootedTree2();

        incrementalMetric.initCalculationState(currentTestTree, targetTree);
        Random random = new Random(777);
        SubtreeEcr3Utils ecr3Utils = new SubtreeEcr3Utils(true); // true = unrooted for MS

        for (int iter = 0; iter < ECR_FUZZ_ITERATIONS; iter++) {
            List<Ecr3Move> validMoves = new ArrayList<>();
            for (int i = 0; i < currentTestTree.getInternalNodeCount(); i++) {
                Node rootOfCluster = currentTestTree.getInternalNode(i);

                // =========================================================
                // PRAWIDŁOWE ZABEZPIECZENIE DLA MS: Omijamy korzeń, który ma 3 dzieci!
                // =========================================================
                if (rootOfCluster == currentTestTree.getRoot()) continue;

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

            assertEquals(classicDist, evalDist, DELTA, "MS 3-sECR Evaluate mismatch!");
            assertEquals(classicDist, commitDist, DELTA, "MS 3-sECR Commit mismatch!");
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

    private List<NniMove> getAllValidNniMoves(Tree tree) {
        List<NniMove> moves = new ArrayList<>();
        int intCount = tree.getInternalNodeCount();
        for (int i = 0; i < intCount; i++) {
            Node u = tree.getInternalNode(i);
            if (u.isRoot()) continue;
            Node p = u.getParent();
            if (p == null) continue;
            Node s = null;
            for (int j = 0; j < p.getChildCount(); j++) {
                if (p.getChild(j) != u) { s = p.getChild(j); break; }
            }
            if (s == null) continue;
            if (!u.isLeaf() && u.getChildCount() >= 2) {
                moves.add(new NniMove(u.getChild(0), s));
                moves.add(new NniMove(u.getChild(1), s));
            }
        }
        return moves;
    }
}