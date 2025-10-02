// CladeSystem.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.tree;

import java.io.*;

import pal.misc.*;

/**
 * data structure for a set of splits 
 *
 * @version $Id: CladeSystem.java,v 1.1 2002/06/03 09:17:52 alexi Exp $
 *
 * @author Alexei Drummond
 */
public class CladeSystem {
	//
	// Public stuff
	//

	/**
	 * @param idGroup  sequence labels
	 * @param size     number of clades
	 */
	public CladeSystem(IdGroup idGroup, int size) {
		this.idGroup = idGroup;
		clades = new boolean[size][idGroup.getIdCount()];
	}

    /**
     * Returns the total number of clades (rows) stored in this system.
     *
     * @return The number of clades.
     */
    public int getCladeCount() {
        return clades.length;
    }

    /**
     * Returns the number of labels (taxa or leaves) represented in each clade (column count).
     *
     * @return The number of labels (taxa) in the system.
     */
    public int getLabelCount() {
        return clades[0].length;
    }

    /**
     * Returns the 2D array representing the entire clade system.
     * The array is organized as {@code [clade_index][label_index]}.
     *
     * @return The boolean 2D array where {@code true} indicates a label belongs to a clade.
     */
    public boolean[][] getCladeArray() {
        return clades;
    }

    /**
     * Returns the boolean array for a single clade at the specified index.
     *
     * @param i The zero-based index of the clade to retrieve.
     * @return The boolean array representing the membership of labels in the clade.
     */
    public boolean[] getClade(int i) {
        return clades[i];
    }

    /**
     * Returns the IdGroup object containing the identifiers (labels) corresponding to the columns in the clade array.
     *
     * @return The IdGroup associated with this clade system.
     */
    public IdGroup getIdGroup() {
        return idGroup;
    }

    /**
     * Tests whether a given clade (split) is already contained in this clade system.
     * This assumes the input clade array uses the same leaf order as the internal system.
     *
     * @param clade The boolean array representing the clade (split) to test for existence.
     * @return {@code true} if an identical clade is found in the system; otherwise, {@code false}.
     */
    public boolean hasClade(boolean[] clade) {
        for (int i = 0; i < clades.length; i++)
        {
            if (SplitUtils.isSame(clade, clades[i])) return true;
        }

        return false;
    }


    /**
     * Returns a string representation of the clade system, listing the labels followed by a matrix
     * visualization of the clades (where '*' indicates membership and '.' indicates exclusion).
     *
     * @return A string containing the formatted clade system.
     */
    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        for (int i = 0; i < getLabelCount(); i++)
        {
            pw.println(idGroup.getIdentifier(i));
        }
        pw.println();

        for (int i = 0; i < getCladeCount(); i++)
        {
            for (int j = 0; j < getLabelCount(); j++)
            {
                if (clades[i][j] == true)
                    pw.print('*');
                else
                    pw.print('.');
            }

            pw.println();
        }

        return sw.toString();
    }
	
	// ********************************************************************
	// STATIC METHODS
	// ********************************************************************

    /**
     * Generates an array of {@code CladeSystem} objects, where each system represents the clades
     * (splits) present in a corresponding tree from the input array.
     * The leaf order for all generated clade systems is derived from the first tree.
     *
     * @param trees An array of {@code Tree} objects for which the clade systems are to be generated.
     * @return An array of {@code CladeSystem} objects, one for each input tree.
     */
    public static CladeSystem[] getCladeSystems(Tree[] trees) {
        IdGroup idGroup = TreeUtils.getLeafIdGroup(trees[0]);
        CladeSystem[] cladeSystems = new CladeSystem[trees.length];
        for (int i =0; i < cladeSystems.length; i++) {
            cladeSystems[i] = getClades(idGroup, trees[i]);
        }
        return cladeSystems;
    }

	public static void calculateCladeProbabilities(Tree tree, CladeSystem[] cladeSystems) {
	
		CladeSystem cladeSystem = getClades(cladeSystems[0].getIdGroup(), tree);
		
		for (int i =0; i < tree.getInternalNodeCount()-1; i++) {
			Node node = tree.getInternalNode(i);
			boolean[] clade = cladeSystem.getClade(i);
			if (node.isRoot()) throw new RuntimeException("Root node does not have clade probability!");
				
			int cladeCount = 0;
			for (int j = 0; j < cladeSystems.length; j++) {
				if (cladeSystems[j].hasClade(clade)) cladeCount += 1;
			}
			double pr = (double)cladeCount / (double)cladeSystems.length; 
			tree.setAttribute(node, AttributeNode.CLADE_PROBABILITY, new Double(pr));	
		}
	}

    /**
     * Creates a {@code CladeSystem} from a tree, using a pre-specified order of sequences (labels).
     * The resulting {@code CladeSystem} contains a boolean array representing each non-root internal node's corresponding split.
     *
     * @param idGroup The {@code IdGroup} defining the specific sequence order for the output matrix (columns).
     * @param tree The {@code Tree} object from which the clades are extracted.
     * @return The generated {@code CladeSystem} containing all non-root splits of the tree.
     */
    public static CladeSystem getClades(IdGroup idGroup, Tree tree) {
        tree.createNodeList();

        int size = tree.getInternalNodeCount()-1;
        CladeSystem cladeSystem = new CladeSystem(idGroup, size);

        boolean[][] clades = cladeSystem.getCladeArray();

        for (int i = 0; i < size; i++) {
            getClade(idGroup, tree.getInternalNode(i), clades[i]);
        }

        return cladeSystem;
    }

    /**
     * Creates a {@code CladeSystem} from a tree, deriving the order of sequences (labels) from the tree itself.
     *
     * @param tree The {@code Tree} object from which the clades and sequence order are derived.
     * @return The generated {@code CladeSystem} containing all non-root splits of the tree.
     */
    public static CladeSystem getClades(Tree tree) {
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);

        return getClades(idGroup, tree);
    }

    /**
     * Marks the leaf nodes belonging to the clade defined by a specific internal node in a boolean array.
     *
     * @param idGroup The {@code IdGroup} defining the order of labels (columns) used in the clade array.
     * @param internalNode The internal {@code Node} that defines the split/clade to be extracted.
     * @param clade The boolean array to be populated; {@code true} marks a leaf as belonging to the clade defined by {@code internalNode}.
     * @throws IllegalArgumentException If the provided node is a leaf or the root node.
     */
    public static void getClade(IdGroup idGroup, Node internalNode, boolean[] clade) {
        if (internalNode.isLeaf() || internalNode.isRoot()) {
            throw new IllegalArgumentException("Only internal nodes (and no root) nodes allowed");
        }

        // make sure clade is reset
        for (int i = 0; i < clade.length; i++) {
            clade[i] = false;
        }

        // mark all leafs downstream of the node
        // AJD removed loop, as doesn't appear to be necessary
        SplitUtils.markNode(idGroup, internalNode, clade);
    }

    /**
     * Checks whether two clades (represented by boolean arrays) are structurally identical.
     * This comparison assumes that both arrays have the same length and use the same leaf order.
     *
     * @param s1 The boolean array representing clade 1.
     * @param s2 The boolean array representing clade 2.
     * @return {@code true} if the two clades are identical in terms of leaf membership; otherwise, {@code false}.
     * @throws IllegalArgumentException If the two clade arrays do not have the same length.
     */
    public static boolean isSame(boolean[] s1, boolean[] s2)
    {
        if (s1.length != s2.length)
            throw new IllegalArgumentException("Clades must be of the same length!");

        for (int i = 0; i < s1.length; i++) {
            // clades not identical
            if (s1[i] != s2[i]) return false;
        }
        return true;
    }
	
	//
	// Private stuff
	//
	
	private IdGroup idGroup;
	private boolean[][] clades;
}
