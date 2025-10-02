// TreeTool.java
//
// (c) 1999-2003 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.tree;

import pal.misc.*;
import pal.io.*;

import java.io.*;
import java.util.*;
import pal.distance.*;

/**
 * Simple access for tree functions. The purpose of this class is to provide a set
 * interface for doing basic tree operations, and for example code.
 *
 * <b>History</b>
 * <ul>
 *  <li> 15/09/2003 - Created </li>
 * </ul>
 *
 * @version $Id: TreeTool.java,v 1.4 2004/04/25 22:53:14 matt Exp $
 *
 * @author Matthew Goode
 *
 */
import java.io.*;

public final class TreeTool  {
	/**
	 * Read a tree from an input source. Currently only understands the Newick format
	 * @param r A reader object (is not closed)
	 * @return A tree
	 * @throws IOException if there was a problem
	 */
	public final static Tree readTree(Reader r) throws IOException {
		try {
			return new ReadTree(new PushbackReader(r));
		} catch(TreeParseException e) {
			throw new IOException("Parse exception:"+e);
		}
	}
	/**
	 * Neighbour-joining tree construction based on a distance matrix
	 * @param dm The related DistanceMatrix object
	 * @return A tree formed by the neighbour-joining process using the input distance matrix
	 */
	public static final Tree createNeighbourJoiningTree(DistanceMatrix dm) {
	  return new NeighborJoiningTree(dm);
	}
	/**
	 * UPGMA tree construction based on a distance matrix
	 * @param dm The related DistanceMatrix object
	 * @return A tree formed by the UPGMA process using the input distance matrix
	 */
	public static final Tree createUPGMA(DistanceMatrix dm) {
	  return new UPGMATree(dm);
	}


	/**
	 * Neighbour-joining tree construction based on a distance matrix
	 * @param dm A matrix of doubles that forms the distance matrix. It is assumed this matrix is perfectly square and the diagonals match
	 * @param otuNames The list of operational taxonimic units that match the column/rows of the distance matrix.
	 * @return A tree formed by the neighbour-joining process using the input distance matrix
	 */
	public static final Tree createNeighbourJoiningTree(double[][] dm, String[] otuNames) {
	  return new NeighborJoiningTree(new DistanceMatrix(dm, new SimpleIdGroup(otuNames)));
	}
	/**
	 * UPGMA tree construction based on a distance matrix
	 * @param dm A matrix of doubles that forms the distance matrix. It is assumed this matrix is perfectly square and the diagonals match
	 * @param otuNames The list of operational taxonimic units that match the column/rows of the distance matrix.
	 * @return A tree formed by the neighbour-joining process using the input distance matrix
	 */
	public static final Tree createUPGMATree(double[][] dm, String[] otuNames) {
	  return new UPGMATree(new DistanceMatrix(dm, new SimpleIdGroup(otuNames)));
	}
    /**
     * Unroots a tree by removing the root, resulting in a **multifurcation** (typically a trifurcation)
     * at the base of the returned tree. The sum of all branch lengths is conserved.
     *
     * @param t The input tree, which may be rooted or already unrooted.
     * @return A new {@code Tree} object representing the unrooted topology.
     * @see TreeManipulator
     */
    public static final Tree getUnrooted(Tree t) {
        return TreeManipulator.getUnrooted(t);
    }
    /**
     * Roots a tree using the **midpoint rooting** method, which places the new root
     * halfway along the longest path between any two terminal taxa. The sum of all branch lengths is conserved.
     *
     * @param t The input tree, which may be unrooted or rooted.
     * @return A new {@code Tree} object rooted at the midpoint.
     * @see TreeManipulator
     */
    public static final Tree getMidPointRooted(Tree t) {
        return TreeManipulator.getMidpointRooted(t);
    }
    /**
     * Roots a tree using an **outgroup** defined by a set of taxon names. The tree is rooted
     * on the branch leading to the Most Recent Common Ancestor (MRCA) of the specified outgroup members.
     * The sum of all branch lengths is conserved.
     *
     * @param t The input tree, which may be unrooted or rooted.
     * @param outgroupMembers The names of the outgroup members (must be at least one). The clade containing
     * all specified members is used to determine the rooting point.
     * @return A new {@code Tree} object rooted by the determined outgroup.
     * @see TreeManipulator
     */
    public static final Tree getRooted(Tree t, String[] outgroupMembers) {
        return TreeManipulator.getRootedBy(t,outgroupMembers);
    }
}
