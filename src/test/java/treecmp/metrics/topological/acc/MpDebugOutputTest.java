package treecmp.heuristics.spr;

import org.junit.jupiter.api.Test;
import pal.tree.Tree;
import treecmp.heuristics.spr.acc.IncrementalSprWalker;
import treecmp.metrics.topological.MatchingPairMetric;
import treecmp.metrics.topological.acc.MPIncrementalMetric;
import treecmp.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.fail;

public class MpDebugOutputTest {

    @Test
    public void debugMpMoveByMove() {
        // Identyczne drzewa jak w benchmarku i poprzednim teście
        Tree baseTree = TestTreeFactory.randomRootedBinaryTree(10, 12345L);
        Tree targetTree = TestTreeFactory.randomRootedBinaryTree(10, 67890L);

        if (baseTree instanceof pal.tree.SimpleTree) ((pal.tree.SimpleTree) baseTree).createNodeList();
        if (targetTree instanceof pal.tree.SimpleTree) ((pal.tree.SimpleTree) targetTree).createNodeList();

        MatchingPairMetric classicMp = new MatchingPairMetric();
        MPIncrementalMetric incMp = new MPIncrementalMetric();
        incMp.initCalculationState(baseTree, targetTree);

        IncrementalSprWalker walker = new IncrementalSprWalker();
        SprUtils sprUtils = new SprUtils();

        System.out.println("\n--- START SZCZEGÓŁOWEGO SKANOWANIA RUCHÓW MP ---");

        walker.walk(baseTree, incMp, (incDist, prune, target) -> {
            // 1. Bezpieczne zbudowanie drzewa dla Klasyka
            Tree classicTree = sprUtils.createAndFixSprTree(baseTree, prune, target);

            // 2. Obliczenie klasycznego dystansu
            double classicDist = classicMp.getDistance(classicTree, targetTree);

            // 3. Konfrontacja
            if (Math.abs(incDist - classicDist) > 0.0001) {
                System.out.println("==========================================");
                System.out.println("!!! ZNALEZIONO ROZBIEŻNOŚĆ W RUCHU SPR !!!");
                System.out.println("Odcięto (Prune) : Węzeł " + prune.getNumber() + (prune.isLeaf() ? " [LIŚĆ]" : " [WEWN.]"));
                System.out.println("Podpięto (Target): Węzeł " + target.getNumber() + (target.isLeaf() ? " [LIŚĆ]" : " [WEWN.]"));
                System.out.println("------------------------------------------");
                System.out.println("Dystans INKREMENTAL : " + incDist);
                System.out.println("Dystans CLASSIC     : " + classicDist);
                System.out.println("Różnica             : " + Math.abs(incDist - classicDist));
                System.out.println("==========================================\n");

                fail("Test przerwany: Wykryto rozbieżność w konkretnym ruchu SPR.");
            }
        });

        System.out.println("WSZYSTKIE RUCHY SĄ IDENTYCZNE! BRAK ROZBIEŻNOŚCI!");
    }
}