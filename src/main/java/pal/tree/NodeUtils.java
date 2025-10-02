// NodeUtils.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.tree;

import pal.misc.*;
import java.io.*;
import java.util.*;
import pal.io.*;
import pal.util.*;

/**
 * Helper routines for dealing with nodes.
 *
 * @version $Id: NodeUtils.java,v 1.28 2003/05/14 05:53:36 matt Exp $
 *
 * @author Alexei Drummond
 * @author Korbinian Strimmer
 * @author Matthew Goode
 */
public class NodeUtils {
	/**
	 * Appends all external nodes from tree defined by root to Vector store
	 * @param root The root node defining tree
	 * @param store Where leaf nodes are stored (original contents is not touched)
	 */
	public static void getExternalNodes(Node root, Vector store) {
		if(root.isLeaf()) {
			store.addElement(root);
		} else {
			for(int i = 0 ; i < root.getChildCount() ; i++) {
				getExternalNodes(root.getChild(i),store);
			}
		}
	}
	/**
	 * Obtains all external nodes from tree defined by root and returns as an array
	 * @param root The root node defining tree
	 * @return an array of nodes where each node is a leaf node, and is a member
	 * of the tree defined by root
	 */
	public static Node[] getExternalNodes(Node root) {
		Vector v = new Vector();
		getExternalNodes(root,v);
		Node[] result = new Node[v.size()];
		v.copyInto(result);
		return result;
	}
	/**
	 * Appends all internal nodes from tree defined by root to Vector store
	 * @param root The root node defining tree
	 * @param store Where internal nodes are stored (original contents is not touched)
	 * Note: Root will be the first node added
	 */
	public static void getInternalNodes(Node root, Vector store) {
		if(!root.isLeaf()) {
			store.addElement(root);
			for(int i = 0 ; i < root.getChildCount() ; i++) {
				getInternalNodes(root.getChild(i),store);
			}
		}
	}
    /**
     * Obtains all internal nodes from the tree defined by the root and returns them as an array.
     *
     * @param root The root node defining the tree structure.
     * @param includeRoot If {@code true}, the root node is included as the first element in the returned array; otherwise, it is excluded.
     * @return An array of {@code Node} objects, where each node is an internal node of the tree defined by the root.
     */
    public static Node[] getInternalNodes(Node root, boolean includeRoot) {
        Vector v = new Vector();
        getInternalNodes(root,v);
        Node[] result = new Node[v.size()];
        v.copyInto(result);
        if(includeRoot) {
            return result;
        }
        Node[] adjustedResult = new Node[result.length-1];
        System.arraycopy(result, 1,adjustedResult,0,adjustedResult.length);
        return adjustedResult;
    }

    /**
     * Calculates the maximum depth of the tree, measured in the number of nodes from the root to the deepest leaf.
     *
     * @param root The root node of the tree.
     * @return The maximum distance in nodes from the root to any leaf (CompSci depth measure).
     * @see #getPathLengthInfo(Node)
     */
    public static int getMaxNodeDepth(Node root) {
        int max = 0;
        for(int i = 0 ; i < root.getChildCount() ; i++) {
            int depth = getMaxNodeDepth(root.getChild(i));
            if(depth>max) {
                max = depth;
            }
        }
        return max+1;
    }

    /**
     * Calculates four key path length statistics from the root to the leaves, taking into account branch lengths.
     *
     * @param root The root node of the tree.
     * @return A double array of size 4 where elements are:
     * <ul>
     * <li>Index 0: The minimum length path from root to leaf.</li>
     * <li>Index 1: The second most minimum length path from root to leaf.</li>
     * <li>Index 2: The second most maximum length path from root to leaf.</li>
     * <li>Index 3: The maximum length path from root to leaf.</li>
     * </ul>
     */
    public static final double[] getPathLengthInfo(Node root) {
        double[] lengthInfo = new double[] { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY };
        getLengthInfo(root,0,lengthInfo);
        return lengthInfo;
    }

    /**
     * Calculates the maximum length of a path from the root to any leaf, based on branch lengths.
     *
     * @param root The root node of the tree.
     * @return The maximum path length from the root to a leaf.
     */
    public static final double getMaximumPathLengthLengthToLeaf(Node root) {
        if(root.isLeaf()) { return 0; }
        double maxLength = Double.NEGATIVE_INFINITY;
        for(int i = 0 ; i < root.getChildCount() ; i++) {
            Node c = root.getChild(i);
            double length = c.getBranchLength()+getMaximumPathLengthLengthToLeaf(c);
            maxLength=Math.max(length,maxLength);
        }
        return maxLength;
    }

    /**
     * Calculates the minimum length of a path from the root to any leaf, based on branch lengths.
     *
     * @param root The root node of the tree.
     * @return The minimum path length from the root to a leaf.
     */
    public static final double getMinimumPathLengthLengthToLeaf(Node root) {
        if(root.isLeaf()) { return 0; }
        double minLength = Double.POSITIVE_INFINITY;
        for(int i = 0 ; i < root.getChildCount() ; i++) {
            Node c = root.getChild(i);
            double length = c.getBranchLength()+getMinimumPathLengthLengthToLeaf(c);
            minLength=Math.min(length,minLength);
        }
        return minLength;
    }

    /**
     * Traverses the tree recursively (pre-order) and updates the {@code lengthInfo} array
     * each time a leaf node is encountered, tracking the four extreme path lengths.
     *
     * @param root The current node in the tree traversal.
     * @param lengthFromRoot The cumulative path length from the initial root to the current node.
     * @param lengthInfo The double array (size 4) storing the four extreme path lengths found so far.
     */
    private static final void getLengthInfo(Node root, double lengthFromRoot, double[] lengthInfo) {
        if(root.isLeaf()) {
            if(lengthFromRoot<lengthInfo[1]) {
                if(lengthFromRoot<lengthInfo[0]) {
                    lengthInfo[1] = lengthInfo[0];
                    lengthInfo[0] = lengthFromRoot;
                } else {
                    lengthInfo[1] = lengthFromRoot;
                }
            }
            if(lengthFromRoot>lengthInfo[2]) {
                if(lengthFromRoot>lengthInfo[3]) {
                    lengthInfo[2] = lengthInfo[3];
                    lengthInfo[3] = lengthFromRoot;
                } else {
                    lengthInfo[2] = lengthFromRoot;
                }
            }
        } else {
            for(int i = 0 ; i < root.getChildCount() ; i++) {
                Node c = root.getChild(i);
                getLengthInfo(c, lengthFromRoot+c.getBranchLength(),lengthInfo);
            }
        }
    }

    /**
     * Converts branch lengths into node heights for the entire tree, assuming the latest descendant
     * (the leaf on the longest path) is at height 0.0.
     *
     * @param root The root node of the tree.
     */
    public static void lengths2Heights(Node root) {
        // This calculates the maximum path length and uses it as the starting height (Time = 0 at the tips)
        lengths2Heights(root, getMaximumPathLengthLengthToLeaf(root));
    }

    /**
     * Returns the total number of internal nodes in the tree defined by the root.
     *
     * @param root The root node of the tree.
     * @return The count of internal nodes, including the root node (if it is not a leaf).
     */
    public static int getInternalNodeCount(Node root) {
        if(root.isLeaf()) {
            return 0;
        }
        int count = 0;
        for(int i = 0 ; i < root.getChildCount() ; i++) {
            count+=getInternalNodeCount(root.getChild(i));
        }
        return count+1;
    }

    /**
     * Converts branch lengths into node heights, ensuring that the existing height values of the tips are preserved.
     * Internal node heights are calculated based on the maximum (or mean) height implied by their children.
     *
     * @param node The current node in the traversal.
     * @param useMax If {@code true}, the parent height is set to the maximum height implied by its children. If {@code false}, the mean is used (with max as a lower bound).
     */
    public static void lengths2HeightsKeepTips(Node node, boolean useMax) {

        if (!node.isLeaf()) {
            for (int i = 0; i < node.getChildCount(); i++) {
                lengths2HeightsKeepTips(node.getChild(i), useMax);
            }

            double totalHL = 0.0;
            double maxHL = 0.0;
            double hl = 0.0;
            double maxH = 0.0;
            double h = 0.0;
            for (int i = 0; i < node.getChildCount(); i++) {
                h = node.getChild(i).getNodeHeight();
                hl = node.getChild(i).getBranchLength() + h;
                if (hl > maxHL) maxHL = hl;
                if (h > maxH) maxH = h;
                totalHL += hl;
            }
            if (useMax) {
                hl = maxHL; // set parent height to maximum parent height implied by children
            } else {
                hl = totalHL /  node.getChildCount(); // get mean parent height
                if (hl < maxH) hl = maxHL; // if mean parent height is not greater than all children height, fall back on max parent height.
            }
            node.setNodeHeight(hl); // set new parent height

            // change lengths in children to reflect changes.
            for (int i = 0; i < node.getChildCount(); i++) {
                h = node.getChild(i).getNodeHeight();
                node.getChild(i).setBranchLength(hl - h);
            }
        }
    }


    /**
     * Recursively sets the node heights of a tree starting from the root, given the absolute height of the root.
     * Branch lengths are preserved, and node heights are calculated as {@code ancestor.height - branch.length}.
     *
     * @param node The current node in the traversal.
     * @param newHeight The target height to be set for the current node (if not the root) or the starting height (if the root).
     */
    private static void lengths2Heights(Node node, double newHeight) {

        if (!node.isRoot()) {
            newHeight -= node.getBranchLength();
            node.setNodeHeight(newHeight);
        } else {
            node.setNodeHeight(newHeight);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            lengths2Heights(node.getChild(i), newHeight);
        }
    }

    /**
     * Exchanges the data fields between two nodes.
     * Specifically, it swaps identifiers, branch lengths, node heights, and branch length standard errors (SEs).
     *
     * @param node1 The first node in the exchange operation.
     * @param node2 The second node in the exchange operation.
     */
    public static void exchangeInfo(Node node1, Node node2) {

        Identifier swaps;
        double swapd;

        swaps = node1.getIdentifier();
        node1.setIdentifier(node2.getIdentifier());
        node2.setIdentifier(swaps);

        swapd = node1.getBranchLength();
        node1.setBranchLength(node2.getBranchLength());
        node2.setBranchLength(swapd);

        swapd = node1.getNodeHeight();
        node1.setNodeHeight(node2.getNodeHeight());
        node2.setNodeHeight(swapd);

        swapd = node1.getBranchLengthSE();
        node1.setBranchLengthSE(node2.getBranchLengthSE());
        node2.setBranchLengthSE(swapd);
    }

    /**
     * Determines the branch lengths of this node and all descendent nodes from their heights.
     * This conversion globally respects the minimum allowed branch length (BranchLimits.MINARC).
     *
     * @param node The node from which to start the conversion (typically the root).
     */
    public static void heights2Lengths(Node node) {
        heights2Lengths(node, true); //respect minimum
    }

    /**
     * Determines the branch lengths of this node and all descendent nodes from their heights.
     *
     * @param node The node from which to start the conversion.
     * @param respectMinimum If {@code true}, ensures that no resulting branch length is less than {@code BranchLimits.MINARC}.
     */
    public static void heights2Lengths(Node node, boolean respectMinimum) {
        for (int i = 0; i < node.getChildCount(); i++) {
            heights2Lengths(node.getChild(i));
        }

        if (node.isRoot()) {
            node.setBranchLength(0.0);
        }
        else {
            node.setBranchLength(node.getParent().getNodeHeight() - node.getNodeHeight());
            if (respectMinimum && (node.getBranchLength() < BranchLimits.MINARC))
            {
                node.setBranchLength(BranchLimits.MINARC);
            }
        }
    }

    /**
     * Determines the branch lengths of this node and its immediate descendent nodes from their heights.
     *
     * @param node The node for which to calculate its and its children's branch lengths.
     * @param respectMinimum If {@code true}, ensures that no resulting branch length is less than {@code BranchLimits.MINARC}.
     */
    public static void localHeights2Lengths(Node node, boolean respectMinimum) {

        for (int i = 0; i < node.getChildCount(); i++) {
            Node child = node.getChild(i);

            child.setBranchLength(node.getNodeHeight() - child.getNodeHeight());
        }

        if (node.isRoot()) {
            node.setBranchLength(0.0);
        }
        else {
            node.setBranchLength(node.getParent().getNodeHeight() - node.getNodeHeight());
            if (respectMinimum && (node.getBranchLength() < BranchLimits.MINARC))
            {
                node.setBranchLength(BranchLimits.MINARC);
            }
        }
    }


    /**
     * Finds the maximum node height among all children of the given node.
     *
     * @param node The parent node whose children's heights are to be inspected.
     * @return The largest node height value among the children.
     */
    public static double findLargestChild(Node node) {
        // find child with largest height
        double max = node.getChild(0).getNodeHeight();
        for (int j = 1; j < node.getChildCount(); j++){
            double h = node.getChild(j).getNodeHeight();
            if (h > max)
            {
                max = h;
            }
        }
        return max;
    }

    /**
     * Removes a specified child node from its parent.
     *
     * @param parent The node from which the child will be removed.
     * @param child The child node to be removed.
     */
    public static void removeChild(Node parent, Node child)
    {
        int rm = -1;
        for (int i = 0; i < parent.getChildCount(); i++)
        {
            if (child == parent.getChild(i))
            {
                rm = i;
                break;
            }
        }

        parent.removeChild(rm);
    }

    /**
     * Collapses an internal branch by removing the specified node and transferring its children
     * directly to its parent. The node's structural information is retained for potential restoration.
     *
     * @param node The internal node associated with the branch to be removed (collapsed).
     * @throws IllegalArgumentException If the node is the root or a leaf.
     */
    public static void removeBranch(Node node)
    {
        if (node.isRoot() || node.isLeaf())
        {
            throw new IllegalArgumentException("INTERNAL NODE REQUIRED (NOT ROOT)");
        }

        Node parent = node.getParent();

        // add childs of node to parent
        // (node still contains the link to childs
        // to allow later restoration)
        int numChilds = node.getChildCount();
        for (int i = 0; i < numChilds; i++)
        {
            parent.addChild(node.getChild(i));
        }

        // remove node from parent
        // (link to parent is restored and the
        // position is stored)
        int rm = -1;
        for (int i = 0; i < parent.getChildCount(); i++)
        {
            if (node == parent.getChild(i))
            {
                rm = i;
                break;
            }
        }
        parent.removeChild(rm);
        node.setParent(parent);
        node.setNumber(rm);
    }

    /**
     * Restores a previously removed (collapsed) internal branch by reinserting the node
     * between its parent and its original children.
     *
     * @param node The node associated with the internal branch to be restored.
     * @throws IllegalArgumentException If the node is the root or a leaf.
     */
    public static void restoreBranch(Node node)
    {
        if (node.isRoot() || node.isLeaf())
        {
            throw new IllegalArgumentException("INTERNAL NODE REQUIRED (NOT ROOT)");
        }

        Node parent = node.getParent();

        // remove childs of node from parent and make node their parent
        int numChilds = node.getChildCount();
        for (int i = 0; i < numChilds; i++)
        {
            Node c = node.getChild(i);
            removeChild(parent, c);
            c.setParent(node);
        }

        // insert node into parent
        parent.insertChild(node, node.getNumber());
    }



    /**
     * Joins two child nodes of a parent node, introducing a new internal node
     * that replaces the first child and becomes the parent of the original two children.
     *
     * @param node The parent node whose children are to be joined.
     * @param n1 The index of the first child.
     * @param n2 The index of the second child.
     * @throws IllegalArgumentException If {@code n1} and {@code n2} are the same index.
     */
    public static void joinChilds(Node node, int n1, int n2) {

        if (n1 == n2) {
            throw new IllegalArgumentException("CHILDREN MUST BE DIFFERENT");
        }

        int c1, c2;
        if (n2 < n1)
        {
            c1 = n2;
            c2 = n1;
        }
        else
        {
            c1 = n1;
            c2 = n2;
        }

        Node newNode = NodeFactory.createNode();

        Node child1 = node.getChild(c1);
        Node child2 = node.getChild(c2);

        node.setChild(c1, newNode);
        newNode.setParent(node);
        node.removeChild(c2); // now parent of child2 = null

        newNode.addChild(child1);
        newNode.addChild(child2);
    }

    /**
     * Determines the next node in a pre-order traversal sequence (Root -&gt; Left -&gt; Right).
     *
     * @param node The current node.
     * @return The next node in the pre-order sequence, or the root node if traversal has completed.
     */
    public static Node preorderSuccessor(Node node) {

        Node next = null;

        if (node.isLeaf()) {
            Node cn = node, ln = null; // Current and last node

            // Go up
            do
            {
                if (cn.isRoot())
                {
                    next = cn;
                    break;
                }
                ln = cn;
                cn = cn.getParent();
            }
            while (cn.getChild(cn.getChildCount()-1) == ln);

            // Determine next node
            if (next == null)
            {
                // Go down one node
                for (int i = 0; i < cn.getChildCount()-1; i++)
                {
                    if (cn.getChild(i) == ln)
                    {
                        next = cn.getChild(i+1);
                        break;
                    }
                }
            }
        }
        else
        {
            next = node.getChild(0);
        }

        return next;
    }

    /**
     * Determines the next node in a post-order traversal sequence (Left -&gt; Right -&gt; Root).
     *
     * @param node The current node.
     * @return The next node in the post-order sequence.
     */
    public static Node postorderSuccessor(Node node) {

        Node cn = null;
        Node parent = node.getParent();

        if (node.isRoot()){
            cn = node;
        }  else{

            // Go up one node
            if (parent.getChild(parent.getChildCount()-1) == node) {
                return parent;
            }
            // Go down one node
            for (int i = 0; i < parent.getChildCount()-1; i++) {
                if (parent.getChild(i) == node)    {
                    cn = parent.getChild(i+1);
                    break;
                }
            }
        }
        // Go down until leaf
        while (cn.getChildCount() > 0)
        {

            cn = cn.getChild(0);
        }


        return cn;
    }

    /**
     * Prints the node and its descendants in Newick (New Hamshire) format.
     *
     * @param out The {@code PrintWriter} to write the output to.
     * @param node The node from which to start printing (typically the root).
     * @param printLengths If {@code true}, branch lengths are included in the output.
     * @param printInternalLabels If {@code true}, labels of internal nodes are included in the output.
     */
    public static void printNH(PrintWriter out, Node node,
                               boolean printLengths, boolean printInternalLabels) {

        printNH(out, node, printLengths, printInternalLabels, 0, true);
    }

    /**
     * Prints the node and its descendants in Newick (New Hamshire) format, with control over line breaking and column positioning.
     *
     * @param out The {@code PrintWriter} to write the output to.
     * @param node The current node in the traversal.
     * @param printLengths If {@code true}, branch lengths are included.
     * @param printInternalLabels If {@code true}, internal node labels are included.
     * @param column The current column position in the output line (used for line breaking).
     * @param breakLines If {@code true}, line breaks are inserted when the output exceeds a column threshold.
     * @return The new column position after printing the current node and its subtree.
     */
    public static int printNH(PrintWriter out, Node node,
                              boolean printLengths, boolean printInternalLabels, int column, boolean breakLines) {

        if (breakLines) column = breakLine(out, column);

        if (!node.isLeaf())
        {
            out.print("(");
            column++;

            for (int i = 0; i < node.getChildCount(); i++)
            {
                if (i != 0)
                {
                    out.print(",");
                    column++;
                }

                column = printNH(out, node.getChild(i), printLengths, printInternalLabels, column, breakLines);
            }

            out.print(")");
            column++;
        }

        if (!node.isRoot())
        {
            if (node.isLeaf() || printInternalLabels)
            {
                if (breakLines) column = breakLine(out, column);

                String id = node.getIdentifier().toString();
                out.print(id);
                column += id.length();
            }

            if (printLengths)
            {
                out.print(":");
                column++;

                if (breakLines) column = breakLine(out, column);

                column += FormattedOutput.getInstance().displayDecimal(out, node.getBranchLength(), 7);
            }
        }

        return column;
    }

	private static int breakLine(PrintWriter out, int column)
	{
		if (column > 70)
		{
			out.println();
			column = 0;
		}

		return column;
	}

    /**
     * Traverses the tree defined by the root node and attempts to find the first node matching each of the required identifiers.
     * The search is performed recursively (typically pre-order) until the first match is found for a given identifier name.
     *
     * @param node The root node from which to start the search.
     * @param identifierNames An array of string names of the identifiers to search for.
     * @return An array of {@code Node} objects corresponding to the input {@code identifierNames}. Returns {@code null} if none of the identifiers match any node in the tree. Otherwise, returns an array that may contain {@code null} elements for identifiers that did not match a node.
     */
    public static final Node[] findByIdentifier(Node node, String[] identifierNames) {
        Node[] nodes = new Node[identifierNames.length];
        boolean foundSomething = false;
        for(int i = 0 ; i < nodes.length ; i++) {
            nodes[i] = findByIdentifier(node,identifierNames[i]);
            foundSomething = foundSomething||(nodes[i]!=null);
        }
        if(!foundSomething) {
            return null;
        }
        return nodes;
    }

    /**
     * Traverses the tree defined by the root node and attempts to find the first node matching each of the required identifiers.
     *
     * @param node The root node from which to start the search.
     * @param identifiers An array of {@code Identifier} objects whose names are searched for.
     * @return An array of {@code Node} objects corresponding to the input {@code identifiers}.
     */
    public static final Node[] findByIdentifier(Node node, Identifier[] identifiers) {
        Node[] nodes = new Node[identifiers.length];
        for(int i = 0 ; i < nodes.length ; i++) {
            nodes[i] = findByIdentifier(node,identifiers[i]);
        }
        return nodes;
    }

    /**
     * Traverses the tree defined by the root node and attempts to find the first node matching the required identifier.
     *
     * @param node The root node from which to start the search.
     * @param identifier The {@code Identifier} object to search for.
     * @return The first {@code Node} found with a matching identifier, or {@code null} if no match is found.
     */
    public static final Node findByIdentifier(Node node, Identifier identifier) {
        return findByIdentifier(node,identifier.getName());
    }

    /**
     * Traverses the tree defined by the root node and attempts to find the first node whose identifier name matches the required string.
     * This search is typically performed in a depth-first manner (pre-order).
     *
     * @param node The root node from which to start the search.
     * @param identifierName The string name of the identifier to search for.
     * @return The first {@code Node} found with a matching identifier name, or {@code null} if no match is found.
     */
    public static final Node findByIdentifier(Node node, String identifierName) {

        Log.getDefaultLogger().debug("node identifier = " + node.getIdentifier());
        Log.getDefaultLogger().debug("target identifier name = " + identifierName);

        if (node.getIdentifier().getName().equals(identifierName)) {
            return node;
        } else {
            Node pos = null;
            for (int i = 0; i < node.getChildCount(); i++) {
                pos = findByIdentifier(node.getChild(i), identifierName);
                if (pos != null) return pos;
            }
            //if (pos == null && !node.isRoot()) {
            // pos = findByIdentifier(node.getParent(), identifier);
            //}
            if (pos != null) return pos;
            return null;
        }
    }

    /**
     * Determines the cumulative branch length distance from the given node up to the root of the tree.
     *
     * @param node The node from which to calculate the distance to the root.
     * @return The total distance to the root (sum of all branch lengths along the path).
     */
    public static double getDistanceToRoot(Node node)
    {
        if (node.isRoot())
        {
            return 0.0;
        }
        else
        {
            return node.getBranchLength() + getDistanceToRoot(node.getParent());
        }
    }

    /**
     * Returns the number of terminal leaves (tip nodes) descended from the given node, including the node itself if it is a leaf.
     *
     * @param node The starting node of the subtree.
     * @return The count of leaf nodes in the subtree rooted at this node.
     */
    public static int getLeafCount(Node node) {

        int count = 0;
        if (!node.isLeaf()) {
            for (int i = 0; i < node.getChildCount(); i++) {
                count += getLeafCount(node.getChild(i));
            }
        } else {
            count = 1;
        }
        return count;
    }

    /**
     * Checks if the first node is an ancestor of the second node (including the case where the nodes are the same).
     *
     * @param possibleAncestor The node that may be an ancestor.
     * @param node The descendant node being checked.
     * @return {@code true} if {@code possibleAncestor} is an ancestor of {@code node} or if {@code possibleAncestor} equals {@code node}; otherwise, {@code false}.
     */
    public static boolean isAncestor(Node possibleAncestor, Node node) {
        if(node==possibleAncestor) {
            return true;
        }
        while(!node.isRoot()){
            node = node.getParent();
            if(node==possibleAncestor) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the most recent common ancestor (MRCA) for a set of nodes in a tree.
     *
     * @param nodes An array of {@code Node} objects to check; {@code null} array elements are skipped.
     * @return The {@code Node} representing the MRCA of all non-null nodes, or {@code null} if the set of nodes is disjoint (i.e., they belong to different trees or have no common ancestor).
     */
    public static Node getFirstCommonAncestor(Node[] nodes) {
        Node currentCA = nodes[0];
        for(int i = 1; i < nodes.length ;i++) {
            if(currentCA!=null&&nodes[i]!=null) {
                currentCA = getFirstCommonAncestor(currentCA,nodes[i]);
                if(currentCA==null) {
                    return null;
                }
            }
        }
        return currentCA;
    }

    /**
     * Finds the most recent common ancestor (MRCA) for two nodes in a tree.
     *
     * @param nodeOne The first node.
     * @param nodeTwo The second node.
     * @return The {@code Node} representing the MRCA of the two nodes. This may be one of the input nodes if one is the ancestor of the other, or {@code null} if the two nodes are disjoint (from different trees).
     */
    public static Node getFirstCommonAncestor(Node nodeOne, Node nodeTwo) {
        if(isAncestor(nodeTwo, nodeOne)) {
            return nodeTwo;
        }
        if(isAncestor(nodeOne, nodeTwo)) {
            return nodeOne;
        }
        while(!nodeTwo.isRoot()) {
            nodeTwo = nodeTwo.getParent();
            if(isAncestor(nodeTwo, nodeOne)) {
                return nodeTwo;
            }
        }
        return null;
    }

    /**
     * Returns the number of branches that meet at an internal node in an unrooted tree context.
     * This is equivalent to the number of children plus the one branch connecting to the parent (unless the node is the root).
     *
     * @param center The internal node to check.
     * @return The number of adjacent branches (degrees of the node in the unrooted topology).
     */
    public static final int getUnrootedBranchCount(Node center) {
        if (center.isRoot())   {
            return center.getChildCount();
        }
        else {
            return center.getChildCount()+1;
        }
    }

    /**
     * Forces all negative branch lengths in the subtree defined by the given node to zero.
     * After setting negative lengths to zero, the method recalculates all node heights based on the new branch lengths.
     *
     * @param node The root of the subtree to process.
     */
    public static final void convertNegativeBranchLengthsToZeroLength(Node node) {
        convertNegativeBranchLengthsToZeroLengthImpl(node);
        lengths2Heights(node);
    }
	private static final void convertNegativeBranchLengthsToZeroLengthImpl(Node node) {
		if(node.getBranchLength()<0) {
			node.setBranchLength(0);
		}
		for(int i = 0 ; i < node.getChildCount() ; i++) {
			convertNegativeBranchLengthsToZeroLengthImpl(node.getChild(i));
		}
	}
}
