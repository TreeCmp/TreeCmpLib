package treecmp.metrics.topological.acc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.tree.Tree;
import treecmp.heuristics.spr.acc.IncrementalUsprWalker;
import treecmp.util.TestTreeFactory;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zautomatyzowany test regresyjny dla operacji uSPR w metryce M3 (Matching Triplets).
 * Gwarantuje, że szybkie aktualizacje na maskach BitSet (O(1)) dają identyczne rezultaty
 * co pełne przebudowywanie drzewa (O(N^4)), ze szczególnym naciskiem na regrafty na liściach.
 */
public class M3UsprRegressionFuzzTest {

    private M3IncrementalMetric incM3;
    private IncrementalUsprWalker walker;
    private static final double DELTA = 0.000001;

    @BeforeEach
    void setUp() {
        incM3 = new M3IncrementalMetric();
        walker = new IncrementalUsprWalker();
    }

    @Test
    void testUsprWalkerRegressionOnRandomTrees() {
        int numberOfRandomTrees = 50;
        Random sizeRng = new Random(2026); // Stały seed dla determinizmu

        int totalUsprEvaluations = 0;
        int leafRegraftCount = 0;
        int internalRegraftCount = 0;

        System.out.println("Rozpoczynam Fuzz Test dla M3 uSPR...");

        for (int i = 0; i < numberOfRandomTrees; i++) {
            // Generujemy drzewa nieukorzenione od 10 do 40 liści
            int numLeaves = 10 + sizeRng.nextInt(31);
            Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(numLeaves, sizeRng.nextLong());
            Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(numLeaves, sizeRng.nextLong());

            incM3.initCalculationState(t1, t2);

            // Tablice do przechowywania stanu z wewnątrz wyrażenia lambda
            int[] localStats = new int[3]; // [0]=total, [1]=leaf, [2]=internal

            walker.walk(t1, incM3, (distInc, pruneNode, targetNode) -> {
                // Wyrocznia: wymuszamy powolną, ale bezbłędną kalkulację klasyczną
                double distClassic = incM3.evaluateSprRegraft(pruneNode, targetNode);

                // Zabezpieczenie przed błędem "86.0 vs 79.0" (duplikacja trójek na liściach)
                assertEquals(distClassic, distInc, DELTA,
                        String.format("BŁĄD REGRESJI M3! Różnica w dystansie uSPR.\n" +
                                        "Rozmiar drzewa: %d\n" +
                                        "Odcięto węzeł: %d\n" +
                                        "Podpięto pod węzeł: %d (Liść: %b)",
                                numLeaves, pruneNode.getNumber(), targetNode.getNumber(), targetNode.isLeaf()));

                localStats[0]++;
                if (targetNode.isLeaf()) {
                    localStats[1]++;
                } else {
                    localStats[2]++;
                }
            });

            totalUsprEvaluations += localStats[0];
            leafRegraftCount += localStats[1];
            internalRegraftCount += localStats[2];
        }

        // Twarde asercje weryfikujące czy fuzzer faktycznie wszedł w problematyczne ścieżki
        assertTrue(totalUsprEvaluations > 1000, "Zbyt mało przetestowanych ścieżek uSPR. Wymagane > 1000.");
        assertTrue(leafRegraftCount > 100, "Nie przetestowano regraftów na liściach! To tutaj występował NPE i błąd maski.");
        assertTrue(internalRegraftCount > 100, "Nie przetestowano regraftów na węzłach wewnętrznych.");

        System.out.println("==================================================");
        System.out.println("SUKCES! Test regresyjny M3 uSPR przeszedł.");
        System.out.println("Przeanalizowano w locie: " + totalUsprEvaluations + " ruchów uSPR.");
        System.out.println("Regrafty na liściach: " + leafRegraftCount);
        System.out.println("Regrafty na węzłach wewnętrznych: " + internalRegraftCount);
        System.out.println("==================================================");
    }
}