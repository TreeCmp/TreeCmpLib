package treecmp.spr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.metrics.Metric;
import treecmp.util.TreeCreator;

import static org.junit.jupiter.api.Assertions.*;

class UsprHeuristicRFMetricTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void testGetMetricTwoMarsupialsTreesWithSPR_1_distance() throws TreeCmpException {
        Tree baseTree[] = TreeCreator.getTwoMarsupialsSPR_1_distance_trees();
        Metric srf = new UsprHeuristicRFMetric();
        Double distance = srf.getDistance(baseTree[0], baseTree[1]);
        assertTrue(distance >= 1.0);
    }

    @Test
    void testGetMetricTwoMarsupialsTreesWithSPR_2_distance() throws TreeCmpException {
        Tree baseTree[] = TreeCreator.getTwoMarsupialsSPR_2_distance_trees();
        Metric srf = new UsprHeuristicRFMetric();
        Double distance = srf.getDistance(baseTree[0], baseTree[1]);
        assertTrue(distance >= 2.0);
    }

    @Test
    void testGetMetricTwoMarsupialsTreesWithSPR_3_distance() throws TreeCmpException {
        Tree baseTree[] = TreeCreator.getTwoMarsupialsSPR_3_distance_trees();
        Metric srf = new UsprHeuristicRFMetric();
        Double distance = srf.getDistance(baseTree[0], baseTree[1]);
        assertTrue(distance >= 3.0);
    }

    @Test
    void testGetMetricTwoMarsupialsTreesWithSPR_4_distance() throws TreeCmpException {
        Tree baseTree[] = TreeCreator.getTwoMarsupialsSPR_4_distance_trees();
        Metric srf = new UsprHeuristicRFMetric();
        Double distance = srf.getDistance(baseTree[0], baseTree[1]);
        assertTrue(distance >= 4.0);
    }

    @Test
    void testGetMetricTwoMarsupialsTreesWithSPR_4_distance_withoutLabels() throws TreeCmpException {
        Tree baseTree[] = TreeCreator.getTwoMarsupialsSPR_4_distance_trees_withoutLabels();
        Metric srf = new UsprHeuristicRFMetric();
        Double distance = srf.getDistance(baseTree[0], baseTree[1]);
        assertTrue(distance >= 4.0);
    }
}