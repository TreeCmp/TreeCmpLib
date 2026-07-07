package treecmp.metrics.topological.acc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.moves.NniMove;
import treecmp.metrics.topological.MatchingPairMetric;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying the mathematical consistency of the MatchingPairIncrementalMetric.
 * It ensures that the optimized hybrid approach maps perfectly to the warm-start LAP
 * and produces exact distance values identical to the baseline MatchingPairMetric.
 */
public class MPIncrementalMetricTest {

    private MPIncrementalMetric incrementalMetric;
    private MatchingPairMetric classicMetric;

    private static final double DELTA = 0.000001;

    private Tree baseTree;
    private Tree targetTree;

    @BeforeEach
    void setUp() {
        incrementalMetric = new MPIncrementalMetric();
        classicMetric = new MatchingPairMetric();

        // MP wymaga drzew ukorzenionych
        baseTree = TestTreeFactory.tenLeavesRootedTree1();
        targetTree = TestTreeFactory.tenLeavesRootedTree2();
    }

    @Test
    void testInitialDistanceConsistency() throws Exception {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double expectedClassicDist = classicMetric.getDistance(baseTree, targetTree);

        assertEquals(expectedClassicDist, incrementalMetric.getCurrentDistance(), DELTA,
                "Initial MP distance must be perfectly identical between incremental and classic metrics!");
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
                "The target-matching NNI move should reduce the incremental MP distance to 0.0!");

        incrementalMetric.undoNni(move);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "The rollback operation (undo) failed to restore the exact original MP distance!");
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
        double dist2 = incrementalMetric.applyNni(move2);
        incrementalMetric.applyNni(move3);

        incrementalMetric.undoNni(move3);
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

        // 1. Bezpieczne odszukanie działającego klastra 2-sECR typu Fork
        Node top = null, m1 = null, m2 = null;
        Node[] bounds = new Node[4];
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
        assertNotNull(top, "Nie znaleziono klastra 2-sECR w bazowym drzewie testowym!");

        SubtreeEcr2Utils.TopologyTemplate2sECR template = SubtreeEcr2Utils.getTemplates().get(1); // Dowolny inny niż bazowy

        // Act 1: Evaluate
        double evalDist = incrementalMetric.evaluate2sEcrMove(top, m1, m2, bounds, template);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "Wywołanie 'evaluate' NIE MOŻE mutować bieżącego stanu macierzy ani dystansu!");

        // Act 2: Commit
        double commitDist = incrementalMetric.commit2sEcrMove(top, m1, m2, bounds, template);
        assertEquals(evalDist, commitDist, DELTA,
                "Commit musi zwrócić idealnie ten sam dystans co Evaluate!");
        assertEquals(commitDist, incrementalMetric.getCurrentDistance(), DELTA,
                "Stan po wykonaniu Commit musi odzwierciedlać nową topologię!");
    }

    @Test
    void testEcr3MoveEvaluationAndCommitConsistency() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        SubtreeEcr3Utils ecr3Utils = new SubtreeEcr3Utils(false); // false = Rooted
        List<Node> validCluster = null;
        Node[] bounds = null;

        // 1. Bezpieczne odszukanie 4-węzłowego klastra 3-sECR
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
        assertNotNull(validCluster, "Nie znaleziono klastra 3-sECR w bazowym drzewie testowym!");

        SubtreeEcr3Utils.TopologyTemplate3sECR template = SubtreeEcr3Utils.getTemplates().get(10); // Szablon "gdzieś ze środka"

        // Act 1: Evaluate
        double evalDist = incrementalMetric.evaluate3sEcrMove(validCluster, bounds, template);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "Wywołanie 'evaluate' NIE MOŻE trwale uszkadzać stanu metryki!");

        // Act 2: Commit
        double commitDist = incrementalMetric.commit3sEcrMove(validCluster, bounds, template);
        assertEquals(evalDist, commitDist, DELTA);
        assertEquals(commitDist, incrementalMetric.getCurrentDistance(), DELTA);
    }
}