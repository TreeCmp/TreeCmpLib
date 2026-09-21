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
        if (tree == null || move == null) return tree;

        // POPRAWKA: w SprMove pole to nazywa się sourceNode
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

    public Tree createAndFixSprTree(Tree baseTree, Node pruneNode, Node targetNode) {
        Tree resultTree;
        try {
            resultTree = buildPerfectRootedSprTree(baseTree, pruneNode, targetNode);
        } catch (Exception e) {
            resultTree = createSprTree(baseTree, pruneNode, targetNode);
            if (resultTree instanceof SimpleTree) {
                ((SimpleTree) resultTree).createNodeList();
            }
            if (resultTree != null) {
                pal.tree.TreeUtils.computeParentPointers(resultTree.getRoot());
                pal.misc.IdGroup idGroup = pal.tree.TreeUtils.getLeafIdGroup(baseTree);
                pal.tree.TreeUtils.mapExternalIdentifiers(idGroup, resultTree);
            }
        }

        // OSTATECZNA TARCZA TOPOLOGICZNA:
        // Wyklucza obcięte drzewa (np. błąd z 5 liściami w 163903.txt) przed zwrotem z heurystyki!
        if (!SprTopologyGuard.isStrictlyValidUnrootedTree(resultTree, baseTree.getExternalNodeCount())) {
            return null;
        }

        return resultTree;
    }

    private Tree buildPerfectRootedSprTree(Tree baseTree, Node pruneNode, Node targetNode) {
        SimpleTree copyTree = new SimpleTree(baseTree);
        copyTree.createNodeList();
        pal.tree.TreeUtils.computeParentPointers(copyTree.getRoot());

        Node s = findByPath(baseTree.getRoot(), copyTree.getRoot(), pruneNode);
        Node v = findByPath(baseTree.getRoot(), copyTree.getRoot(), targetNode);

        if (s == null || v == null) return null;
        Node p = s.getParent();
        if (p == null || p == v || p == v.getParent() || isDescendant(v, s)) return null;

        Node pp = p.getParent();
        Node sibling = (p.getChild(0) == s) ? p.getChild(1) : p.getChild(0);

        Node newRoot = copyTree.getRoot();

        if (pp != null) {
            int pIdx = findChildPos(p, pp);
            pp.setChild(pIdx, sibling);
            sibling.setParent(pp);
        } else {
            newRoot = sibling;
            sibling.setParent(null);
        }

        Node q = v.getParent();
        if (q != null) {
            int vIdx = findChildPos(v, q);
            q.setChild(vIdx, p);
            p.setParent(q);
        } else {
            newRoot = p;
            p.setParent(null);
        }

        p.setChild(0, s);
        s.setParent(p);
        p.setChild(1, v);
        v.setParent(p);

        SimpleTree finalTree = new SimpleTree(newRoot);
        finalTree.createNodeList();
        pal.tree.TreeUtils.computeParentPointers(finalTree.getRoot());

        pal.misc.IdGroup idGroup = pal.tree.TreeUtils.getLeafIdGroup(baseTree);
        pal.tree.TreeUtils.mapExternalIdentifiers(idGroup, finalTree);

        return finalTree;
    }

    private Node findByPath(Node baseRoot, Node copyRoot, Node target) {
        if (baseRoot == target) return copyRoot;
        if (!baseRoot.isLeaf()) {
            for (int i = 0; i < baseRoot.getChildCount(); i++) {
                Node res = findByPath(baseRoot.getChild(i), copyRoot.getChild(i), target);
                if (res != null) return res;
            }
        }
        return null;
    }

    private boolean isDescendant(Node child, Node ancestor) {
        Node curr = child;
        while (curr != null) {
            if (curr == ancestor) return true;
            curr = curr.getParent();
        }
        return false;
    }

    public void forEachSprTree(Tree tree, Consumer<Tree> action) {
        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();

        // Zamieniamy Set<TreeHolder> na Set<String>, aby odciążyć Garbage Collector
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
            Tree resultTree = createAndFixSprTree(baseTree, s, t);
            if (resultTree != null) {
                // Generujemy lekki łańcuch znaków zamiast trzymać cały obiekt
                String topologyHash = getCanonicalTopology(resultTree.getRoot());

                // Dodajemy hash do zbioru - jeśli go tam nie było, akceptujemy drzewo
                if (seen.add(topologyHash)) {
                    SprMove move = new SprMove(s, t);
                    registerTreeCost(resultTree, move.getNniEquivalentCost());
                    registerTreeMove(resultTree, move);

                    action.accept(resultTree);
                }
            }
        }
    }

    private String getCanonicalTopology(Node node) {
        if (node.isLeaf()) {
            return node.getIdentifier().getName();
        }
        List<String> childStrings = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            childStrings.add(getCanonicalTopology(node.getChild(i)));
        }

        Collections.sort(childStrings);

        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < childStrings.size(); i++) {
            sb.append(childStrings.get(i));
            if (i < childStrings.size() - 1) sb.append(",");
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public void forEachNeighbour(Tree tree, java.util.function.Consumer<Tree> action) {
        forEachSprTree(tree, action);
    }
}