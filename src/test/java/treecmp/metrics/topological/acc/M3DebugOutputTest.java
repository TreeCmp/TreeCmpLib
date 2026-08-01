package treecmp.metrics.topological.acc;

import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.spr.acc.IncrementalUsprWalker;
import treecmp.metrics.topological.acc.M3IncrementalMetric;
import treecmp.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.fail;

public class M3DebugOutputTest {

    @Test
    public void debugM3MoveByMove() {
        // Losowe drzewa nieukorzenione 10-liściowe
        Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(10, 12345L);
        Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(10, 67890L);

        M3IncrementalMetric incM3 = new M3IncrementalMetric();
        incM3.initCalculationState(t1, t2);

        IncrementalUsprWalker walker = new IncrementalUsprWalker();

        System.out.println("\n--- START SZCZEGÓŁOWEGO SKANOWANIA RUCHÓW M3 (uSPR) ---");

        walker.walk(t1, incM3, (distInc, pruneNode, targetNode) -> {

            // Wymuszamy na Inkrementalu czystą kalkulację klasyczną
            double distClassic = incM3.evaluateSprRegraft(pruneNode, targetNode);

            if (Math.abs(distInc - distClassic) > 0.001) {
                System.out.println("==========================================");
                System.out.println("!!! ZNALEZIONO ROZBIEŻNOŚĆ W RUCHU uSPR !!!");
                System.out.println("Odcięto (Prune) : Węzeł " + pruneNode.getNumber() + " [" + (pruneNode.isLeaf() ? "LIŚĆ" : "WEWN.") + "]");
                System.out.println("Podpięto (Target): Węzeł " + targetNode.getNumber() + " [" + (targetNode.isLeaf() ? "LIŚĆ" : "WEWN.") + "]");
                System.out.println("------------------------------------------");
                System.out.println("Dystans INKREMENTAL : " + distInc);
                System.out.println("Dystans CLASSIC     : " + distClassic);
                System.out.println("Różnica             : " + Math.abs(distInc - distClassic));
                System.out.println("==========================================\n");

                fail("Test przerwany: Wykryto rozbieżność w konkretnym ruchu uSPR (M3).");
            }
        });

        System.out.println("WSZYSTKIE RUCHY SĄ IDENTYCZNE! BRAK ROZBIEŻNOŚCI!");
    }
}