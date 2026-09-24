package treecmp.heuristics;

import pal.io.InputSource;
import pal.misc.IdGroup;
import pal.misc.Identifier;
import pal.tree.*;
import treecmp.common.TreeCmpException;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.moves.SprMove;
import treecmp.heuristics.moves.TbrMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.spr.BestTreeChooser;
import treecmp.heuristics.spr.TreeValuePair;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public abstract class TreeNeighborhoodUtils {

    public int num = 0;

    private final Map<Tree, TreeMove> treeMoves = new IdentityHashMap<>();

    public void registerTreeMove(Tree tree, TreeMove move) {
        if (tree != null && move != null) {
            treeMoves.put(tree, move);
        }
    }

    public TreeMove getMoveForTree(Tree tree) {
        return treeMoves.get(tree);
    }

    public Tree getTreeFromString(String treeStr) {
        Tree tree = null;
        try (InputSource is = InputSource.openString(treeStr)) {
            tree = new ReadTree(is);
        } catch (TreeParseException | IOException e) {
            e.printStackTrace();
        }
        return tree;
    }

    // ==========================================
    // METODY WSPÓŁDZIELONE DLA TBR i uTBR
    // ==========================================

    public boolean isValidTbrMove(Node pruneNode, Node rerootNode, Node targetNode) {
        if (targetNode == null || pruneNode == null || rerootNode == null) return false;
        if (pruneNode == rerootNode) {
            return isValidSprMove(pruneNode, targetNode);
        }
        if (targetNode == pruneNode.getParent()) return false;
        Node curr = targetNode;
        while (curr != null) {
            if (curr == pruneNode) return false;
            curr = curr.getParent();
        }
        if (targetNode.isRoot() && pruneNode.getParent() != null && pruneNode.getParent().isRoot()) return false;
        return true;
    }

    public boolean isValidUTbrMove(Node pruneNode, Node rerootNode, Node targetNode) {
        if (targetNode == null || pruneNode == null || rerootNode == null) return false;

        if (targetNode.isRoot() && pruneNode.getParent() != null && pruneNode.getParent().isRoot()) return false;
        if (targetNode == pruneNode.getParent()) return false;

        Node curr = targetNode;
        while (curr != null) {
            if (curr == pruneNode) return false;
            curr = curr.getParent();
        }

        if (pruneNode == rerootNode) {
            if (sameParent(pruneNode, targetNode)) return false;
            if (isChildParent(pruneNode, targetNode)) return false;
        }

        return true;
    }

    public static class CloneResult {
        public SimpleTree tree;
        public Node source;
        public Node reroot;
        public Node target;
    }

    public Tree createTbrTree(Tree baseTree, Node s, Node r, Node t) {
        int expectedLeaves = baseTree.getExternalNodeCount();

        CloneResult cr = fastTreeCloneWithEquivalents(baseTree, s, r, t);
        Node source = cr.source;
        Node reroot = cr.reroot;
        Node target = cr.target;

        if (source == null || reroot == null || target == null) return null;

        Node sourceParent = source.getParent();
        if (sourceParent == null) return null;

        Node provisionalRoot = cr.tree.getRoot();

        if (sourceParent.isRoot()) {
            Node[] otherChildren = findOtherChildren(source, sourceParent);
            if (otherChildren.length == 1) {
                provisionalRoot = otherChildren[0];
                provisionalRoot.setParent(null);
                source.setParent(null);
            } else {
                int sIdx = findChildPos(source, sourceParent);
                sourceParent.removeChild(sIdx);
                source.setParent(null);
            }
        } else {
            Node otherSourceChild = findOtherChild(source, sourceParent);
            Node sourceParent2 = sourceParent.getParent();
            int sourceParentPos = findChildPos(sourceParent, sourceParent2);

            sourceParent2.setChild(sourceParentPos, otherSourceChild);
            if (otherSourceChild != null) {
                otherSourceChild.setParent(sourceParent2);
            }
            source.setParent(null);
        }

        Node newSubtreeRoot = rerootDetachedSubtree(source, reroot);
        if (newSubtreeRoot != null) {
            newSubtreeRoot.setParent(null);
        }

        Node newNode = new SimpleNode();
        Node targetParent = target.getParent();
        Node rootNode;

        if (target == provisionalRoot || targetParent == null) {
            newNode.addChild(target); target.setParent(newNode);
            newNode.addChild(newSubtreeRoot); newSubtreeRoot.setParent(newNode);
            newNode.setParent(null);
            rootNode = newNode;
        } else {
            int targetPos = findChildPos(target, targetParent);
            if (targetPos == -1) return null;
            targetParent.setChild(targetPos, newNode);
            newNode.setParent(targetParent);

            newNode.addChild(target); target.setParent(newNode);
            newNode.addChild(newSubtreeRoot); newSubtreeRoot.setParent(newNode);
            rootNode = provisionalRoot;
        }

        int maxAllowedNodes = 4 * expectedLeaves + 10;
        if (hasCycle(rootNode, maxAllowedNodes)) {
            return null;
        }

        pal.tree.TreeUtils.computeParentPointers(rootNode);

        SimpleTree resTree = new SimpleTree(rootNode);
        resTree.createNodeList();

        if (resTree.getExternalNodeCount() != expectedLeaves) {
            return null;
        }

        TreeMove move = (s == r) ? new SprMove(s, t) : new TbrMove(s, r, t);
        registerTreeMove(resTree, move);
        registerTreeCost(resTree, move.getNniEquivalentCost());

        return resTree;
    }

    public static boolean hasCycle(Node root) {
        return hasCycle(root, 1000);
    }

    public static boolean hasCycle(Node root, int maxNodes) {
        if (root == null) return false;
        return countNodesDfs(root, 0, maxNodes) > maxNodes;
    }

    private static int countNodesDfs(Node node, int currentCount, int maxNodes) {
        if (node == null || currentCount > maxNodes) return currentCount + 1;
        int count = currentCount + 1;
        for (int i = 0; i < node.getChildCount(); i++) {
            count = countNodesDfs(node.getChild(i), count, maxNodes);
            if (count > maxNodes) return count;
        }
        return count;
    }

    public static boolean isStrictlyValidUnrootedTreeFast(Tree tree, int expectedLeaves) {
        if (tree == null) return false;
        Node root = tree.getRoot();
        if (root == null || root.getChildCount() < 3) return false;
        if (tree.getExternalNodeCount() != expectedLeaves) return false;

        int maxAllowed = 4 * expectedLeaves + 10;
        int[] counts = new int[2];
        if (!validateNodeDfs(root, true, counts, maxAllowed)) {
            return false;
        }
        return counts[0] == expectedLeaves;
    }

    private static boolean validateNodeDfs(Node node, boolean isRoot, int[] counts, int maxAllowed) {
        if (node == null) return false;
        if (counts[0] + counts[1] > maxAllowed) return false;

        if (node.isLeaf()) {
            counts[0]++;
            return true;
        }

        counts[1]++;
        int chCount = node.getChildCount();
        if (isRoot) {
            if (chCount < 3) return false;
        } else {
            if (chCount < 2) return false;
        }

        for (int i = 0; i < chCount; i++) {
            Node ch = node.getChild(i);
            if (ch == null || ch.getParent() != node) return false;
            if (!validateNodeDfs(ch, false, counts, maxAllowed)) return false;
        }
        return true;
    }

    public Tree createSprTree(Tree baseTree, Node s, Node t) {
        return createTbrTree(baseTree, s, s, t);
    }

    protected Node rerootDetachedSubtree(Node oldRoot, Node newRootEdgeChild) {
        if (oldRoot == newRootEdgeChild) return oldRoot;

        Node newRoot = new SimpleNode();
        Node curr = newRootEdgeChild.getParent();

        newRoot.addChild(newRootEdgeChild);
        newRootEdgeChild.setParent(newRoot);

        Node childComingFrom = newRootEdgeChild;
        Node parentForCurr = newRoot;

        while (curr != null) {
            Node nextParent = curr.getParent();
            Node sibling = findOtherChild(childComingFrom, curr);

            if (curr == oldRoot) {
                if (sibling != null) {
                    parentForCurr.addChild(sibling);
                    sibling.setParent(parentForCurr);
                }
                break;
            } else {
                parentForCurr.addChild(curr);
                curr.setParent(parentForCurr);

                int childCount = curr.getChildCount();
                for (int k = childCount - 1; k >= 0; k--) {
                    curr.removeChild(k);
                }

                if (sibling != null) {
                    curr.addChild(sibling);
                    sibling.setParent(curr);
                }
            }

            childComingFrom = curr;
            parentForCurr = curr;
            curr = nextParent;
        }
        return newRoot;
    }

    public static CloneResult fastTreeCloneWithEquivalents(Tree original, Node s, Node r, Node t) {
        CloneResult cr = new CloneResult();
        SimpleNode rootClone = fastNodeCloneWithEquivalents(original.getRoot(), s, r, t, cr);
        cr.tree = new SimpleTree(rootClone);
        return cr;
    }

    private static SimpleNode fastNodeCloneWithEquivalents(Node orig, Node s, Node r, Node t, CloneResult cr) {
        SimpleNode copy = new SimpleNode();
        if (orig.getIdentifier() != null) {
            copy.setIdentifier(orig.getIdentifier());
        }
        copy.setBranchLength(orig.getBranchLength());
        copy.setNumber(orig.getNumber());

        if (orig == s) cr.source = copy;
        if (orig == r) cr.reroot = copy;
        if (orig == t) cr.target = copy;

        for (int i = 0; i < orig.getChildCount(); i++) {
            Node childCopy = fastNodeCloneWithEquivalents(orig.getChild(i), s, r, t, cr);
            copy.insertChild(childCopy, i);
            childCopy.setParent(copy);
        }
        return copy;
    }

    protected Node findNodeEquivalent(Tree baseTree, Tree newTree, Node oldNode) {
        if (oldNode == null) return null;
        if (oldNode.isLeaf() && oldNode.getIdentifier() != null) {
            Node n = TreeUtils.getNodeByName(newTree, oldNode.getIdentifier().getName());
            if (n != null) return n;
        }
        if (baseTree != null) {
            List<Integer> path = new ArrayList<>();
            if (getPathToNode(baseTree.getRoot(), oldNode, path)) {
                Node n = findNodeByPath(newTree.getRoot(), path);
                if (n != null) return n;
            }
        }
        int num = oldNode.getNumber();
        if (num >= 0 && num < newTree.getInternalNodeCount()) {
            return newTree.getInternalNode(num);
        }
        return null;
    }

    protected Node findNodeEquivalent(Tree newTree, Node oldNode) {
        return findNodeEquivalent(null, newTree, oldNode);
    }

    public List<Node> getAllNodes(Tree tree) {
        List<Node> list = new ArrayList<>();
        collectNodes(tree.getRoot(), list);
        return list;
    }

    public void collectNodes(Node node, List<Node> list) {
        list.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNodes(node.getChild(i), list);
        }
    }

    public List<Node> getSubtreeNodes(Node root) {
        List<Node> list = new ArrayList<>();
        collectNodes(root, list);
        return list;
    }

    public TreeValuePair findBestNeighbour(Tree tree, BestTreeChooser btc, double neighSizeFrac, double inputTreeValue) throws TreeCmpException {
        int extNum = tree.getExternalNodeCount();
        int intNum = tree.getInternalNodeCount();
        int neighSize = calcSprNeighbours(tree);
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
                if (isValidSprMove(s, t)) {
                    resultTree = createSprTree(tree, s, t);
                    analyzedTreeNum++;
                    resultValue = btc.getValueForTree(resultTree);
                    if (resultValue < bestValue) {
                        bestTree = resultTree;
                        bestValue = resultValue;
                    }
                    frac = (double) analyzedTreeNum / (double) estimatedMax;
                    if (frac > neighSizeFrac && inputTreeValue > bestValue) {
                        TreeValuePair tvPair = new TreeValuePair();
                        tvPair.setTree(bestTree);
                        tvPair.setValue(bestValue);
                        return tvPair;
                    }
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
                    analyzedTreeNum++;
                    resultValue = btc.getValueForTree(resultTree);
                    if (resultValue < bestValue) {
                        bestTree = resultTree;
                        bestValue = resultValue;
                    }
                    frac = (double) analyzedTreeNum / (double) estimatedMax;
                    if (frac > neighSizeFrac && inputTreeValue > bestValue) {
                        TreeValuePair tvPair = new TreeValuePair();
                        tvPair.setTree(bestTree);
                        tvPair.setValue(bestValue);
                        return tvPair;
                    }
                }
            }
        }

        for (int i = 0; i < extNum; i++) {
            s = tree.getExternalNode(i);
            for (int j = 0; j < intNum; j++) {
                t = tree.getInternalNode(j);
                if (isValidSprMove(s, t)) {
                    resultTree = createSprTree(tree, s, t);
                    analyzedTreeNum++;
                    resultValue = btc.getValueForTree(resultTree);
                    if (resultValue < bestValue) {
                        bestTree = resultTree;
                        bestValue = resultValue;
                    }
                    frac = (double) analyzedTreeNum / (double) estimatedMax;
                    if (frac > neighSizeFrac && inputTreeValue > bestValue) {
                        TreeValuePair tvPair = new TreeValuePair();
                        tvPair.setTree(bestTree);
                        tvPair.setValue(bestValue);
                        return tvPair;
                    }
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
                        analyzedTreeNum++;
                        resultValue = btc.getValueForTree(resultTree);
                        if (resultValue < bestValue && inputTreeValue > bestValue) {
                            bestTree = resultTree;
                            bestValue = resultValue;
                        }
                        frac = (double) analyzedTreeNum / (double) estimatedMax;
                        if (frac > neighSizeFrac) {
                            TreeValuePair tvPair = new TreeValuePair();
                            tvPair.setTree(bestTree);
                            tvPair.setValue(bestValue);
                            return tvPair;
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
        if (!n1Root && !n2Root) {
            return (n1.getParent() == n2.getParent());
        }
        return false;
    }

    public boolean isChildParent(Node n1, Node n2) {
        return (n2 == n1.getParent() || n1 == n2.getParent());
    }

    public boolean isInnerMove(Node s, Node t) {
        Node lca = NodeUtils.getFirstCommonAncestor(s, t);
        return lca == s;
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
        if (sParent != null) {
            for (int i = 0; i < sParent.getChildCount(); i++) {
                if (sParent.getChild(i) == tParent) return true;
            }
        }
        if (tParent != null) {
            for (int i = 0; i < tParent.getChildCount(); i++) {
                if (tParent.getChild(i) == sParent) return true;
            }
        }
        return false;
    }

    private boolean distanceEqual2Inner(Node s, Node t) {
        if (!s.isLeaf()) {
            for (int i = 0; i < s.getChildCount(); i++) {
                Node child = s.getChild(i);
                for (int j = 0; j < child.getChildCount(); j++) {
                    if (child.getChild(j) == t) return true;
                }
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
        while (!node.isRoot()) {
            depth++;
            node = node.getParent();
        }
        return depth;
    }

    public int calcSprNeighbours(Tree baseTree) {
        int n = baseTree.getExternalNodeCount();
        int intNum = baseTree.getInternalNodeCount();
        Node node;
        int gammaSum = 0;
        for (int i = 0; i < intNum; i++) {
            node = baseTree.getInternalNode(i);
            if (node.isRoot()) continue;
            gammaSum += (getNodeDepth(node) - 1);
        }
        return 2 * (n - 2) * (2 * n - 5) - 2 * gammaSum;
    }

    public int calcUsprNeighbours(Tree baseTree) {
        int n = baseTree.getExternalNodeCount();
        return 2 * (n - 3) * (2 * n - 7);
    }

    public Tree createUsprTree(Tree baseTree, Node s, Node t) {
        boolean isInnerMove = false;
        if (isInnerMove(s, t)) {
            isInnerMove = true;
            Node tmpS = s;
            s = t;
            t = tmpS;
        }

        Tree resultTree = fastTreeClone(baseTree);
        Node resultRoot = resultTree.getRoot();
        int sourceNum = s.getNumber();
        int targetNum = t.getNumber();

        Node source, target;
        if (s.isLeaf()) {
            source = resultTree.getExternalNode(sourceNum);
        } else {
            source = resultTree.getInternalNode(sourceNum);
        }

        if (t.isLeaf()) {
            target = resultTree.getExternalNode(targetNum);
        } else {
            target = resultTree.getInternalNode(targetNum);
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
        }

        if (!isSourceParentRoot) {
            if (isInnerMove) {
                int sourcePos = findChildPos(source, sourceParent);
                sourceParent.removeChild(sourcePos);
            } else {
                for (int i = 0; i < otherSourceChildren.length; i++) {
                    sourceParent2.setChild(sourceParentPos, otherSourceChildren[i]);
                }
            }
        }

        if (isInnerMove) {
            Node child0 = target.getChild(0);
            Node child1 = target.getChild(1);
            Node newRoot = null;

            if (child0.isLeaf() && child1.isLeaf()) {
                Node join = new SimpleNode();
                child0.setParent(join);
                child1.setParent(join);
                join.addChild(child0);
                join.addChild(child1);
                newRoot = join;
            } else if (child1.isLeaf()) {
                child0.setParent(null);
                child1.setParent(child0);
                child0.addChild(child1);
                newRoot = child0;
            } else {
                child1.setParent(null);
                child0.setParent(child1);
                child1.addChild(child0);
                newRoot = child1;
            }
            Identifier NewRootTidentifier = new Identifier("NewRoot");
            sourceParent.setIdentifier(NewRootTidentifier);
            SimpleTree targetSubtree = new SimpleTree(newRoot);
            Node newRootInTargetSubtree = TreeUtils.getNodeByName(targetSubtree, NewRootTidentifier.getName());
            targetSubtree.reroot(newRootInTargetSubtree);
            target = targetSubtree.getRoot();
        }

        newNode.addChild(target);
        newNode.addChild(source);

        if (isTargetRoot) {
            newNode.setParent(null);
            resultTree.setRoot(newNode);
        } else if (isSourceParentRoot) {
            if (otherSourceChildren.length >= 2) {
                otherSourceChildren[0].setParent(null);
                otherSourceChildren[1].setParent(null);

                if (otherSourceChildren[0].isLeaf() && otherSourceChildren[1].isLeaf()) {
                    Node join = new SimpleNode();
                    join.addChild(otherSourceChildren[0]); otherSourceChildren[0].setParent(join);
                    join.addChild(otherSourceChildren[1]); otherSourceChildren[1].setParent(join);
                    resultTree.setRoot(join);
                } else if (otherSourceChildren[0].isLeaf()) {
                    otherSourceChildren[1].addChild(otherSourceChildren[0]);
                    resultTree.setRoot(otherSourceChildren[1]);
                } else {
                    otherSourceChildren[0].addChild(otherSourceChildren[1]);
                    resultTree.setRoot(otherSourceChildren[0]);
                }
            } else if (otherSourceChildren.length == 1) {
                otherSourceChildren[0].setParent(null);
                resultTree.setRoot(otherSourceChildren[0]);
            }
        } else {
            resultRoot.setParent(null);
            resultTree.setRoot(resultRoot);
        }

        if (resultTree instanceof pal.tree.SimpleTree) {
            pal.tree.TreeUtils.computeParentPointers(resultTree.getRoot());
            ((pal.tree.SimpleTree) resultTree).createNodeList();
        }

        SprMove move = new SprMove(s, t);
        registerTreeMove(resultTree, move);
        registerTreeCost(resultTree, move.getNniEquivalentCost());

        return resultTree;
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

    // =================================================================================
    // WSPÓLNE METODY OPTYMALIZACYJNE (FAST CLONE & BITWISE DEDUPLICATION)
    // =================================================================================

    public static pal.tree.SimpleTree fastTreeClone(pal.tree.Tree original) {
        pal.tree.SimpleNode rootClone = fastNodeClone(original.getRoot());
        pal.tree.SimpleTree newTree = new pal.tree.SimpleTree(rootClone);
        newTree.createNodeList();
        pal.tree.TreeUtils.computeParentPointers(newTree.getRoot());
        return newTree;
    }

    public static pal.tree.SimpleNode fastNodeClone(pal.tree.Node orig) {
        pal.tree.SimpleNode copy = new pal.tree.SimpleNode();
        if (orig.getIdentifier() != null) {
            copy.setIdentifier(orig.getIdentifier());
        }
        copy.setBranchLength(orig.getBranchLength());
        copy.setNumber(orig.getNumber());

        for (int i = 0; i < orig.getChildCount(); i++) {
            pal.tree.Node childCopy = fastNodeClone(orig.getChild(i));
            copy.insertChild(childCopy, i);
            childCopy.setParent(copy);
        }
        return copy;
    }

    public static Tree fastUnrootIfNeeded(Tree tree) {
        if (tree == null) return null;
        Node root = tree.getRoot();
        if (root.getChildCount() != 2) return tree;

        Node c0 = root.getChild(0);
        Node c1 = root.getChild(1);

        Node newRoot = c0.isLeaf() ? c1 : c0;
        Node attachedChild = c0.isLeaf() ? c0 : c1;

        if (newRoot.isLeaf()) {
            return TreeCmpUtils.unrootTreeIfNeeded(tree);
        }

        if (root instanceof SimpleNode && newRoot instanceof SimpleNode && attachedChild instanceof SimpleNode) {
            ((SimpleNode) root).removeChild(newRoot);
            ((SimpleNode) root).removeChild(attachedChild);
            ((SimpleNode) newRoot).addChild(attachedChild);
            attachedChild.setParent(newRoot);
            newRoot.setParent(null);

            pal.tree.TreeUtils.computeParentPointers(newRoot);
            SimpleTree unrooted = new SimpleTree(newRoot);
            unrooted.createNodeList();
            return unrooted;
        }

        return TreeCmpUtils.unrootTreeIfNeeded(tree);
    }

    public static CanonicalTopologyKey buildCanonicalKey(Tree tree, IdGroup idGroup, int numLeaves) {
        if (numLeaves <= 64) {
            long allMask = (numLeaves == 64) ? -1L : ((1L << numLeaves) - 1L);
            long[] splits = new long[tree.getInternalNodeCount()];
            int[] count = new int[1];
            collectSplitsLong(tree.getRoot(), idGroup, allMask, splits, count);
            Arrays.sort(splits, 0, count[0]);
            return new CanonicalTopologyKey(Arrays.copyOf(splits, count[0]));
        } else {
            List<BitSet> splits = new ArrayList<>(tree.getInternalNodeCount());
            collectSplitsBitSet(tree.getRoot(), idGroup, numLeaves, splits);
            splits.sort(BITSET_COMPARATOR);
            return new CanonicalTopologyKey(splits);
        }
    }

    private static long collectSplitsLong(Node node, IdGroup idGroup, long allMask, long[] outSplits, int[] count) {
        if (node.isLeaf()) {
            if (node.getIdentifier() != null && node.getIdentifier().getName() != null) {
                int id = idGroup.whichIdNumber(node.getIdentifier().getName());
                if (id >= 0 && id < 64) return 1L << id;
            }
            return 0L;
        }

        long mask = 0L;
        for (int i = 0; i < node.getChildCount(); i++) {
            mask |= collectSplitsLong(node.getChild(i), idGroup, allMask, outSplits, count);
        }

        if (!node.isRoot()) {
            long normalized = mask;
            if ((normalized & 1L) != 0L) {
                normalized = (~normalized) & allMask;
            }
            outSplits[count[0]++] = normalized;
        }
        return mask;
    }

    private static BitSet collectSplitsBitSet(Node node, IdGroup idGroup, int numLeaves, List<BitSet> splits) {
        BitSet bs = new BitSet(numLeaves);
        if (node.isLeaf()) {
            if (node.getIdentifier() != null && node.getIdentifier().getName() != null) {
                int id = idGroup.whichIdNumber(node.getIdentifier().getName());
                if (id >= 0 && id < numLeaves) bs.set(id);
            }
            return bs;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            bs.or(collectSplitsBitSet(node.getChild(i), idGroup, numLeaves, splits));
        }

        if (!node.isRoot()) {
            BitSet normalized = (BitSet) bs.clone();
            if (normalized.get(0)) {
                normalized.flip(0, numLeaves);
            }
            splits.add(normalized);
        }
        return bs;
    }

    public static final Comparator<BitSet> BITSET_COMPARATOR = (a, b) -> {
        if (a == b) return 0;
        int cA = a.cardinality(), cB = b.cardinality();
        if (cA != cB) return Integer.compare(cA, cB);
        int i = a.nextSetBit(0), j = b.nextSetBit(0);
        while (i >= 0 && j >= 0) {
            if (i != j) return Integer.compare(i, j);
            i = a.nextSetBit(i + 1);
            j = b.nextSetBit(j + 1);
        }
        return 0;
    };

    public static final class CanonicalTopologyKey {
        private final long[] longSplits;
        private final BitSet[] bitsetSplits;
        private final int hash;

        public CanonicalTopologyKey(long[] splits) {
            this.longSplits = splits;
            this.bitsetSplits = null;
            this.hash = Arrays.hashCode(splits);
        }

        public CanonicalTopologyKey(List<BitSet> splits) {
            this.longSplits = null;
            this.bitsetSplits = splits.toArray(new BitSet[0]);
            this.hash = Arrays.hashCode(this.bitsetSplits);
        }

        @Override
        public int hashCode() { return hash; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CanonicalTopologyKey)) return false;
            CanonicalTopologyKey other = (CanonicalTopologyKey) obj;
            if (this.hash != other.hash) return false;
            if (this.longSplits != null && other.longSplits != null) {
                return Arrays.equals(this.longSplits, other.longSplits);
            }
            if (this.bitsetSplits != null && other.bitsetSplits != null) {
                return Arrays.equals(this.bitsetSplits, other.bitsetSplits);
            }
            return false;
        }
    }

    protected static boolean getPathToNode(pal.tree.Node current, pal.tree.Node target, java.util.List<Integer> path) {
        if (current == target) return true;
        for (int i = 0; i < current.getChildCount(); i++) {
            path.add(i);
            if (getPathToNode(current.getChild(i), target, path)) return true;
            path.remove(path.size() - 1);
        }
        return false;
    }

    protected static pal.tree.Node findNodeByPath(pal.tree.Node root, java.util.List<Integer> path) {
        pal.tree.Node cur = root;
        for (Integer idx : path) {
            if (idx < 0 || idx >= cur.getChildCount()) return null;
            cur = cur.getChild(idx);
        }
        return cur;
    }

    public static pal.tree.Tree refreshTreeInPlace(pal.tree.Tree tree) {
        if (tree instanceof pal.tree.SimpleTree) {
            ((pal.tree.SimpleTree) tree).createNodeList();
        }
        return tree;
    }

    protected java.util.IdentityHashMap<pal.tree.Tree, Double> treeCosts = new java.util.IdentityHashMap<>();

    public double getTreeCost(pal.tree.Tree t) {
        return treeCosts.getOrDefault(t, 1.0);
    }

    protected void registerTreeCost(pal.tree.Tree t, double cost) {
        if (t != null) {
            treeCosts.put(t, cost);
        }
    }

    public abstract void forEachNeighbour(Tree tree, Consumer<Tree> action);

    public void clearCosts() {
        treeCosts.clear();
        treeMoves.clear();
    }

    public void clearMoves() {
        treeMoves.clear();
    }
}