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

package treecmp.common;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;

public class ClusterDist {

    public ClusterDist() {
    }

    public static int clusterXor(boolean[] clade_t1, boolean[] clade_t2)
    {
        int n=clade_t1.length;
        int neq=0;

        for(int i=0;i<n;i++) {
            if(clade_t1[i]!=clade_t2[i]) neq++;
        }

        return  neq;
    }
    
    public static BitSet[] RootedTree2BitSetArray(Tree t, IdGroup idGroup) {
        int N = t.getInternalNodeCount();
        int n = t.getExternalNodeCount();
        Node node;
        int j = 0;
        BitSet[] bsA = new BitSet[N - 1];

        for (int i = 0; i < N; i++) {
            node = t.getInternalNode(i);
            if (node.isRoot()) {
                continue;
            }
            bsA[j] = new BitSet(n);
            markRootedTreeNode(idGroup, node, bsA[j]);
            j++;
        }

        return bsA;
    }

    public static BitSet[] UnuootedTree2BitSetArray(Tree t, IdGroup idGroup) {
        int N = t.getInternalNodeCount();
        int n = t.getExternalNodeCount();
        Node node;
        int j = 0;
        BitSet[] bsA = new BitSet[N];

        for (int i = 0; i < N; i++) {
            node = t.getInternalNode(i);
            bsA[j] = new BitSet(n);
            markUnrootedTreeNode(idGroup, node, bsA[j]);
            j++;
        }

        // Arrays.sort(bsA, (BitSet lhs, BitSet rhs) -> compareBitSets(lhs, rhs));
        Arrays.sort(bsA, new Comparator<BitSet>() {
            @Override
            public int compare(BitSet lhs, BitSet rhs) {
                return compareBitSets(lhs, rhs);
            }
        });
        return bsA;
    }

    private static int compareBitSets(BitSet lhs, BitSet rhs) {
        if (lhs.equals(rhs)) return 0;
        if (lhs.cardinality() != rhs.cardinality()) {
            return lhs.cardinality() > rhs.cardinality() ? 1 : - 1;
        }
        else {
            return lhs.nextSetBit(0) > rhs.nextSetBit(0) ? 1 : - 1;
        }
    }

    private static void swap(BitSet lhs, BitSet rhs) {
        lhs.xor(rhs);
        rhs.xor(lhs);
        lhs.xor(rhs);
    }
     
    static void markRootedTreeNode(IdGroup idGroup, Node node, BitSet cluster) {
        if (node.isLeaf()) {
            String name = node.getIdentifier().getName();
            int index = idGroup.whichIdNumber(name);

            if (index < 0) {
                throw new IllegalArgumentException("INCOMPATIBLE IDENTIFIER (" + name + ")");
            }
            cluster.set(index);
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                markRootedTreeNode(idGroup, node.getChild(i), cluster);
            }
        }
    }

    private static void markUnrootedTreeNode(IdGroup idGroup, Node node, BitSet cluster) {
        BitSet[] subTrees = new BitSet[3];
        for (int i = 0; i < 3; i++) subTrees[i] = new BitSet();
        markRootedTreeNode(idGroup, node.getChild(0), subTrees[0]);
        markRootedTreeNode(idGroup, node.getChild(1), subTrees[1]);
        subTrees[2].set(0, idGroup.getIdCount(), true);
        subTrees[2].andNot(subTrees[0]);
        subTrees[2].andNot(subTrees[1]);
        //Arrays.sort(subTrees, (BitSet lhs, BitSet rhs) -> compareBitSets(lhs, rhs));
        Arrays.sort(subTrees, new Comparator<BitSet>() {
            @Override
            public int compare(BitSet lhs, BitSet rhs) {
                return compareBitSets(lhs, rhs);
            }
        });
        cluster.or(subTrees[0]);
        cluster.or(subTrees[1]);
    }

    public static int getDistXorBit(BitSet cluster1, BitSet cluster2) {

        BitSet temp = (BitSet) cluster1.clone();
        temp.xor(cluster2);
        int d = temp.cardinality();
        return d;
    }
    public static int getAndBit(BitSet cluster1, BitSet cluster2) {

        BitSet temp = (BitSet) cluster1.clone();
        temp.and(cluster2);
        int d = temp.cardinality();
        return d;
    }

    public static int getDistToOAsMinBit(BitSet cluster) {

        int t = cluster.cardinality();
        return t;

    }
}
