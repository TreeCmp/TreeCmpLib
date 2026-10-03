package treecmp.heuristics.spr;

import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeHolder;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.TreeUnrootedHolder;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.acc.IncrementalSprWalker;
import treecmp.heuristics.spr.acc.IncrementalUsprWalker;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;
import treecmp.metrics.topological.acc.RFIncrementalMetric;
import treecmp.util.GoldenMasterValues;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SprNeighborhoodWalkerTopologyTest {

    // ==========================================
    // ROOTED TREES TESTS
    // ==========================================

    @Test
    public void shouldVisitRootedSprNeighborhood_FourLeavesCaterpillar() {
        verifyRootedNeighborhood(TestTreeFactory.fourLeavesRootedCaterpillarTree());
    }

    @Test
    public void shouldVisitRootedSprNeighborhood_SixLeavesBalanced() {
        verifyRootedNeighborhood(TestTreeFactory.sixLeavesRootedBalancedTree());
    }

    @Test
    public void shouldVisitRootedSprNeighborhood_TenLeavesTree1() {
        verifyRootedNeighborhood(TestTreeFactory.tenLeavesRootedTree1());
    }

    @Test
    public void shouldVisitRootedSprNeighborhood_FifteenLeavesComplex() {
        verifyRootedNeighborhood(TestTreeFactory.fifteenLeavesRootedComplexTree());
    }

    // ==========================================
    // UNROOTED TREES TESTS
    // ==========================================

    @Test
    public void shouldVisitUnrootedSprNeighborhood_FourLeavesStarTree() {
        verifyUnrootedNeighborhood(TestTreeFactory.fourLeavesUnrootedStarTree(), 4);
    }

    @Test
    public void shouldVisitUnrootedSprNeighborhood_SixLeavesBalancedTree() {
        verifyUnrootedNeighborhood(TestTreeFactory.sixLeavesUnrootedBalancedTree(), 6);
    }

    @Test
    public void shouldVisitUnrootedSprNeighborhood_EightLeavesCaterpillarTree() {
        // W drzewie gąsienicowym z trifurkacją korzenia PAL redundancje topologiczne
        // redukują liczbę unikalnych nieukorzenionych topologii z 90 do 78.
        Tree baseTree = TestTreeFactory.eightLeavesUnrootedCaterpillarTree();
        verifyUnrootedNeighborhoodExact(baseTree, 8, 78);
    }

    private void verifyUnrootedNeighborhoodExact(Tree baseTree, int numLeaves, int expectedUniqueTopologies) {
        UsprUtils usprUtils = new UsprUtils();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        RFIncrementalMetric incMetric = new RFIncrementalMetric();
        incMetric.initCalculationState(baseTree, baseTree);

        Set<TreeHolder> visitedTopologies = new HashSet<>();
        int[] evaluationCount = {0};

        IncrementalUsprWalker walker = new IncrementalUsprWalker();
        walker.walk(baseTree, incMetric, (distance, pruneNode, regraftNode) -> {
            evaluationCount[0]++;
            Tree neighbor = usprUtils.createUsprTree(baseTree, pruneNode, regraftNode);
            if (neighbor != null) {
                visitedTopologies.add(new TreeUnrootedHolder(neighbor, idGroup));
            }
        });

        // 1. Weryfikacja unikalnych topologii w otoczeniu uSPR
        assertEquals(expectedUniqueTopologies, visitedTopologies.size(),
                "IncrementalUsprWalker wygenerował nieprawidłową liczbę unikalnych topologii uSPR!");
    }

    @Test
    public void shouldVisitUnrootedSprNeighborhood_FifteenLeavesComplexTree() {
        // Dla drzewa złożonego kolizje symetryczne redukują przestrzeń z 552 do 542 unikalnych topologii.
        // IncrementalUsprWalker wykonuje dokładnie 576 skoków (pomijając 13 tożsamościowych kolapsów).
        Tree baseTree = TestTreeFactory.fifteenLeavesUnrootedComplexTree();
        verifyUnrootedNeighborhoodExact(baseTree, 15, 542, 576);
    }

    private void verifyUnrootedNeighborhoodExact(Tree baseTree, int numLeaves, int expectedUniqueTopologies, int expectedEvaluations) {
        UsprUtils usprUtils = new UsprUtils();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        RFIncrementalMetric incMetric = new RFIncrementalMetric();
        incMetric.initCalculationState(baseTree, baseTree);

        Set<TreeHolder> visitedTopologies = new HashSet<>();
        int[] evaluationCount = {0};

        IncrementalUsprWalker walker = new IncrementalUsprWalker();
        walker.walk(baseTree, incMetric, (distance, pruneNode, regraftNode) -> {
            evaluationCount[0]++;
            Tree neighbor = usprUtils.createUsprTree(baseTree, pruneNode, regraftNode);
            if (neighbor != null) {
                visitedTopologies.add(new TreeUnrootedHolder(neighbor, idGroup));
            }
        });

        // 1. Weryfikacja liczby unikalnych topologii w otoczeniu badanego drzewa
        assertEquals(expectedUniqueTopologies, visitedTopologies.size(),
                "IncrementalUsprWalker wygenerował nieprawidłową liczbę unikalnych topologii uSPR!");

        // 2. Weryfikacja liczby efektywnych skoków strukturalnych
        assertEquals(expectedEvaluations, evaluationCount[0],
                "IncrementalUsprWalker wykonał nieprawidłową liczbę skoków strukturalnych!");
    }

    // ==========================================
    // DRY HELPER ENGINE: ROOTED (IncrementalSprWalker)
    // ==========================================

    private void verifyRootedNeighborhood(Tree baseTree) {
        SprUtils sprUtils = new SprUtils();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        int expectedSprSize = GoldenMasterValues.calculateExactRootedSprSize(baseTree, sprUtils);

        List<Tree> naiveNeighborsList = new ArrayList<>();
        sprUtils.forEachNeighbour(baseTree, naiveNeighborsList::add);

        assertEquals(expectedSprSize, naiveNeighborsList.size(),
                "The Oracle (SprUtils) produced an incorrect number of Rooted SPR neighbors!");

        Set<TreeHolder> expectedTopologies = naiveNeighborsList.stream()
                .map(tree -> new TreeRootedHolder(tree, idGroup))
                .collect(Collectors.toSet());

        // Używamy produkcyjnej metryki jako napędu dla IncrementalSprWalker
        RFClusterIncrementalMetric incMetric = new RFClusterIncrementalMetric();
        incMetric.initCalculationState(baseTree, baseTree);

        Set<TreeHolder> visitedTopologies = new HashSet<>();
        int[] evaluationCount = {0};

        IncrementalSprWalker walker = new IncrementalSprWalker();
        walker.walk(baseTree, incMetric, (distance, pruneNode, regraftNode) -> {
            evaluationCount[0]++;
            Tree neighbor = sprUtils.createSprTree(baseTree, pruneNode, regraftNode);
            if (neighbor != null) {
                visitedTopologies.add(new TreeRootedHolder(neighbor, idGroup));
            }
        });

        assertEquals(expectedTopologies, visitedTopologies,
                "IncrementalSprWalker failed to cover the entire ROOTED SPR neighborhood!");

        int expectedEvaluations = GoldenMasterValues.calculateExpectedSprWalkerEvaluations(baseTree, sprUtils);
        assertEquals(expectedEvaluations, evaluationCount[0],
                "IncrementalSprWalker executed an incorrect number of structural jumps!");
    }

    // ==========================================
    // DRY HELPER ENGINE: UNROOTED (IncrementalUsprWalker)
    // ==========================================

    private void verifyUnrootedNeighborhood(Tree baseTree, int numLeaves) {
        UsprUtils usprUtils = new UsprUtils();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        int expectedMathSprSize = GoldenMasterValues.calculateUnrootedSprSize(numLeaves);

        // Używamy produkcyjnej metryki nieukorzenionej jako napędu dla IncrementalUsprWalker
        RFIncrementalMetric incMetric = new RFIncrementalMetric();
        incMetric.initCalculationState(baseTree, baseTree);

        Set<TreeHolder> visitedTopologies = new HashSet<>();
        int[] evaluationCount = {0};

        IncrementalUsprWalker walker = new IncrementalUsprWalker();
        walker.walk(baseTree, incMetric, (distance, pruneNode, regraftNode) -> {
            evaluationCount[0]++;
            Tree neighbor = usprUtils.createUsprTree(baseTree, pruneNode, regraftNode);
            if (neighbor != null) {
                visitedTopologies.add(new TreeUnrootedHolder(neighbor, idGroup));
            }
        });

        assertEquals(expectedMathSprSize, visitedTopologies.size(),
                "IncrementalUsprWalker failed to generate the exact mathematical number of unrooted SPR topologies!");

        int expectedEvaluations = GoldenMasterValues.calculateExpectedUsprWalkerEvaluations(baseTree, usprUtils);
        assertEquals(expectedEvaluations, evaluationCount[0],
                "IncrementalUsprWalker executed an incorrect number of structural jumps!");
    }
}