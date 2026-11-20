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
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.ClustIntersectInfoMatrix;
import treecmp.common.LapSolver;
import treecmp.common.TreeCmpUtils;
import treecmp.metrics.*;

public class MatchingClusterMetricFree extends BaseMetric implements Metric {

    protected int[] costId2NumT1;
    protected int[] costId2NumT2;
    protected int[] rowsol;
    protected int[] colsol;
    protected short[][] assigncost;
    protected ClustIntersectInfoMatrix cIntM;

    // PREALOKOWANE BUFORY ROBOCZE (Scratchpad Buffers) - eliminacja alokacji w gorących ścieżkach
    private int currentLapCapacity = 0;
    private int currentNodeCapacity = 0;
    private int[] u;
    private int[] v;
    private int[] t1Nums;
    private int[] t1Sizes;
    private int[] t2Nums;
    private int[] t2Sizes;
    private Node[] scratchNodesT1;
    private Node[] scratchNodesT2;

    private void ensureCapacity(int lapSize, int nodeSize) {
        if (assigncost == null || currentLapCapacity < lapSize) {
            int newLap = Math.max(lapSize, (currentLapCapacity == 0 ? 32 : currentLapCapacity * 2));
            assigncost = new short[newLap][newLap];
            rowsol = new int[newLap];
            colsol = new int[newLap];
            u = new int[newLap];
            v = new int[newLap];
            currentLapCapacity = newLap;
        }
        if (t1Nums == null || currentNodeCapacity < nodeSize) {
            int newNodes = Math.max(nodeSize, (currentNodeCapacity == 0 ? 32 : currentNodeCapacity * 2));
            t1Nums = new int[newNodes];
            t1Sizes = new int[newNodes];
            t2Nums = new int[newNodes];
            t2Sizes = new int[newNodes];
            scratchNodesT1 = new Node[newNodes];
            scratchNodesT2 = new Node[newNodes];
            currentNodeCapacity = newNodes;
        }
    }

    @Override
    public double getDistance(Tree t1, Tree t2, int... indexes) {
        IdGroup idGroup1 = TreeUtils.getLeafIdGroup(t1);
        IdGroup idGroup2 = TreeUtils.getLeafIdGroup(t2);
        // OPTYMALIZACJA 3: Szybkie sprawdzenie tożsamości grup liści przed łączeniem
        IdGroup idGroup = (idGroup1 == idGroup2 || idGroup1.equals(idGroup2)) ? idGroup1 : TreeCmpUtils.mergeIdGroups(idGroup1, idGroup2);
        cIntM = TreeCmpUtils.calcClustIntersectMatrix(t1, t2, idGroup);

        int totSize1 = t1.getInternalNodeCount() + t1.getExternalNodeCount();
        int totSize2 = t2.getInternalNodeCount() + t2.getExternalNodeCount();
        int size = Math.max(totSize1, totSize2);

        if (size <= 0) {
            return 0;
        }

        // OPTYMALIZACJA 1: Zero-Allocation na stercie
        ensureCapacity(size, Math.max(totSize1, totSize2));

        Node[] nodeT1 = TreeCmpUtils.getAllNodes(t1);
        Node[] nodeT2 = TreeCmpUtils.getAllNodes(t2);

        // OPTYMALIZACJA 2a: Hoisting — wyciągamy rozmiary klastrów z wyprzedzeniem
        for (int i = 0; i < totSize1; i++) {
            Node n1 = nodeT1[i];
            scratchNodesT1[i] = n1;
            t1Nums[i] = n1.getNumber();
            t1Sizes[i] = n1.isLeaf() ? 1 : cIntM.cSize1[t1Nums[i]];
        }

        for (int j = 0; j < totSize2; j++) {
            Node n2 = nodeT2[j];
            scratchNodesT2[j] = n2;
            t2Nums[j] = n2.getNumber();
            t2Sizes[j] = n2.isLeaf() ? 1 : cIntM.cSize2[t2Nums[j]];
        }

        // OPTYMALIZACJA 2b: 4-Quadrant Branchless Matrix Filling
        // Kwadrant 1: Pary węzłów [0..totSize1-1][0..totSize2-1]
        for (int r = 0; r < totSize1; r++) {
            Node n1 = scratchNodesT1[r];
            int s1 = t1Sizes[r];
            short[] row = assigncost[r];
            for (int c = 0; c < totSize2; c++) {
                int intCsize = cIntM.getInterSize(n1, scratchNodesT2[c]);
                row[c] = (short) (s1 + t2Sizes[c] - (intCsize << 1));
            }
        }

        // Kwadrant 2: Widmowe T1 / koszt węzłów T2 [totSize1..size-1][0..totSize2-1]
        for (int r = totSize1; r < size; r++) {
            short[] row = assigncost[r];
            for (int c = 0; c < totSize2; c++) {
                row[c] = (short) t2Sizes[c];
            }
        }

        // Kwadrant 3: Widmowe T2 / koszt węzłów T1 [0..totSize1-1][totSize2..size-1]
        for (int r = 0; r < totSize1; r++) {
            short[] row = assigncost[r];
            short s1 = (short) t1Sizes[r];
            for (int c = totSize2; c < size; c++) {
                row[c] = s1;
            }
        }

        // Kwadrant 4: Zera [totSize1..size-1][totSize2..size-1]
        for (int r = totSize1; r < size; r++) {
            short[] row = assigncost[r];
            for (int c = totSize2; c < size; c++) {
                row[c] = 0;
            }
        }

        return LapSolver.lapShort(size, assigncost, rowsol, colsol, u, v);
    }
}