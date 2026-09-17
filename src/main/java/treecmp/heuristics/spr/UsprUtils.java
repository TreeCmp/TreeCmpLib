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

    public Tree[] generateNeighboursOBSOLETE(Tree tree) {
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

    public TreeValuePair findBestNeighbour(Tree tree, BestTreeChooser btc, double neighSizeFrac, double inputTreeValue) throws TreeCmpException {
        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();
        int neighSize = calcUsprNeighbours(tree);
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
                if (isValidUsprMove(s, t)) {
                    resultTree = createUsprTree(tree, s, t);
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
                if (isValidUsprMove(s, t)) {
                    resultTree = createUsprTree(tree, s, t);
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
                if (isValidUsprMove(s, t)) {
                    resultTree = createUsprTree(tree, s, t);
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
                if (isValidUsprMove(s, t)) {
                    resultTree = createUsprTree(tree, s, t);
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
        if (n1Root && n2Root) return true;
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
        if (s == null || t == null || s == t) return false;
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

    public Tree createSprTree(Tree baseTree, Node s, Node t) {
        Tree resultTree = fastTreeClone(baseTree);
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

        if (!SprTopologyGuard.isStrictlyValidUnrootedTree(resultTree, baseTree.getExternalNodeCount())) {
            return null;
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
        if (baseTree == null || s == null || t == null || s == t) {
            return null;
        }
        if (s.isRoot() || t.isRoot()) {
            return null;
        }
        if (sameParent(s, t) || isChildParent(s, t)) {
            return null;
        }

        // Zastąpiono powolne getCopy() szybkim klonowaniem w pamięci
        Tree resultTree = fastTreeClone(baseTree);
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

            Node pathChild = (path.size() >= 2) ? path.get(path.size() - 2) : null;

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
                    Node f = null;
                    for (int cIdx = 0; cIdx < source.getChildCount(); cIdx++) {
                        Node candidate = source.getChild(cIdx);
                        if (candidate != pathChild) {
                            f = candidate;
                            break;
                        }
                    }
                    if (f != null) {
                        bParent.addChild(f);
                        f.setParent(bParent);
                    }
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

            resultTree = fastUnrootIfNeeded(resultTree);

            if (resultTree instanceof SimpleTree) {
                pal.tree.TreeUtils.computeParentPointers(resultTree.getRoot());
                ((SimpleTree) resultTree).createNodeList();
            }

            if (!SprTopologyGuard.isStrictlyValidUnrootedTree(resultTree, baseTree.getExternalNodeCount())) {
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

        resultTree = fastUnrootIfNeeded(resultTree);

        if (resultTree instanceof SimpleTree) {
            pal.tree.TreeUtils.computeParentPointers(resultTree.getRoot());
            ((SimpleTree) resultTree).createNodeList();
        }

        if (!SprTopologyGuard.isStrictlyValidUnrootedTree(resultTree, baseTree.getExternalNodeCount())) {
            return null;
        }

        return resultTree;
    }

    @Override
    public void forEachNeighbour(Tree tree, Consumer<Tree> action) {
        forEachUsprTree(tree, action);
    }

    public void forEachUsprTree(Tree tree, Consumer<Tree> action) {
        Tree workingTree = tree;
        if (workingTree.getRoot().getChildCount() == 2) {
            workingTree = fastUnrootIfNeeded(fastTreeClone(workingTree));
        }

        int extNum = workingTree.getExternalNodeCount();
        int intNum = workingTree.getInternalNodeCount();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(workingTree);
        int numLeaves = extNum;

        Set<CanonicalTopologyKey> seenTopologies = new HashSet<>();
        seenTopologies.add(buildCanonicalKey(workingTree, idGroup, numLeaves));
        Node s, t;

        for (int i = 0; i < extNum; i++) {
            s = workingTree.getExternalNode(i);
            for (int j = 0; j < extNum; j++) {
                t = workingTree.getExternalNode(j);
                processAndYieldUspr(workingTree, s, t, idGroup, numLeaves, seenTopologies, action);
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = workingTree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < extNum; j++) {
                t = workingTree.getExternalNode(j);
                processAndYieldUspr(workingTree, s, t, idGroup, numLeaves, seenTopologies, action);
            }
        }
        for (int i = 0; i < extNum; i++) {
            s = workingTree.getExternalNode(i);
            for (int j = 0; j < intNum; j++) {
                t = workingTree.getInternalNode(j);
                processAndYieldUspr(workingTree, s, t, idGroup, numLeaves, seenTopologies, action);
            }
        }
        for (int i = 0; i < intNum; i++) {
            s = workingTree.getInternalNode(i);
            if (s.isRoot()) continue;
            for (int j = 0; j < intNum; j++) {
                t = workingTree.getInternalNode(j);
                processAndYieldUspr(workingTree, s, t, idGroup, numLeaves, seenTopologies, action);
            }
        }
    }

    private void processAndYieldUspr(Tree baseTree, Node s, Node t, IdGroup idGroup, int numLeaves, Set<CanonicalTopologyKey> seen, Consumer<Tree> action) {
        if (isValidUsprMove(s, t)) {
            Tree resultTree = createUsprTree(baseTree, s, t);
            if (resultTree != null) {
                CanonicalTopologyKey topologyKey = buildCanonicalKey(resultTree, idGroup, numLeaves);
                if (seen.add(topologyKey)) {
                    SprMove move = new SprMove(s, t);
                    registerTreeCost(resultTree, move.getNniEquivalentCost());
                    registerTreeMove(resultTree, move);

                    action.accept(resultTree);
                }
            }
        }
    }
}