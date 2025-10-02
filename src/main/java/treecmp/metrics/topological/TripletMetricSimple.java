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
import treecmp.common.TreeCmpUtils;
import treecmp.metrics.*;

public class TripletMetricSimple extends BaseMetric implements Metric {
  

    public double getDistance(Tree t1, Tree t2, int... indexes) {
        return getDistForArbitrary(t1, t2);
    }


    /**
     * Calculates the **triplet distance** between two trees, {@code t1} and {@code t2}, using
     * a simple $O(n^3)$ algorithm that enumerates all possible triplets (subsets of three leaves).
     *
     * <p>The distance is computed by finding the total number of triplets where the resolution
     * (topology) differs between the two trees, or where one or both trees leave the triplet
     * unresolved. The trees must share the same set of leaves for a meaningful comparison.
     *
     * @param t1 The first phylogenetic tree.
     * @param t2 The second phylogenetic tree.
     * @return The triplet distance between {@code t1} and {@code t2}, returned as a {@code double}.
     */
    public double getDistForArbitrary(Tree t1, Tree t2) {

        IdGroup id1 = TreeUtils.getLeafIdGroup(t1);
        int[][] nsMatrix1 = TreeCmpUtils.calcNodalSplittedMatrix(t1, null);
        int[][] nsMatrix2 = TreeCmpUtils.calcNodalSplittedMatrix(t2, id1);
        long unResolved_T1 = 0;
        long unResolved_T2 = 0;
        long unResolved_Common = 0;
        long resolved_Common = 0;
        long sum = 0;
        int type1, type2;

        int leafNum = t1.getExternalNodeCount();
        for (int i = 0; i < leafNum; i++) {
            for (int j = i + 1; j < leafNum; j++) {
                for (int k = j + 1; k < leafNum; k++) {
                    type1 = getTripletType(i, j, k, nsMatrix1);
                    type2 = getTripletType(i, j, k, nsMatrix2);
                    if (type1 == -1) {
                        unResolved_T1++;
                    }
                    if (type1 == -1) {
                        unResolved_T2++;
                    }
                    if (type1 == type2) {
                        if (type1 == -1) {
                            unResolved_Common++;
                        } else {
                            resolved_Common++;
                        }
                    }
                    sum++;
                }
            }
        }

        long dist = sum - unResolved_Common - resolved_Common;
        return (double) dist;
    }

    /**
     * Returns the **type** of a triplet of leaves (i, j, k), which is the index of the leaf
     * whose **Least Common Ancestor (LCA)** with the other two leaves is closest to the root.
     *
     * <p>The method uses the Nodal Splitted Matrix (`nsMatrix`), where `nsMatrix[x][y]` typically
     * represents the depth (distance from the root) of the LCA of leaves `x` and `y`.
     *
     * <ul>
     * <li>Returns **i** for a topology like (i, (j, k)) where LCA(j, k) is deeper than LCA(i, j) or LCA(i, k).</li>
     * <li>Returns **j** for a topology like (j, (i, k)).</li>
     * <li>Returns **k** for a topology like (k, (i, j)).</li>
     * <li>Returns **-1** if the triplet is **unresolved** (i.e., all three leaves share the same LCA, as in (i, j, k)).</li>
     * </ul>
     *
     * @param i The index of the first leaf.
     * @param j The index of the second leaf.
     * @param k The index of the third leaf.
     * @param nsMatrix The Nodal Splitted Matrix where `nsMatrix[x][y]` gives the depth of LCA(x, y).
     * @return The index (i, j, or k) of the leaf defining the deepest split, or -1 if the triplet is unresolved.
     */
    private int getTripletType(int i, int j, int k, int nsMatrix[][]) {

        if (nsMatrix[j][i] > nsMatrix[j][k]) {
            return i;
        } else if (nsMatrix[j][i] < nsMatrix[j][k]) {
            return k;
        } else {
            //two situation are possible: type = j or (-1) - triplet is unresoved
            if (nsMatrix[i][j] > nsMatrix[i][k]) {
                return j;
            }
            //nsMatrix[i][j] < nsMatrix[i][k] could not happen
            //only nsMatrix[i][j] = nsMatrix[i][k] is possible here
            //hence, triplet is unresolved
        }
        return -1;
    }
}
