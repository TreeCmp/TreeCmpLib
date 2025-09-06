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
import pal.misc.Identifier;
import pal.misc.SimpleIdGroup;
import pal.tree.*;

import java.util.*;

public class TreeCmpUtils {

    public static int[][] calcLcaMatrix(Tree tree, IdGroup idGroup) {

        int leafNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();
        if (idGroup == null)
            idGroup = TreeUtils.getLeafIdGroup(tree);

        int[] alias = TreeUtils.mapExternalIdentifiers(idGroup, tree);
        int[][] lcaMatrix = new int[leafNum][leafNum];
        for (int i=0;i<leafNum;i++)
            lcaMatrix[i][i] = -1;
        TCUtilsNode[] nodeInfoTab = new TCUtilsNode[intNum];
        Node childNode, curNode = tree.getExternalNode(0);
        int childCount, nodeIndex, childIndex;
        boolean endLoop = true;

        while (endLoop) {
            if (curNode.isRoot()) {
                endLoop = false;
            }

            if (!curNode.isLeaf()) {
                nodeIndex = curNode.getNumber();
                childCount = curNode.getChildCount();
                nodeInfoTab[nodeIndex] = new TCUtilsNode();

                for (int i = 0; i < childCount; i++) {
                    childNode = curNode.getChild(i);
                    childIndex = childNode.getNumber();
                    if (childNode.isLeaf()) {
                        nodeInfoTab[nodeIndex].addLeaf(alias[childIndex]);
                    } else {
                        nodeInfoTab[nodeIndex].add(nodeInfoTab[childIndex]);
                    }
                }
            }
            curNode = NodeUtils.postorderSuccessor(curNode);
        }

        TCUtilsNode utilsNode;
        List<List<Integer>> leafSet;
        for (int i = 0; i < intNum; i++) {
            utilsNode = nodeInfoTab[i];
            leafSet = utilsNode.getLeafSets();
            for (int j = 0; j < leafSet.size() - 1; j++) {
                List<Integer> baseList = leafSet.get(j);
                for (Integer i1 : baseList) {
                    for (int k = j + 1; k < leafSet.size(); k++) {
                        List<Integer> secondList = leafSet.get(k);
                        for (Integer i2 : secondList) {
                            lcaMatrix[i1][i2] = i;
                            lcaMatrix[i2][i1] = i;
                        }
                    }
                }
            }
        }
        return lcaMatrix;
    }

    public static int[][][] calcNcvMatrix(Tree tree, IdGroup idGroup, int[][] lcaMatrix) {

        int leafNum = tree.getExternalNodeCount();
        //int intNum = tree.getInternalNodeCount();
        if (idGroup == null)
            idGroup = TreeUtils.getLeafIdGroup(tree);

        int[] alias = TreeUtils.mapExternalIdentifiers(idGroup, tree);
        if (lcaMatrix == null) {
            lcaMatrix = calcLcaMatrix(tree, idGroup);
        }

        int[][][] ncvMatrix = new int[leafNum][leafNum][leafNum];

        for (int i=0;i<leafNum;i++) {
            for (int j=0;j<leafNum;j++) {
                ncvMatrix[i][i][j] = -1;
                ncvMatrix[i][j][j] = -1;
                ncvMatrix[j][i][j] = -1;
            }
        }

        for (int i=0;i<leafNum;i++) {
            for (int j=i+1;j<leafNum;j++) {
                for (int k=j+1;k<leafNum;k++) {
                    // ncv to unikalny z trzech lca (i,j), (i,k) albo (j,k).
                    int i_j_lca = lcaMatrix[alias[i]][alias[j]];
                    int i_k_lca = lcaMatrix[alias[i]][alias[k]];
                    int j_k_lca = lcaMatrix[alias[j]][alias[k]];
                    int ncv;
                    if (i_j_lca == i_k_lca) {
                        ncv = j_k_lca;
                    }
                    else if (i_j_lca == j_k_lca) {
                        ncv = i_k_lca;
                    }
                    else {
                        ncv = i_j_lca;
                    }
                    ncvMatrix[alias[i]][alias[j]][alias[k]] =
                            ncvMatrix[alias[i]][alias[k]][alias[j]] =
                                    ncvMatrix[alias[j]][alias[i]][alias[k]] =
                                            ncvMatrix[alias[j]][alias[k]][alias[i]] =
                                                    ncvMatrix[alias[k]][alias[i]][alias[j]] =
                                                            ncvMatrix[alias[k]][alias[j]][alias[i]] = ncv;
                }

            }
        }

        return ncvMatrix;
    }

    public static int getNcv(Tree tree, int i, int j, int k, int[][] lcaMatrix, int[] alias) {

        if (alias == null || lcaMatrix == null) {
            IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
            alias = TreeUtils.mapExternalIdentifiers(idGroup, tree);
            lcaMatrix = calcLcaMatrix(tree, idGroup);
        }

        // ncv to unikalny z trzech lca (i,j), (i,k) albo (j,k).
        int i_j_lca = lcaMatrix[alias[i]][alias[j]];
        int i_k_lca = lcaMatrix[alias[i]][alias[k]];
        int j_k_lca = lcaMatrix[alias[j]][alias[k]];
        int ncv;
        if (i_j_lca == i_k_lca) {
            ncv = j_k_lca;
        }
        else if (i_j_lca == j_k_lca) {
            ncv = i_k_lca;
        }
        else {
            ncv = i_j_lca;
        }
        return ncv;
    }

    public static Set<Node>[] getVerticesOutsideClade(Tree tree) {
        Set<Node>[] verticesInsideClade;
        Set<Node>[] verticesOutsideClade;
        int leafNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();

        verticesInsideClade = new Set[intNum];
        verticesOutsideClade = new Set[intNum];
        Node[] postOrder = TreeCmpUtils.getNodesInPostOrder(tree);
        for (Node curNode: postOrder){
            if (curNode.isLeaf()) {
                continue;
            }
            else {
                int curNodeNumber = curNode.getNumber();
                verticesOutsideClade[curNodeNumber] = new HashSet<Node>();
                verticesInsideClade[curNodeNumber] = new HashSet<Node>();
                for (int i = 0; i < leafNum; i++) {
                    verticesOutsideClade[curNodeNumber].add(tree.getExternalNode(i));
                }
                int childCount = curNode.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    Node child = curNode.getChild(i);
                    if (child.isLeaf()) {
                        verticesOutsideClade[curNodeNumber].remove(child);
                        verticesInsideClade[curNodeNumber].add(child);
                    }
                    else {
                        Set<Node> childVertices = verticesInsideClade[child.getNumber()];
                        for (Node vertex: childVertices) {
                            verticesOutsideClade[curNodeNumber].remove(vertex);
                            verticesInsideClade[curNodeNumber].add(vertex);
                        }
                    }
                }
            }
        }
        return verticesOutsideClade;
    }

    /* This matix M contains the distances (number of edges) between
     * each of two leaves and their lowest common ancestor LCA (MRCA).
     * For any two leaves i and j:
     * - M[i][j] = distance from i to LCA(i,j),
     * - M[j][i] = distance from j to LCA(i,j).
     */
    public static int[][] calcNodalSplittedMatrix(Tree tree, IdGroup idGroup) {

        int leafNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();
        if (idGroup == null)
            idGroup = TreeUtils.getLeafIdGroup(tree);

        int[] alias = TreeUtils.mapExternalIdentifiers(idGroup, tree);

        int[][] nodalSplittedMatrix = new int[leafNum][leafNum];
        /*for (int i=0;i<leafNum;i++)
            nodalSplittedMatrix[i][i] = 0;*/

        TCUtilsNodeEx[] nodeInfoTab = new TCUtilsNodeEx[intNum];
        Node childNode, curNode = tree.getExternalNode(0);
        int childCount, nodeIndex, childIndex;
        boolean loop = true;

        while (loop) {
            if (curNode.isRoot()) {
                loop = false;
            }

            if (!curNode.isLeaf()) {
                nodeIndex = curNode.getNumber();
                childCount = curNode.getChildCount();
                nodeInfoTab[nodeIndex] = new TCUtilsNodeEx();

                for (int i = 0; i < childCount; i++) {
                    childNode = curNode.getChild(i);
                    childIndex = childNode.getNumber();
                    if (childNode.isLeaf()) {
                        nodeInfoTab[nodeIndex].addLeaf(alias[childIndex]);
                    } else {
                        nodeInfoTab[nodeIndex].add(nodeInfoTab[childIndex]);
                    }
                }
            }
            curNode = NodeUtils.postorderSuccessor(curNode);
        }

        TCUtilsNodeEx utilsNode;
        List<List<NodeExInfo>> leafSet;
        for (int i = 0; i < intNum; i++) {
            utilsNode = nodeInfoTab[i];
            leafSet = utilsNode.getLeafSets();
            for (int j = 0; j < leafSet.size() - 1; j++) {
                List<NodeExInfo> baseList = leafSet.get(j);
                for (NodeExInfo i1 : baseList) {
                    for (int k = j + 1; k < leafSet.size(); k++) {
                        List<NodeExInfo> secondList = leafSet.get(k);
                        for (NodeExInfo i2 : secondList) {
                            nodalSplittedMatrix[i1.leafId][i2.leafId] = i1.dist;
                            nodalSplittedMatrix[i2.leafId][i1.leafId] = i2.dist;
                        }
                    }
                }
            }
        }
        return nodalSplittedMatrix;
    }

    public static ClustIntersectInfoMatrix calcClustIntersectMatrix(Tree tree1, Tree tree2, IdGroup idGroup) {

        int intT1Num = tree1.getInternalNodeCount();
        int extT1Num = tree1.getExternalNodeCount();

        int intT2Num = tree2.getInternalNodeCount();
        int extT2Num = tree2.getExternalNodeCount();
        ClustIntersectInfoMatrix resultMatrix = new ClustIntersectInfoMatrix(tree1, tree2, idGroup);
        resultMatrix.init();

        int allT1Num = intT1Num + extT1Num;
        int allT2Num = intT2Num + extT2Num;

        Node[] postOrderT1 = getNodesInPostOrder(tree1);
        Node[] postOrderT2 = getNodesInPostOrder(tree2);

        calcCladeSizes(tree1, postOrderT1, resultMatrix.cSize1);
        calcCladeSizes(tree2, postOrderT2, resultMatrix.cSize2);

        Node uNode, vNode, xNode;
        boolean uNodeLeaf, vNodeLeaf;
        int uNodeNum, vNodeNum, xNodeNum;
        int i = 0, j = 0, k = 0;
        short sum = 0, cs = 0;

        for (i = 0; i < allT1Num; i++) {
            for (j = 0; j < allT2Num; j++) {


                uNode = postOrderT1[i];
                uNodeNum = uNode.getNumber();
                uNodeLeaf = uNode.isLeaf();

                vNode = postOrderT2[j];
                vNodeNum = vNode.getNumber();
                vNodeLeaf = vNode.isLeaf();
                //u - leaf node, v - leaf node
                if (uNodeLeaf && vNodeLeaf)
                    continue;

                //u - leaf node, v - not leaf
                if (uNodeLeaf && (! vNodeLeaf)) {
                    sum = 0;
                    for (k = 0; k < vNode.getChildCount(); k++) {
                        xNode = vNode.getChild(k);
                        xNodeNum = xNode.getNumber();
                        if (xNode.isLeaf()) {
                            cs = resultMatrix.getT1Ext_T2Ext(uNodeNum, xNodeNum);
                        } else {
                            cs = resultMatrix.getT1Ext_T2Int(uNodeNum, xNodeNum);
                        }
                        sum += cs;
                    }
                    resultMatrix.setT1Ext_T2Int(uNodeNum, vNodeNum, sum);
                }

                //u - not leaf, v - any
                if (!uNodeLeaf ) {

                    sum = 0;
                    for (k = 0; k < uNode.getChildCount(); k++) {
                        xNode = uNode.getChild(k);
                        xNodeNum = xNode.getNumber();

                        if (xNode.isLeaf()) {
                            if (vNodeLeaf) {
                                cs = resultMatrix.getT1Ext_T2Ext(xNodeNum, vNodeNum);
                            } else {
                                cs = resultMatrix.getT1Ext_T2Int(xNodeNum, vNodeNum);
                            }
                        } else {
                            if (vNodeLeaf) {
                                cs = resultMatrix.getT1Int_T2Ext(xNodeNum, vNodeNum);
                            } else {
                                cs = resultMatrix.getT1Int_T2Int(xNodeNum, vNodeNum);
                            }
                        }
                        sum += cs;
                    }
                    if (vNodeLeaf)
                        resultMatrix.setT1Int_T2Ext(uNodeNum, vNodeNum, sum);
                    else
                        resultMatrix.setT1Int_T2Int(uNodeNum, vNodeNum, sum);
                }
            }
        }

        return resultMatrix;
    }

    private static Node[] getInternalNodes(Tree t) {
        int intTNum = t.getInternalNodeCount();
        Node[] internalNodes = new Node[intTNum];
        for (int i = 0; i < intTNum; i++) {
            internalNodes[intTNum - i - 1] = t.getInternalNode(i);
        }
        return internalNodes;
    }

    public static Node[] getNodesInPreOrder(Tree t){

        int intTNum = t.getInternalNodeCount();
        int extTNum = t.getExternalNodeCount();
        int allTNum = intTNum + extTNum;
        Node curNode = t.getRoot();
        Node[] preOrderT = new Node[allTNum];
        boolean loop = true;
        int i = 0;

        do {
            preOrderT[i] = curNode;
            i++;
            curNode = NodeUtils.preorderSuccessor(curNode);

            if (curNode.isRoot()) {
                loop = false;
            }
        }
        while (loop);
        return preOrderT;
    }

    public static Node[] getNodesInPostOrder(Tree t){

        int intTNum = t.getInternalNodeCount();
        int extTNum = t.getExternalNodeCount();
        int allTNum = intTNum + extTNum;
        Node curNode = t.getExternalNode(0);
        Node[] postOrderT = new Node[allTNum];
        boolean loop = true;
        int i = 0;

        while (loop) {
            if (curNode.isRoot()) {
                loop = false;
            }

            postOrderT[i] = curNode;
            i++;
            curNode = NodeUtils.postorderSuccessor(curNode);
        }
        return postOrderT;
    }

    public static Node[] getAllNodes(Tree t){

        int intTNum = t.getInternalNodeCount();
        int extTNum = t.getExternalNodeCount();
        int allTNum = intTNum + extTNum;

        int i=0;
        Node[] nodes = new Node[allTNum];

        for (int j = 0; j < extTNum; j++){
            nodes[i] = t.getExternalNode(j);
            i++;
        }

        for (int j = 0; j < intTNum; j++){
            nodes[i] = t.getInternalNode(j);
            i++;
        }

        return nodes;
    }

    //Returns array of sizes of clusters related to internal nodes.
    //The index in the array correspond to the number of an internal node in tree t
    //input parameters:
    // - Tree t,
    // - Node[] postOrderNodes,
    //output:
    // - short[] cladeSizeTab

    public static void calcCladeSizes(Tree t, Node[] postOrderNodes, short[] cladeSizeTab ){

        int intTNum = t.getInternalNodeCount();
        int extTNum = t.getExternalNodeCount();
        int allTNum = intTNum + extTNum;

        int childNum = 0, childId = 0;
        int intId = 0;
        Node curNode = null, child = null;
        boolean curNodeLeaf;
        short cSize = 0;
        for (int i = 0; i<allTNum; i++){
            curNode = postOrderNodes[i];
            intId = curNode.getNumber();
            curNodeLeaf = curNode.isLeaf();
            if (curNodeLeaf)
                continue;

            childNum = curNode.getChildCount();
            cSize = 0;
            for (int j = 0; j<childNum; j++){
                child = curNode.getChild(j);
                if (child.isLeaf()){
                    cSize++;
                } else {
                    childId = child.getNumber();
                    cSize += cladeSizeTab[childId];
                }
            }
            cladeSizeTab[intId] = cSize;
        }
    }

    //intput parametes:
    // - Tree t
    // - Node[] postOrderNodes,
    // - short[] cladeSizeTab
    //output = retrun
    //Algorithm according to  section 7.2 "Computing |R(T1)|, |U(T1)| and |U(T2)|"
    //form Mukul S. Bansal,  Jianrong Dong, David Fernández-Baca
    //"Comparing and Aggregating Partially Resolved Trees"
    public static long calcResolvedTriplets(Tree t, Node[] postOrderNodes, short[] cladeSizeTab){
        int intTNum = t.getInternalNodeCount();
        int extTNum = t.getExternalNodeCount();
        int allTNum = intTNum + extTNum;

        int childNum = 0, childId = 0;
        int intId = 0;
        long R, alfa_v, alfa_x, beta_v, gamma_v, fi_v, n, child_a_sum;
        n = extTNum;

        Node curNode = null, child = null;
        boolean curNodeLeaf, curNodeRoot;
        R = 0;
        for (int i = 0; i<allTNum; i++){
            curNode = postOrderNodes[i];
            intId = curNode.getNumber();
            curNodeLeaf = curNode.isLeaf();
            curNodeRoot = curNode.isRoot();
            if (curNodeLeaf || curNodeRoot)
                continue;

            alfa_v = cladeSizeTab[intId];
            beta_v = n - alfa_v;
            gamma_v = ((alfa_v * (alfa_v - 1)) >> 1)* beta_v ;

            childNum = curNode.getChildCount();
            child_a_sum = 0;
            for (int j = 0; j<childNum; j++){
                child = curNode.getChild(j);
                if (child.isLeaf())
                    continue;

                childId = child.getNumber();
                alfa_x = cladeSizeTab[childId];
                child_a_sum += ((alfa_x * (alfa_x - 1)) >> 1);

            }
            fi_v = gamma_v - child_a_sum * beta_v;
            R +=  fi_v;
        }

        return R;
    }

    public static long calcResolvedAndEqualTriplets(ClustIntersectInfoMatrix cIM, Node[] postOrderT1, Node[] postOrderT2){

        int intT1Num = cIM.getT1().getInternalNodeCount();
        int extT1Num = cIM.getT1().getExternalNodeCount();

        int intT2Num = cIM.getT2().getInternalNodeCount();
        int extT2Num = cIM.getT2().getExternalNodeCount();

        int allT1Num = intT1Num + extT1Num;
        int allT2Num = intT2Num + extT2Num;

        long n = extT1Num;

        Node uNode, vNode, xNode, yNode;
        boolean uNodeLeaf, vNodeLeaf, uNodeRoot, vNodeRoot;
        int uNodeNum, vNodeNum, xNodeNum, yNodeNum, childNodeCount, childNodeCount2;
        int i, j, k, l ;
        long S =0, s, n1, n2, n3 ,n4;
        long uvSize, uvSizeNeg;
        long xvSize, uxSize, xySize;
        long child_u_sum, child_v_sum, child_y_sum, child_xy_sum;

        for (i = 0; i < allT1Num; i++) {
            uNode = postOrderT1[i];
            uNodeNum = uNode.getNumber();
            uNodeLeaf = uNode.isLeaf();
            uNodeRoot = uNode.isRoot();
            if (uNodeLeaf || uNodeRoot)
                continue;

            for (j = 0; j < allT2Num; j++) {
                vNode = postOrderT2[j];
                vNodeNum = vNode.getNumber();
                vNodeLeaf = vNode.isLeaf();
                vNodeRoot = vNode.isRoot();
                if (vNodeLeaf || vNodeRoot)
                    continue;
                //n1 ----
                uvSize = cIM.getT1Int_T2Int(uNodeNum, vNodeNum);
                uvSizeNeg = n - (cIM.cSize1[uNodeNum] + cIM.cSize2[vNodeNum] - uvSize);

                n1 = choose2(uvSize) * uvSizeNeg;
                //n1 ----

                //n2 ----
                childNodeCount = uNode.getChildCount();
                child_u_sum = 0;
                for (k = 0; k < childNodeCount; k++){
                    xNode = uNode.getChild(k);
                    if (xNode.isLeaf())
                        continue;

                    xNodeNum = xNode.getNumber();
                    xvSize = cIM.getT1Int_T2Int(xNodeNum,vNodeNum);
                    child_u_sum += choose2(xvSize);
                }
                n2 = child_u_sum * uvSizeNeg;
                //n2 ----

                //n3 ----
                childNodeCount = vNode.getChildCount();
                child_v_sum = 0;
                for (k = 0; k < childNodeCount; k++){
                    xNode = vNode.getChild(k);
                    if (xNode.isLeaf())
                        continue;

                    xNodeNum = xNode.getNumber();
                    uxSize = cIM.getT1Int_T2Int(uNodeNum,xNodeNum);
                    child_v_sum += choose2(uxSize);
                }
                n3 = child_v_sum * uvSizeNeg;
                //n3 ----

                //n4 ----
                childNodeCount = uNode.getChildCount();
                child_xy_sum = 0;
                for (k = 0; k < childNodeCount; k++){
                    xNode = uNode.getChild(k);
                    if (xNode.isLeaf())
                        continue;

                    childNodeCount2 = vNode.getChildCount();
                    child_y_sum = 0;
                    xNodeNum = xNode.getNumber();
                    for (l = 0; l < childNodeCount2; l++){
                        yNode = vNode.getChild(l);
                        if (yNode.isLeaf())
                            continue;
                        yNodeNum = yNode.getNumber();
                        xySize = cIM.getT1Int_T2Int(xNodeNum,yNodeNum);
                        child_y_sum += choose2(xySize);
                    }
                    child_xy_sum += child_y_sum;
                }
                n4 = child_xy_sum * uvSizeNeg;
                //n4 ----

                s = n1 - n2 - n3 + n4;
                S += s;
            }
        }
        return S;
    }


    public static long calcResolvedOnlyInT1(ClustIntersectInfoMatrix cIM, Node[] postOrderT1, Node[] postOrderT2){

        int intT1Num = cIM.getT1().getInternalNodeCount();
        int extT1Num = cIM.getT1().getExternalNodeCount();

        int intT2Num = cIM.getT2().getInternalNodeCount();
        int extT2Num = cIM.getT2().getExternalNodeCount();

        int allT1Num = intT1Num + extT1Num;
        int allT2Num = intT2Num + extT2Num;

        long n = extT1Num;

        Node uNode, vNode;
        boolean uNodeLeaf, uNodeRoot;
        int i, j;
        long R =0, r;

        Map<Triple,Long> gammaMap = new HashMap<Triple,Long>();

        for (i = 0; i < allT1Num; i++) {
            uNode = postOrderT1[i];
            uNodeLeaf = uNode.isLeaf();
            uNodeRoot = uNode.isRoot();
            if (uNodeLeaf || uNodeRoot)
                continue;

            for (j = 0; j < allT2Num; j++) {
                vNode = postOrderT2[j];

                if (vNode.getChildCount() <= 2)
                    continue;
                r = r1(uNode,vNode, gammaMap,cIM);

                R += r;
            }
        }
        return R;
    }

    private static long r1(Node u, Node v, Map<Triple,Long> gammaMap, ClustIntersectInfoMatrix cIM){
        Node pa_u = u.getParent();

        int vNum = v.getNumber();
        int uNum = u.getNumber();
        int pa_uNum = pa_u.getNumber();
        int xNum,childNum = u.getChildCount();
        Triple t = new Triple();
        Node x;
        long sum = 0, gm;
        Long g;

        for (int i = 0; i < childNum; i++){
            x = u.getChild(i);
            xNum = x.getNumber();
            if (!x.isLeaf()){
                t.n1 = pa_uNum;
                t.n2 = xNum;
                t.n3 = vNum;
                g = gammaMap.get(t);
                if (g == null){
                    gm = gamma(pa_u,x,v,cIM);
                    gammaMap.put(t, gm);
                    g = gm;
                }

                sum += g;
            }
        }
        t.n1 = pa_uNum;
        t.n2 = uNum;
        t.n3 = vNum;
        g = gammaMap.get(t);
        if (g == null){
            gm = gamma(pa_u,u,v,cIM);
            gammaMap.put(t, gm);
            g = gm;
        }
        long r1 = g - sum;

        return r1;
    }


    private static long gamma(Node u1, Node uk, Node v, ClustIntersectInfoMatrix cIM){

        Node u2;
        long sum, u2Negx;
        if (uk.getParent().getNumber() == u1.getNumber())
            u2 = uk;
        else
            u2 = uk.getParent();
        //n1
        long u2Negv = cIM.getSizeT2(v) - cIM.getInterSize(u2, v);
        long n1 = choose2(cIM.getInterSize(uk, v))* u2Negv;

        int childNum = v.getChildCount();
        Node x;

        sum = 0;
        for (int i = 0; i<childNum; i++){
            x = v.getChild(i);
            u2Negx = cIM.getSizeT2(x) - cIM.getInterSize(u2, x);
            sum += choose2(cIM.getInterSize(uk, x))* u2Negx;
        }
        long n2 = sum;

        sum =0;
        for (int i = 0; i<childNum; i++){
            x = v.getChild(i);
            u2Negv = cIM.getSizeT2(v) - cIM.getInterSize(u2, v);
            u2Negx = cIM.getSizeT2(x) - cIM.getInterSize(u2, x);

            sum += choose2(cIM.getInterSize(uk, x))* (u2Negv - u2Negx);
        }
        long n3 = sum;

        sum =0;
        for (int i = 0; i<childNum; i++){
            x = v.getChild(i);
            u2Negx = cIM.getSizeT2(x) - cIM.getInterSize(u2, x);
            sum += cIM.getInterSize(uk, x)*u2Negx*(cIM.getInterSize(uk, v) - cIM.getInterSize(uk, x));
        }
        long n4 = sum;

        long r1 = n1 - n2 - n3 - n4;

        return r1;

    }


    public static long choose2(long n){

        if (n < 2)
            return 0;
        else if (n == 2)
            return 1;
        else
            return ((n * (n - 1)) >> 1);
    }

    public static boolean isBinary(Tree tree, boolean isRooted){
        int intNum = tree.getInternalNodeCount();
        int childNum = 0;
        Node node;
        for (int i=0; i<intNum; i++){
            node = tree.getInternalNode(i);
            childNum = node.getChildCount();
            if (node.isRoot()){
                if (isRooted){
                    if (childNum != 2)
                        return false;
                }else {
                    if (childNum != 3)
                        return false;
                }
            }else {
                if (childNum != 2)
                    return false;
            }
        }
        return true;
    }

    public static Node[] getNeighboringNodes(Node node) {
        final int numberOfChildren = node.getChildCount();
        final int numberOfNeighboringNodes = node.isRoot()
                ? numberOfChildren
                : numberOfChildren + 1;
        final Node[] neighbors = new Node[numberOfNeighboringNodes];
        for (int i=0; i<numberOfChildren; i++) {
            neighbors[i] = node.getChild(i);
        }
        if (!node.isRoot()) {
            neighbors[numberOfChildren] = node.getParent();
        }
        return neighbors;
    }

    public static int getNodeDepth(Node node){
        int depth=0;

        if (node.isRoot())
            return 0;

        while(!node.isRoot()){
            depth++;
            node=node.getParent();
        }

        return depth;
    }

    public static Tree unrootTreeIfNeeded(Tree t) {
        if(t.getExternalNodeCount() == 2) {
            return t;
        }
        Tree ut = null;
        if (t != null) {
            Node r = t.getRoot();
            if (r.getChildCount() == 2) {
                ut = TreeTool.getUnrooted(t);
            } else {
                ut = t;
            }
        }
        return ut;
    }

    public static IdGroup mergeIdGroups (IdGroup g1, IdGroup g2){
        int g1Num = g1.getIdCount();
        int g2Num = g2.getIdCount();

        Set<String> nameSet = new HashSet<String>(((g1Num+g2Num)*4)/3);
        for (int i =0; i<g1Num;i++){
            String name = g1.getIdentifier(i).getName();
            if (!nameSet.contains(name))
                nameSet.add(name);

        }

        for (int i =0; i<g2Num;i++){
            String name = g2.getIdentifier(i).getName();
            if (!nameSet.contains(name))
                nameSet.add(name);
        }

        Identifier[] idTab= new Identifier[nameSet.size()];
        int i=0;
        for(String name: nameSet){
            idTab[i] = new Identifier(name);
            i++;
        }
        IdGroup g = new SimpleIdGroup(idTab);
        return g;
    }

    /**
     * Note that in order to use the method
     * both the input trees must be rooted binary trees.
     * @param tree1
     * @param tree2
     * @param idGroup
     * @return
     */
    public static IntersectInfoMatrix calcMastIntersectMatrix(Tree tree1, Tree tree2, IdGroup idGroup) {
        int intT1Num = tree1.getInternalNodeCount();
        int extT1Num = tree1.getExternalNodeCount();

        int intT2Num = tree2.getInternalNodeCount();
        int extT2Num = tree2.getExternalNodeCount();
        IntersectInfoMatrix resultMatrix = new IntersectInfoMatrix(tree1, tree2, idGroup);
        resultMatrix.init();

        int allT1Num = intT1Num + extT1Num;
        int allT2Num = intT2Num + extT2Num;

        Node[] postOrderT1 = getNodesInPostOrder(tree1);
        Node[] postOrderT2 = getNodesInPostOrder(tree2);

        Node aNode, wNode, xNode, yNode, bNode, cNode;
        boolean aNodeLeaf, wNodeLeaf;
        int aNodeNum, wNodeNum;

        for (int i = 0; i < allT1Num; i++) {
            for (int j = 0; j < allT2Num; j++) {
                aNode = postOrderT1[i];
                aNodeNum = aNode.getNumber();
                aNodeLeaf = aNode.isLeaf();

                wNode = postOrderT2[j];
                wNodeNum = wNode.getNumber();
                wNodeLeaf = wNode.isLeaf();

                if (aNodeLeaf && wNodeLeaf) { //a - leaf node, w - leaf node
                    continue;
                } else  if (aNodeLeaf && (!wNodeLeaf)) { //a - leaf node, w - not leaf
                    xNode = wNode.getChild(0);
                    yNode = wNode.getChild(1);
                    short xMastSize = resultMatrix.getSize(aNode, xNode);
                    short yMastSize = resultMatrix.getSize(aNode, yNode);
                    short mastSize = xMastSize;
                    if (yMastSize > xMastSize) {
                        mastSize = yMastSize;
                    }
                    resultMatrix.setT1Ext_T2Int(aNodeNum, wNodeNum, mastSize);
                } else if (!aNodeLeaf && wNodeLeaf) { //a - not leaf, w - leaf
                    bNode = aNode.getChild(0);
                    cNode = aNode.getChild(1);
                    short bMastSize = resultMatrix.getSize(bNode, wNode);
                    short cMastSize = resultMatrix.getSize(cNode, wNode);
                    short mastSize = bMastSize;
                    if (cMastSize > bMastSize) {
                        mastSize = cMastSize;
                    }
                    resultMatrix.setT1Int_T2Ext(aNodeNum, wNodeNum, mastSize);
                } else { //a - not leaf, w - not leaf
                    short mastTab[] = new short[6];
                    bNode = aNode.getChild(0);
                    cNode = aNode.getChild(1);
                    xNode = wNode.getChild(0);
                    yNode = wNode.getChild(1);
                    mastTab[0] = (short) (resultMatrix.getSize(bNode, xNode) + resultMatrix.getSize(cNode, yNode));
                    mastTab[1] = (short) (resultMatrix.getSize(bNode, yNode) + resultMatrix.getSize(cNode, xNode));
                    mastTab[2] = resultMatrix.getSize(aNode, xNode);
                    mastTab[3] = resultMatrix.getSize(aNode, yNode);
                    mastTab[4] = resultMatrix.getSize(bNode, wNode);
                    mastTab[5] = resultMatrix.getSize(cNode, wNode);
                    short mastSize = max(mastTab);
                    resultMatrix.setT1Int_T2Int(aNodeNum, wNodeNum, mastSize);
                }
            }
        }
        return resultMatrix;
    }

    /**
     *
     * @param t
     * @param preOrderNodes
     * @param externalNodesDepthTab
     * @param internalNodesDepthTab
     * @param idGroup
     */
    public static void calcNodeDepth(Tree t, Node[] preOrderNodes, short[] externalNodesDepthTab, short[] internalNodesDepthTab, IdGroup idGroup) {
        int parentNum, curNodeNum;
        Node curNode;
        short depth;
        if (idGroup == null) {
            idGroup = TreeUtils.getLeafIdGroup(t);
        }

        int[] alias = TreeUtils.mapExternalIdentifiers(idGroup, t);
        for (int i = 0; i < preOrderNodes.length; i++) {
            curNode = preOrderNodes[i];
            curNodeNum = curNode.getNumber();
            if (curNode.isRoot()) {
                internalNodesDepthTab[curNodeNum] = 0;
            } else {
                parentNum = curNode.getParent().getNumber();
                depth = (short) (internalNodesDepthTab[parentNum] + 1);
                if (curNode.isLeaf()) {
                    externalNodesDepthTab[alias[curNodeNum]] = depth;
                } else {
                    internalNodesDepthTab[curNodeNum] = depth;
                }
            }
        }
    }

    /**
     *
     * @param t
     * @param preOrderNodes
     * @param externalNodesDepthTab
     * @param internalNodesDepthTab
     * @param idGroup
     */
    public static void calcNodeDepth(Tree t, Node[] preOrderNodes, double[] externalNodesDepthTab, double[] internalNodesDepthTab, IdGroup idGroup) {
        int parentNum, curNodeNum;
        Node curNode;
        double depth;
        if (idGroup == null) {
            idGroup = TreeUtils.getLeafIdGroup(t);
        }

        int[] alias = TreeUtils.mapExternalIdentifiers(idGroup, t);
        for (int i = 0; i < preOrderNodes.length; i++) {
            curNode = preOrderNodes[i];
            curNodeNum = curNode.getNumber();
            if (curNode.isRoot()) {
                internalNodesDepthTab[curNodeNum] = 0;
            } else {
                parentNum = curNode.getParent().getNumber();
                depth = internalNodesDepthTab[parentNum] + curNode.getBranchLength();
                if (curNode.isLeaf()) {
                    externalNodesDepthTab[alias[curNodeNum]] = depth;
                } else {
                    internalNodesDepthTab[curNodeNum] = depth;
                }
            }
        }
    }

    public static short max (short [] tab){
        short currMax = tab[0];
        for (int i = 1; i < tab.length; i++){
            if (tab[i] > currMax){
                currMax = tab[i];
            }
        }
        return currMax;
    }

    static int countSackinIndex(Node node, int depth) {
        if(node.isLeaf()) {
            return depth;
        }
        else{
            int sackinIndex = 0;
            final int numberOfChildren = node.getChildCount();
            for (int i=0; i<numberOfChildren; i++) {
                sackinIndex += countSackinIndex(node.getChild(i),depth+1);
            }
            return sackinIndex;
        }
    }

    public static double getSackinIndex(Tree tree) {
        Node node = tree.getRoot();
        int depth = 0;
        return (double) countSackinIndex(node, depth);
    }

    public static double getSackinUnrootedIndex(Tree tree) {
        Tree unrootedTree = unrootTreeIfNeeded(tree);
        final SimpleTree t = new SimpleTree(unrootedTree);
        int treeInternalNodeCount = t.getInternalNodeCount();
        double minSackinIndex = Double.MAX_VALUE;
        for (int i=0; i<treeInternalNodeCount; i++) {
            t.reroot(t.getInternalNode(i));
            double tmpSackinIndex = getSackinIndex(t);
            if (minSackinIndex > tmpSackinIndex) {
                minSackinIndex = tmpSackinIndex;
            }
        }
        return minSackinIndex;
    }

}




class TCUtilsNode{
    private List< List<Integer> > leafSets;

    public List<List<Integer>> getLeafSets() {
        return leafSets;
    }

    public void setLeafSets(List<List<Integer>> leafSets) {
        this.leafSets = leafSets;
    }

    public TCUtilsNode(List<List<Integer>> leafSets) {
        this.leafSets = leafSets;
    }

    public TCUtilsNode() {
        this.leafSets = new ArrayList<List<Integer>>();
    }

    void add(TCUtilsNode node){
        List<Integer> tempList = new LinkedList<Integer>();

        for(List<Integer> li: node.getLeafSets()){
            for (Integer i:li ){
                tempList.add( new Integer(i));
            }
        }
        this.leafSets.add(tempList);
    }

    void addLeaf(Integer leaf){
        List<Integer> tempList = new LinkedList<Integer>();

        tempList.add(new Integer(leaf));
        this.leafSets.add(tempList);
    }
}

class TCUtilsNodeEx{
    private List< List<NodeExInfo> > leafSets;

    public List<List<NodeExInfo>> getLeafSets() {
        return leafSets;
    }

    public void setLeafSets(List<List<NodeExInfo>> leafSets) {
        this.leafSets = leafSets;
    }

    public TCUtilsNodeEx(List<List<NodeExInfo>> leafSets) {
        this.leafSets = leafSets;
    }

    public TCUtilsNodeEx() {
        this.leafSets = new ArrayList<List<NodeExInfo>>();
    }

    void add(TCUtilsNodeEx node){
        List<NodeExInfo> tempList = new LinkedList<NodeExInfo>();

        for(List<NodeExInfo> li: node.getLeafSets()){
            for (NodeExInfo i:li ){
                NodeExInfo newNode = new NodeExInfo();
                newNode.dist = i.dist + 1;
                newNode.leafId = i.leafId;
                tempList.add(newNode);
            }
        }
        this.leafSets.add(tempList);
    }

    void addLeaf(int leaf){
        List<NodeExInfo> tempList = new LinkedList<NodeExInfo>();

        NodeExInfo nodeExInfo = new NodeExInfo();
        nodeExInfo.leafId = leaf;
        nodeExInfo.dist = 1;

        tempList.add(nodeExInfo);
        this.leafSets.add(tempList);
    }

}

class NodeExInfo {

    public int dist;
    public int leafId;

    public NodeExInfo() {
        dist = 0;
        leafId = -1;
    }
}

class Triple {
    public int n1;
    public int n2;
    public int n3;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (obj.getClass() != this.getClass())) {
            return false;
        }
        Triple ref = (Triple)obj;
        if (n1 == ref.n1 && n2 == ref.n2 && n3 == ref.n3)
            return true;
        else
            return false;

    }

    @Override
    public int hashCode() {
        int prime = 31;
        int hash = (n1 + prime * n2)^n3;
        return hash;
    }


}
