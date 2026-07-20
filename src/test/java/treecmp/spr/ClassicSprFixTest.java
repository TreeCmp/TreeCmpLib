package treecmp.heuristics.spr;

import org.junit.jupiter.api.Test;
import pal.tree.Tree;
import treecmp.metrics.topological.MatchingPairMetric;
import treecmp.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassicSprFixTest {

    @Test
    public void testClassicMpFindsGlobalMinimumInNeighborhood() {
        // Losowe drzewa ukorzenione
        Tree t1 = TestTreeFactory.randomRootedBinaryTree(10, 12345L);
        Tree t2 = TestTreeFactory.randomRootedBinaryTree(10, 67890L);

        MatchingPairMetric classicMp = new MatchingPairMetric();
        SprUtils utils = new SprUtils();

        // Obliczamy minimum bezpośrednio klasykiem
        final double[] bestClassicDist = {classicMp.getDistance(t1, t2)};

        utils.forEachSprTree(t1, neighbor -> {
            // Zabezpieczenie wymagane przez stare miary
            if (neighbor instanceof pal.tree.SimpleTree) {
                ((pal.tree.SimpleTree) neighbor).createNodeList();
            }

            double d = classicMp.getDistance(neighbor, t2);
            if (d < bestClassicDist[0]) {
                bestClassicDist[0] = d;
            }
        });

        // Ostateczna weryfikacja. Klasyk po naprawieniu mapowania w SprUtils
        // poprawnie rozpoznaje lżejsze drzewo i znajduje prawdziwe minimum (21.0)!
        assertEquals(21.0, bestClassicDist[0],
                "Klasyczna miara MP musi poprawnie zejść do dystansu 21.0 bez gubienia par!");
    }
}