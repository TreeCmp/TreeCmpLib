package treecmp.metrics.topological;

import pal.misc.IdGroup;
import pal.misc.SimpleIdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.*;
import treecmp.common.ClustIntersectInfoMatrix.ClustPair;
import treecmp.config.IOSettings;
import treecmp.metrics.*;

import java.util.Arrays;

public class MatchingSplitMetricO3 extends BaseMetric implements Metric {

    protected int[] costId2NumT1;
    protected int[] costId2NumT2;
    protected int[] rowsol;
    protected int[] colsol;
    protected short[][] assigncost;
    protected ClustIntersectInfoMatrix cIntM;

    // PREALOKOWANE BUFORY ROBOCZE (Scratchpad Buffers) - eliminacja alokacji w gorących ścieżkach
    private int currentLapCapacity = 0;
    private int currentItCapacity = 0;
    private int currentLapSize = 0;
    private int[] u;
    private int[] v;
    private int[] t1Ids;
    private int[] t1Sizes;
    private int[] t2Ids;
    private int[] t2Sizes;

    private void ensureCapacity(int lapSize, int itSize) {
        if (assigncost == null || currentLapCapacity < lapSize) {
            int newLap = Math.max(lapSize, (currentLapCapacity == 0 ? 32 : currentLapCapacity * 2));
            assigncost = new short[newLap][newLap];
            rowsol = new int[newLap];
            colsol = new int[newLap];
            u = new int[newLap];
            v = new int[newLap];
            currentLapCapacity = newLap;
        }
        if (t1Ids == null || currentItCapacity < itSize) {
            int newIt = Math.max(itSize, (currentItCapacity == 0 ? 32 : currentItCapacity * 2));
            t1Ids = new int[newIt];
            t1Sizes = new int[newIt];
            t2Ids = new int[newIt];
            t2Sizes = new int[newIt];
            costId2NumT1 = new int[newIt];
            costId2NumT2 = new int[newIt];
            currentItCapacity = newIt;
        }
    }

    public double getDistance(Tree t1, Tree t2, int... indexes) {

        int n = t1.getExternalNodeCount();
        IdGroup idGroup1 = TreeUtils.getLeafIdGroup(t1);
        IdGroup idGroup2 = TreeUtils.getLeafIdGroup(t2);
        // OPTYMALIZACJA 3: Unikanie zbędnej alokacji SimpleIdGroup przy identycznych grupach liści
        IdGroup idGroup = (idGroup1 == idGroup2 || idGroup1.equals(idGroup2)) ? idGroup1 : new SimpleIdGroup(idGroup1, idGroup2);
        cIntM = TreeCmpUtils.calcClustIntersectMatrix(t1, t2, idGroup);

        int size1 = t1.getInternalNodeCount();
        int size2 = t2.getInternalNodeCount();
        int eqClustSize = cIntM.eqClustList.size();

        int size = Math.max(size1 - eqClustSize, size2 - eqClustSize);
        int sizeIt = Math.max(size1, size2);

        if (size <= 0) {
            currentLapSize = 0;
            return 0;
        }

        // OPTYMALIZACJA 1: Zero-Allocation — używamy buforów instancji
        ensureCapacity(size, sizeIt);
        this.currentLapSize = size;

        // OPTYMALIZACJA 2a: Row Hoisting — wyciągamy rozmiary klastrów poza pętlę LAP
        int n1 = 0;
        for (int i = 0; i < size1; i++) {
            Node t1Node = t1.getInternalNode(i);
            if (t1Node.isRoot()) continue;
            int t1NodeNum = t1Node.getNumber();
            if (cIntM.eqClustT1[t1NodeNum]) continue;
            t1Ids[n1] = t1NodeNum;
            t1Sizes[n1] = cIntM.cSize1[t1NodeNum];
            n1++;
        }

        int n2 = 0;
        for (int j = 0; j < size2; j++) {
            Node t2Node = t2.getInternalNode(j);
            if (t2Node.isRoot()) continue;
            int t2NodeNum = t2Node.getNumber();
            if (cIntM.eqClustT2[t2NodeNum]) continue;
            t2Ids[n2] = t2NodeNum;
            t2Sizes[n2] = cIntM.cSize2[t2NodeNum];
            n2++;
        }

        if (IOSettings.getIOSettings().isGenAlignments()) {
            System.arraycopy(t1Ids, 0, costId2NumT1, 0, n1);
            Arrays.fill(costId2NumT1, n1, sizeIt, -1);
            System.arraycopy(t2Ids, 0, costId2NumT2, 0, n2);
            Arrays.fill(costId2NumT2, n2, sizeIt, -1);
        }

        // OPTYMALIZACJA 2b: 4-Quadrant Branchless Matrix Filling (sztywna formuła MS dla par podziałów)
        // Kwadrant 1: Prawdziwe pary [0..n1-1][0..n2-1]
        for (int r = 0; r < n1; r++) {
            int id1 = t1Ids[r];
            int s1 = t1Sizes[r];
            short[] row = assigncost[r];
            short[] interRow = cIntM.intCladeSize[id1];
            for (int c = 0; c < n2; c++) {
                int id2 = t2Ids[c];
                int x1 = s1 + t2Sizes[c] - (interRow[id2] << 1);
                int x2 = n - x1;
                row[c] = (short) Math.min(x1, x2);
            }
        }

        // Kwadrant 2: Widmowe T1 / koszt podziałów T2 [n1..size-1][0..n2-1]
        for (int r = n1; r < size; r++) {
            short[] row = assigncost[r];
            for (int c = 0; c < n2; c++) {
                int s2 = t2Sizes[c];
                row[c] = (short) Math.min(n - s2, s2);
            }
        }

        // Kwadrant 3: Widmowe T2 / koszt podziałów T1 [0..n1-1][n2..size-1]
        for (int r = 0; r < n1; r++) {
            short[] row = assigncost[r];
            short cost = (short) Math.min(n - t1Sizes[r], t1Sizes[r]);
            for (int c = n2; c < size; c++) {
                row[c] = cost;
            }
        }

        // Kwadrant 4: Zera [n1..size-1][n2..size-1]
        for (int r = n1; r < size; r++) {
            short[] row = assigncost[r];
            for (int c = n2; c < size; c++) {
                row[c] = 0;
            }
        }

        return LapSolver.lapShort(size, assigncost, rowsol, colsol, u, v);
    }

    @Override
    public AlignInfo getAlignment() {

        Tree t1 = cIntM.getT1();
        Tree t2 = cIntM.getT2();

        int leafSize = t1.getExternalNodeCount();
        int size1 = t1.getInternalNodeCount();
        int size2 = t2.getInternalNodeCount();
        AlignInfo alignInfo = new AlignInfo();
        int j, cost;
        int size = Math.max(size1, size2);

        int sizeWithoutRoot = size - 1;
        IntNodePair[] aln = new IntNodePair[sizeWithoutRoot];
        int alnNum = 0;
        for (ClustPair cp : cIntM.eqClustList) {
            if (cIntM.cSize1[cp.t1IntId] == leafSize)
                continue;
            aln[alnNum] = new IntNodePair();
            aln[alnNum].t1_node = cp.t1IntId;
            aln[alnNum].t2_node = cp.t2IntId;
            aln[alnNum].cost = 0;
            alnNum++;
        }

        int totalCost = 0;
        for (int i = 0; i < currentLapSize; i++) {
            j = rowsol[i];
            cost = assigncost[i][j];
            totalCost += cost;
            aln[alnNum] = new IntNodePair();
            aln[alnNum].t1_node = costId2NumT1[i];
            aln[alnNum].t2_node = costId2NumT2[j];
            aln[alnNum].cost = cost;
            alnNum++;
        }

        alignInfo.setAln(aln);
        alignInfo.setUseClusters(false);
        alignInfo.setT1(t1);
        alignInfo.setT2(t2);
        alignInfo.setTotalCost(totalCost);
        return alignInfo;
    }
}