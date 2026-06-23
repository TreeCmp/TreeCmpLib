package treecmp.spr;

import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeHolder;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.TreeUnrootedHolder;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.acc.SprNeighborhoodWalker;
import treecmp.heuristics.spr.acc.UsprNeighborhoodWalker;
import treecmp.util.CoverageMockMetric;
import treecmp.util.GoldenMasterValues;
import treecmp.util.TestTreeFactory;

import java.util.Arrays;
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
        // Balanced topologies check if Walker properly visits overlapping isomorphic subtrees
        verifyRootedNeighborhood(TestTreeFactory.sixLeavesRootedBalancedTree());
    }

    @Test
    public void shouldVisitRootedSprNeighborhood_TenLeavesTree1() {
        // Larger tree tests deep recursion in the traverseRegraft method
        verifyRootedNeighborhood(TestTreeFactory.tenLeavesRootedTree1());
    }

    @Test
    public void shouldVisitRootedSprNeighborhood_FifteenLeavesComplex() {
        // Stress test for the O(1) capabilities on a very large structural neighborhood
        verifyRootedNeighborhood(TestTreeFactory.fifteenLeavesRootedComplexTree());
    }

    // ==========================================
    // UNROOTED TREES TESTS
    // ==========================================

    @Test
    public void shouldVisitUnrootedSprNeighborhood_FourLeavesStarTree() {
        // n=4 is the absolute minimum boundary for unrooted SPR moves
        verifyUnrootedNeighborhood(TestTreeFactory.fourLeavesUnrootedStarTree(), 4);
    }

    @Test
    public void shouldVisitUnrootedSprNeighborhood_SixLeavesBalancedTree() {
        verifyUnrootedNeighborhood(TestTreeFactory.sixLeavesUnrootedBalancedTree(), 6);
    }

    @Test
    public void shouldVisitUnrootedSprNeighborhood_EightLeavesCaterpillarTree() {
        // Extreme asymmetry in unrooted trees
        verifyUnrootedNeighborhood(TestTreeFactory.eightLeavesUnrootedCaterpillarTree(), 8);
    }

    @Test
    public void shouldVisitUnrootedSprNeighborhood_FifteenLeavesComplexTree() {
        // Mathematical size for n=15 is exactly 552 unique topologies
        verifyUnrootedNeighborhood(TestTreeFactory.fifteenLeavesUnrootedComplexTree(), 15);
    }

    // ==========================================
    // DRY HELPER ENGINE: ROOTED
    // ==========================================

    private void verifyRootedNeighborhood(Tree baseTree) {
        SprUtils naiveSprUtils = new SprUtils();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        int expectedSprSize = GoldenMasterValues.calculateExactRootedSprSize(baseTree, naiveSprUtils);
        Tree[] naiveNeighborsArray = naiveSprUtils.generateNeighbours(baseTree);

        assertEquals(expectedSprSize, naiveNeighborsArray.length,
                "The Oracle (SprUtils) produced an incorrect number of Rooted SPR neighbors!");

        Set<TreeHolder> expectedTopologies = Arrays.stream(naiveNeighborsArray)
                .map(tree -> new TreeRootedHolder(tree, idGroup))
                .collect(Collectors.toSet());

        // TRUE flag initializes CoverageMockMetric for Rooted trees
        CoverageMockMetric mockMetric = new CoverageMockMetric(true);
        mockMetric.initCalculationState(baseTree, null);

        SprNeighborhoodWalker walker = new SprNeighborhoodWalker();
        walker.walk(baseTree, mockMetric, (distance, pruneNode, regraftNode) -> {});

        assertEquals(expectedTopologies, mockMetric.getVisitedTopologies(),
                "The Walker failed to cover the entire ROOTED SPR neighborhood!");

        int expectedEvaluations = GoldenMasterValues.calculateExpectedSprWalkerEvaluations(baseTree, naiveSprUtils);
        assertEquals(expectedEvaluations, mockMetric.getEvaluationCount(),
                "The Walker executed an incorrect number of structural jumps (evaluateSprRegraft)!");
    }

    // ==========================================
    // DRY HELPER ENGINE: UNROOTED
    // ==========================================

    private void verifyUnrootedNeighborhood(Tree baseTree, int numLeaves) {
        // UŻYWAMY TWOJEJ KLASY!
        UsprUtils usprUtils = new UsprUtils();

        // Prawda absolutna ze wzoru Allena i Steela (np. 552 dla n=15)
        int expectedMathSprSize = GoldenMasterValues.calculateUnrootedSprSize(numLeaves);

        // FALSE flag inicjalizuje Mocka dla drzew Unrooted
        CoverageMockMetric mockMetric = new CoverageMockMetric(false);
        mockMetric.initCalculationState(baseTree, null);

        // Odpalamy nowego Walkera dla uSPR
        UsprNeighborhoodWalker walker = new UsprNeighborhoodWalker();
        walker.walk(baseTree, mockMetric, (distance, pruneNode, regraftNode) -> {});

        Set<treecmp.heuristics.TreeHolder> visitedTopologies = mockMetric.getVisitedTopologies();

        // 1. Weryfikacja liczby unikalnych topologii z twardą matematyką
        assertEquals(expectedMathSprSize, visitedTopologies.size(),
                "UsprNeighborhoodWalker failed to generate the exact mathematical number of unrooted SPR topologies!");

        // 2. Weryfikacja złożoności algorytmu (wykorzystuje isValidUsprMove)
        int expectedEvaluations = GoldenMasterValues.calculateExpectedUsprWalkerEvaluations(baseTree, usprUtils);
        assertEquals(expectedEvaluations, mockMetric.getEvaluationCount(),
                "UsprNeighborhoodWalker executed an incorrect number of structural jumps!");
    }
}