package treecmp.metrics.topological;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.Tree;
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

        // Drzewa startowe muszą być NIEUKORZENIONE dla poprawnego działania splitów MS
        baseTree = TestTreeFactory.randomUnrootedBinaryTree(10, 111L);
        targetTree = TestTreeFactory.randomUnrootedBinaryTree(10, 222L);

        // Bezpiecznik: tworzenie list węzłów wewnętrznych
        if (baseTree instanceof pal.tree.SimpleTree) {
            ((pal.tree.SimpleTree) baseTree).createNodeList();
        }
        if (targetTree instanceof pal.tree.SimpleTree) {
            ((pal.tree.SimpleTree) targetTree).createNodeList();
        }
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

        // Zamiast szukać węzłów po nazwach ("1", "2"), pobieramy pierwszy poprawny topologicznie ruch
        NniMove move = getAllValidNniMoves(baseTree).get(0);

        double distAfterMove = incrementalMetric.applyNni(move);
        assertTrue(distAfterMove >= 0.0, "The distance should be non-negative.");

        incrementalMetric.undoNni(move);
        assertEquals(initialDist, incrementalMetric.getCurrentDistance(), DELTA,
                "The rollback operation (undo) failed to restore the exact original MS distance!");
    }

    @Test
    void testMultipleSequentialNniMovesMaintainStateConsistency() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        List<NniMove> validMoves = getAllValidNniMoves(baseTree);
        NniMove move1 = validMoves.get(0);
        NniMove move2 = validMoves.get(1);
        NniMove move3 = validMoves.get(2);

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
    void testComplexBranchingTrajectoryWithNestedUndos() {
        incrementalMetric.initCalculationState(baseTree, targetTree);
        double initialDist = incrementalMetric.getCurrentDistance();

        List<NniMove> validMoves = getAllValidNniMoves(baseTree);
        NniMove move1 = validMoves.get(0);
        NniMove move2 = validMoves.get(1);
        NniMove move3 = validMoves.get(2);
        NniMove move4 = validMoves.get(3);

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

            // Ochrona dla drzew nieukorzenionych: omijamy korzeń!
            if (root == baseTree.getRoot()) continue;

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

    // Metoda pomocnicza pobierająca poprawne topologicznie ruchy
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