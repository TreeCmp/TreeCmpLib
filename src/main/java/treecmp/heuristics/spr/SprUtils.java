package treecmp.heuristics.spr;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import pal.misc.IdGroup;
import pal.tree.*;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.moves.SprMove;

public class SprUtils extends TreeNeighborhoodUtils {

    public Tree applyPhysicalSprMove(Tree tree, SprMove move) {
        if (tree == null || move == null) return tree;

        Node s = move.sourceNode;
        Node v = move.targetNode;
        if (s == null || v == null) return tree;

        Node p = s.getParent();
        if (p == null || p == v.getParent()) return tree;

        Node pp = p.getParent();
        Node sibling = getSibling(s);
        Node q = v.getParent();

        if (q == null || pp == null) {
            return createAndFixSprTree(tree, s, v);
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
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }

        return tree;
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

    public Tree createAndFixSprTree(Tree baseTree, Node pruneNode, Node targetNode) {
        if (baseTree == null || pruneNode == null || targetNode == null) return null;
        return createTbrTree(baseTree, pruneNode, pruneNode, targetNode);
    }

    public void forEachSprTree(Tree tree, Consumer<Tree> action) {
        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();

        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        Set<CanonicalTopologyKey> seenTopologies = new HashSet<>();
        seenTopologies.add(buildCanonicalKey(tree, idGroup, extNum));

        Node s, t;

        for (int i = 0; i < extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j = 0; j < extNum; j++) {
                t = tree.getExternalNode(j);
                processAndYield(tree, s, t, idGroup, extNum, seenTopologies, action);
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < extNum; j++) {
                t = tree.getExternalNode(j);
                processAndYield(tree, s, t, idGroup, extNum, seenTopologies, action);
            }
        }
        for (int i = 0; i < extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j = 0; j < intNum; j++) {
                t = tree.getInternalNode(j);
                processAndYield(tree, s, t, idGroup, extNum, seenTopologies, action);
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < intNum; j++) {
                t = tree.getInternalNode(j);
                processAndYield(tree, s, t, idGroup, extNum, seenTopologies, action);
            }
        }
    }

    private void processAndYield(Tree baseTree, Node s, Node t, IdGroup idGroup, int numLeaves, Set<CanonicalTopologyKey> seen, Consumer<Tree> action) {
        if (isValidSprMove(s, t)) {
            Tree resultTree = createAndFixSprTree(baseTree, s, t);
            if (resultTree != null) {
                CanonicalTopologyKey key = buildCanonicalKey(resultTree, idGroup, numLeaves);
                if (seen.add(key)) {
                    SprMove move = new SprMove(s, t);
                    registerTreeCost(resultTree, move.getNniEquivalentCost());
                    registerTreeMove(resultTree, move);

                    action.accept(resultTree);
                }
            }
        }
    }

    @Override
    public void forEachNeighbour(Tree tree, java.util.function.Consumer<Tree> action) {
        clearCosts();
        forEachSprTree(tree, action);
    }
}