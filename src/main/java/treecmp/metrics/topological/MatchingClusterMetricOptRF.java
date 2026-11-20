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
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.ClusterDist;
import treecmp.common.LapSolver;
import treecmp.metrics.*;

import java.util.*;

public class MatchingClusterMetricOptRF extends BaseMetric implements Metric {

    // PREALOKOWANE BUFORY ROBOCZE (Scratchpad Buffers)
    private int currentLapCapacity = 0;
    private short[][] assigncost;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private void ensureCapacity(int lapSize) {
        if (assigncost == null || currentLapCapacity < lapSize) {
            int newLap = Math.max(lapSize, (currentLapCapacity == 0 ? 32 : currentLapCapacity * 2));
            assigncost = new short[newLap][newLap];
            rowsol = new int[newLap];
            colsol = new int[newLap];
            u = new int[newLap];
            v = new int[newLap];
            currentLapCapacity = newLap;
        }
    }

    @Override
    public double getDistance(Tree t1, Tree t2, int... indexes) {
        IdGroup idGroup = TreeUtils.getLeafIdGroup(t1);
        BitSet[] c1Temp = ClusterDist.RootedTree2BitSetArray(t1, idGroup);
        BitSet[] c2Temp = ClusterDist.RootedTree2BitSetArray(t2, idGroup);

        BitSet[] c1, c2;
        if (c1Temp.length <= c2Temp.length) {
            c1 = c1Temp;
            c2 = c2Temp;
        } else {
            c2 = c1Temp;
            c1 = c2Temp;
        }
        // c1 jest mniejsze lub równe

        // OPTYMALIZACJA 1: Wykorzystujemy HashSet do szybkiego O(1) odsiewania klastrów wspólnych
        int hashSetSize = (4 * (c2.length + 1)) / 3;
        HashSet<BitSet> set2 = new HashSet<>(hashSetSize);
        Collections.addAll(set2, c2);

        // OPTYMALIZACJA 2: ArrayList zapewnia liniowy dostęp do pamięci (L1 cache locality)
        ArrayList<BitSet> list1 = new ArrayList<>(c1.length);
        for (BitSet bs : c1) {
            if (!set2.remove(bs)) {
                list1.add(bs);
            }
        }

        int size1 = list1.size();
        int size2 = set2.size();
        int size = Math.max(size1, size2);

        if (size <= 0) {
            return 0;
        }

        // OPTYMALIZACJA 3: Zero-Allocation buforów solwera LAP
        ensureCapacity(size);

        BitSet[] arr1 = list1.toArray(new BitSet[0]);
        BitSet[] arr2 = set2.toArray(new BitSet[0]);

        // OPTYMALIZACJA 4: Bezpośrednie indeksowanie tablicowe zamiast iterators.next()
        BitSet[] rowBits = (size1 > size2) ? arr1 : arr2;
        BitSet[] colBits = (size1 > size2) ? arr2 : arr1;
        int rowCount = rowBits.length;
        int colCount = colBits.length;

        for (int r = 0; r < rowCount; r++) {
            BitSet bsRow = rowBits[r];
            short[] row = assigncost[r];
            for (int c = 0; c < colCount; c++) {
                row[c] = (short) ClusterDist.getDistXorBit(bsRow, colBits[c]);
            }
            short phantomCost = (short) ClusterDist.getDistToOAsMinBit(bsRow);
            for (int c = colCount; c < size; c++) {
                row[c] = phantomCost;
            }
        }

        return LapSolver.lapShort(size, assigncost, rowsol, colsol, u, v);
    }
}