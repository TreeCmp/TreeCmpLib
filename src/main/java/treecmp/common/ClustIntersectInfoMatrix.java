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
import pal.tree.TreeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClustIntersectInfoMatrix {

    protected int intT1Num;
    protected int extT1Num;
    protected int intT2Num;
    protected int extT2Num;

    protected Tree t1;
    protected Tree t2;
    protected IdGroup idGroup;

    protected int[] alias1;
    protected int[] alias2;

    // =========================================================
    // NOWE BUFORY ZERO-ALLOCATION (Rozwiązują Twój błąd!)
    // =========================================================
    public Node[] postOrderT1;
    public Node[] postOrderT2;

    public short[][] intCladeSize;
    public short[] cSize1;
    public short[] cSize2;

    public boolean[][] intT1toLeafT2;
    public boolean[][] intT2toLeafT1;

    public List<ClustPair> eqClustList;
    public boolean[] eqClustT1;
    public boolean[] eqClustT2;

    private ClustPair[] clustPairPool;

    public ClustIntersectInfoMatrix(Tree t1, Tree t2, IdGroup idGroup) {
        this.t1 = t1;
        this.t2 = t2;
        this.idGroup = idGroup;
    }

    public void setup(Tree t1, Tree t2, IdGroup idGroup) {
        this.t1 = t1;
        this.t2 = t2;
        this.idGroup = idGroup;
        init();
    }

    public void init(){
        intT1Num = t1.getInternalNodeCount();
        extT1Num = t1.getExternalNodeCount();
        intT2Num = t2.getInternalNodeCount();
        extT2Num = t2.getExternalNodeCount();

        int maxIdCount = idGroup.getIdCount();

        // 1. BEZALOKACYJNE ALIASOWANIE
        if (alias1 == null || alias1.length < extT1Num) alias1 = new int[Math.max(32, extT1Num * 2)];
        if (alias2 == null || alias2.length < extT2Num) alias2 = new int[Math.max(32, extT2Num * 2)];
        TreeUtils.mapExternalIdentifiers(idGroup, t1, alias1);
        TreeUtils.mapExternalIdentifiers(idGroup, t2, alias2);

        // 2. BEZALOKACYJNY POST-ORDER
        int allT1Num = intT1Num + extT1Num;
        int allT2Num = intT2Num + extT2Num;
        if (postOrderT1 == null || postOrderT1.length < allT1Num) postOrderT1 = new Node[Math.max(32, allT1Num * 2)];
        if (postOrderT2 == null || postOrderT2.length < allT2Num) postOrderT2 = new Node[Math.max(32, allT2Num * 2)];
        TreeCmpUtils.getNodesInPostOrder(t1, postOrderT1);
        TreeCmpUtils.getNodesInPostOrder(t2, postOrderT2);

        if (intCladeSize == null || intT1Num > intCladeSize.length || intT2Num > intCladeSize[0].length) {
            int newInt1 = Math.max(intT1Num, intCladeSize == null ? 32 : intCladeSize.length * 2);
            int newInt2 = Math.max(intT2Num, intCladeSize == null ? 32 : intCladeSize[0].length * 2);
            intCladeSize = new short[newInt1][newInt2];
        }
        for (int i = 0; i < intT1Num; i++) Arrays.fill(intCladeSize[i], 0, intT2Num, (short)0);

        if (intT1toLeafT2 == null || intT1Num > intT1toLeafT2.length || maxIdCount > intT1toLeafT2[0].length) {
            int newInt1 = Math.max(intT1Num, intT1toLeafT2 == null ? 32 : intT1toLeafT2.length * 2);
            int newExt2 = Math.max(maxIdCount, intT1toLeafT2 == null ? 32 : intT1toLeafT2[0].length * 2);
            intT1toLeafT2 = new boolean[newInt1][newExt2];
        }
        for (int i = 0; i < intT1Num; i++) Arrays.fill(intT1toLeafT2[i], 0, maxIdCount, false);

        if (intT2toLeafT1 == null || intT2Num > intT2toLeafT1.length || maxIdCount > intT2toLeafT1[0].length) {
            int newInt2 = Math.max(intT2Num, intT2toLeafT1 == null ? 32 : intT2toLeafT1.length * 2);
            int newExt1 = Math.max(maxIdCount, intT2toLeafT1 == null ? 32 : intT2toLeafT1[0].length * 2);
            intT2toLeafT1 = new boolean[newInt2][newExt1];
        }
        for (int i = 0; i < intT2Num; i++) Arrays.fill(intT2toLeafT1[i], 0, maxIdCount, false);

        if (cSize1 == null || intT1Num > cSize1.length) {
            int newInt1 = Math.max(intT1Num, cSize1 == null ? 32 : cSize1.length * 2);
            cSize1 = new short[newInt1];
            eqClustT1 = new boolean[newInt1];
        }
        Arrays.fill(cSize1, 0, intT1Num, (short)0);
        Arrays.fill(eqClustT1, 0, intT1Num, false);

        if (cSize2 == null || intT2Num > cSize2.length) {
            int newInt2 = Math.max(intT2Num, cSize2 == null ? 32 : cSize2.length * 2);
            cSize2 = new short[newInt2];
            eqClustT2 = new boolean[newInt2];
        }
        Arrays.fill(cSize2, 0, intT2Num, (short)0);
        Arrays.fill(eqClustT2, 0, intT2Num, false);

        if (eqClustList == null) {
            int minIntNodeNum = Math.max(32, Math.min(intT1Num, intT2Num));
            eqClustList = new ArrayList<ClustPair>(minIntNodeNum);
            clustPairPool = new ClustPair[minIntNodeNum * 2];
            for(int i = 0; i < clustPairPool.length; i++) clustPairPool[i] = new ClustPair();
        }
        eqClustList.clear();
    }

    public int getExtT1Num() { return extT1Num; }
    public int getExtT2Num() { return extT2Num; }
    public int getIntT1Num() { return intT1Num; }
    public int getIntT2Num() { return intT2Num; }
    public int[] getAlias1() { return alias1; }
    public int[] getAlias2() { return alias2; }
    public IdGroup getIdGroup() { return idGroup; }
    public Tree getT1() { return t1; }
    public Tree getT2() { return t2; }

    public short getT1Ext_T2Ext(int t1ExtId, int t2ExtId){
        if (alias1[t1ExtId] == alias2[t2ExtId]) return 1;
        else return 0;
    }

    public short getT1Int_T2Ext(int t1IntId, int t2ExtId){
        if (intT1toLeafT2[t1IntId][alias2[t2ExtId]]) return 1;
        else return 0;
    }

    public short getT1Ext_T2Int(int t1ExtId, int t2IntId){
        if (intT2toLeafT1[t2IntId][alias1[t1ExtId]]) return 1;
        else return 0;
    }

    public short getT1Int_T2Int(int t1IntId, int t2IntId){
        return intCladeSize[t1IntId][t2IntId];
    }

    public void setT1Int_T2Ext(int t1IntId, int t2ExtId, short intSize) {
        if (intSize == 1) intT1toLeafT2[t1IntId][alias2[t2ExtId]] = true;
    }

    public void setT1Ext_T2Int(int t1ExtId, int t2IntId, short intSize) {
        if (intSize == 1) intT2toLeafT1[t2IntId][alias1[t1ExtId]] = true;
    }

    public void setT1Int_T2Int(int t1IntId, int t2IntId, short intSize){
        intCladeSize[t1IntId][t2IntId] = intSize;

        //check if these are the same clusters
        if (intSize == cSize1[t1IntId] && intSize == cSize2[t2IntId] ){
            if (eqClustList.size() >= clustPairPool.length) {
                int oldSize = clustPairPool.length;
                ClustPair[] newPool = new ClustPair[oldSize * 2];
                System.arraycopy(clustPairPool, 0, newPool, 0, oldSize);
                for(int i = oldSize; i < newPool.length; i++) newPool[i] = new ClustPair();
                clustPairPool = newPool;
            }

            ClustPair cp = clustPairPool[eqClustList.size()];
            cp.t1IntId = t1IntId;
            cp.t2IntId = t2IntId;
            eqClustList.add(cp);

            eqClustT1[t1IntId] = true;
            eqClustT2[t2IntId] = true;
        }
    }

    public short getInterSize(Node n1, Node n2){
        int n1Num = n1.getNumber();
        int n2Num = n2.getNumber();
        boolean n1Leaf = n1.isLeaf();
        boolean n2Leaf = n2.isLeaf();

        if ((!n1Leaf) && (!n2Leaf)) return getT1Int_T2Int(n1Num,n2Num);
        if (n1Leaf && n2Leaf) return getT1Ext_T2Ext(n1Num,n2Num);
        if (n1Leaf && (!n2Leaf)) return getT1Ext_T2Int(n1Num,n2Num);
        return getT1Int_T2Ext(n1Num,n2Num);
    }

    public short getSizeT1(Node n){
        if (n.isLeaf()) return 1;
        else return cSize1[n.getNumber()];
    }

    public short getSizeT2(Node n){
        if (n.isLeaf()) return 1;
        else return cSize2[n.getNumber()];
    }

    public class ClustPair{
        public int t1IntId;
        public int t2IntId;
    }
}