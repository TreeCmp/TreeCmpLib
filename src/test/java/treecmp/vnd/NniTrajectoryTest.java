package treecmp.vnd;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import pal.tree.ReadTree;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.metrics.topological.RFMetric;

import java.io.PushbackReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NniTrajectoryTest {

    /**
     * Metoda weryfikująca: sprawdza, czy każde przejście w trajektorii
     * to legalny, elementarny ruch NNI w bibliotece TreeCmp (dystans RF == 1.0, 2.0 lub 0.0).
     */
    public static boolean verifyNniTrajectory(List<Tree> trajectory) {
        if (trajectory == null || trajectory.size() <= 1) {
            return true;
        }

        RFMetric rf = new RFMetric();
        for (int i = 0; i < trajectory.size() - 1; i++) {
            double dist = rf.getDistance(trajectory.get(i), trajectory.get(i + 1));

            // W TreeCmp elementarny krok NNI zmienia dystans RF o 1.0 lub 2.0.
            // Wartości > 2.0 (np. 3.0 lub 4.0) oznaczają nielegalny skok o kilka ruchów naraz.
            if (dist != 1.0 && dist != 2.0 && dist != 0.0) {
                System.err.println("Błąd NNI w kroku " + i + " -> " + (i + 1)
                        + ": Dystans RF = " + dist + " (oczekiwano 1.0, 2.0 lub 0.0)");
                return false;
            }
        }
        return true;
    }

    // ========================================================================
    // TEST 2: Pozytywny test syntetyczny – ręczna, poprawna sekwencja NNI
    // ========================================================================
    @Test
    public void testValidSyntheticNniSequence() {
        // T1 -> T2 -> T3: sekwencja elementarnych rotacji NNI
        Tree t1 = parseTree("((1,2),(3,(4,(5,6))));");
        Tree t2 = parseTree("((1,3),(2,(4,(5,6))));");
        Tree t3 = parseTree("((1,3),(2,(5,(4,6))));");

        List<Tree> validTrajectory = Arrays.asList(t1, t2, t3);

        assertTrue(verifyNniTrajectory(validTrajectory),
                "Poprawna sekwencja NNI powinna przejść weryfikację");
    }

    // ========================================================================
    // TEST 3: Negatywny test syntetyczny – wykrycie nielegalnego skoku (RF >= 3.0)
    // ========================================================================
    @Test
    public void testInvalidJumpShouldFailVerification() {
        Tree t1 = parseTree("((1,2),(3,(4,(5,6))));");
        // tJump różni się od t1 o wielokrotny skok topologiczny (RF = 4.0) – brak kroków pośrednich!
        Tree tJump = parseTree("((1,4),(5,(2,(3,6))));");

        List<Tree> invalidTrajectory = Arrays.asList(t1, tJump);

        assertFalse(verifyNniTrajectory(invalidTrajectory),
                "Weryfikator musi odrzucić przejście o dystansie RF > 2.0");
    }

    // ========================================================================
    // TEST 4: Przypadki brzegowe (drzewa izomorficzne, pusta lista, jeden element)
    // ========================================================================
    @Test
    public void testEdgeCasesAndIsomorphicTrees() {
        Tree t1 = parseTree("((1,2),(3,(4,(5,6))));");
        Tree t1Twin = parseTree("((2,1),(3,(4,(6,5))));"); // Ta sama topologia (RF = 0.0)

        assertTrue(verifyNniTrajectory(Arrays.asList(t1, t1Twin)),
                "Drzewa izomorficzne (RF=0.0) powinny być akceptowane");

        assertTrue(verifyNniTrajectory(Collections.emptyList()),
                "Pusta lista jest formalnie poprawną trajektorią");
        assertTrue(verifyNniTrajectory(Collections.singletonList(t1)),
                "Jednoelementowa lista nie zawiera nielegalnych przejść");
    }

    // ========================================================================
    // METODA POMOCNICZA: Szybkie parsowanie Newicka do obiektu Tree
    // ========================================================================
    private static Tree parseTree(String newick) {
        try {
            PushbackReader reader = new PushbackReader(new StringReader(newick));
            return new ReadTree(reader);
        } catch (Exception e) {
            throw new RuntimeException("Błąd parsowania drzewa Newick: " + newick, e);
        }
    }
}