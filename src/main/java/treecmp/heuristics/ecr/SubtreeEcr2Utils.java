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
        // "Chain" topology templates
        int[][] chainPerms = {
                {0, 1, 2, 3}, {0, 2, 1, 3}, {0, 3, 1, 2},
                {1, 0, 2, 3}, {1, 2, 0, 3}, {1, 3, 0, 2},
                {2, 0, 1, 3}, {2, 1, 0, 3}, {2, 3, 0, 1},
                {3, 0, 1, 2}, {3, 1, 0, 2}, {3, 2, 0, 1}
        };
        for (int[] p : chainPerms) TEMPLATES.add(new TopologyTemplate2sECR(false, p));

        // "Fork" topology templates
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
    public void forEachNeighbour(Tree tree, java.util.function.Consumer<Tree> action) {
        forEachEcr2Tree(tree, action);
    }

    /**
     * Pamięciowo oszczędny generator sąsiedztwa 2-sECR.
     * Przekazuje każde nowe, unikalne drzewo bezpośrednio do konsumenta.
     */
    private void forEachEcr2Tree(Tree tree, java.util.function.Consumer<Tree> action) {
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        Set<treecmp.heuristics.TreeHolder> seenHolders = new HashSet<>();
        int intNum = tree.getInternalNodeCount();

        for (int i = 0; i < intNum; i++) {
            Node node = tree.getInternalNode(i);

            if (node != tree.getRoot()) {
                Node c = node.getParent();
                if (c != null && !c.isLeaf()) {
                    Node p = c.getParent();
                    if (p != null && !p.isLeaf()) {
                        generateCombinationsStreaming(tree, p, c, node, false, seenHolders, idGroup, action);
                    }
                }
            }

            List<Node> internalChildren = new ArrayList<>();
            for (int j = 0; j < node.getChildCount(); j++) {
                Node child = node.getChild(j);
                if (!child.isLeaf()) internalChildren.add(child);
            }

            if (internalChildren.size() >= 2) {
                for (int a = 0; a < internalChildren.size(); a++) {
                    for (int b = a + 1; b < internalChildren.size(); b++) {
                        generateCombinationsStreaming(tree, node, internalChildren.get(a), internalChildren.get(b), true, seenHolders, idGroup, action);
                    }
                }
            }
        }
    }

    private void generateCombinationsStreaming(Tree tree, Node top, Node m1, Node m2, boolean isOriginalFork,
                                               Set<treecmp.heuristics.TreeHolder> seenHolders, IdGroup idGroup,
                                               java.util.function.Consumer<Tree> action) {
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
            // Pomijamy odtworzenie struktury identycznej ze startową
            if (template.isFork == isOriginalFork && Arrays.equals(template.indices, new int[]{0, 1, 2, 3})) {
                continue;
            }

            Tree newTree = createEcrTree(tree, top, m1, m2, s, template, isOriginalFork);
            if (newTree != null) {
                treecmp.heuristics.moves.Ecr2Move move =
                        new treecmp.heuristics.moves.Ecr2Move(top, m1, m2, s, template);

                // Rejestrujemy koszt i ruch (metody z klasy bazowej TreeNeighborhoodUtils)
                registerTreeCost(newTree, move.getNniEquivalentCost());
                registerTreeMove(newTree, move);

                // Deduplikacja topologii (z uwzględnieniem faktu czy korzeń ma znaczenie)
                treecmp.heuristics.TreeHolder holder = unrooted ?
                        new TreeUnrootedHolder(newTree, idGroup) :
                        new TreeRootedHolder(newTree, idGroup);

                // Jeśli topologia pojawia się po raz pierwszy (dodanie do setu się powiodło),
                // natychmiast wypychamy ją do HeuristicBaseMetric bez budowania listy.
                if (seenHolders.add(holder)) {
                    action.accept(newTree);
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

    public static Tree createEcrTree(Tree tree, Node top, Node m1, Node m2, Node[] s, TopologyTemplate2sECR template, boolean isOriginalFork) {
        try {
            List<Integer> pathTop = new ArrayList<>(); getPathToNode(tree.getRoot(), top, pathTop);
            List<Integer> pathM1 = new ArrayList<>(); getPathToNode(tree.getRoot(), m1, pathM1);
            List<Integer> pathM2 = new ArrayList<>(); getPathToNode(tree.getRoot(), m2, pathM2);

            if ((top != tree.getRoot() && pathTop.isEmpty()) || pathM1.isEmpty() || pathM2.isEmpty()) {
                return null;
            }

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

            int portA, portB;
            if (isOriginalFork) {
                portA = getChildIndex(nTop, nM1);
                portB = getChildIndex(nTop, nM2);
            } else {
                portA = getChildIndex(nTop, nS[0]);
                portB = getChildIndex(nTop, nM1);
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

        int portA, portB;
        if (isOriginalFork) {
            portA = getChildIndex(nTop, nM1);
            portB = getChildIndex(nTop, nM2);
        } else {
            portA = getChildIndex(nTop, nS[0]);
            portB = getChildIndex(nTop, nM1);
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

        public TopologyTemplate2sECR(boolean isFork, int[] indices) {
            this.isFork = isFork;
            this.indices = indices;
        }
    }
}