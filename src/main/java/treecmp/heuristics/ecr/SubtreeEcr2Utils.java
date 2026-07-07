package treecmp.heuristics.ecr;

import java.util.*;

import pal.misc.IdGroup;
import pal.tree.*;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.TreeUnrootedHolder;

public class SubtreeEcr2Utils extends TreeNeighborhoodUtils {

    private final boolean unrooted;
    private static final List<TopologyTemplate2sECR> TEMPLATES = new ArrayList<>();

    static {
        int[][] chainPerms = {
                {0, 1, 2, 3}, {0, 2, 1, 3}, {0, 3, 1, 2},
                {1, 0, 2, 3}, {1, 2, 0, 3}, {1, 3, 0, 2},
                {2, 0, 1, 3}, {2, 1, 0, 3}, {2, 3, 0, 1},
                {3, 0, 1, 2}, {3, 1, 0, 2}, {3, 2, 0, 1}
        };
        for (int[] p : chainPerms) TEMPLATES.add(new TopologyTemplate2sECR(false, p));

        int[][] forkPerms = {
                {0, 1, 2, 3}, {0, 2, 1, 3}, {0, 3, 1, 2}
        };
        for (int[] p : forkPerms) TEMPLATES.add(new TopologyTemplate2sECR(true, p));
    }

    public SubtreeEcr2Utils(boolean unrooted) {
        this.unrooted = unrooted;
    }

    public static List<TopologyTemplate2sECR> getTemplates() {
        return TEMPLATES;
    }

    @Override
    public Tree[] generateNeighbours(Tree tree) {
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        Set<treecmp.heuristics.TreeHolder> ecrTreeSet = new HashSet<>();
        int intNum = tree.getInternalNodeCount();

        for (int i = 0; i < intNum; i++) {
            Node node = tree.getInternalNode(i);

            // 1. Wyszukiwanie kształtu CHAIN (p -> c -> node)
            if (node != tree.getRoot()) {
                Node c = node.getParent();
                if (c != null && !c.isLeaf()) {
                    Node p = c.getParent();
                    if (p != null && !p.isLeaf()) {
                        generateCombinations(tree, p, c, node, false, ecrTreeSet, idGroup);
                    }
                }
            }

            // 2. Wyszukiwanie kształtu FORK (c1 <- node -> c2)
            List<Node> internalChildren = new ArrayList<>();
            for (int j = 0; j < node.getChildCount(); j++) {
                Node child = node.getChild(j);
                if (!child.isLeaf()) internalChildren.add(child);
            }

            if (internalChildren.size() >= 2) {
                for (int a = 0; a < internalChildren.size(); a++) {
                    for (int b = a + 1; b < internalChildren.size(); b++) {
                        generateCombinations(tree, node, internalChildren.get(a), internalChildren.get(b), true, ecrTreeSet, idGroup);
                    }
                }
            }
        }

        int n = ecrTreeSet.size();
        Tree[] ecrTreeArray = new Tree[n];
        int idx = 0;
        for (treecmp.heuristics.TreeHolder th : ecrTreeSet) {
            ecrTreeArray[idx++] = th.tree;
        }
        return ecrTreeArray;
    }

    private void generateCombinations(Tree tree, Node top, Node m1, Node m2, boolean isOriginalFork,
                                      Set<treecmp.heuristics.TreeHolder> set, IdGroup idGroup) {
        Node[] s = new Node[4];

        if (!isOriginalFork) {
            s[0] = getOtherChild(top, m1);
            s[1] = getOtherChild(m1, m2);
            s[2] = m2.getChild(0);
            s[3] = m2.getChild(1);
        } else {
            s[0] = m1.getChild(0);
            s[1] = m1.getChild(1);
            s[2] = m2.getChild(0);
            s[3] = m2.getChild(1);
        }

        for (TopologyTemplate2sECR template : TEMPLATES) {
            if (template.isFork == isOriginalFork && Arrays.equals(template.indices, new int[]{0, 1, 2, 3})) {
                continue;
            }

            Tree newTree = createEcrTree(tree, top, m1, m2, s, template, isOriginalFork);
            if (newTree != null) {
                if (unrooted) {
                    set.add(new TreeUnrootedHolder(newTree, idGroup));
                } else {
                    set.add(new TreeRootedHolder(newTree, idGroup));
                }
            }
        }
    }

    private Node getOtherChild(Node parent, Node exclude) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            Node child = parent.getChild(i);
            if (child != exclude) return child;
        }
        return null;
    }

    private static int getChildIndex(Node parent, Node child) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) == child) return i;
        }
        return -1;
    }

    private static Tree createEcrTree(Tree tree, Node top, Node m1, Node m2, Node[] s, TopologyTemplate2sECR template, boolean isOriginalFork) {
        try {
            List<Integer> pathTop = new ArrayList<>(); getPathToNode(tree.getRoot(), top, pathTop);
            List<Integer> pathM1 = new ArrayList<>(); getPathToNode(tree.getRoot(), m1, pathM1);
            List<Integer> pathM2 = new ArrayList<>(); getPathToNode(tree.getRoot(), m2, pathM2);

            List<List<Integer>> pathS = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                List<Integer> p = new ArrayList<>();
                getPathToNode(tree.getRoot(), s[i], p);
                pathS.add(p);
            }

            Tree newTree = tree.getCopy();
            Node root = newTree.getRoot();

            Node nTop = findNodeByPath(root, pathTop);
            Node nM1 = findNodeByPath(root, pathM1);
            Node nM2 = findNodeByPath(root, pathM2);
            Node[] nS = new Node[4];
            for (int i = 0; i < 4; i++) nS[i] = findNodeByPath(root, pathS.get(i));

            // System inteligentnych portów - zachowuje 3 gałąź korzenia!
            int portA, portB;
            if (isOriginalFork) {
                portA = getChildIndex(nTop, nM1);
                portB = getChildIndex(nTop, nM2);
            } else {
                portA = getChildIndex(nTop, nS[0]);
                portB = getChildIndex(nTop, nM1);
            }

            // Fallback (zabezpieczenie na wypadek niespójności)
            if (portA == -1) portA = 0;
            if (portB == -1) portB = 1;
            if (portA == portB) { portA = 0; portB = 1; }

            // Budowa z dynamicznymi portami
            if (template.isFork) {
                nTop.setChild(portA, nM1); nM1.setParent(nTop);
                nTop.setChild(portB, nM2); nM2.setParent(nTop);

                nM1.setChild(0, nS[template.indices[0]]); nS[template.indices[0]].setParent(nM1);
                nM1.setChild(1, nS[template.indices[1]]); nS[template.indices[1]].setParent(nM1);

                nM2.setChild(0, nS[template.indices[2]]); nS[template.indices[2]].setParent(nM2);
                nM2.setChild(1, nS[template.indices[3]]); nS[template.indices[3]].setParent(nM2);
            } else {
                nTop.setChild(portA, nS[template.indices[0]]); nS[template.indices[0]].setParent(nTop);
                nTop.setChild(portB, nM1); nM1.setParent(nTop);

                nM1.setChild(0, nS[template.indices[1]]); nS[template.indices[1]].setParent(nM1);
                nM1.setChild(1, nM2); nM2.setParent(nM1);

                nM2.setChild(0, nS[template.indices[2]]); nS[template.indices[2]].setParent(nM2);
                nM2.setChild(1, nS[template.indices[3]]); nS[template.indices[3]].setParent(nM2);
            }

            if (newTree instanceof SimpleTree) {
                ((SimpleTree) newTree).createNodeList();
            }

            return newTree;
        } catch (Exception e) {
            return null;
        }
    }

    public Tree applyPhysicalMove(Tree tree, treecmp.heuristics.moves.Ecr2Move move) {
        Node nTop = move.top;
        Node nM1 = move.m1;
        Node nM2 = move.m2;
        Node[] nS = move.boundarySubtrees;
        TopologyTemplate2sECR template = move.template;

        boolean isOriginalFork = (nM2.getParent() == nTop);
        int portA = -1, portB = -1;
        for (int i = 0; i < nTop.getChildCount(); i++) {
            if (nTop.getChild(i) == (isOriginalFork ? nM1 : nS[0])) portA = i;
            if (nTop.getChild(i) == (isOriginalFork ? nM2 : nM1)) portB = i;
        }
        if (portA == -1) portA = 0;
        if (portB == -1) portB = 1;
        if (portA == portB) { portA = 0; portB = 1; }

        if (template.isFork) {
            nTop.setChild(portA, nM1); nM1.setParent(nTop);
            nTop.setChild(portB, nM2); nM2.setParent(nTop);

            nM1.setChild(0, nS[template.indices[0]]); nS[template.indices[0]].setParent(nM1);
            nM1.setChild(1, nS[template.indices[1]]); nS[template.indices[1]].setParent(nM1);

            nM2.setChild(0, nS[template.indices[2]]); nS[template.indices[2]].setParent(nM2);
            nM2.setChild(1, nS[template.indices[3]]); nS[template.indices[3]].setParent(nM2);
        } else {
            nTop.setChild(portA, nS[template.indices[0]]); nS[template.indices[0]].setParent(nTop);
            nTop.setChild(portB, nM1); nM1.setParent(nTop);

            nM1.setChild(0, nS[template.indices[1]]); nS[template.indices[1]].setParent(nM1);
            nM1.setChild(1, nM2); nM2.setParent(nM1);

            nM2.setChild(0, nS[template.indices[2]]); nS[template.indices[2]].setParent(nM2);
            nM2.setChild(1, nS[template.indices[3]]); nS[template.indices[3]].setParent(nM2);
        }
        return tree;
    }

    public static class TopologyTemplate2sECR {
        public boolean isFork;
        public int[] indices;

        TopologyTemplate2sECR(boolean isFork, int[] indices) {
            this.isFork = isFork;
            this.indices = indices;
        }
    }
}