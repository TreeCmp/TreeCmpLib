/** This file is part of TreeCmp, a tool for comparing phylogenetic trees
 using the Matching Split distance and other metrics.
 Copyright (C) 2011,  Damian Bogdanowicz

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package treecmp.metrics.topological;

import pal.misc.IdGroup;
import pal.misc.SimpleIdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.AlignInfo;
import treecmp.common.ClustIntersectInfoMatrix;
import treecmp.common.ClustIntersectInfoMatrix.ClustPair;
import treecmp.common.IntNodePair;
import treecmp.common.LapSolver;
import treecmp.common.TreeCmpUtils;
import treecmp.config.IOSettings;
import treecmp.metrics.*;

import java.util.Arrays;

public class MatchingClusterMetricO3Long extends BaseMetric implements Metric {

    protected int[] costId2NumT1;
    protected int[] costId2NumT2;
    protected int[] rowsol;
    protected int[] colsol;
    protected long[][] assigncost;
    protected ClustIntersectInfoMatrix cIntM;

    // PREALOKOWANE BUFORY ROBOCZE (Scratchpad Buffers) - eliminacja alokacji w gorących ścieżkach
    private int currentLapCapacity = 0;
    private int currentItCapacity = 0;
    private int currentLapSize = 0;
    private long[] u;
    private long[] v;
    private int[] t1Ids;
    private long[] t1Sizes;
    private int[] t2Ids;
    private long[] t2Sizes;

    private void ensureCapacity(int lapSize, int itSize) {
        if (assigncost == null || currentLapCapacity < lapSize) {
            int newLap = Math.max(lapSize, (currentLapCapacity == 0 ? 32 : currentLapCapacity * 2));
            assigncost = new long[newLap][newLap];
            rowsol = new int[newLap];
            colsol = new int[newLap];
            u = new long[newLap];
            v = new long[newLap];
            currentLapCapacity = newLap;
        }
        if (t1Ids == null || currentItCapacity < itSize) {
            int newIt = Math.max(itSize, (currentItCapacity == 0 ? 32 : currentItCapacity * 2));
            t1Ids = new int[newIt];
            t1Sizes = new long[newIt];
            t2Ids = new int[newIt];
            t2Sizes = new long[newIt];
            costId2NumT1 = new int[newIt];
            costId2NumT2 = new int[newIt];
            currentItCapacity = newIt;
        }
    }

    @Override
    public double getDistance(Tree t1, Tree t2, int... indexes) {
        IdGroup idGroup1 = TreeUtils.getLeafIdGroup(t1);
        IdGroup idGroup2 = TreeUtils.getLeafIdGroup(t2);
        IdGroup idGroup = (idGroup1 == idGroup2 || idGroup1.equals(idGroup2)) ? idGroup1 : new SimpleIdGroup(idGroup1, idGroup2);
        cIntM = TreeCmpUtils.calcClustIntersectMatrix(t1, t2, idGroup);

        int size1 = t1.getInternalNodeCount();
        int size2 = t2.getInternalNodeCount();
        int n = t1.getExternalNodeCount();
        long counter = (long) n * n * n;
        long maxMulClustVal = (long) n * n;
        int eqClustSize = cIntM.eqClustList.size();

        int size = Math.max(size1 - eqClustSize, size2 - eqClustSize);
        int sizeIt = Math.max(size1, size2);

        if (size <= 0) {
            this.currentLapSize = 0;
            return 0;
        }

        // OPTYMALIZACJA 1: Zero-Allocation dla algebry 64-bitowej
        ensureCapacity(size, sizeIt);
        this.currentLapSize = size;

        // OPTYMALIZACJA 2a: Row Hoisting
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

        // OPTYMALIZACJA 2b: 4-Quadrant Branchless Matrix Filling dla typu long
        // Kwadrant 1: Prawdziwe pary [0..n1-1][0..n2-1]
        for (int r = 0; r < n1; r++) {
            int id1 = t1Ids[r];
            long s1 = t1Sizes[r];
            long[] row = assigncost[r];
            short[] interRow = cIntM.intCladeSize[id1];
            for (int c = 0; c < n2; c++) {
                int id2 = t2Ids[c];
                long s2 = t2Sizes[c];
                long baseCost = s1 + s2 - (interRow[id2] << 1);
                row[c] = baseCost * counter + maxMulClustVal - (s1 * s2);
            }
        }

        // Kwadrant 2: Widmowe T1 / koszt klastrów T2 [n1..size-1][0..n2-1]
        for (int r = n1; r < size; r++) {
            long[] row = assigncost[r];
            for (int c = 0; c < n2; c++) {
                row[c] = t2Sizes[c] * counter;
            }
        }

        // Kwadrant 3: Widmowe T2 / koszt klastrów T1 [0..n1-1][n2..size-1]
        for (int r = 0; r < n1; r++) {
            long[] row = assigncost[r];
            long s1Cost = t1Sizes[r] * counter;
            for (int c = n2; c < size; c++) {
                row[c] = s1Cost;
            }
        }

        // Kwadrant 4: Zera [n1..size-1][n2..size-1]
        for (int r = n1; r < size; r++) {
            long[] row = assigncost[r];
            for (int c = n2; c < size; c++) {
                row[c] = 0L;
            }
        }

        long metricScaled = LapSolver.lapLong(size, assigncost, rowsol, colsol, u, v);
        return (double) (metricScaled / counter);
    }

    @Override
    public AlignInfo getAlignment() {
        Tree t1 = cIntM.getT1();
        Tree t2 = cIntM.getT2();

        int leafSize = t1.getExternalNodeCount();
        long counter = (long) leafSize * leafSize * leafSize;
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
            cost = (int) (assigncost[i][j] / counter);
            totalCost += cost;
            aln[alnNum] = new IntNodePair();
            aln[alnNum].t1_node = costId2NumT1[i];
            aln[alnNum].t2_node = costId2NumT2[j];
            aln[alnNum].cost = cost;
            alnNum++;
        }

        alignInfo.setAln(aln);
        alignInfo.setUseClusters(true);
        alignInfo.setT1(t1);
        alignInfo.setT2(t2);
        alignInfo.setTotalCost(totalCost);
        return alignInfo;
    }
}