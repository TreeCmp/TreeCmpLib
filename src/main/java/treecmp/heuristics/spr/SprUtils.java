package treecmp.heuristics.spr;

import java.util.HashSet;
import java.util.Set;

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

        // 1. Odcięcie (Prune)
        if (pp != null) {
            int pIdx = findChildPos(p, pp);
            pp.setChild(pIdx, sibling);
        }

        // 2. Wpięcie (Regraft)
        Node q = v.getParent();
        if (q != null) {
            int vIdx = findChildPos(v, q);
            q.setChild(vIdx, p);
            p.setChild(0, s);
            p.setChild(1, v);
        }

        // 3. Refresh Błyskawiczny (Bez Stringów i Parserów!)
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

        // Zależnie od implementacji `createSprTree` w klasie bazowej,
        // jeśli ona używała `tree.getCopy()`, upewnij się, że ją też podmienisz na `fastTreeClone`!

        // leaf to leaf
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
        // non-leaf and non-root to leaf
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
        // leaf - non-leaf
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
        // non-leaf, non-root to non-leaf
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
}