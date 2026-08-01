package treecmp.metrics.topological;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.LapSolver;
import treecmp.common.TreeCmpUtils;
import treecmp.metrics.BaseMetric;
import treecmp.metrics.Metric;

import java.util.Arrays;

public class MatchingPairMetric extends BaseMetric implements Metric {

    protected int[] rowsol;
    protected int[] colsol;
    protected int[][] assigncost;

    // PREALOKOWANE BUFORY ROBOCZE (Scratchpad Buffers - Zero Allocation w gorącej ścieżce)
    private int currentLapCapacity = 0;
    private int currentNodeCapacity = 0;
    private int[] u;
    private int[] v;
    private short[] cSize1;
    private short[] cSize2;
    private int[] t1IntPairCount;
    private int[] t2IntPairCount;

    public MatchingPairMetric() {
        super();
    }

    private void ensureCapacity(int lapSize, int nodeSize) {
        if (assigncost == null || currentLapCapacity < lapSize) {
            int newLap = Math.max(lapSize, (currentLapCapacity == 0 ? 32 : currentLapCapacity * 2));
            assigncost = new int[newLap][newLap];
            rowsol = new int[newLap];
            colsol = new int[newLap];
            u = new int[newLap];
            v = new int[newLap];
            currentLapCapacity = newLap;
        }
        if (cSize1 == null || currentNodeCapacity < nodeSize) {
            int newNodes = Math.max(nodeSize, (currentNodeCapacity == 0 ? 32 : currentNodeCapacity * 2));
            cSize1 = new short[newNodes];
            cSize2 = new short[newNodes];
            t1IntPairCount = new int[newNodes];
            t2IntPairCount = new int[newNodes];
            currentNodeCapacity = newNodes;
        }
    }

    @Override
    public double getDistance(Tree t1, Tree t2, int... indexes) {

        int N = t1.getExternalNodeCount();
        if (N <= 2) {
            return 0.0;
        }

        // Poprawne mapowanie identyfikatorów liści w bibliotece PAL
        IdGroup id1 = TreeUtils.getLeafIdGroup(t1);
        int[][] lcaMatrix1 = TreeCmpUtils.calcLcaMatrix(t1, null);
        int[][] lcaMatrix2 = TreeCmpUtils.calcLcaMatrix(t2, id1);

        int intT1Num = t1.getInternalNodeCount();
        int intT2Num = t2.getInternalNodeCount();
        int size = Math.max(intT1Num, intT2Num);

        if (size <= 0) {
            return 0.0;
        }

        // Dynamiczna prealokacja buforów instancji bez alokowania sterty w pętli
        ensureCapacity(size, Math.max(intT1Num, intT2Num) + 10);

        // OPTYMALIZACJA CACHE: Selektywne zerowanie wyłącznie Kwadrantu 1 (pod zliczanie LCA)
        // oraz Kwadrantu 4 (dla zerowych komórek LAP). Kwadranty 2 i 3 są nadpisywane bezpośrednio!
        for (int i = 0; i < intT1Num; i++) {
            Arrays.fill(assigncost[i], 0, intT2Num, 0);
        }
        for (int i = intT1Num; i < size; i++) {
            Arrays.fill(assigncost[i], intT2Num, size, 0);
        }

        Node[] postOrderT1 = TreeCmpUtils.getNodesInPostOrder(t1);
        Node[] postOrderT2 = TreeCmpUtils.getNodesInPostOrder(t2);

        TreeCmpUtils.calcCladeSizes(t1, postOrderT1, cSize1);
        TreeCmpUtils.calcCladeSizes(t2, postOrderT2, cSize2);

        // Zliczanie par liści w macierzy LCA (Kwadrant 1)
        for (int i = 0; i < N; i++) {
            int[] rowLca1 = lcaMatrix1[i];
            int[] rowLca2 = lcaMatrix2[i];
            for (int j = i + 1; j < N; j++) {
                assigncost[rowLca1[j]][rowLca2[j]]++;
            }
        }

        // Bezalokacyjne zliczanie par dzieci
        for (int i = 0; i < intT1Num; i++) {
            t1IntPairCount[i] = coutChildrenPairsFast(t1.getInternalNode(i), cSize1);
        }
        for (int i = 0; i < intT2Num; i++) {
            t2IntPairCount[i] = coutChildrenPairsFast(t2.getInternalNode(i), cSize2);
        }

        // 4-Quadrant Branchless Matrix Filling (brak instrukcji warunkowych if-else w pętli)
        // Kwadrant 1: Prawdziwe pary węzłów [0..intT1Num-1][0..intT2Num-1]
        for (int i = 0; i < intT1Num; i++) {
            int[] row = assigncost[i];
            int pairs1 = t1IntPairCount[i];
            for (int j = 0; j < intT2Num; j++) {
                row[j] = pairs1 + t2IntPairCount[j] - (row[j] << 1);
            }
        }

        // Kwadrant 2: Widmowe T1 [intT1Num..size-1][0..intT2Num-1]
        for (int i = intT1Num; i < size; i++) {
            int[] row = assigncost[i];
            System.arraycopy(t2IntPairCount, 0, row, 0, intT2Num);
        }

        // Kwadrant 3: Widmowe T2 [0..intT1Num-1][intT2Num..size-1]
        for (int i = 0; i < intT1Num; i++) {
            int[] row = assigncost[i];
            int pairs1 = t1IntPairCount[i];
            Arrays.fill(row, intT2Num, size, pairs1);
        }

        // Kwadrant 4: Zera [intT1Num..size-1][intT2Num..size-1] -> wyzerowane selektywnie na wstępie!

        int metric = LapSolver.lap(size, assigncost, rowsol, colsol, u, v);
        return 0.5 * (double) metric;
    }

    // Bezalokacyjna metoda O(k) oparta na tożsamości: sum(a_i * a_j) = ((sum a_i)^2 - sum(a_i^2)) / 2
    private int coutChildrenPairsFast(Node n, short[] clustSizeTab) {
        int chCount = n.getChildCount();
        int sum = 0;
        int sumSq = 0;

        for (int i = 0; i < chCount; i++) {
            Node chNode = n.getChild(i);
            int size = chNode.isLeaf() ? 1 : clustSizeTab[chNode.getNumber()];
            sum += size;
            sumSq += size * size;
        }
        return (sum * sum - sumSq) >> 1;
    }
}