package treecmp.tbr;

import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.TbrHeuristicRFCMetric;
import treecmp.util.TreeCreator;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TbrUtilsTest {

    @Test
    void testTbrNeighborhoodSizeIsGreaterOrEqualSpr() {
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),4),(5,6));");
        TbrUtils tbrUtils = new TbrUtils();
        SprUtils sprUtils = new SprUtils();

        Tree[] tbrNeighbors = tbrUtils.generateNeighbours(baseTree);
        Tree[] sprNeighbors = sprUtils.generateNeighbours(baseTree);

        // Zbiór TBR musi być co najmniej tak duży jak SPR
        assertTrue(tbrNeighbors.length >= sprNeighbors.length,
                "Otoczenie rTBR (" + tbrNeighbors.length + ") musi być >= rSPR (" + sprNeighbors.length + ")");
    }

    @Test
    void testTbrNeighborsAreAllUnique() {
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),4),(5,6));");
        TbrUtils tbrUtils = new TbrUtils();
        Tree[] tbrNeighbors = tbrUtils.generateNeighbours(baseTree);

        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
        Set<TreeRootedHolder> uniqueTrees = new HashSet<>();

        for (Tree t : tbrNeighbors) {
            uniqueTrees.add(new TreeRootedHolder(t, idGroup));
        }

        assertEquals(tbrNeighbors.length, uniqueTrees.size(),
                "Wszystkie wygenerowane drzewa TBR muszą być unikalne izomorficznie!");
    }

    @Test
    void testAllTbrNeighborsAreExactlyOneStepAway() throws TreeCmpException {
        // Małe drzewo dla szybkości przeliczania heurystyki
        Tree baseTree = TreeCreator.getTreeFromString("(((1,2),3),(4,5));");
        TbrUtils tbrUtils = new TbrUtils();
        Tree[] tbrNeighbors = tbrUtils.generateNeighbours(baseTree);

        TbrHeuristicRFCMetric tbrMetric = new TbrHeuristicRFCMetric();

        // Jeśli drzewo jest w otoczeniu TbrUtils, to metryka MUST zwrócić dystans = 1.0
        for (Tree neighbor : tbrNeighbors) {
            double dist = tbrMetric.getDistance(baseTree, neighbor);
            assertEquals(1.0, dist, 0.000001,
                    "Każdy sąsiad wygenerowany przez TbrUtils musi być w odległości dokładnie 1 ruchu TBR");
        }
    }
}