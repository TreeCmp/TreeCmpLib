package treecmp.heuristics.spr;

import pal.misc.IdGroup;
import pal.tree.*;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.TreeUnrootedHolder;
import treecmp.heuristics.moves.SprMove;

import java.util.function.Consumer;
import java.util.List;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class UsprUtils extends TreeNeighborhoodUtils {

    public Tree[] generateNeighbours(Tree tree) {
        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        int neighSize = calcUsprNeighbours(tree);
        Set<TreeUnrootedHolder> usprTreeSet = new HashSet<TreeUnrootedHolder>((4 * neighSize) / 3);
        Node s, t;
        Tree resultTree;

        for (int i = 0; i < extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j = 0; j < extNum; j++) {
                t = tree.getExternalNode(j);
                if (isValidUsprMove(s, t)) {
                    resultTree = createUsprTree(tree, s, t);
                    if (resultTree != null) {
                        SprMove move = new SprMove(s, t);
                        registerTreeCost(resultTree, move.getNniEquivalentCost());
                        registerTreeMove(resultTree, move);
                        usprTreeSet.add(new TreeUnrootedHolder(resultTree, idGroup));
                    }
                }
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < extNum; j++) {
                t = tree.getExternalNode(j);
                if (isValidUsprMove(s, t)) {
                    resultTree = createUsprTree(tree, s, t);
                    if (resultTree != null) {
                        SprMove move = new SprMove(s, t);
                        registerTreeCost(resultTree, move.getNniEquivalentCost());
                        registerTreeMove(resultTree, move);
                        try { usprTreeSet.add(new TreeUnrootedHolder(resultTree, idGroup)); } catch (Exception e) {}
                    }
                }
            }
        }
        for (int i = 0; i < extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j = 0; j < intNum; j++) {
                t = tree.getInternalNode(j);
                if (isValidUsprMove(s, t)) {
                    resultTree = createUsprTree(tree, s, t);
                    if (resultTree != null) {
                        SprMove move = new SprMove(s, t);
                        registerTreeCost(resultTree, move.getNniEquivalentCost());
                        registerTreeMove(resultTree, move);
                        usprTreeSet.add(new TreeUnrootedHolder(resultTree, idGroup));
                    }
                }
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < intNum; j++) {
                t = tree.getInternalNode(j);
                if (isValidUsprMove(s, t)) {
                    resultTree = createUsprTree(tree, s, t);
                    if (resultTree != null) {
                        SprMove move = new SprMove(s, t);
                        registerTreeCost(resultTree, move.getNniEquivalentCost());
                        registerTreeMove(resultTree, move);
                        usprTreeSet.add(new TreeUnrootedHolder(resultTree, idGroup));
                    }
                }
            }
        }

        int n = usprTreeSet.size();
        Tree[] usprTreeArray = new Tree[n];
        int i = 0;
        for (TreeUnrootedHolder th : usprTreeSet) {
            usprTreeArray[i] = th.tree;
            i++;
        }
        return usprTreeArray;
    }

    // =========================================================================================
    // NAPRAWIONY findBestNeighbour - używa teraz createUsprTree i calcUsprNeighbours!
    // =========================================================================================
    public TreeValuePair findBestNeighbour(Tree tree, BestTreeChooser btc, double neighSizeFrac, double inputTreeValue) throws TreeCmpException {
        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();
        int neighSize = calcUsprNeighbours(tree); // NAPRAWIONO: calcUsprNeighbours
        int estimatedMax = (extNum + intNum) * (extNum + intNum);
        int analyzedTreeNum = 0;
        double frac;

        Node s, t;
        Tree resultTree, bestTree = null;
        double bestValue = Double.MAX_VALUE;
        double resultValue;

        for (int i = 0; i < extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j = 0; j < extNum; j++) {
                t = tree.getExternalNode(j);
                if (isValidUsprMove(s, t)) { // NAPRAWIONO: isValidUsprMove
                    resultTree = createUsprTree(tree, s, t); // NAPRAWIONO: createUsprTree
                    if (resultTree != null) {
                        analyzedTreeNum++;
                        resultValue = btc.getValueForTree(resultTree);
                        if (resultValue < bestValue) { bestTree = resultTree; bestValue = resultValue; }
                        frac = (double) analyzedTreeNum / (double) estimatedMax;
                        if (frac > neighSizeFrac && inputTreeValue > bestValue) {
                            TreeValuePair tvPair = new TreeValuePair(); tvPair.setTree(bestTree); tvPair.setValue(bestValue); return tvPair;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < extNum; j++) {
                t = tree.getExternalNode(j);
                if (isValidUsprMove(s, t)) { // NAPRAWIONO: isValidUsprMove
                    resultTree = createUsprTree(tree, s, t); // NAPRAWIONO: createUsprTree
                    if (resultTree != null) {
                        analyzedTreeNum++;
                        resultValue = btc.getValueForTree(resultTree);
                        if (resultValue < bestValue) { bestTree = resultTree; bestValue = resultValue; }
                        frac = (double) analyzedTreeNum / (double) estimatedMax;
                        if (frac > neighSizeFrac && inputTreeValue > bestValue) {
                            TreeValuePair tvPair = new TreeValuePair(); tvPair.setTree(bestTree); tvPair.setValue(bestValue); return tvPair;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j = 0; j < intNum; j++) {
                t = tree.getInternalNode(j);
                if (isValidUsprMove(s, t)) { // NAPRAWIONO: isValidUsprMove
                    resultTree = createUsprTree(tree, s, t); // NAPRAWIONO: createUsprTree
                    if (resultTree != null) {
                        analyzedTreeNum++;
                        resultValue = btc.getValueForTree(resultTree);
                        if (resultValue < bestValue) { bestTree = resultTree; bestValue = resultValue; }
                        frac = (double) analyzedTreeNum / (double) estimatedMax;
                        if (frac > neighSizeFrac && inputTreeValue > bestValue) {
                            TreeValuePair tvPair = new TreeValuePair(); tvPair.setTree(bestTree); tvPair.setValue(bestValue); return tvPair;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < intNum; j++) {
                t = tree.getInternalNode(j);
                if (isValidUsprMove(s, t)) { // NAPRAWIONO: isValidUsprMove
                    resultTree = createUsprTree(tree, s, t); // NAPRAWIONO: createUsprTree
                    if (resultTree != null) {
                        analyzedTreeNum++;
                        resultValue = btc.getValueForTree(resultTree);
                        if (resultValue < bestValue && inputTreeValue > bestValue) { bestTree = resultTree; bestValue = resultValue; }
                        frac = (double) analyzedTreeNum / (double) estimatedMax;
                        if (frac > neighSizeFrac) {
                            TreeValuePair tvPair = new TreeValuePair(); tvPair.setTree(bestTree); tvPair.setValue(bestValue); return tvPair;
                        }
                    }
                }
            }
        }

        TreeValuePair tvPair = new TreeValuePair();
        tvPair.setTree(bestTree);
        tvPair.setValue(bestValue);
        return tvPair;
    }

    public boolean sameParent(Node n1, Node n2) {
        boolean n1Root = n1.isRoot();
        boolean n2Root = n2.isRoot();
        if (n1Root && n1Root) return true;
        if (!n1Root && !n2Root) { return (n1.getParent() == n2.getParent()); }
        return false;
    }

    public boolean isChildParent(Node n1, Node n2) {
        return (n2 == n1.getParent() || n1 == n2.getParent());
    }

    public boolean isInnerMove(Node s, Node t) {
        return NodeUtils.getFirstCommonAncestor(s, t) == s;
    }

    public boolean isValidSprMove(Node s, Node t) {
        if (sameParent(s, t)) return false;
        if (isChildParent(s, t)) return false;
        if (isInnerMove(s, t)) return false;
        return true;
    }

    public boolean isValidUsprMove(Node s, Node t) {
        if (sameParent(s, t)) return false;
        if (isChildParent(s, t)) return false;
        if (s.isRoot() || t.isRoot()) return false;
        if (distanceEqual3(s, t) && !isSmalestInNNI(s, t)) return false;
        if (distanceEqual2Inner(s, t) && !isSmalestInNNI(s.getParent(), t)) return false;
        if (distanceEqual2Inner(s, t) && !isSmalestInNNI(findOtherChild(s.getParent(), s), t)) return false;
        return true;
    }

    private boolean distanceEqual3(Node s, Node t) {
        Node sParent = s.getParent();
        Node tParent = t.getParent();
        if (sParent.isRoot() || tParent.isRoot()) return false;
        if (sParent != null) { for (int i = 0; i < sParent.getChildCount(); i++) { if (sParent.getChild(i) == tParent) return true; } }
        if (tParent != null) { for (int i = 0; i < tParent.getChildCount(); i++) { if (tParent.getChild(i) == sParent) return true; } }
        return false;
    }

    private boolean distanceEqual2Inner(Node s, Node t) {
        if (!s.isLeaf()) {
            for (int i = 0; i < s.getChildCount(); i++) {
                Node child = s.getChild(i);
                for (int j = 0; j < child.getChildCount(); j++) { if (child.getChild(j) == t) return true; }
            }
        }
        return false;
    }

    private boolean isSmaler(Node s, Node t) {
        if (s == null) return false;
        if (s.isLeaf()) {
            if (t.isLeaf()) return s.getNumber() < t.getNumber();
            else return false;
        } else {
            if (t.isLeaf()) return true;
            else return s.getNumber() < t.getNumber();
        }
    }

    private boolean isSmalestInNNI(Node s, Node t) {
        if (isSmaler(t, s)) return false;
        Node sBrother = findOtherChild(s.getParent(), s);
        if (isSmaler(sBrother, s)) return false;
        Node tBrother = findOtherChild(t.getParent(), t);
        if (isSmaler(tBrother, s)) return false;
        return true;
    }

    public int getNodeDepth(Node node) {
        int depth = 0;
        if (node.isRoot()) return 0;
        while (!node.isRoot()) { depth++; node = node.getParent(); }
        return depth;
    }

    public int calcSprNeighbours(Tree baseTree) {
        int n = baseTree.getExternalNodeCount();
        int intNum = baseTree.getInternalNodeCount();
        int gammaSum = 0;
        for (int i = 0; i < intNum; i++) {
            Node node = baseTree.getInternalNode(i);
            if (node.isRoot()) continue;
            gammaSum += getNodeDepth(node) - 1;
        }
        return 2 * (n - 2) * (2 * n - 5) - 2 * gammaSum;
    }

    public int calcUsprNeighbours(Tree baseTree) {
        int n = baseTree.getExternalNodeCount();
        return 2 * (n - 3) * (2 * n - 7);
    }

    protected Node findNodeEquivalent(Tree newTree, Node oldNode) {
        if (oldNode.isLeaf()) return newTree.getExternalNode(oldNode.getNumber());
        return newTree.getInternalNode(oldNode.getNumber());
    }

    public Tree createSprTree(Tree baseTree, Node s, Node t) {
        Tree resultTree = baseTree.getCopy();
        Node source = findNodeEquivalent(resultTree, s);
        Node target = findNodeEquivalent(resultTree, t);

        if (source == null || target == null) return null;

        Node sourceParent = source.getParent();
        Node targetParent = target.getParent();
        boolean isTargetRoot = target.isRoot();
        boolean isSourceParentRoot = sourceParent.isRoot();

        if (isTargetRoot && isSourceParentRoot) return null;

        Node otherSourceChild = findOtherChild(source, sourceParent);
        Node sourceParent2 = null;
        int sourceParentPos = -1;
        if (!isSourceParentRoot) {
            sourceParent2 = sourceParent.getParent();
            sourceParentPos = findChildPos(sourceParent, sourceParent2);
        }

        Node newNode = new SimpleNode();
        if (!isTargetRoot) {
            int targetPos = findChildPos(target, targetParent);
            targetParent.setChild(targetPos, newNode);
            newNode.setParent(targetParent);
        }

        if (!isSourceParentRoot) {
            sourceParent2.setChild(sourceParentPos, otherSourceChild);
            otherSourceChild.setParent(sourceParent2);
        }

        safeDetach(target);
        newNode.addChild(target);
        target.setParent(newNode);

        safeDetach(source);
        newNode.addChild(source);
        source.setParent(newNode);

        if (isTargetRoot) {
            newNode.setParent(null);
            resultTree.setRoot(newNode);
        } else if (isSourceParentRoot) {
            otherSourceChild.setParent(null);
            resultTree.setRoot(otherSourceChild);
        } else {
            resultTree.getRoot().setParent(null);
        }

        if (resultTree instanceof pal.tree.SimpleTree) {
            pal.tree.TreeUtils.computeParentPointers(resultTree.getRoot());
            ((pal.tree.SimpleTree) resultTree).createNodeList();
        }

        return resultTree;
    }

    private void safeDetach(Node child) {
        if (child != null && child.getParent() != null) {
            Node oldParent = child.getParent();
            for (int i = 0; i < oldParent.getChildCount(); i++) {
                if (oldParent.getChild(i) == child) {
                    oldParent.removeChild(i);
                    break;
                }
            }
            child.setParent(null);
        }
    }

    public Tree createUsprTree(Tree baseTree, Node s, Node t) {
        Tree resultTree = baseTree.getCopy();
        Node source = findNodeEquivalent(resultTree, s);
        Node target = findNodeEquivalent(resultTree, t);

        if (source == null || target == null) return null;

        if (isInnerMove(s, t)) {
            Node sParent = source.getParent();
            Node tParent = target.getParent();

            int sPos = findChildPos(source, sParent);
            if (sPos != -1) sParent.removeChild(sPos);
            source.setParent(null);

            List<Node> path = new ArrayList<>();
            Node curr = tParent;
            while (curr != null && curr != source) {
                path.add(curr);
                curr = curr.getParent();
            }
            path.add(source);

            for (int i = 0; i < path.size() - 1; i++) {
                Node child = path.get(i);
                Node parent = path.get(i + 1);

                int pos = findChildPos(child, parent);
                if (pos != -1) parent.removeChild(pos);

                child.addChild(parent);
                parent.setParent(child);
            }
            tParent.setParent(null);

            Node bParent = source.getParent();
            if (bParent != null) {
                int bPos = findChildPos(source, bParent);
                if (bPos != -1) bParent.removeChild(bPos);

                if (source.getChildCount() > 0) {
                    Node f = source.getChild(0);
                    bParent.addChild(f);
                    f.setParent(bParent);
                }
            }

            int tPos = findChildPos(target, tParent);
            if (tPos != -1) tParent.removeChild(tPos);

            Node newNode = new SimpleNode();

            safeDetach(target);
            newNode.addChild(target);
            target.setParent(newNode);

            safeDetach(source);
            newNode.addChild(tParent);
            tParent.setParent(newNode);

            sParent.addChild(newNode);
            newNode.setParent(sParent);

            if (resultTree instanceof SimpleTree) {
                pal.tree.TreeUtils.computeParentPointers(resultTree.getRoot());
                ((SimpleTree) resultTree).createNodeList();
            }
            treecmp.common.TreeCmpUtils.unrootTreeIfNeeded(resultTree);
            if (resultTree instanceof SimpleTree) {
                pal.tree.TreeUtils.computeParentPointers(resultTree.getRoot());
                ((SimpleTree) resultTree).createNodeList();
            }
            if (!isStrictlyValidUnrootedTree(resultTree, baseTree.getExternalNodeCount())) {
                return null;
            }

            return resultTree;
        }

        Node sourceParent = source.getParent();
        Node targetParent = target.getParent();
        boolean isTargetRoot = target.isRoot();
        boolean isSourceParentRoot = sourceParent.isRoot();

        if (isTargetRoot && isSourceParentRoot) return null;

        Node[] otherSourceChildren = findOtherChildren(source, sourceParent);
        Node sourceParent2 = null;
        int sourceParentPos = -1;

        if (!isSourceParentRoot) {
            sourceParent2 = sourceParent.getParent();
            sourceParentPos = findChildPos(sourceParent, sourceParent2);
        }

        Node newNode = new SimpleNode();

        if (!isTargetRoot) {
            int targetPos = findChildPos(target, targetParent);
            targetParent.setChild(targetPos, newNode);
            newNode.setParent(targetParent);
        }

        safeDetach(target);
        newNode.addChild(target);
        target.setParent(newNode);

        safeDetach(source);
        newNode.addChild(source);
        source.setParent(newNode);

        if (!isSourceParentRoot) {
            if (otherSourceChildren.length > 0) {
                Node otherChild = otherSourceChildren[0];
                sourceParent2.setChild(sourceParentPos, otherChild);
                otherChild.setParent(sourceParent2);
            } else {
                sourceParent2.removeChild(sourceParentPos);
            }
        }

        if (isTargetRoot) {
            newNode.setParent(null);
            resultTree.setRoot(newNode);
        } else if (isSourceParentRoot) {
            if (otherSourceChildren.length == 2) {
                Node c0 = otherSourceChildren[0];
                Node c1 = otherSourceChildren[1];
                c0.setParent(null);
                c1.setParent(null);

                if (c0.isLeaf()) {
                    c1.addChild(c0);
                    c0.setParent(c1);
                    resultTree.setRoot(c1);
                } else {
                    c0.addChild(c1);
                    c1.setParent(c0);
                    resultTree.setRoot(c0);
                }
            } else if (otherSourceChildren.length == 1) {
                Node c0 = otherSourceChildren[0];
                c0.setParent(null);
                resultTree.setRoot(c0);
            }
        } else {
            resultTree.getRoot().setParent(null);
        }

        if (resultTree instanceof SimpleTree) {
            pal.tree.TreeUtils.computeParentPointers(resultTree.getRoot());
            ((SimpleTree) resultTree).createNodeList();
        }

        treecmp.common.TreeCmpUtils.unrootTreeIfNeeded(resultTree);

        if (resultTree instanceof SimpleTree) {
            pal.tree.TreeUtils.computeParentPointers(resultTree.getRoot());
            ((SimpleTree) resultTree).createNodeList();
        }

        if (!isStrictlyValidUnrootedTree(resultTree, baseTree.getExternalNodeCount())) {
            return null;
        }

        return resultTree;
    }

    /**
     * Ostateczna Tarcza Newicka: Sprawdza unikalność liści, stopnie węzłów,
     * zbalansowanie nawiasów oraz zakazane podciągi ("null", ",,", "()").
     */
    private boolean isStrictlyValidUnrootedTree(Tree tree, int expectedLeafCount) {
        if (tree == null || tree.getRoot() == null) return false;
        if (tree.getExternalNodeCount() != expectedLeafCount) return false;
        if (tree.getRoot().getChildCount() < 2) return false;

        Set<String> uniqueLeafNames = new HashSet<>();
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            String name = tree.getExternalNode(i).getIdentifier().getName();
            if (name == null || name.trim().isEmpty() || !uniqueLeafNames.add(name)) {
                return false; // Wykryto duplikat lub pustą etykietę liścia!
            }
        }

        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            Node node = tree.getInternalNode(i);
            if (!node.isRoot() && node.getChildCount() < 2) {
                return false; // Węzeł stopnia 1 (zdegenerowany)
            }
        }

        String newick = tree.toString();

        // 1. Zakazane podciągi, na których wykłada się parser DendroPy w Pythonie
        if (newick.contains("null") || newick.contains(",,") || newick.contains("()")) {
            return false;
        }

        // 2. Weryfikacja zbalansowania nawiasów '(' vs ')' oraz liczby przecinków
        int openParens = 0;
        int closeParens = 0;
        int commaCount = 0;
        for (int i = 0; i < newick.length(); i++) {
            char ch = newick.charAt(i);
            if (ch == '(') openParens++;
            else if (ch == ')') closeParens++;
            else if (ch == ',') commaCount++;
        }

        if (openParens != closeParens) return false;
        if (commaCount != expectedLeafCount - 1) return false;

        return true;
    }

    public int findChildPos(Node child, Node parent) {
        int childNum = parent.getChildCount();
        for (int i = 0; i < childNum; i++) {
            if (parent.getChild(i) == child) return i;
        }
        return -1;
    }

    public Node[] findOtherChildren(Node child1, Node parent) {
        int childNum = parent.getChildCount();
        Node[] nodes = new Node[childNum - 1];
        int childInd = 0;
        for (int i = 0; i < childNum; i++) {
            Node ch = parent.getChild(i);
            if (ch != child1) {
                nodes[childInd] = ch;
                childInd++;
            }
        }
        return nodes;
    }

    public Node findOtherChild(Node child1, Node parent) {
        int childNum = parent.getChildCount();
        for (int i = 0; i < childNum; i++) {
            Node ch = parent.getChild(i);
            if (ch != child1) return ch;
        }
        return null;
    }

    public void forEachUsprTree(Tree tree, Consumer<Tree> action) {
        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        int numLeaves = extNum;

        Set<String> seenTopologies = new HashSet<>();
        Node s, t;

        for (int i = 0; i < extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j = 0; j < extNum; j++) {
                t = tree.getExternalNode(j);
                processAndYieldUspr(tree, s, t, idGroup, numLeaves, seenTopologies, action);
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < extNum; j++) {
                t = tree.getExternalNode(j);
                processAndYieldUspr(tree, s, t, idGroup, numLeaves, seenTopologies, action);
            }
        }
        for (int i = 0; i < extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j = 0; j < intNum; j++) {
                t = tree.getInternalNode(j);
                processAndYieldUspr(tree, s, t, idGroup, numLeaves, seenTopologies, action);
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = tree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < intNum; j++) {
                t = tree.getInternalNode(j);
                processAndYieldUspr(tree, s, t, idGroup, numLeaves, seenTopologies, action);
            }
        }
    }

    private void processAndYieldUspr(Tree baseTree, Node s, Node t, IdGroup idGroup, int numLeaves, Set<String> seen, Consumer<Tree> action) {
        if (isValidUsprMove(s, t)) {
            Tree resultTree = createUsprTree(baseTree, s, t);
            if (resultTree != null) {
                String topologyHash = getUnrootedCanonicalTopology(resultTree, idGroup, numLeaves);
                if (seen.add(topologyHash)) {
                    action.accept(resultTree);
                }
            }
        }
    }

    private String getUnrootedCanonicalTopology(Tree tree, IdGroup idGroup, int numLeaves) {
        List<String> splits = new ArrayList<>();
        getSplits(tree.getRoot(), idGroup, numLeaves, splits);
        Collections.sort(splits);
        StringBuilder sb = new StringBuilder();
        for (String split : splits) {
            sb.append(split).append("|");
        }
        return sb.toString();
    }

    private BitSet getSplits(Node node, IdGroup idGroup, int numLeaves, List<String> splits) {
        BitSet bs = new BitSet(numLeaves);
        if (node.isLeaf()) {
            bs.set(idGroup.whichIdNumber(node.getIdentifier().getName()));
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                bs.or(getSplits(node.getChild(i), idGroup, numLeaves, splits));
            }
        }

        if (!node.isRoot()) {
            BitSet normalized = (BitSet) bs.clone();
            if (normalized.get(0)) {
                normalized.flip(0, numLeaves);
            }
            splits.add(normalized.toString());
        }
        return bs;
    }
}