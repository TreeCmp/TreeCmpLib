package treecmp.heuristics.tbr;

import org.junit.jupiter.api.Test;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.heuristics.tbr.acc.IncrementalTbrWalker;
import treecmp.heuristics.tbr.acc.RootedTbrMetric;
import treecmp.heuristics.tbr.acc.TbrNeighborhoodWalker;
import treecmp.heuristics.tbr.acc.UtbrNeighborhoodWalker;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test Spójności Dystansu dla Wędrowców TBR i uTBR.
 * Weryfikuje, czy wyceny inkrementalne dla każdego odwiedzanego ruchu w otoczeniu TBR
 * zachowują 100% zgodności z fizyczną topologią orakularną (TbrUtils / UTbrUtils).
 */
public class TbrWalkerDistanceConsistencyTest {

    private static final double EPSILON = 1e-9;
    private static final int TREE_SIZE = 6; // N=6 generuje wystarczającą głębokość przekorzenień i wpięć

    // ==========================================
    // ROOTED WALKERS (RFC, MC, MP)
    // ==========================================

    @Test
    public void testTbrWalker_RFC_Consistency() {
        verifyRootedTbrWalkerConsistency(new RFClusterIncrementalMetric(), new RFClusterMetric());
    }

    @Test
    public void testTbrWalker_MC_Consistency() {
        verifyRootedTbrWalkerConsistency(new MCIncrementalMetric(), new MatchingClusterMetric());
    }

    @Test
    public void testTbrWalker_MP_Consistency() {
        verifyRootedTbrWalkerConsistency(new MPIncrementalMetric(), new MatchingPairMetric());
    }

    // ==========================================
    // UNROOTED WALKERS (RF, MS, M3)
    // ==========================================

    @Test
    public void testUtbrWalker_RF_Consistency() {
        verifyUnrootedUtbrWalkerConsistency(new RFIncrementalMetric(), new RFMetric());
    }

    @Test
    public void testUtbrWalker_MS_Consistency() {
        verifyUnrootedUtbrWalkerConsistency(new MSIncrementalMetric(), new MatchingSplitMetric());
    }

    @Test
    public void testUtbrWalker_M3_Consistency() {
        verifyUnrootedUtbrWalkerConsistency(new M3IncrementalMetric(), new MatchingTripletMetric());
    }

    // ==========================================
    // SILNIKI WERYFIKUJĄCE
    // ==========================================

    private void verifyRootedTbrWalkerConsistency(IncrementalMetric incMetric, Metric classicMetric) {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(TREE_SIZE, 123L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(TREE_SIZE, 456L);
        assignNumbers(t1);
        assignNumbers(t2);

        incMetric.initCalculationState(t1, t2);
        TbrUtils tbrUtils = new TbrUtils();
        TbrNeighborhoodWalker walker = new TbrNeighborhoodWalker();

        walker.walk(t1, incMetric, (actualDist, pruneNode, rerootNode, targetNode) -> {
            Tree physicalTree = tbrUtils.createTbrTree(t1, pruneNode, rerootNode, targetNode);
            if (physicalTree != null) {
                assignNumbers(physicalTree);
                double expectedDist;
                try {
                    expectedDist = classicMetric.getDistance(physicalTree, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }

                if (Math.abs(expectedDist - actualDist) > EPSILON) {
                    System.out.println("==================================================");
                    System.out.println("DETEKCJA BŁĘDU rTBR DLA MP:");
                    System.out.println("Ruch: P=" + pruneNode.getNumber() + " (" + pruneNode.getIdentifier() + ")"
                            + ", R=" + rerootNode.getNumber() + " (" + rerootNode.getIdentifier() + ")"
                            + ", T=" + targetNode.getNumber() + " (" + targetNode.getIdentifier() + ")");
                    System.out.println("T1 Start : " + t1);
                    System.out.println("T2 Target: " + t2);
                    System.out.println("T1 po TBR: " + physicalTree);
                    System.out.println("Expected (Classic MP): " + expectedDist);
                    System.out.println("Actual   (Walker MP) : " + actualDist);
                    System.out.println("==================================================");
                }

                assertEquals(expectedDist, actualDist, EPSILON,
                        String.format("BŁĄD ZGODNOŚCI rTBR (%s)! Ruch P:%s, R:%s, T:%s dał błędny dystans.",
                                classicMetric.getCommandLineName(),
                                pruneNode.getNumber(), rerootNode.getNumber(), targetNode.getNumber()));
            }
        });
    }

    private void verifyUnrootedUtbrWalkerConsistency(IncrementalMetric incMetric, Metric classicMetric) {
        Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(TREE_SIZE, 123L);
        Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(TREE_SIZE, 456L);
        assignNumbers(t1);
        assignNumbers(t2);

        incMetric.initCalculationState(t1, t2);
        UTbrUtils utbrUtils = new UTbrUtils();
        UtbrNeighborhoodWalker walker = new UtbrNeighborhoodWalker();

        walker.walk(t1, incMetric, (actualDist, pruneNode, rerootNode, targetNode) -> {
            Tree physicalTree = utbrUtils.createUtbrTree(t1, pruneNode, rerootNode, targetNode);
            if (physicalTree != null) {
                assignNumbers(physicalTree);
                double expectedDist;
                try {
                    expectedDist = classicMetric.getDistance(physicalTree, t2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }

                assertEquals(expectedDist, actualDist, EPSILON,
                        String.format("BŁĄD ZGODNOŚCI uTBR (%s)! Ruch P:%s, R:%s, T:%s dał błędny dystans.",
                                classicMetric.getCommandLineName(),
                                pruneNode.getNumber(), rerootNode.getNumber(), targetNode.getNumber()));
            }
        });
    }

    private void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    // =========================================================================
    // 1. BEZPOŚREDNI TEST WĘDROWCA 2D-DFS (IncrementalTbrWalker)
    // =========================================================================

    @Test
    public void testIncrementalTbrWalker_2D_DFS_Consistency_AllRootedMetrics() {
        // Testuje właściwego wędrowca inkrementalnego używanego przez heurystykę
        IncrementalMetric[] incMetrics = {
                new RFClusterIncrementalMetric(),
                new MCIncrementalMetric(),
                new MPIncrementalMetric()
        };
        Metric[] classicMetrics = {
                new RFClusterMetric(),
                new MatchingClusterMetric(),
                new MatchingPairMetric()
        };

        for (int m = 0; m < incMetrics.length; m++) {
            IncrementalMetric inc = incMetrics[m];
            Metric classic = classicMetrics[m];

            Tree t1 = TestTreeFactory.randomRootedBinaryTree(TREE_SIZE, 789L);
            Tree t2 = TestTreeFactory.randomRootedBinaryTree(TREE_SIZE, 999L);
            assignNumbers(t1);
            assignNumbers(t2);

            inc.initCalculationState(t1, t2);
            IncrementalTbrWalker walker = new IncrementalTbrWalker();
            TbrUtils tbrUtils = new TbrUtils();

            walker.walk(t1, (RootedTbrMetric) inc, (actualDist, pruneNode, rerootNode, targetNode) -> {
                Tree physicalTree = tbrUtils.createTbrTree(t1, pruneNode, rerootNode, targetNode);
                if (physicalTree != null) {
                    assignNumbers(physicalTree);
                    double expectedDist;
                    try {
                        expectedDist = classic.getDistance(physicalTree, t2);
                    } catch (TreeCmpException e) {
                        throw new RuntimeException(e);
                    }

                    assertEquals(expectedDist, actualDist, EPSILON,
                            String.format("BŁĄD 2D-DFS rTBR (%s)! P:%s, R:%s, T:%s",
                                    classic.getCommandLineName(),
                                    pruneNode.getNumber(), rerootNode.getNumber(), targetNode.getNumber()));
                }
            });
        }
    }

    // =========================================================================
    // 2. STRESS TEST TOPOLOGICZNY (Caterpillar i Balanced dla N=8)
    // =========================================================================

    @Test
    public void testTbrWalker_TopologicalExtremes_CaterpillarAndBalanced() {
        // Testuje skrajne geometrie drzew: grzebień (maksymalna asymetria) i drzewo zrównoważone
        Tree tCaterpillar = TestTreeFactory.eightLeavesRootedCaterpillarTree();
        Tree tBalanced = TestTreeFactory.eightLeavesRootedBalancedTree();
        assignNumbers(tCaterpillar);
        assignNumbers(tBalanced);

        MPIncrementalMetric incMetric = new MPIncrementalMetric();
        MatchingPairMetric classicMetric = new MatchingPairMetric();
        incMetric.initCalculationState(tCaterpillar, tBalanced);

        TbrUtils tbrUtils = new TbrUtils();
        TbrNeighborhoodWalker walker = new TbrNeighborhoodWalker();

        walker.walk(tCaterpillar, incMetric, (actualDist, p, r, t) -> {
            Tree physical = tbrUtils.createTbrTree(tCaterpillar, p, r, t);
            if (physical != null) {
                assignNumbers(physical);
                double expected = classicMetric.getDistance(physical, tBalanced);
                assertEquals(expected, actualDist, EPSILON, "Niezgodność dla geometrii Caterpillar -> Balanced");
            }
        });
    }

    // =========================================================================
    // 3. ZERO-LEAK ASSERTION TEST (Strażnik wycieków pamięci)
    // =========================================================================

    @Test
    public void testTbrNeighborhood_ZeroMemoryLeakInvariant() {
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(8, 42L);
        assignNumbers(t1);

        TbrUtils tbrUtils = new TbrUtils();

        // Symulacja 5 kolejnych kroków VND (ok. 10 000 wygenerowanych drzew)
        for (int step = 0; step < 5; step++) {
            tbrUtils.forEachNeighbour(t1, neighbor -> {
                // konsumujemy drzewo
                assertNotNull(neighbor.getRoot());
            });
        }

        // Sprawdzamy, czy tablica kosztów/ruchów została wyczyszczona
        assertEquals(1.0, tbrUtils.getTreeCost(t1), EPSILON, "Tree costs powinny domyślnie zwracać 1.0 po czyszczeniu");
        assertNull(tbrUtils.getMoveForTree(t1), "Mapa ruchów musi być pusta po przejściu sąsiedztwa (ochrona przed OOM)");
    }
}