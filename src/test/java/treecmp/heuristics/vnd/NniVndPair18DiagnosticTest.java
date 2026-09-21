package treecmp.heuristics.vnd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic;
import treecmp.metrics.topological.MatchingClusterMetric;
import treecmp.metrics.topological.acc.MCIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NniVndPair18DiagnosticTest {

    private List<Tree> loadBenchmarkTrees() {
        String path = "datasets/n10y200rb.newick";
        if (!new File(path).exists()) {
            path = "../datasets/n10y200rb.newick";
        }
        List<Tree> trees = TestTreeFactory.loadTrees(path);
        assertNotNull(trees, "Nie udało się załadować drzew z " + path);
        assertTrue(trees.size() >= 36, "Plik musi zawierać co najmniej 18 par (36 drzew)");
        return trees;
    }

    private static void prepareTree(Tree t) {
        if (t instanceof SimpleTree) {
            ((SimpleTree) t).createNodeList();
        }
        TreeUtils.computeParentPointers(t.getRoot());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("Diagnostyka zawieszenia 18. pary w VND NNI->SPR->TBR (Inc) dla MC")
    void testDiagnose18thPairVndHang() {
        List<Tree> trees = loadBenchmarkTrees();

        // Para #18 w benchmarku: indeks 17 (drzewa 34 i 35)
        int pairIdx = 17;
        Tree t1 = trees.get(pairIdx * 2);
        Tree t2 = trees.get(pairIdx * 2 + 1);
        prepareTree(t1);
        prepareTree(t2);

        System.out.println("================================================================================");
        System.out.printf(">>> URUCHAMIANIE DIAGNOSTYKI DLA PARY #%d (Drzewa %d i %d) <<<%n", pairIdx + 1, pairIdx * 2, pairIdx * 2 + 1);
        System.out.println("T1: " + t1);
        System.out.println("T2: " + t2);
        System.out.println("================================================================================");
        System.out.flush();

        // ---------------------------------------------------------------------
        // WATCHDOG: Wykrywa pętlę i zrzuca dokładny StackTrace z numerem linii
        // ---------------------------------------------------------------------
        final Thread testThread = Thread.currentThread();
        final AtomicBoolean completed = new AtomicBoolean(false);

        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(1500); // 1.5 sekundy
                if (!completed.get()) {
                    System.err.println("\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    System.err.println("!!! [WATCHDOG ALERT] ALGORYTM ZAWIESZONY NA 18. PARZE!");
                    System.err.println("!!! AKTUALNIE WYKONYWANA LINIA W KODZIE (MIEJSCE PĘTLI):");
                    System.err.println("--------------------------------------------------------------------------------");
                    StackTraceElement[] stack = testThread.getStackTrace();
                    for (StackTraceElement ste : stack) {
                        String line = ste.toString();
                        if (line.contains("treecmp") || line.contains("pal")) {
                            System.err.println("  -> AT: " + line);
                        }
                    }
                    System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n");
                    System.err.flush();
                }
            } catch (InterruptedException ignored) {
            }
        }, "Watchdog-Pair18");
        watchdog.setDaemon(true);
        watchdog.start();

        // Krok 1: Sprawdzenie całego łańcucha VND z wariantu 7
        NniVndIncrementalHeuristic vndShort = new NniVndIncrementalHeuristic(Arrays.asList(
                new NniIncrementalHeuristic(new MCIncrementalMetric(), "MC"),
                new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), null, "MC"),
                new TbrIncrementalHeuristic(new MCIncrementalMetric(), null, "MC")
        ), null, "MC");

        long start = System.currentTimeMillis();
        double distance;
        try {
            distance = vndShort.getDistance(new SimpleTree(t1), t2);
        } finally {
            completed.set(true);
            watchdog.interrupt();
        }
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("<<< WYNIK VND DLA PARY #18: Dystans = %.4f | Czas = %d ms%n", distance, elapsed);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Izolacja poszczególnych faz (NNI, SPR, TBR) na 18. parze")
    void testIsolatePhasesOnPair18() {
        List<Tree> trees = loadBenchmarkTrees();

        int pairIdx = 17;
        Tree t1 = trees.get(pairIdx * 2);
        Tree t2 = trees.get(pairIdx * 2 + 1);
        prepareTree(t1);
        prepareTree(t2);

        System.out.println("\n--- TEST IZOLOWANYCH OPERATORÓW DLA PARY #18 ---");

        // Faza 1: NNI
        System.out.print("1. Wykonywanie NniIncrementalHeuristic... ");
        System.out.flush();
        NniIncrementalHeuristic nni = new NniIncrementalHeuristic(new MCIncrementalMetric(), "MC");
        double distNni = nni.performLocalDescent(t1, t2);
        Tree tAfterNni = nni.getLastOptimumTree();
        System.out.printf("OK (Dystans po NNI: %.4f)%n", distNni);

        // Faza 2: SPR
        System.out.print("2. Wykonywanie SprIncrementalHeuristicMetric na wyniku NNI... ");
        System.out.flush();
        SprIncrementalHeuristicMetric spr = new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), null, "MC");
        double distSpr = spr.performLocalDescent(tAfterNni, t2);
        Tree tAfterSpr = spr.getLastOptimumTree();
        System.out.printf("OK (Dystans po SPR: %.4f)%n", distSpr);

        // Faza 3: TBR
        System.out.print("3. Wykonywanie TbrIncrementalHeuristic na wyniku SPR... ");
        System.out.flush();
        TbrIncrementalHeuristic tbr = new TbrIncrementalHeuristic(new MCIncrementalMetric(), null, "MC");
        double distTbr = tbr.performLocalDescent(tAfterSpr, t2);
        System.out.printf("OK (Dystans po TBR: %.4f)%n", distTbr);
    }
}