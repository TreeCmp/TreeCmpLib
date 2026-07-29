package treecmp.vnd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristic;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristic;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic;
import treecmp.metrics.topological.acc.RFIncrementalMetric;
import treecmp.util.TreeCreator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VndHangDiagnosticTest {

    private Tree t1;
    private Tree t2;
    private RFIncrementalMetric rfInc;

    @BeforeEach
    public void setUp() throws Exception {
        // Wczytujemy parę drzew, która zawiesiła testy (N=10, ub)
        String file = "datasets/n10y200ub.newick";
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            t1 = new SimpleTree(TreeCreator.getTreeFromString(br.readLine().trim()));
            t2 = new SimpleTree(TreeCreator.getTreeFromString(br.readLine().trim()));
        }
        ((SimpleTree) t1).createNodeList();
        ((SimpleTree) t2).createNodeList();
        rfInc = new RFIncrementalMetric();
    }

    // Jeśli ten test "spadnie" z błędem Timeout, to znaczy że winny jest nowy kod uSPR
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testShortChainDoesNotHang() {
        System.out.println("Testuję Wariant Krótki: NNI -> uSPR...");
        List<IncrementalHeuristicBaseMetric> chainShort = Arrays.asList(
                new NniIncrementalHeuristic(rfInc, "RF"),
                new UsprIncrementalHeuristicMetric(rfInc, "RF")
        );
        NniVndIncrementalHeuristic vndShort = new NniVndIncrementalHeuristic(chainShort, null, "RF");

        double dist = vndShort.getDistance(t1, t2);
        System.out.println("[SUKCES] Wariant krótki zakończony. Dystans: " + dist);
        assertTrue(dist >= 0);
    }

    // Jeśli ten test "spadnie" z błędem Timeout, to znaczy że winne są heurystyki ECR
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testFullChainDoesNotHang() {
        System.out.println("Testuję Wariant Pełny: NNI -> 2-sECR -> 3-sECR -> uSPR...");
        List<IncrementalHeuristicBaseMetric> chainFull = Arrays.asList(
                new NniIncrementalHeuristic(rfInc, "RF"),
                new Ecr2IncrementalHeuristic(rfInc, "RF"),
                new Ecr3IncrementalHeuristic(rfInc, "RF"),
                new UsprIncrementalHeuristicMetric(rfInc, "RF")
        );
        NniVndIncrementalHeuristic vndFull = new NniVndIncrementalHeuristic(chainFull, null, "RF");

        double dist = vndFull.getDistance(t1, t2);
        System.out.println("[SUKCES] Wariant pełny zakończony. Dystans: " + dist);
        assertTrue(dist >= 0);
    }
    @Test
    public void findPoisonPillPair() throws Exception {
        System.out.println("--- ROZPOCZYNAM SKANOWANIE WSZYSTKICH 100 PAR DRZEW (RF) ---");

        String file = "datasets/n10y200ub.newick";
        List<Tree> trees = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty() && !line.startsWith("#")) {
                    trees.add(TreeCreator.getTreeFromString(line.trim()));
                }
            }
        }

        RFIncrementalMetric rfInc = new RFIncrementalMetric();

        for (int i = 0; i < trees.size(); i += 2) {
            int pairIndex = i / 2;
            System.out.println("-> Badam parę nr " + pairIndex + "...");

            Tree t1 = new SimpleTree(trees.get(i));
            Tree t2 = new SimpleTree(trees.get(i + 1));
            ((SimpleTree) t1).createNodeList();
            ((SimpleTree) t2).createNodeList();

            // Używamy Wariantu 6 (Najbardziej podatnego na zapętlenia uSPR)
            List<IncrementalHeuristicBaseMetric> chain = Arrays.asList(
                    new NniIncrementalHeuristic(rfInc, "RF"),
                    new UsprIncrementalHeuristicMetric(rfInc, "RF")
            );
            NniVndIncrementalHeuristic vnd = new NniVndIncrementalHeuristic(chain, null, "RF");

            double dist = vnd.getDistance(t1, t2);
            System.out.println("[OK] Para " + pairIndex + " policzona. Dystans: " + dist);
        }

        System.out.println("--- SKANOWANIE ZAKOŃCZONE SUKCESEM ---");
    }
}