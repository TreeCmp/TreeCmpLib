package treecmp.heuristics.tbr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.metrics.topological.acc.M3IncrementalMetric;
import treecmp.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Weryfikacja poprawności zliczania kroków uTBR")
public class UtbrIncrementalCorrectnessTest {

    private static void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
        TreeUtils.computeParentPointers(tree.getRoot());
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            tree.getExternalNode(i).setNumber(i);
        }
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            tree.getInternalNode(i).setNumber(i);
        }
    }

    @Test
    @DisplayName("Metoda getDistance() musi zwracać jednostkowe kroki TBR, a nie skumulowany koszt NNI")
    void testGetDistanceReturnsUnitTbrSteps() {
        Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(10, 42L);
        Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(10, 99L);
        assignNumbers(t1);
        assignNumbers(t2);

        UtbrIncrementalHeuristic heuristic = new UtbrIncrementalHeuristic(new M3IncrementalMetric(), "M3");

        double dist = heuristic.getDistance(new SimpleTree(t1), new SimpleTree(t2));

        assertTrue(dist > 0 && dist < 12, String.format("Zwrócony dystans uTBR (%.1f) jest zbyt duży, co sugeruje przeciek kosztu NNI.", dist));
        assertEquals((double) heuristic.getUtbrStepsCount(), dist, 1e-9, "getDistance() nie odpowiada precyzyjnie zliczonej liczbie kroków uTBR!");
    }
}