package treecmp.tbr;

import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeUnrootedHolder;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.heuristics.tbr.UTbrHeuristicRFMetric;
import treecmp.util.TreeCreator;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UTbrUtilsTest {

    @Test
    void testExactSizeFor5LeavesUnrooted() {
        // MATEMATYCZNE ODKRYCIE: Dla 5 liści otoczenie TBR jest DOKŁADNIE równe otoczeniu SPR (12 drzew).
        // Dlaczego? Aby TBR różniło się od SPR, odcięte poddrzewo musi dać się "przekorzenić".
        // W 5-liściowym drzewie każde cięcie dzieli je na (1,4) lub (2,3).
        // Zarówno pojedynczy liść, jak i "wiśnia" (2 liście) mają tylko 1 krawędź wewnętrzną!
        // Nie da się ich przekorzenić. Dlatego wszystkie ruchy TBR dla 5 liści redukują się do SPR!
        Tree baseTree = TreeCreator.getTreeFromString("(((1,2),3),4,5);");
        UTbrUtils utbrUtils = new UTbrUtils();

        Tree[] tbrNeighbors = utbrUtils.generateNeighbours(baseTree);

        assertEquals(12, tbrNeighbors.length,
                "Dla 5 liści uTBR i uSPR są matematycznie identyczne i generują dokładnie 12 unikalnych sąsiadów!");
    }

    @Test
    void testUTbrNeighborhoodSizeIsGreaterOrEqualUspr() {
        // Otoczenie uTBR musi być zawsze nadzbiorem otoczenia uSPR
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),4),5,6);");
        UTbrUtils utbrUtils = new UTbrUtils();
        UsprUtils usprUtils = new UsprUtils();

        Tree[] tbrNeighbors = utbrUtils.generateNeighbours(baseTree);
        Tree[] sprNeighbors = usprUtils.generateNeighbours(baseTree);

        assertTrue(tbrNeighbors.length >= sprNeighbors.length,
                "Otoczenie uTBR (" + tbrNeighbors.length + ") musi być >= uSPR (" + sprNeighbors.length + ")");
    }

    @Test
    void testUTbrNeighborsAreAllUnique() {
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),4),5,6);");
        UTbrUtils utbrUtils = new UTbrUtils();
        Tree[] tbrNeighbors = utbrUtils.generateNeighbours(baseTree);

        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
        Set<TreeUnrootedHolder> uniqueTrees = new HashSet<>();

        for (Tree t : tbrNeighbors) {
            try {
                // TreeUnrootedHolder zadba o to, by drzewa izomorficzne miały ten sam hash
                uniqueTrees.add(new TreeUnrootedHolder(t, idGroup));
            } catch (Exception e) {
                // Ignorowane puste błędy PAL-a przy krawędziowych topologiach
            }
        }

        assertEquals(tbrNeighbors.length, uniqueTrees.size(),
                "Wszystkie wygenerowane drzewa uTBR muszą być unikalne w sensie topologii nieukorzenionej!");
    }

    @Test
    void testAllUTbrNeighborsAreExactlyOneStepAway() throws TreeCmpException {
        // Bierzemy małe drzewo 5-liściowe, żeby ewaluacja działała błyskawicznie
        Tree baseTree = TreeCreator.getTreeFromString("(((1,2),3),4,5);");
        UTbrUtils utbrUtils = new UTbrUtils();
        Tree[] tbrNeighbors = utbrUtils.generateNeighbours(baseTree);

        UTbrHeuristicRFMetric utbrMetric = new UTbrHeuristicRFMetric();

        for (Tree neighbor : tbrNeighbors) {
            double dist = utbrMetric.getDistance(baseTree, neighbor);
            assertEquals(1.0, dist, 0.000001,
                    "Każdy wygenerowany przez UTbrUtils sąsiad musi być odległy o dokładnie 1 krok uTBR");
        }
    }
}
