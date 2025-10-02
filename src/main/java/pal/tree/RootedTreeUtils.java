// RootedTreeUtils.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)
package pal.tree;

import pal.misc.Identifier;
import pal.datatype.*;
import pal.tree.*;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This class contains utility methods. These include: <BR>
 * 1. gathering information about subtrees from a set of trees <BR>
 * 2. comparing subtrees and clades. <BR>
 * All these methods assume rooted trees!
 *
 * @author Alexei Drummond
 * @version  $Id: RootedTreeUtils.java,v 1.3 2003/06/11 05:26:46 matt Exp $
 */
public class RootedTreeUtils {

	/**
	 * @return true if the first node contains a subtree identical to the second node
	 * or is identical to the second node.
	 * @param root the root of the tree in which search for a subtree
	 * @param node the subtree to search for.
	 */
	public static boolean containsSubtree(Node root, Node node) {
		return (getSubtree(root, node) != null);
	}

    /**
     * Checks if the tree rooted at {@code root} contains a clade (subtree) that holds
     * the **exact same set of tip taxa** as the tree rooted at {@code clade},
     * regardless of the internal topology.
     *
     * @param root The root of the tree to search within.
     * @param clade The root of the subtree whose taxa set is being searched for.
     * @return {@code true} if a node exists in {@code root}'s tree that encompasses the exact taxa set of {@code clade}'s tree; otherwise, {@code false}.
     */
    public static boolean containsClade(Node root, Node clade) {
        return (getClade(root, clade) != null);
    }

    /**
     * Traverses the tree rooted at {@code root} to find a subtree that has the **same labeled topology**
     * as the tree rooted at {@code node}. Child order is considered unimportant for non-leaf nodes.
     *
     * @param root The root of the tree to search within.
     * @param node The root of the subtree whose labeled topology is being searched for.
     * @return The first {@code Node} in {@code root}'s tree that matches the labeled topology of {@code node}'s tree, or {@code null} if no such subtree exists.
     */
    public static Node getSubtree(Node root, Node node) {
        if (equal(root, node)) return root;
        for (int i =0; i < root.getChildCount(); i++) {
            Node match = getSubtree(root.getChild(i), node);
            if (match != null) return match;
        }
        return null;
    }

    /**
     * Traverses the tree rooted at {@code root} to find a clade (subtree) that contains
     * the **exact same set of tip labels** as the tree rooted at {@code clade}, regardless of topology.
     *
     * @param root The root of the tree to search within.
     * @param clade The root of the subtree whose set of tip labels is being searched for.
     * @return The first {@code Node} in {@code root}'s tree that contains the same set of tip labels as {@code clade}'s tree, or {@code null} if no such clade exists.
     */
    public static Node getClade(Node root, Node clade) {
        if (sameTaxa(root, clade)) return root;
        for (int i =0; i < root.getChildCount(); i++) {
            Node match = getClade(root.getChild(i), clade);
            if (match != null) return match;
        }
        return null;
    }



    /**
     * Checks if two tree structures have the **same tip-labeled topology**.
     * The order of children for internal nodes is considered arbitrary (not important).
     *
     * @param node1 The root node of the first tree (or subtree).
     * @param node2 The root node of the second tree (or subtree).
     * @return {@code true} if the trees have an identical labeled topology (allowing for child swapping); otherwise, {@code false}.
     */
    public static boolean equal(Node node1, Node node2) {
        int nodeCount1 = node1.getChildCount();
        int nodeCount2 = node2.getChildCount();

        // if different childCount not the same
        if (nodeCount1 != nodeCount2) return false;

        if (nodeCount1 == 0) {
            return (node1.getIdentifier().getName().equals(node2.getIdentifier().getName()));
        } else {
            // ASSUMES BIFURCATING TREES
            // CHILD ORDER DIFFERENCES ARE ALLOWED!
            if (equal(node1.getChild(0), node2.getChild(0))) {
                return (equal(node1.getChild(1), node2.getChild(1)));
            } else if (equal(node1.getChild(0), node2.getChild(1))) {
                return (equal(node1.getChild(1), node2.getChild(0)));
            } else return false;
        }
    }

    /**
     * Checks if two tree structures contain the **exact same set of tip labels (taxa)**, regardless of their internal topology.
     *
     * @param node1 The root node of the first tree (or subtree).
     * @param node2 The root node of the second tree (or subtree).
     * @return {@code true} if the two trees have the same number of leaves and their tip label sets are identical; otherwise, {@code false}.
     */
    public static boolean sameTaxa(Node node1, Node node2) {
        int leafCount1 = NodeUtils.getLeafCount(node1);
        int leafCount2 = NodeUtils.getLeafCount(node2);

        if (leafCount1 != leafCount2) return false;

        Hashtable table = new Hashtable(leafCount1+1);
        collectTaxa(node1, table);
        return !containsNovelTaxa(node2, table);
    }

    /**
     * Recursively collects the names of all tip taxa in the tree rooted at {@code root} into a hashtable.
     *
     * @param root The root node of the subtree being traversed.
     * @param table A {@code Hashtable} used to store the taxa names (keys and values are the names).
     * @return The number of new (previously unencountered) taxa names added to the hashtable from this subtree.
     */
    public static int collectTaxa(Node root, Hashtable table) {
        int nc = root.getChildCount();
        if (nc == 0) {
            String name = root.getIdentifier().getName();
            if (table.containsKey(name)) {
                return 0;
            } else {
                table.put(name, name);
                return 1;
            }
        } else {
            int newTaxaCount = 0;
            for (int i = 0; i < nc; i++) {
                newTaxaCount += collectTaxa(root.getChild(i), table);
            }
            return newTaxaCount;
        }
    }

	/**
	 * @return true if the given tree contains taxa not already in the given hashtable.
	 * @param root the root node of the tree.
	 * @param taxa a hashtable holding taxa names.
	 */
	public static boolean containsNovelTaxa(Node root, Hashtable taxa) {
		int nc = root.getChildCount();
		if (nc == 0) {
			return !taxa.containsKey(root.getIdentifier().getName());
		} else {
			for (int i = 0; i < nc; i++) {
				if (containsNovelTaxa(root.getChild(i), taxa)) return true;
			}
			return false;
		}
	}



	/**
	 * @return the number of taxa in the given tree that are NOT in the given hashtable.
	 * @param root the root node of the tree.
	 * @param taxa a hashtable holding taxa names.
	 */
	private static int newTaxaCount(Node root, Hashtable table) {
		int nc = root.getChildCount();
		if (nc == 0) {
			return (table.containsKey(root.getIdentifier().getName()) ? 0 : 1);
		} else {
			int newTaxaCount = 0;
			for (int i = 0; i < nc; i++) {
				newTaxaCount += newTaxaCount(root.getChild(i), table);
			}
			return newTaxaCount;
		}
	}

	/**
	 * @return the number of times the subtree was found in the
	 * given list of trees. If a subtree occurs more than once in a tree
	 * (for some bizarre reason) it is counted only once.
	 * @param subtree the subtree being searched for.
	 * @param trees a vector of trees to search for the subtree in.
	 */
	public static int subtreeCount(Node subtree, Vector trees) {
		int count = 0;
		Node root;
		for (int i = 0; i < trees.size(); i++) {
			root = ((Tree)trees.elementAt(i)).getRoot();
			if (containsSubtree(root, subtree)) {
				count += 1;
			}
		}
		return count;
	}

	/**
	 * @return the mean height of the given subtree in the
	 * given list of trees. If a subtree occurs more than once in a tree
	 * (for some bizarre reason) results are undefined.
	 * @param subtree the subtree being searched for.
	 * @param trees a vector of trees to search for the subtree in.
	 */
	public static double getMeanSubtreeHeight(Node subtree, Vector trees) {
		int count = 0;
		double totalHeight = 0.0;
		Node root;
		for (int i = 0; i < trees.size(); i++) {
			root = ((Tree)trees.elementAt(i)).getRoot();
			Node match = getSubtree(root, subtree);
			if (match != null) {
				count += 1;
				totalHeight += match.getNodeHeight();
			}
		}
		return totalHeight / (double)count;
	}

	/**
	 * @return the mean height of the given clade in the
	 * given list of trees. If a clade occurs more than once in a tree
	 * (for some bizarre reason) results are undefined.
	 * @param clade a node containing the clade being searched for.
	 * @param trees a vector of trees to search for the clade in.
	 */
	public static double getMeanCladeHeight(Node clade, Vector trees) {
		int count = 0;
		double totalHeight = 0.0;
		Node root;
		for (int i = 0; i < trees.size(); i++) {
			root = ((Tree)trees.elementAt(i)).getRoot();
			Node match = getClade(root, clade);
			if (match != null) {
				count += 1;
				totalHeight += match.getNodeHeight();
			}
		}
		return totalHeight / (double)count;
	}

	/**
	 * @return the number of times the clade was found in the
	 * given list of trees. If a clade occurs more than once in a tree
	 * (for some bizarre reason) it is counted only once.
	 * @param subtree a subtree containing the taxaset being searched for.
	 * @param trees a vector of trees to search for the clade in.
	 */
	public static int cladeCount(Node subtree, Vector trees) {
		int count = 0;
		Node root;
		for (int i = 0; i < trees.size(); i++) {
			root = ((Tree)trees.elementAt(i)).getRoot();
			if (containsClade(root, subtree)) {
				count += 1;
			}
		}
		return count;
	}

	public static void collectProportions(Tree tree, Vector trees) {

		for (int i =0; i < tree.getInternalNodeCount(); i++) {
			Node node = tree.getInternalNode(i);
			if (!node.isRoot()) {
				int cladeCount = cladeCount(node, trees);
				StringBuffer buffer = new StringBuffer();
				collectLeafNames(node, buffer);
				double pr = (double)cladeCount / (double)trees.size();
				tree.setAttribute(node, AttributeNode.CLADE_PROBABILITY, new Double(pr));

				double meanCladeHeight = getMeanCladeHeight(node, trees);
				tree.setAttribute(node, AttributeNode.MEAN_CLADE_HEIGHT, new Double(meanCladeHeight));

			}
			int subtreeCount = subtreeCount(node, trees);
			double pr = (double)subtreeCount / (double)trees.size();
			tree.setAttribute(node, AttributeNode.SUBTREE_PROBABILITY, new Double(pr));
		}
	}

	/**
	 * Fills given string buffer with preorder traversal space-delimited leaf names.
	 */
	private static void collectLeafNames(Node node, StringBuffer buffer) {

		if (node.isLeaf()) {
			buffer.append(node.getIdentifier().getName());
			buffer.append(' ');
		} else {
			for (int i = 0; i < node.getChildCount(); i++) {
				collectLeafNames(node.getChild(i), buffer);
			}
		}
	}

}

