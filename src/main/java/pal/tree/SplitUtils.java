// SplitUtils.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.tree;

import pal.misc.*;

/**
 * utilities for split systems
 *
 * @version $Id: SplitUtils.java,v 1.6 2002/06/05 23:23:14 matt Exp $
 *
 * @author Korbinian Strimmer
 */
public class SplitUtils
{
	//
	// Public stuff
	//

    /**
     * Creates a {@code SplitSystem} (a collection of splits) from a rooted tree.
     * The order of labels (sequences) in the resulting split vectors is determined by the provided {@code IdGroup}.
     *
     * @param idGroup The {@code IdGroup} defining the predefined order of taxa/sequences for the split matrix columns.
     * @param tree The {@code Tree} object from which the splits are derived.
     * @return A new {@code SplitSystem} containing all non-trivial splits induced by the internal branches of the tree, ordered according to {@code idGroup}.
     */
    public static SplitSystem getSplits(IdGroup idGroup, Tree tree)
    {
        int size = tree.getInternalNodeCount()-1;
        SplitSystem splitSystem = new SplitSystem(idGroup, size);

        boolean[][] splits = splitSystem.getSplitVector();

        for (int i = 0; i < size; i++)
        {
            getSplit(idGroup, tree.getInternalNode(i), splits[i]);
        }


        return splitSystem;
    }



    /**
     * Creates a {@code SplitSystem} from a rooted tree.
     * The order of labels (sequences) in the resulting split vectors is determined by the natural leaf order of the tree.
     *
     * @param tree The {@code Tree} object from which the splits are derived.
     * @return A new {@code SplitSystem} containing all non-trivial splits induced by the internal branches of the tree.
     */
    public static SplitSystem getSplits(Tree tree)
    {
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);

        return getSplits(idGroup, tree);
    }



    /**
     * Populates a boolean array representing the split induced by the branch leading to a given internal node.
     * The split partitions the leaves of the tree into two sets: those descended from the node and all others.
     *
     * @param idGroup The {@code IdGroup} that defines the target order of labels (columns) for the {@code split} array.
     * @param internalNode The internal node (excluding the root) defining the branch that generates the split.
     * @param split The pre-allocated boolean array to be filled with the split information. It must have a length equal to the number of labels in {@code idGroup}.
     * @throws IllegalArgumentException If the node is a leaf or the root of the tree.
     */
    public static void getSplit(IdGroup idGroup, Node internalNode, boolean[] split)
    {
        if (internalNode.isLeaf() || internalNode.isRoot())
        {
            throw new IllegalArgumentException("Only internal nodes (and no root) nodes allowed");
        }

        // make sure split is reset
        for (int i = 0; i < split.length; i++)
        {
            split[i] = false;
        }

        // mark all leafs downstream of the node

        for (int i = 0; i < internalNode.getChildCount(); i++)
        {
            markNode(idGroup, internalNode, split);
        }

        // standardize split (i.e. first index is alway true)
        if (split[0] == false)
        {
            for (int i = 0; i < split.length; i++)
            {
                if (split[i] == false)
                    split[i] = true;
                else
                    split[i] = false;
            }
        }
    }

    /**
     * Checks whether two splits are identical, allowing for one split to be the exact reverse
     * (complement) of the other, but assuming they use the same leaf order.
     *
     * @param s1 The first split (boolean array).
     * @param s2 The second split (boolean array).
     * @return {@code true} if the two splits are identical or complements of each other; otherwise, {@code false}.
     * @throws IllegalArgumentException If the two split arrays do not have the same length.
     */
    public static boolean isSame(boolean[] s1, boolean[] s2)
    {
        boolean reverse;
        if (s1[0] == s2[0]) reverse = false;
        else reverse = true;

        if (s1.length != s2.length)
            throw new IllegalArgumentException("Splits must be of the same length!");

        for (int i = 0; i < s1.length; i++)
        {
            if (reverse)
            {
                // splits not identical
                if (s1[i] == s2[i]) return false;
            }
            else
            {
                // splits not identical
                if (s1[i] != s2[i]) return false;
            }
        }

        return true;
    }

	//
	// Package stuff
	//

	static void markNode(IdGroup idGroup, Node node, boolean[] split)
	{
		if (node.isLeaf())
		{
			String name = node.getIdentifier().getName();
			int index = idGroup.whichIdNumber(name);

			if (index < 0)
			{
				throw new IllegalArgumentException("INCOMPATIBLE IDENTIFIER (" + name + ")");
			}

			split[index] = true;
		}
		else
		{
			for (int i = 0; i < node.getChildCount(); i++)
			{
				markNode(idGroup, node.getChild(i), split);
			}
		}
	}

}
