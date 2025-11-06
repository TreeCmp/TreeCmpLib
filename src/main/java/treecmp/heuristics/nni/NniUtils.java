/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.nni;

import java.io.IOException;
import java.util.*;

import pal.io.InputSource;
import pal.misc.IdGroup;
import pal.misc.Identifier;
import pal.tree.*;
import treecmp.common.ClusterDist;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.TreeUnootedHolder;
import treecmp.heuristics.spr.BestTreeChooser;
import treecmp.heuristics.spr.TreeValuePair;
import treecmp.metrics.topological.RFClusterMetric;

/**
 *
 * @author Tomasz Goluch
 */
public class NniUtils extends TreeNeighborhoodUtils {

    private final boolean unrooted;

    public NniUtils(boolean unrooted) {
        this.unrooted = unrooted;
    }

    /**
     * Generates the NNI (Nearest Neighbor Interchange) neighborhood for the given tree.
     *
     * <p>This implementation delegates to an internal method, using the object's
     * {@code unrooted} flag (set during construction). It will generate either the
     * <b>rooted NNI (rNNI)</b> or <b>unrooted NNI (uNNI)</b> neighborhood
     * based on this state.
     *
     * @param tree the input phylogenetic tree
     * @return an array of trees representing the NNI neighbors
     */
    @Override
    public Tree[] generateNeighbours(Tree tree) {
        return this.generateNniNeighboursInternal(tree, this.unrooted);
    }

    private Tree[] generateNniNeighboursInternal(Tree tree, boolean unrooted) {
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        Set<treecmp.heuristics.TreeHolder> nniTreeSet = new HashSet<>();
        int intNum = tree.getInternalNodeCount();

        for (int i = 0; i < intNum; i++) {
            Node node = tree.getInternalNode(i);
            if (node.isRoot()) continue;

            Node parent = node.getParent();
            if (parent == null) continue;
            if (node.getChildCount() != 2) continue;

            boolean parentIsBinary = (parent.getChildCount() == 2);
            boolean parentIsUnrootedPolytomy = (unrooted && parent.isRoot() && parent.getChildCount() > 2);

            if (!parentIsBinary && !parentIsUnrootedPolytomy) {
                continue;
            }


            Node a = node.getChild(0);
            Node b = node.getChild(1);

            for (int k = 0; k < parent.getChildCount(); k++) {
                Node c = parent.getChild(k);
                if (c == node) continue;

                Tree t1 = createNNITree(tree, node, parent, a, c);
                Tree t2 = createNNITree(tree, node, parent, b, c);

                if (unrooted) {
                    if (t1 != null) nniTreeSet.add(new TreeUnootedHolder(t1, idGroup));
                    if (t2 != null) nniTreeSet.add(new TreeUnootedHolder(t2, idGroup));
                } else {
                    if (t1 != null) nniTreeSet.add(new TreeRootedHolder(t1, idGroup));
                    if (t2 != null) nniTreeSet.add(new TreeRootedHolder(t2, idGroup));
                }

                if (parentIsBinary) {
                    break;
                }
            }
        }

        int n = nniTreeSet.size();
        Tree[] nniTreeArray = new Tree[n];
        int idx = 0;
        for (treecmp.heuristics.TreeHolder th : nniTreeSet) {
            nniTreeArray[idx++] = th.tree;
        }
        return nniTreeArray;
    }


    /**
     * Creates a new tree obtained by performing a single NNI (Nearest Neighbor Interchange) move
     * along the edge connecting {@code parent} and {@code node}.
     *
     * The move consists of swapping a child from {@code node} with the sibling of {@code node}
     * (a child of {@code parent}), creating a new tree topology.
     *
     * @param tree the original tree
     * @param node the internal node on one side of the edge
     * @param parent the parent node on the other side of the edge
     * @param swapFromNode the child of {@code node} to be swapped
     * @param swapFromParent the sibling of {@code node} under {@code parent} to be swapped
     * @return a new tree with the modified topology, or {@code null} if the operation fails
     */
    private static Tree createNNITree(Tree tree, Node node, Node parent, Node swapFromNode, Node swapFromParent) {
        try {
            // 1) compute paths (child-index lists) to each of the involved nodes in the original tree
            List<Integer> pathToNode = new ArrayList<>();
            if (!getPathToNode(tree.getRoot(), node, pathToNode)) return null;

            List<Integer> pathToParent = new ArrayList<>();
            if (!getPathToNode(tree.getRoot(), parent, pathToParent)) return null;

            List<Integer> pathToSwapFromNode = new ArrayList<>();
            if (!getPathToNode(tree.getRoot(), swapFromNode, pathToSwapFromNode)) return null;

            List<Integer> pathToSwapFromParent = new ArrayList<>();
            if (!getPathToNode(tree.getRoot(), swapFromParent, pathToSwapFromParent)) return null;

            Tree newTree = tree.getCopy();
            Node newRoot = newTree.getRoot();

            // 3) locate corresponding nodes in the copy using paths
            Node newNode = findNodeByPath(newRoot, pathToNode);
            Node newParent = findNodeByPath(newRoot, pathToParent);
            Node newSwapFromNode = findNodeByPath(newRoot, pathToSwapFromNode);
            Node newSwapFromParent = findNodeByPath(newRoot, pathToSwapFromParent);

            if (newNode == null || newParent == null || newSwapFromNode == null || newSwapFromParent == null) {
                return null;
            }

            // compute child indices relative to their parents (last element of respective paths)
            int idxSwapFromNode = pathToSwapFromNode.get(pathToSwapFromNode.size() - 1);
            int idxSwapFromParent = pathToSwapFromParent.get(pathToSwapFromParent.size() - 1);

            // 4) remove and re-insert swapped subtrees
            // remove child from newNode and newParent (use indices so identity issues avoided)
            newNode.removeChild(idxSwapFromNode);
            newParent.removeChild(idxSwapFromParent);

            // insert at the same positions (to keep tree order stable)
            newNode.insertChild(newSwapFromParent, idxSwapFromNode);
            newSwapFromParent.setParent(newNode);

            newParent.insertChild(newSwapFromNode, idxSwapFromParent);
            newSwapFromNode.setParent(newParent);

            return newTree;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Recursively finds a path (list of child indices) from {@code root} to {@code target}.
     *
     * The path is stored in {@code path} (mutable list); on success returns true and {@code path} is populated.
     *
     * @param current current node in recursion
     * @param target target node reference (identity comparison)
     * @param path mutable list that will contain child indices leading from root to target
     * @return true if target was found (and path is filled), false otherwise
     */
    private static boolean getPathToNode(Node current, Node target, List<Integer> path) {
        if (current == target) {
            return true;
        }
        for (int i = 0; i < current.getChildCount(); i++) {
            path.add(i);
            if (getPathToNode(current.getChild(i), target, path)) return true;
            path.remove(path.size() - 1);
        }
        return false;
    }

    /**
     * Follows a path (list of child indices) from {@code root} and returns the node reached.
     *
     * @param root start node
     * @param path list of child indices (empty => root)
     * @return node at the path or null if path invalid
     */
    private static Node findNodeByPath(Node root, List<Integer> path) {
        Node cur = root;
        for (Integer idx : path) {
            if (idx < 0 || idx >= cur.getChildCount()) return null;
            cur = cur.getChild(idx);
        }
        return cur;
    }

}
