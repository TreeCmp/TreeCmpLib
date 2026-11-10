package treecmp.metrics.topological.acc;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.ReadTree;
import java.io.PushbackReader;
import pal.tree.Tree;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.spr.acc.IncrementalSprWalker;
import treecmp.heuristics.spr.acc.IncrementalUsprWalker;

import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Strażnik regresji wydajnościowych i pamięciowych dla metryk inkrementalnych (MS, MC, MP, M3).
 * Chroni przed:
 * 1) Wyciekami alokacji na stercie podczas spacerów SPR/uSPR (np. saveCurrentStateToHistory / fallback O(N^3)),
 * 2) Alokacją O(N^3) na stosie delta (deltaOldRows[d].length > 4 dla d >= 2),
 * 3) Nadmiarowym przeliczaniem wierszy (k = 2 zamiast k = 1) w krokach radialnych M3/MS,
 * 4) Usunięciem hybrydowej macierzy targetLcaColMatrix w MPIncrementalMetric.
 */
public class IncrementalMetricsRegressionGuardTest {

    private static Tree buildSyntheticBinaryTree(int numLeaves, int seed) throws Exception {
        // Generuje deterministyczne drzewo binarne w formacie Newick
        java.util.List<String> nodes = new java.util.ArrayList<>();
        for (int i = 1; i <= numLeaves; i++) {
            nodes.add("t" + i);
        }
        java.util.Random rnd = new java.util.Random(seed);
        while (nodes.size() > 1) {
            int i = rnd.nextInt(nodes.size());
            String a = nodes.remove(i);
            int j = rnd.nextInt(nodes.size());
            String b = nodes.remove(j);
            nodes.add("(" + a + "," + b + ")");
        }
        String newick = nodes.get(0) + ";";
        // POPRAWNIE:
        return new ReadTree(new PushbackReader(new StringReader(newick)));
    }

    @Test
    public void guardMemoryComplexityOfDeltaStacks() throws Exception {
        Tree t1 = buildSyntheticBinaryTree(40, 101);
        Tree t2 = buildSyntheticBinaryTree(40, 202);

        MPIncrementalMetric mp = new MPIncrementalMetric();
        MCIncrementalMetric mc = new MCIncrementalMetric();
        MSIncrementalMetric ms = new MSIncrementalMetric();
        M3IncrementalMetric m3 = new M3IncrementalMetric();

        mp.initCalculationState(t1, t2);
        mc.initCalculationState(t1, t2);

        Tree u1 = t1.getCopy();
        Tree u2 = t2.getCopy();
        TreeCmpUtils.unrootTreeIfNeeded(u1);
        TreeCmpUtils.unrootTreeIfNeeded(u2);
        ms.initCalculationState(u1, u2);
        m3.initCalculationState(u1, u2);

        // 1. Sprawdzenie obecności hybrydowej tablicy targetLcaColMatrix w MP
        Field lcaField = MPIncrementalMetric.class.getDeclaredField("targetLcaColMatrix");
        lcaField.setAccessible(true);
        assertNotNull(lcaField.get(mp),
                "REGRESJA W MP: Brak tablicy targetLcaColMatrix! Usunięcie rzadkiego LCA powoduje 2x spowolnienie.");

        // 2. Sprawdzenie, czy deltaOldRows na głębokości d >= 2 ma rozmiar O(1) (<= 4), a nie O(dim)
        for (Object metric : new Object[]{mp, mc, ms, m3}) {
            Field oldRowsField = metric.getClass().getDeclaredField("deltaOldRows");
            oldRowsField.setAccessible(true);
            Object[] deltaOldRows = (Object[]) oldRowsField.get(metric);
            for (int d = 2; d < deltaOldRows.length; d++) {
                int slots = java.lang.reflect.Array.getLength(deltaOldRows[d]);
                assertTrue(slots <= 4,
                        "REGRESJA PAMIĘCI O(N^3) w " + metric.getClass().getSimpleName()
                                + ": deltaOldRows[" + d + "] ma " + slots + " slotów zamiast <= 4!");
            }
        }
    }

    @Test
    public void guardZeroAllocationAndReversibilityInSprWalks() throws Exception {
        int N = 35;
        Tree t1 = buildSyntheticBinaryTree(N, 11);
        Tree t2 = buildSyntheticBinaryTree(N, 22);

        ThreadMXBean threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        boolean allocSupported = threadBean.isThreadAllocatedMemorySupported();
        if (allocSupported) {
            threadBean.setThreadAllocatedMemoryEnabled(true);
        }

        // --- Test uSPR (MS oraz M3) ---
        Tree u1 = t1.getCopy();
        Tree u2 = t2.getCopy();
        TreeCmpUtils.unrootTreeIfNeeded(u1);
        TreeCmpUtils.unrootTreeIfNeeded(u2);

        MSIncrementalMetric ms = new MSIncrementalMetric();
        ms.initCalculationState(u1, u2);
        IncrementalUsprWalker usprWalker = new IncrementalUsprWalker();

        // Rozgrzewka
        usprWalker.walk(u1, ms, (d, p, t) -> {});

        long beforeMsBytes = allocSupported ? threadBean.getCurrentThreadAllocatedBytes() : 0;
        double initMsDist = ms.getCurrentDistance();
        usprWalker.walk(u1, ms, (d, p, t) -> {});
        long allocatedMsBytes = allocSupported ? (threadBean.getCurrentThreadAllocatedBytes() - beforeMsBytes) : 0;

        assertEquals(initMsDist, ms.getCurrentDistance(), 1e-9, "MS: Nieodwracalny stan po spacerze uSPR!");

        // Sprawdzenie, że costHistory w MS jest całkowicie puste (brak wywołań saveCurrentStateToHistory)
        Field costHistField = MSIncrementalMetric.class.getDeclaredField("costHistory");
        costHistField.setAccessible(true);
        java.util.Stack<?> costHistory = (java.util.Stack<?>) costHistField.get(ms);
        assertTrue(costHistory.isEmpty(), "REGRESJA W MS: costHistory było używane zamiast stosu delta!");

        if (allocSupported) {
            // Pełny spacer uSPR dla N=35 (ponad 2000 odwiedzonych sąsiadów) nie powinien alokować więcej niż 64 KB
            assertTrue(allocatedMsBytes < 64 * 1024,
                    "REGRESJA ALOKACJI W MS: Spacer uSPR zaalokował " + (allocatedMsBytes / 1024)
                            + " KB! Prawdopodobny wyciek klonowania lub fallback do pełnego drzewa.");
        }

        // --- Test rSPR (MP oraz MC) ---
        MPIncrementalMetric mp = new MPIncrementalMetric();
        mp.initCalculationState(t1, t2);
        IncrementalSprWalker sprWalker = new IncrementalSprWalker();

        sprWalker.walk(t1, mp, (d, p, t) -> {});
        long beforeMpBytes = allocSupported ? threadBean.getCurrentThreadAllocatedBytes() : 0;
        double initMpDist = mp.getCurrentDistance();
        sprWalker.walk(t1, mp, (d, p, t) -> {});
        long allocatedMpBytes = allocSupported ? (threadBean.getCurrentThreadAllocatedBytes() - beforeMpBytes) : 0;

        assertEquals(initMpDist, mp.getCurrentDistance(), 1e-9, "MP: Nieodwracalny stan po spacerze rSPR!");
        if (allocSupported) {
            assertTrue(allocatedMpBytes < 64 * 1024,
                    "REGRESJA ALOKACJI W MP: Spacer rSPR zaalokował " + (allocatedMpBytes / 1024) + " KB!");
        }
    }

    @Test
    public void guardM3SingleRowUpdatePerRadialStep() throws Exception {
        Tree u1 = buildSyntheticBinaryTree(30, 77);
        Tree u2 = buildSyntheticBinaryTree(30, 88);
        TreeCmpUtils.unrootTreeIfNeeded(u1);
        TreeCmpUtils.unrootTreeIfNeeded(u2);

        M3IncrementalMetric m3 = new M3IncrementalMetric();
        m3.initCalculationState(u1, u2);

        Field rowsCountField = M3IncrementalMetric.class.getDeclaredField("deltaRowsCount");
        Field deltaPtrField = M3IncrementalMetric.class.getDeclaredField("deltaPointer");
        rowsCountField.setAccessible(true);
        deltaPtrField.setAccessible(true);

        int[] deltaRowsCount = (int[]) rowsCountField.get(m3);

        Node pruneNode = u1.getInternalNode(0);
        if (pruneNode.isRoot()) pruneNode = u1.getInternalNode(1);

        m3.applySprPrune(pruneNode);
        BitSet mask = m3.getPruneMask(pruneNode);
        Node startNode = pruneNode.getParent();
        Node nextNode = (startNode.getChild(0) != pruneNode) ? startNode.getChild(0) : startNode.getChild(1);

        boolean pushed = m3.applyRadialStep(nextNode, startNode, mask);
        assertTrue(pushed);

        int ptr = (int) deltaPtrField.get(m3);
        int modifiedRowsInRadialStep = deltaRowsCount[ptr - 1];
        assertTrue(modifiedRowsInRadialStep <= 1,
                "REGRESJA WYDAJNOŚCI W M3: applyRadialStep zmodyfikował " + modifiedRowsInRadialStep
                        + " wiersze zamiast maksymalnie 1 (uNode)!");

        m3.undoRadialStep();
        m3.undoSprPrune(pruneNode);
        assertEquals(0, (int) deltaPtrField.get(m3), "M3: Stos delta nie wrócił do 0 po cofnięciu kroków!");
    }
}