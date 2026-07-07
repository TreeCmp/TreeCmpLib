package treecmp.metrics.topological;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.moves.NniMove;
import treecmp.metrics.topological.acc.MSIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying the mathematical consistency of the MSIncrementalMetric (Matching Split).
 * It ensures that the optimized warm-start incremental algorithm produces exactly
 * the same split-based distance values as the full baseline MatchingSplitMetric across
 * various complex NNI, 2-sECR, and 3-sECR trajectories and rollbacks.
 */
public class MSIncrementalMetricTest {

    private MSIncrementalMetric incrementalMetric;
    private MatchingSplitMetric classicMetric;

    private static final double DELTA = 0.000001;

    private Tree baseTree;
    private Tree targetTree;

    @BeforeEach
    void setUp() {
        incrementalMetric = new MSIncrementalMetric();
        classicMetric = new MatchingSplitMetric();

        // Używamy drzew 10-liściowych. MS traktuje je jako nieukorzenione (Unrooted).
        baseTree = TestTreeFactory.tenLeavesRootedTree1();
        targetTree = TestTreeFactory.tenLeavesRootedTree2();
    }

    @Test
    void testInitialDistanceConsistency() throws Exception {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double expectedClassicDist = classicMetric.getDistance(baseTree, targetTree);

        assertEquals(expectedClassicDist, incrementalMetric.getCurrentDistance(), DELTA,
                "Initial MS distance must be perfectly identical between incremental and classic metrics!");
    }

    @Test
    void testSingleNniMoveAndUndoConsistency() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        Node node2 = TreeUtils.getNodeByName(baseTree, "2");
        Node node3 = TreeUtils.getNodeByName(baseTree, "3");
        NniMove move = new NniMove(node2, node3);

        double distAfterMove = incrementalMetric.applyNni(move);
        assertEquals(0.0, distAfterMove, DELTA,
                "The target-matching NNI move should reduce the incremental MS distance to 0.0!");

        incrementalMetric.undoNni(move);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "The rollback operation (undo) failed to restore the exact original MS distance!");
    }

    @Test
    void testMultipleSequentialNniMovesMaintainStateConsistency() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        Node n1 = TreeUtils.getNodeByName(baseTree, "1");
        Node n2 = TreeUtils.getNodeByName(baseTree, "2");
        Node n3 = TreeUtils.getNodeByName(baseTree, "3");
        Node n4 = TreeUtils.getNodeByName(baseTree, "4");

        NniMove move1 = new NniMove(n2, n3);
        NniMove move2 = new NniMove(n3, n4);
        NniMove move3 = new NniMove(n1, n2);

        double dist1 = incrementalMetric.applyNni(move1);
        assertEquals(0.0, dist1, DELTA);

        double dist2 = incrementalMetric.applyNni(move2);
        assertTrue(dist2 >= 0.0, "Split distance should stay non-negative after structural divergence.");

        incrementalMetric.applyNni(move3);

        incrementalMetric.undoNni(move3);
        assertEquals(dist2, incrementalMetric.getCurrentDistance(), DELTA);

        incrementalMetric.undoNni(move2);
        assertEquals(dist1, incrementalMetric.getCurrentDistance(), DELTA);

        incrementalMetric.undoNni(move1);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA);
    }

    @Test
    void testComplexBranchingTrajectoryWithNestedUndos() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        Node n1 = TreeUtils.getNodeByName(baseTree, "1");
        Node n2 = TreeUtils.getNodeByName(baseTree, "2");
        Node n3 = TreeUtils.getNodeByName(baseTree, "3");
        Node n4 = TreeUtils.getNodeByName(baseTree, "4");
        Node n5 = TreeUtils.getNodeByName(baseTree, "5");

        NniMove move1 = new NniMove(n2, n3);
        NniMove move2 = new NniMove(n4, n5);
        NniMove move3 = new NniMove(n2, n4);
        NniMove move4 = new NniMove(n1, n5);

        double dist1 = incrementalMetric.applyNni(move1);
        double dist2 = incrementalMetric.applyNni(move2);
        incrementalMetric.applyNni(move3);

        incrementalMetric.undoNni(move3);
        assertEquals(dist2, incrementalMetric.getCurrentDistance(), DELTA);

        incrementalMetric.applyNni(move4);
        incrementalMetric.undoNni(move4);
        assertEquals(dist2, incrementalMetric.getCurrentDistance(), DELTA);

        incrementalMetric.undoNni(move2);
        assertEquals(dist1, incrementalMetric.getCurrentDistance(), DELTA);

        incrementalMetric.undoNni(move1);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA);
    }

    @Test
    void testEcr2MoveEvaluationAndCommitConsistency() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        Node top = null, m1 = null, m2 = null;
        Node[] bounds = new Node[4];

        // Zabezpieczamy wyszukiwanie klastra 2-sECR (kształt Fork)
        for (int i = 0; i < baseTree.getInternalNodeCount(); i++) {
            Node n = baseTree.getInternalNode(i);
            List<Node> intChildren = new ArrayList<>();
            for(int j=0; j<n.getChildCount(); j++) if(!n.getChild(j).isLeaf()) intChildren.add(n.getChild(j));

            if (intChildren.size() >= 2) {
                top = n; m1 = intChildren.get(0); m2 = intChildren.get(1);
                bounds[0] = m1.getChild(0); bounds[1] = m1.getChild(1);
                bounds[2] = m2.getChild(0); bounds[3] = m2.getChild(1);
                break;
            }
        }
        assertNotNull(top, "Nie znaleziono klastra 2-sECR!");

        SubtreeEcr2Utils.TopologyTemplate2sECR template = SubtreeEcr2Utils.getTemplates().get(2);

        // Act 1: Evaluate (sondowanie grafu bez jego mutacji)
        double evalDist = incrementalMetric.evaluate2sEcrMove(top, m1, m2, bounds, template);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "Wywołanie 'evaluate2sEcrMove' w MS zmutowało stan macierzy!");

        // Act 2: Commit (zatwierdzenie)
        double commitDist = incrementalMetric.commit2sEcrMove(top, m1, m2, bounds, template);
        assertEquals(evalDist, commitDist, DELTA, "Commit MS niezgodny z Evaluate!");
        assertEquals(commitDist, incrementalMetric.getCurrentDistance(), DELTA);
    }

    @Test
    void testEcr3MoveEvaluationAndCommitConsistency() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        SubtreeEcr3Utils ecr3Utils = new SubtreeEcr3Utils(true); // true = Unrooted dla MS
        List<Node> validCluster = null;
        Node[] bounds = null;

        for (int i = 0; i < baseTree.getInternalNodeCount(); i++) {
            Node root = baseTree.getInternalNode(i);
            for (List<Node> cluster : ecr3Utils.getClusters(root, 4)) {
                List<Node> bList = ecr3Utils.getBoundarySubtrees(cluster);
                if (bList.size() == 5) {
                    validCluster = cluster;
                    bounds = bList.toArray(new Node[0]);
                    break;
                }
            }
            if (validCluster != null) break;
        }
        assertNotNull(validCluster, "Nie znaleziono klastra 3-sECR!");

        SubtreeEcr3Utils.TopologyTemplate3sECR template = SubtreeEcr3Utils.getTemplates().get(15);

        // Act 1: Evaluate
        double evalDist = incrementalMetric.evaluate3sEcrMove(validCluster, bounds, template);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "Wywołanie 'evaluate3sEcrMove' w MS uszkodziło macierz LAP!");

        // Act 2: Commit
        double commitDist = incrementalMetric.commit3sEcrMove(validCluster, bounds, template);
        assertEquals(evalDist, commitDist, DELTA);
        assertEquals(commitDist, incrementalMetric.getCurrentDistance(), DELTA);
    }
}