/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package treecmp.spr;

import java.util.List;
import pal.io.OutputTarget;
import pal.misc.Identifier;
import pal.tree.Node;
import pal.tree.NodeUtils;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.ClustIntersectInfoMatrix;
import treecmp.common.ClustIntersectInfoMatrix.ClustPair;
import treecmp.common.NodeUtilsExt;
import treecmp.common.TreeCmpUtils;

/**
 *
 * @author Damian
 */
public class SubtreeUtils {

    public static Tree[] reduceCommonBinarySubtrees(Tree t1, Tree t2) {

        Tree tree1 = t1.getCopy();
        Tree tree2 = t2.getCopy();

        ClustIntersectInfoMatrix cIM = TreeCmpUtils.calcClustIntersectMatrix(tree1, tree2, TreeUtils.getLeafIdGroup(tree1));
        // int t1Tot2Num [] = new int[cIM.eqClustList.size()];
        int t1IntNum = tree1.getInternalNodeCount();

        Node[] postOrderT1 = TreeCmpUtils.getNodesInPostOrder(tree1);
        Node temp;
        Node child1, child2;
        boolean commonSubtree[] = new boolean[t1IntNum];
        int child1Id, child2Id, tempId, tempSize;
        boolean markAsSubtree, tempEq;
        for (int i = 0; i < postOrderT1.length; i++) {
            temp = postOrderT1[i];
            if (temp.isLeaf()) {
                continue;
            }

            tempId = temp.getNumber();
            tempSize = cIM.cSize1[tempId];
            tempEq = cIM.eqClustT1[tempId];
            if (tempEq && (tempSize == 2)) {
                commonSubtree[tempId] = true;
            } else if (tempEq) {
                markAsSubtree = true;
                child1 = temp.getChild(0);
                child2 = temp.getChild(1);
                child1Id = child1.getNumber();
                child2Id = child2.getNumber();
                if (!child1.isLeaf()) {
                    if (!commonSubtree[child1Id]) {
                        markAsSubtree = false;
                    }
                }
                if (!child2.isLeaf()) {
                    if (!commonSubtree[child2Id]) {
                        markAsSubtree = false;
                    }
                }

                if (markAsSubtree) {
                    if (!child1.isLeaf()) {
                        commonSubtree[child1Id] = false;
                    }

                    if (!child2.isLeaf()) {
                        commonSubtree[child2Id] = false;
                    }

                    commonSubtree[tempId] = true;
                }
            }
        }

        Node nodeT1, nodeT2, rootT1, rootT2;
        rootT1 = tree1.getRoot();
        rootT2 = tree2.getRoot();

        int num = 0;
        String contractNodePrefix = "contrN";
        String contractNodeName;
        Identifier id;
        for (ClustPair cp : cIM.eqClustList) {

            nodeT1 = tree1.getInternalNode(cp.t1IntId);
            nodeT2 = tree2.getInternalNode(cp.t2IntId);
            if (commonSubtree[cp.t1IntId]) {
                contractNodeName = contractNodePrefix + num;
                id = new Identifier(contractNodeName);

                //OutputTarget out = OutputTarget.openString();
                //TreeUtils.printNH(t,out,false,false);
                //NodeUtils.printNH(out, nodeT1, false, false);
                //out.close();
                //System.out.println(out.getString());

                nodeT1.removeChild(0);
                nodeT1.removeChild(0);
                nodeT1.setIdentifier(id);

                nodeT2.removeChild(0);
                nodeT2.removeChild(0);
                nodeT2.setIdentifier(id);
                num++;
            }
        }

        Tree ret1, ret2;

        if (num > 0) {
            tree1.createNodeList();
            tree2.createNodeList();

            ret1 = tree1;
            ret2 = tree2;
        } else {
            ret1 = t1;
            ret2 = t2;
        }
        return new Tree[]{ret1, ret2};

    }

    public static Tree[] reduceCommonBinarySubtreesEx(Tree t1, Tree t2, List<SubtreeDef> subtrees) {

        Tree tree1 = t1.getCopy();
        Tree tree2 = t2.getCopy();

        ClustIntersectInfoMatrix cIM = TreeCmpUtils.calcClustIntersectMatrix(tree1, tree2, TreeUtils.getLeafIdGroup(tree1));
        // int t1Tot2Num [] = new int[cIM.eqClustList.size()];
        int t1IntNum = tree1.getInternalNodeCount();

        Node[] postOrderT1 = TreeCmpUtils.getNodesInPostOrder(tree1);
        Node temp;
        Node child1, child2;
        boolean commonSubtree[] = new boolean[t1IntNum];
        int child1Id, child2Id, tempId, tempSize;
        boolean markAsSubtree, tempEq;
        for (int i = 0; i < postOrderT1.length; i++) {
            temp = postOrderT1[i];
            if (temp.isLeaf()) {
                continue;
            }

            tempId = temp.getNumber();
            tempSize = cIM.cSize1[tempId];
            tempEq = cIM.eqClustT1[tempId];
            if (tempEq && (tempSize == 2)) {
                commonSubtree[tempId] = true;
            } else if (tempEq) {
                markAsSubtree = true;
                child1 = temp.getChild(0);
                child2 = temp.getChild(1);
                child1Id = child1.getNumber();
                child2Id = child2.getNumber();
                if (!child1.isLeaf()) {
                    if (!commonSubtree[child1Id]) {
                        markAsSubtree = false;
                    }
                }
                if (!child2.isLeaf()) {
                    if (!commonSubtree[child2Id]) {
                        markAsSubtree = false;
                    }
                }

                if (markAsSubtree) {
                    if (!child1.isLeaf()) {
                        commonSubtree[child1Id] = false;
                    }

                    if (!child2.isLeaf()) {
                        commonSubtree[child2Id] = false;
                    }

                    commonSubtree[tempId] = true;
                }
            }
        }

        Node nodeT1, nodeT2, rootT1, rootT2;
        rootT1 = tree1.getRoot();
        rootT2 = tree2.getRoot();

        int num = tree1.getExternalNodeCount();
        String contractNodePrefix = "contrN";
        String contractNodeName;
        Identifier id;
        for (ClustPair cp : cIM.eqClustList) {

            nodeT1 = tree1.getInternalNode(cp.t1IntId);
            nodeT2 = tree2.getInternalNode(cp.t2IntId);
            if (commonSubtree[cp.t1IntId]) {

                contractNodeName = contractNodePrefix + num;
                if (subtrees != null) {
                    String t1Desc = NodeUtilsExt.treeToSimpleString(nodeT1, false);
                    String t2Desc = NodeUtilsExt.treeToSimpleString(nodeT2, false);
                    SubtreeDef subtreeDef = new SubtreeDef();
                    subtreeDef.setLabel(contractNodeName);
                    subtreeDef.setNewickDescT1(t1Desc);
                    subtreeDef.setNewickDescT2(t2Desc);
                    subtrees.add(subtreeDef);
                }

                id = new Identifier(contractNodeName);

                //OutputTarget out = OutputTarget.openString();
                //TreeUtils.printNH(t,out,false,false);
                //NodeUtils.printNH(out, nodeT1, false, false);
                //out.close();
                //System.out.println(out.getString());

                nodeT1.removeChild(0);
                nodeT1.removeChild(0);
                nodeT1.setIdentifier(id);

                nodeT2.removeChild(0);
                nodeT2.removeChild(0);
                nodeT2.setIdentifier(id);

                num--;
            }
        }

        Tree ret1, ret2;

        if (num > 0) {
            tree1.createNodeList();
            tree2.createNodeList();

            ret1 = tree1;
            ret2 = tree2;
        } else {
            ret1 = t1;
            ret2 = t2;
        }
        return new Tree[]{ret1, ret2};

    }

    public static class SubtreeDef {

        private String label;
        private String newickDescT1;
        private String newickDescT2;

        public String getNewickDescT1() {
            return newickDescT1;
        }

        public void setNewickDescT1(String newickDescT1) {
            this.newickDescT1 = newickDescT1;
        }

        public String getNewickDescT2() {
            return newickDescT2;
        }

        public void setNewickDescT2(String newickDescT2) {
            this.newickDescT2 = newickDescT2;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
