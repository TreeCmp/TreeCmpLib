package treecmp.benchmarks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic;
import treecmp.metrics.topological.acc.MSIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NniVndUnrootedPair16DiagnosticTest {

    private List<Tree> loadUnrootedBenchmarkTrees() {
        // Ładujemy drzewa nieukorzenione z flagą unrootIfNeeded = true
        List<Tree> trees = TestTreeFactory.loadTrees("datasets/n10y200ub.newick", true);
        assertNotNull(trees, "Brak wczytanych drzew z datasets/n10y200ub.newick");
        assertTrue(trees.size() >= 32, "Plik musi zawierać co najmniej 16 par (32 drzewa)");
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
    @DisplayName("1. Diagnostyka zawieszenia 16. pary w VND NNI->SPR->TBR (Inc) dla MS (Unrooted)")
    void testDiagnose16thPairVndHang() {
        List<Tree> trees = loadUnrootedBenchmarkTrees();

        // Para #16 to indeks 15 (drzewa 30 i 31)
        int pairIdx = 15;
        Tree t1 = trees.get(pairIdx * 2);
        Tree t2 = trees.get(pairIdx * 2 + 1);
        prepareTree(t1);
        prepareTree(t2);

        System.out.println("================================================================================");
        System.out.printf(">>> URUCHAMIANIE DIAGNOSTYKI DLA PARY UNROOTED #%d (Drzewa %d i %d) <<<%n", pairIdx + 1, pairIdx * 2, pairIdx * 2 + 1);
        System.out.println("T1: " + t1);
        System.out.println("T2: " + t2);
        System.out.println("================================================================================");
        System.out.flush();

        // WATCHDOG: Wykrywa pętlę i zrzuca aktywny stos wątku
        final Thread testThread = Thread.currentThread();
        final AtomicBoolean completed = new AtomicBoolean(false);

        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(1500); // 1.5 sekundy
                if (!completed.get()) {
                    System.err.println("\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    System.err.println("!!! [WATCHDOG ALERT] ALGORYTM ZAWIESZONY NA 16. PARZE UNROOTED (MS)!");
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
        }, "Watchdog-Unrooted-Pair16");
        watchdog.setDaemon(true);
        watchdog.start();

        // VND wariant 7: NNI -> uSPR -> uTBR (Inc) dla drzew nieukorzenionych
        NniVndIncrementalHeuristic vndShort = new NniVndIncrementalHeuristic(Arrays.asList(
                new NniIncrementalHeuristic(new MSIncrementalMetric(), "MS"),
                new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), null, "MS"),
                new UtbrIncrementalHeuristic(new MSIncrementalMetric(), null, "MS")
        ), null, "MS");

        long start = System.currentTimeMillis();
        double distance;
        try {
            distance = vndShort.getDistance(new SimpleTree(t1), t2);
        } finally {
            completed.set(true);
            watchdog.interrupt();
        }
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("<<< WYNIK VND DLA PARY #16: Dystans = %.4f | Czas = %d ms%n", distance, elapsed);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("2. Izolacja faz NNI, uSPR, uTBR na 16. parze Unrooted")
    void testIsolatePhasesOnPair16() {
        List<Tree> trees = loadUnrootedBenchmarkTrees();

        int pairIdx = 15;
        Tree t1 = trees.get(pairIdx * 2);
        Tree t2 = trees.get(pairIdx * 2 + 1);
        prepareTree(t1);
        prepareTree(t2);

        System.out.println("\n--- TEST IZOLOWANYCH OPERATORÓW DLA PARY #16 (UNROOTED MS) ---");

        // Faza 1: NNI
        System.out.print("1. Wykonywanie NniIncrementalHeuristic... ");
        System.out.flush();
        NniIncrementalHeuristic nni = new NniIncrementalHeuristic(new MSIncrementalMetric(), "MS");
        double distNni = nni.performLocalDescent(t1, t2);
        Tree tAfterNni = nni.getLastOptimumTree();
        System.out.printf("OK (Dystans po NNI: %.4f)%n", distNni);

        // Faza 2: uSPR
        System.out.print("2. Wykonywanie UsprIncrementalHeuristicMetric na wyniku NNI... ");
        System.out.flush();
        UsprIncrementalHeuristicMetric uspr = new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), null, "MS");
        double distUspr = uspr.performLocalDescent(tAfterNni, t2);
        Tree tAfterUspr = uspr.getLastOptimumTree();
        System.out.printf("OK (Dystans po uSPR: %.4f)%n", distUspr);

        // Faza 3: uTBR
        System.out.print("3. Wykonywanie UtbrIncrementalHeuristic na wyniku uSPR... ");
        System.out.flush();
        UtbrIncrementalHeuristic utbr = new UtbrIncrementalHeuristic(new MSIncrementalMetric(), null, "MS");
        double distUtbr = utbr.performLocalDescent(tAfterUspr, t2);
        System.out.printf("OK (Dystans po uTBR: %.4f)%n", distUtbr);
    }
}