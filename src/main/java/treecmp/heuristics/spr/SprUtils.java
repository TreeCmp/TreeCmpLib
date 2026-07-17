package treecmp.heuristics.spr;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import pal.misc.IdGroup;
import pal.tree.*;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.moves.SprMove;

public class SprUtils extends TreeNeighborhoodUtils {

    public Tree applyPhysicalSprMove(Tree tree, SprMove move) {
        Node s = move.movingNode;
        Node v = move.targetNode;
        Node p = s.getParent();

        if (p == null || v == null || p == v.getParent()) return tree;

        Node pp = p.getParent();
        Node sibling = getSibling(s);
        Node q = v.getParent();

        if (q == null || pp == null) {
            return createSprTree(tree, s, v);
        }

        int pIdx = findChildPos(p, pp);
        if (pIdx != -1) {
            pp.setChild(pIdx, sibling);
            if (sibling != null) sibling.setParent(pp);
        }

        int vIdx = findChildPos(v, q);
        if (vIdx != -1) {
            q.setChild(vIdx, p);
            p.setParent(q);
        }

        p.setChild(0, s);
        if (s != null) s.setParent(p);

        p.setChild(1, v);
        if (v != null) v.setParent(p);

        pal.tree.TreeUtils.computeParentPointers(tree.getRoot());

        return refreshTreeInPlace(tree);
    }

    public Node getSibling(Node node) {
        Node p = node.getParent();
        if (p == null) return null;
        return (p.getChild(0) == node) ? p.getChild(1) : p.getChild(0);
    }

    public int findChildPos(Node child, Node parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) == child) return i;
        }
        return -1;
    }

    @Override
    public Tree[] generateNeighbours(Tree tree) {
        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        int neighSize = calcSprNeighbours(tree);
        Set<treecmp.heuristics.TreeHolder> sprTreeSet = new HashSet<>((4 * neighSize) / 3);

        Node s, t;
        Tree resultTree;

        for (int i = 0; i < extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j = 0; j < extNum; j++) {
                t = tree.getExternalNode(j);
                if (isValidSprMove(s, t)) {
                    resultTree = createSprTree(tree, s, t);
                    sprTreeSet.add(new treecmp.heuristics.TreeRootedHolder(resultTree, idGroup));
                }
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < extNum; j++) {
                t = tree.getExternalNode(j);
                if (isValidSprMove(s, t)) {
                    resultTree = createSprTree(tree, s, t);
                    sprTreeSet.add(new treecmp.heuristics.TreeRootedHolder(resultTree, idGroup));
                }
            }
        }
        for (int i = 0; i < extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j = 0; j < intNum; j++) {
                t = tree.getInternalNode(j);
                if (isValidSprMove(s, t)) {
                    resultTree = createSprTree(tree, s, t);
                    sprTreeSet.add(new treecmp.heuristics.TreeRootedHolder(resultTree, idGroup));
                }
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < intNum; j++) {
                t = tree.getInternalNode(j);
                if (isValidSprMove(s, t)) {
                    resultTree = createSprTree(tree, s, t);
                    if (resultTree != null) {
                        sprTreeSet.add(new treecmp.heuristics.TreeRootedHolder(resultTree, idGroup));
                    }
                }
            }
        }

        int n = sprTreeSet.size();
        Tree[] sprTreeArray = new Tree[n];
        int i = 0;
        for (treecmp.heuristics.TreeHolder th : sprTreeSet) {
            sprTreeArray[i] = th.tree;
            i++;
        }
        return sprTreeArray;
    }

    // ========================================================================
    // NOWE METODY: Leniwy Generator Otoczenia i Kanoniczna Sygnatura
    // ========================================================================

    public void forEachSprTree(Tree tree, Consumer<Tree> action) {
        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();

        Set<String> seenTopologies = new HashSet<>();

        Node s, t;

        for (int i = 0; i < extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j = 0; j < extNum; j++) {
                t = tree.getExternalNode(j);
                processAndYield(tree, s, t, seenTopologies, action);
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < extNum; j++) {
                t = tree.getExternalNode(j);
                processAndYield(tree, s, t, seenTopologies, action);
            }
        }
        for (int i = 0; i < extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j = 0; j < intNum; j++) {
                t = tree.getInternalNode(j);
                processAndYield(tree, s, t, seenTopologies, action);
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < intNum; j++) {
                t = tree.getInternalNode(j);
                processAndYield(tree, s, t, seenTopologies, action);
            }
        }
    }

    private void processAndYield(Tree baseTree, Node s, Node t, Set<String> seen, Consumer<Tree> action) {
        if (isValidSprMove(s, t)) {
            Tree resultTree = createSprTree(baseTree, s, t);
            if (resultTree != null) {
                // Generujemy unikalny, matematycznie poprawny podpis drzewa (odporny na izomorfizmy)
                String topologyHash = getCanonicalTopology(resultTree.getRoot());

                if (seen.add(topologyHash)) {
                    action.accept(resultTree);
                }
            }
        }
    }

    /**
     * Rekurencyjnie buduje sygnaturę tekstową drzewa.
     * Sortuje alfabetycznie węzły podrzędne, gwarantując, że (A,B) i (B,A)
     * zwrócą dokładnie taki sam tekst. Całkowicie omija problemy z pamięcią.
     */
    private String getCanonicalTopology(Node node) {
        if (node.isLeaf()) {
            return node.getIdentifier().getName();
        }
        List<String> childStrings = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            childStrings.add(getCanonicalTopology(node.getChild(i)));
        }

        // Sortowanie rozwiązuje problem izomorfizmu!
        Collections.sort(childStrings);

        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < childStrings.size(); i++) {
            sb.append(childStrings.get(i));
            if (i < childStrings.size() - 1) sb.append(",");
        }
        sb.append(")");
        return sb.toString();
    }
}