// TreeUtils.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.tree;

import pal.misc.*;
import pal.io.*;
import pal.alignment.*;
import pal.util.*;
import pal.math.*;
import pal.mep.*;
import java.io.*;
import java.util.*;


/**
 * various utility functions on trees.
 *
 * @version $Id: TreeUtils.java,v 1.51 2004/04/28 01:03:15 matt Exp $
 *
 * @author Alexei Drummond
 * @author Korbinian Strimmer
 * @author Matthew Goode
 */
public class TreeUtils
{

    /**
     * Computes the Robinson-Foulds (1981) distance between two trees.
     *
     * @param t1 The first tree (reference).
     * @param t2 The second tree.
     *
     * Definition: Assuming that t1 is the reference tree, let fn be the
     * false negatives (the number of non-trivial splits/edges in t1 missing in t2),
     * and fp the number of false positives (the number of non-trivial splits/edges
     * in t2 missing in t1). The RF distance is then (fn + fp)/2.
     * @return The Robinson-Foulds distance as an unscaled count of different splits.
     * @throws IllegalArgumentException If the trees do not have the same set of labels (taxa).
     */
    public static double getRobinsonFouldsDistance(Tree t1, Tree t2)
    {
        SplitSystem s1 = SplitUtils.getSplits(t1);

        return getRobinsonFouldsDistance(s1, t2);
    }


    /**
     * Computes the Robinson-Foulds (1981) distance between two trees, where the first tree is pre-computed as a SplitSystem.
     *
     * @param s1 The first tree (reference) represented by its SplitSystem.
     * @param t2 The second tree.
     * @return The Robinson-Foulds distance as an unscaled count of different splits.
     * @throws IllegalArgumentException If the number of labels in s1 and t2 do not match.
     */
    public static double getRobinsonFouldsDistance(SplitSystem s1, Tree t2)
    {
        IdGroup idGroup = s1.getIdGroup();
        SplitSystem s2 = SplitUtils.getSplits(idGroup, t2);

        if (s1.getLabelCount() != s2.getLabelCount())
            throw new IllegalArgumentException("Number of labels must be the same!");

        int ns1 = s1.getSplitCount();
        int ns2 = s2.getSplitCount();

        // number of splits in t1 missing in t2
        int fn = 0;
        for (int i = 0; i < ns1; i++)
        {
            if (!s2.hasSplit(s1.getSplit(i))) fn++;
        }

        // number of splits in t2 missing in t1
        int fp = 0;
        for (int i = 0; i < ns2; i++)
        {
            if (!s1.hasSplit(s2.getSplit(i))) fp++;
        }


        return 0.5*((double) fp + (double) fn);
    }

    /**
     * Computes the Robinson-Foulds (1981) distance between two trees and rescales the result
     * to a normalized number between 0 (identical) and 1 (maximally different).
     * The distance is scaled by the number of non-trivial splits in the first tree.
     *
     * @param t1 The first tree (reference).
     * @param t2 The second tree.
     * @return The rescaled Robinson-Foulds distance (normalized to 0 &lt;= d &lt;= 1).
     */
    public static double getRobinsonFouldsRescaledDistance(Tree t1, Tree t2)
    {
        SplitSystem s1 = SplitUtils.getSplits(t1);

        return getRobinsonFouldsRescaledDistance(s1, t2);
    }


    /**
     * Computes the Robinson-Foulds (1981) distance between two trees, rescaled to a number between 0 and 1.
     * The distance is scaled by the number of non-trivial splits in the first tree.
     *
     * @param s1 The first tree (reference) represented by its SplitSystem.
     * @param t2 The second tree.
     * @return The rescaled Robinson-Foulds distance (normalized to 0 &lt;= d &lt;= 1).
     */
    public static double getRobinsonFouldsRescaledDistance(SplitSystem s1, Tree t2)
    {
        return getRobinsonFouldsDistance(s1, t2)/(double) s1.getSplitCount();
    }

    private static MersenneTwisterFast random = new MersenneTwisterFast();

    /**
     * Returns a uniformly distributed random node from the tree, including
     * both internal and external (leaf) nodes.
     *
     * @param tree The tree to sample the random node from.
     * @return A randomly selected Node object.
     */
    public static Node getRandomNode(Tree tree) {
        int index = random.nextInt(tree.getExternalNodeCount() + tree.getInternalNodeCount());
        if (index >= tree.getExternalNodeCount()) {
            return tree.getInternalNode(index - tree.getExternalNodeCount());
        } else {
            return tree.getExternalNode(index);
        }
    }

    /**
     * Returns the first node found that has a certain name (as determined by the node's Identifier)
     * in the tree defined by a root node. The search starts from the tree's root.
     *
     * @param tree The Tree object supposedly containing such a named node.
     * @param name The name of the node to find.
     * @return The Node with the name, or null if no such node exists.
     * @see Identifier
     * @see Node
     */
    public static final Node getNodeByName(Tree tree, String name) {
        return getNodeByName(tree.getRoot(),name);
    }

    /**
     * Recursively searches for the first node that has a certain name (as determined by the node's Identifier)
     * in the subtree defined by the given root node.
     *
     * @param root The root node of the (sub)tree to search.
     * @param name The name of the node to find.
     * @return The Node with the name, or null if no such node exists in the subtree.
     * @see Identifier
     * @see Node
     */
    public static final Node getNodeByName(Node root, String name) {
        if(root.getIdentifier().getName().equals(name)) {
            return root;
        }
        for(int i = 0 ; i < root.getChildCount() ; i++) {
            Node result = getNodeByName(root.getChild(i), name);
            if(result!=null) {
                return result;
            }
        }
        return null;
    }

    /**
     * Takes a tree (with branch lengths in mutation units, e.g., expected substitutions per site)
     * and returns a scaled version of it (with branch lengths in generation units).
     *
     * @param mutationTree The input tree whose branch lengths are in mutation units.
     * @param muModel The MutationRateModel used for performing the scaling.
     * @return A new Tree object with branch lengths scaled to generation units.
     */
    public static Tree mutationsToGenerations(Tree mutationTree, MutationRateModel muModel) {
        return scale(mutationTree,muModel,Units.GENERATIONS);
    }

    /**
     * Takes a tree (with branch lengths in generation units) and returns a scaled version of it
     * (with branch lengths in mutation units, e.g., expected substitutions per site).
     *
     * @param generationTree The input tree whose branch lengths are in generation units.
     * @param muModel The MutationRateModel used for scaling.
     * @return A new Tree object with branch lengths scaled to mutation units.
     * @throws IllegalArgumentException If the MutationRateModel units are not Units.GENERATIONS.
     */
    public static Tree generationsToMutations(Tree generationTree, MutationRateModel muModel) {

        if (muModel.getUnits() != Units.GENERATIONS) {
            throw new IllegalArgumentException("Mutation rate must be per generation!");
        }

        return generationsToMutations(generationTree, muModel, 1.0);
    }

    /**
     * Takes a tree (with branch lengths in generation units) and returns a scaled version of it
     * (with branch lengths in mutation units, e.g., expected substitutions per site),
     * factoring in a specified generation time.
     *
     * @param generationTree The input tree whose branch lengths are in generation units.
     * @param muModel The MutationRateModel in calendar units used for scaling.
     * @param generationTime The length of a generation in calendar units. If the mutation rate is in mutations per site per year, then the
     * generation time will be in generations per year.
     * @return A new Tree object with branch lengths scaled to mutation units.
     * @throws IllegalArgumentException If the input tree is not in generation units or if the mutation rate is non-positive.
     */
    public static Tree generationsToMutations(Tree generationTree, MutationRateModel muModel, double generationTime) {

        if (generationTree.getUnits() != Units.GENERATIONS) {
            throw new IllegalArgumentException("Tree must be in units of generations!");
        }

        if (muModel.getMutationRate(0.0) <= 0.0) {
            throw new IllegalArgumentException("Non-positive mutation rate is not permitted!");
        }

        SimpleTree tree = new SimpleTree(generationTree);

        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            double oldHeight = tree.getExternalNode(i).getNodeHeight();
            tree.getExternalNode(i).setNodeHeight(muModel.getExpectedSubstitutions(oldHeight) * generationTime);
        }
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            double oldHeight = tree.getInternalNode(i).getNodeHeight();
            tree.getInternalNode(i).setNodeHeight(muModel.getExpectedSubstitutions(oldHeight) * generationTime);
        }
        //Don't respect minimum branch lengths
        NodeUtils.heights2Lengths(tree.getRoot(), false);
        tree.setUnits(Units.EXPECTED_SUBSTITUTIONS);

        return tree;
    }
    /**
     * Takes a tree and returns a scaled version of it using a constant scaling factor.
     *
     * @deprecated Use getScaled(Tree, double, int) instead.
     * @param oldTree The input tree.
     * @param rate The constant scale factor to apply to all branch lengths.
     * @param newUnits The new units for the branch lengths.
     * @return A new scaled Tree object.
     */
    public static Tree scale(Tree oldTree, double rate, int newUnits) {
        return getScaled(oldTree,rate,newUnits);
    }
    /**
     * Takes a tree and returns a scaled version of it, keeping the original units.
     *
     * @param oldTree The input tree.
     * @param rate The constant scale factor to apply to all branch lengths/node heights.
     * @return A new scaled Tree object with the same units as the original.
     */
    public static final Tree getScaled(Tree oldTree, double rate) {
        return getScaled(oldTree,rate,oldTree.getUnits());
    }

    /**
     * Takes a tree and returns a scaled version of it. Scaling is applied to node heights, and new branch lengths are calculated.
     *
     * @param oldTree The input tree.
     * @param rate The constant scale factor. If the original tree is in generations
     * and the desired units are expected substitutions then this scale
     * factor should be equal to the mutation rate.
     * @param newUnits The new units for the branch lengths.
     * @return A new scaled Tree object.
     */
    public static final Tree getScaled(Tree oldTree, double rate, int newUnits) {
        SimpleTree tree = new SimpleTree(oldTree);
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            Node n = tree.getExternalNode(i);
            n.setNodeHeight(rate*n.getNodeHeight());
        }
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            Node n = tree.getInternalNode(i);
            n.setNodeHeight(rate*n.getNodeHeight());
        }
        NodeUtils.heights2Lengths(tree.getRoot());
        tree.setUnits(newUnits);
        return tree;
    }

    /**
     * Takes a tree and returns a scaled version of it using a MutationRateModel.
     *
     * @deprecated Use getScaled(Tree, MutationRateModel) instead.
     * @param mutationRateTree The input tree.
     * @param muModel The MutationRateModel used for scaling.
     * @return A new scaled Tree object.
     */
    public static Tree scale(Tree mutationRateTree, MutationRateModel muModel) {
        return getScaled(mutationRateTree,muModel);
    }
    /**
     * Takes a tree and returns a scaled version of it, with the resulting units defined by the MutationRateModel's units.
     * Scaling is performed by transforming node heights using the model.
     *
     * @param mutationRateTree The input tree.
     * @param muModel The MutationRateModel used for transforming branch lengths/node heights.
     * @return A new scaled Tree object.
     */
    public static Tree getScaled(Tree mutationRateTree, MutationRateModel muModel) {
        return getScaled(mutationRateTree,muModel,muModel.getUnits());
    }
    /**
     * Takes a tree and returns a scaled version of it, specifying the new units.
     *
     * @deprecated Use getScaled(Tree, MutationRateModel, int) instead.
     * @param mutationRateTree The input tree.
     * @param muModel The MutationRateModel used for scaling.
     * @param newUnits The new units for the tree.
     * @return A new scaled Tree object.
     */
    public static Tree scale(Tree mutationRateTree, MutationRateModel muModel, int newUnits) {
        return getScaled(mutationRateTree,muModel,newUnits);
    }
    /**
     * Takes a tree and returns a scaled version of it, with branch lengths transformed by the MutationRateModel and assigned new units.
     *
     * @param mutationRateTree The input tree.
     * @param muModel The MutationRateModel used for transforming branch lengths/node heights.
     * @param newUnits The new units of the tree. (Such as the mutationTree is measured in expected substitutions/newUnits)
     * @return A new scaled Tree object.
     * @throws IllegalArgumentException If the mutation rate is non-positive.
     */
    public static Tree getScaled(Tree mutationRateTree, MutationRateModel muModel, int newUnits) {
        if (muModel.getMutationRate(0.0) <= 0.0) {
            throw new IllegalArgumentException("Non-positive mutation rate is not permitted!");
        }

        SimpleTree tree = new SimpleTree(mutationRateTree);
        if(newUnits == Units.EXPECTED_SUBSTITUTIONS) {
            //Changed for what I think is the correct behaviour for converting to Expected Substitutions
            for (int i = 0; i < tree.getExternalNodeCount(); i++) {
                double oldHeight = tree.getExternalNode(i).getNodeHeight();
                tree.getExternalNode(i).setNodeHeight(muModel.getExpectedSubstitutions(oldHeight));
            }
            for (int i = 0; i < tree.getInternalNodeCount(); i++) {
                double oldHeight = tree.getInternalNode(i).getNodeHeight();
                tree.getInternalNode(i).setNodeHeight(muModel.getExpectedSubstitutions(oldHeight));
            }
        } else {
            for (int i = 0; i < tree.getExternalNodeCount(); i++) {
                double oldHeight = tree.getExternalNode(i).getNodeHeight();
                tree.getExternalNode(i).setNodeHeight(muModel.getTime(oldHeight));
            }
            for (int i = 0; i < tree.getInternalNodeCount(); i++) {
                double oldHeight = tree.getInternalNode(i).getNodeHeight();
                tree.getInternalNode(i).setNodeHeight(muModel.getTime(oldHeight));
            }
        }
        NodeUtils.heights2Lengths(tree.getRoot());
        tree.setUnits(newUnits);
        return tree;
    }
    /**
     * Given a translation table where the keys are the current
     * identifier names (Strings) and the values are the new identifier names (Strings),
     * this method replaces the current identifiers of the external and internal nodes in the tree with the new names where a mapping exists.
     *
     * @param tree The tree whose node identifiers are to be renamed.
     * @param table A Hashtable mapping old node names to new node names.
     */
    public static void renameNodes(Tree tree, Hashtable table) {

        tree.createNodeList();

        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            String newName =
                    (String)table.get(tree.getExternalNode(i).getIdentifier().getName());

            if (newName != null) {
                tree.getExternalNode(i).setIdentifier(new Identifier(newName));
            }
        }
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {



            String newName =
                    (String)table.get(tree.getInternalNode(i).getIdentifier().getName());

            if (newName != null) {
                tree.getInternalNode(i).setIdentifier(new Identifier(newName));
            }
        }
    }

    /**
     * Rotates the branches of all internal nodes in the tree based on the leaf count of the subtrees,
     * primarily for visualization purposes to ensure smaller clades are on one side (e.g., left).
     *
     * @param tree The tree to be rotated.
     * Note: Assumes a strictly binary tree structure!
     */
    public static void rotateByLeafCount(Tree tree) {
        rotateByLeafCount(tree.getRoot());
    }

    /**
     * Returns an IdGroup containing the identifiers of all external nodes (leaves) in the tree,
     * in the order defined by the tree's internal list.
     *
     * @param tree The tree from which to extract the leaf identifiers.
     * @return An IdGroup object listing all leaf identifiers.
     */
    public static final IdGroup getLeafIdGroup(Tree tree)
    {
        tree.createNodeList();

        IdGroup labelList =
                new SimpleIdGroup(tree.getExternalNodeCount());

        for (int i = 0; i < tree.getExternalNodeCount(); i++)
        {
            labelList.setIdentifier(i, tree.getExternalNode(i).getIdentifier());
        }

        return labelList;
    }

    /**
     * Maps the external identifiers in the tree to their corresponding zero-based index numbers within a provided ordered IdGroup.
     *
     * @param idGroup An ordered group of identifiers that must contain all identifiers present in the tree's leaves.
     * @param tree The tree whose external identifiers are to be mapped.
     * @return An array of integers where the element at index i is the index of the i-th external tree node's identifier within the provided IdGroup.
     * @throws IllegalArgumentException If any tree label is not present in the given set of labels.
     */
    public static final int[] mapExternalIdentifiers(IdGroup idGroup, Tree tree)
            throws IllegalArgumentException {

        int[] alias = new int[tree.getExternalNodeCount()];

        // Check whether for each label in tree there is
        // a correspondence in the given set of labels
        for (int i = 0; i < tree.getExternalNodeCount(); i++)
        {
            alias[i] = idGroup.whichIdNumber(tree.getExternalNode(i).getIdentifier() .getName());

            if (alias[i] == -1)
            {
                throw new IllegalArgumentException("Tree label "
                        + tree.getExternalNode(i).getIdentifier() +
                        " not present in given set of labels");
            }
        }

        return alias;
    }

    /**
     * Labels the internal nodes of the tree using unique, sequential numbers starting from 0 (as strings),
     * ensuring that the generated numbers do not conflict with identifiers already used by external leaves.
     *
     * @param tree The tree whose internal nodes are to be labeled.
     */
    public static final void labelInternalNodes(Tree tree) {

        int counter = 0;
        String pos = "0";

        IdGroup ids = getLeafIdGroup(tree);

        for (int i = 0; i < tree.getInternalNodeCount(); i++) {

            //if label already used find a better one
            while (ids.whichIdNumber(pos) >= 0) {
                counter += 1;
                pos = "" + counter;
            }
            tree.getInternalNode(i).setIdentifier(new Identifier(pos));
            counter += 1;
            pos = "" + counter;
        }
    }

    /**
     * Extracts a TimeOrderCharacterData object from the tree, primarily containing
     * the identifiers of the leaves and their node heights (times from the root).
     *
     * @param tree The tree from which to extract the time order data.
     * @param units The units of time associated with the extracted data (typically the tree's units).
     * @return A new TimeOrderCharacterData object containing leaf identifiers and their corresponding node heights.
     */
    public static TimeOrderCharacterData extractTimeOrderCharacterData(Tree tree, int units) {

        tree.createNodeList();
        IdGroup identifiers = getLeafIdGroup(tree);
        TimeOrderCharacterData tocd = new TimeOrderCharacterData(identifiers, units);
        double[] times = new double[tree.getExternalNodeCount()];

        // WARNING: following code assumes that getLeafIdGroup
        //has same order as external node list.
        for (int i = 0; i < times.length; i++) {
            times[i] = tree.getExternalNode(i).getNodeHeight();
        }

        // this sets the ordinals as well
        tocd.setTimes(times, units);

        return tocd;
    }

    /**
     * Extracts a sequence alignment (Alignment) from the tree by collecting the sequences
     * and identifiers stored in the external nodes (leaves).
     *
     * @param tree The tree from which to extract the alignment.
     * @param leaveSeqsInTree If true, the sequences remain attached to the tree nodes; if false, the sequences are set to null on the tree nodes after extraction.
     * @return A new Alignment object containing the extracted leaf sequences and identifiers.
     */
    public static Alignment extractAlignment(Tree tree, boolean leaveSeqsInTree) {

        tree.createNodeList();
        String[] sequences = new String[tree.getExternalNodeCount()];
        Identifier[] ids = new Identifier[sequences.length];

        for (int i = 0; i < sequences.length; i++) {
            sequences[i] = new String(tree.getExternalNode(i).getSequence());
            ids[i] = tree.getExternalNode(i).getIdentifier();
            if (!leaveSeqsInTree) {
                tree.getExternalNode(i).setSequence(null);
            }
        }

        return new SimpleAlignment(ids, sequences, "-", AlignmentUtils.getSuitableInstance(sequences));
    }

    /**
     * Extracts a sequence alignment (Alignment) from the tree, leaving the original sequences in the tree nodes.
     * This is a convenience method equivalent to calling extractAlignment(tree, true).
     *
     * @param tree The tree from which to extract the alignment.
     * @return A new Alignment object containing the extracted leaf sequences and identifiers.
     */
    public static Alignment extractAlignment(Tree tree) {
        return extractAlignment(tree, true);
    }


    /**
     * Prints the tree to an output stream in New Hampshire (Newick) format, including both
     * branch lengths and internal node labels.
     *
     * @param tree The tree to be printed.
     * @param out The PrintWriter output stream.
     */
    public static void printNH(Tree tree, PrintWriter out) {
        printNH(tree, out, true, true);
    }

    /**
     * Prints the tree to an output stream in New Hampshire (Newick) format, with options to include
     * branch lengths and internal node labels.
     *
     * @param tree The tree to be printed.
     * @param out The PrintWriter output stream.
     * @param printLengths A boolean variable determining whether branch lengths should be included in output.
     * @param printInternalLabels A boolean variable determining whether internal labels should be included in output.
     */
    public static void printNH(Tree tree, PrintWriter out,
                               boolean printLengths, boolean printInternalLabels) {

        NodeUtils.printNH(out, tree.getRoot(),
                printLengths, printInternalLabels);
        out.println(";");
    }

	/**
	 * Roots a tree (that was previously unroot - ie 3 or more children at the
	 * compsci tree root)
	 * @param outgroupMembers the names of the nodes that form the outgroup.
	 *      Multiple nodes will make the clade covering all outgroup nodes (and
	 *      any others that fall with in that clade) form the outgroup.
	 * Note: if none of the outgroup members are actually in the tree, or the outgroup clade is
	 * the whole tree, the result is just an unrooted clone of the input tree.
	 */
//	public static final Tree getRooted(Tree unrooted, String[] outgroupMembers) {
//		Tree t2 = new SimpleTree(unrooted);
//		Node[] nodes = NodeUtils.findByIdentifier(t2.getRoot(),outgroupMembers);
//		if(nodes==null) {
//			return t2;
//		}
//		Node common = (nodes.length==1 ? nodes[0] : NodeUtils.getFirstCommonAncestor(nodes));
//		if(common==null) {	return t2;	}
//		if(common==t2.getRoot()) { return t2; }
//		//TreeUtils.reroot(t2,common);
//		Node newRoot = NodeUtils.rootAbove(common);
//		NodeUtils.exchangeInfo(newRoot,t2.getRoot());
//		NodeUtils.lengths2Heights(newRoot);
//		t2.setRoot(newRoot);
//
//		return t2;
//	}

    /**
     * Reroots the given tree such that the provided node becomes the new root node.
     * This operation modifies the tree in place.
     *
     * @param tree The Tree object to be re-rooted.
     * @param node The Node (either internal or external) that should become the new root of the tree.
     */
    public static void reroot(Tree tree, Node node) {
        reroot(node);
        tree.setRoot(node);
    }

	/*
	 * compute distance of external node a to all other leaves
	 * (computational complexity of this method is only O(n), following
	 * D.Bryant and P. Wadell. 1998. MBE 15:1346-1359)
	 *
	 * @param tree tree
	 * @param a node
	 * @param dist array for the node-to-node distance distances
	 * @param idist array for the distance between a and all internal nodes
	 * @param countEdges boolean variable deciding whether the actual
	 *                   branch lengths are used in computing the distance
	 *                   or whether simply all edges larger or equal a certain
	 *                   threshold length are counted (each with weight 1.0)
	 * @param epsilon    minimum branch length for a which an edge is counted
	 */
	public static void computeAllDistances(Tree tree,
		int a, double[] dist, double[] idist,
		boolean countEdges, double epsilon)
	{
		tree.createNodeList();

		dist[a] = 0.0;

		Node node = tree.getExternalNode(a);

		computeNodeDist(node, node.getParent(), dist, idist, countEdges, epsilon);
	}

	private static void computeNodeDist(Node origin, Node center,
		double[] dist, double[] idist,
		boolean countEdges, double epsilon)
	{
		int indexCenter = center.getNumber();
		int indexOrigin = origin.getNumber();
		double[] distCenter;
		double[] distOrigin;
		if (center.isLeaf()) distCenter = dist;
		else distCenter = idist;
		if (origin.isLeaf()) distOrigin = dist;
		else distOrigin = idist;

		double len;
		double tmp;
		if (origin.getParent() == center)
		{
			// center is parent of origin
			tmp = origin.getBranchLength();
		}
		else
		{
			// center is child of origin
			tmp = center.getBranchLength();
		}


		if (countEdges) // count all edges >= epsilon
		{
			if (tmp < epsilon)
			{
				len = 0.0;
			}
			else
			{
				len = 1.0;
			}
		}
		else // use branch lengths
		{
			len = tmp;
		}


		distCenter[indexCenter] = distOrigin[indexOrigin] + len;

		if (!center.isLeaf())
		{
			for (int i = 0; i < center.getChildCount(); i++)
			{
				Node c = center.getChild(i);

				if (c != origin) computeNodeDist(center, c, dist, idist, countEdges, epsilon);
			}

			if (!center.isRoot())
			{
				Node p = center.getParent();

				if (p != origin) computeNodeDist(center,p, dist, idist, countEdges, epsilon);
			}
		}
	}


	private static Node[] path;

	/**
	 * compute distance between two external nodes
	 *
	 * @param tree tree
	 * @param a external node 1
	 * @param b external node 2
	 *
	 * @return distance between node a and b
	 */
	public static final double computeDistance(Tree tree, int a, int b)
	{
		tree.createNodeList();
		int maxLen = tree.getInternalNodeCount()+1;
		if (path == null || path.length < maxLen)
		{
			path = new Node[maxLen];
		}

		// len might be different from path.length
		int len = findPath(tree, a, b);

		double dist = 0.0;
		for (int i = 0; i < len; i++)
		{
			dist += path[i].getBranchLength();
		}

		return dist;
	}

	// Find path between external nodes a and b
	// After calling this method path contains all nodes
	// with edges lying between a and b (including a and b)
	// (note that the node lying on the intersection of a-root
	// and b-root is NOT contained because this node does
	// not contain a branch of the path)
	// The length of the path is also returned
	private static final int findPath(Tree tree, int a, int b)
	{
		// clean path
		for (int i = 0; i < path.length; i++)
		{
			path[i] = null;
		}
		// path from node a to root
		Node node = tree.getExternalNode(a);
		int len = 0;
		path[len] = node;
		len++;
		while (!node.isRoot())
		{
			node = node.getParent();
			path[len] = node;
			len++;
		}

		// find intersection with path from node b to root
		Node stopNode = null;
		node = tree.getExternalNode(b);
		while (!node.isRoot())
		{
			node = node.getParent();
			int pos = findInPath(node);

			if (pos != -1)
			{
				len = pos;
				stopNode = node;
				break;
			}
		}

		// fill rest of path
		node = tree.getExternalNode(b);
		path[len] = node;
		len++;
		node = node.getParent();
		while (node != stopNode)
		{
			path[len] = node;
			len++;
			node = node.getParent();
		}

		// clean rest
		for (int i = len; i < path.length; i++)
		{
			path[i] = null;
		}

		return len;
	}

	private static final int findInPath(Node node)
	{
		for (int i = 0; i < path.length; i++)
		{
			if (path[i] == node)
			{
				return i;
			}
			else if (path[i] == null)
			{
				return -1;
			}
		}

		return -1;
	}

	/**
	 * Rotates branches by leaf count.
	 * WARNING: assumes binary tree!
	 */
	private static void rotateByLeafCount(Node node) {

		if (!node.isLeaf()) {
			if (NodeUtils.getLeafCount(node.getChild(0)) >
				NodeUtils.getLeafCount(node.getChild(1))) {
				Node temp = node.getChild(0);
				node.removeChild(0);
				node.addChild(temp);
			}
			for (int i = 0; i < node.getChildCount(); i++) {
				rotateByLeafCount(node.getChild(i));
			}
		}
	}

	public static void report(Tree tree, PrintWriter out)
	{
		printASCII(tree, out);
		out.println();
		branchInfo(tree, out);
		out.println();
		heightInfo(tree, out);
	}


	private static FormattedOutput format;

	private static double proportion;
	private static int minLength;
	private static boolean[] umbrella;
	private static int[] position;

	private static int numExternalNodes;
	private static int numInternalNodes;
	private static int numBranches;


	// Print picture of current tree in ASCII
	private static void printASCII(Tree tree, PrintWriter out)
	{
		format = FormattedOutput.getInstance();

		tree.createNodeList();

		numExternalNodes = tree.getExternalNodeCount();
		numInternalNodes = tree.getInternalNodeCount();
		numBranches = numInternalNodes+numExternalNodes-1;

		umbrella = new boolean[numExternalNodes];
		position = new int[numExternalNodes];

		minLength = (Integer.toString(numBranches)).length() + 1;

		int MAXCOLUMN = 40;
		Node root = tree.getRoot();
		if (root.getNodeHeight() == 0.0) {
			NodeUtils.lengths2Heights(root);
		}
		proportion = (double) MAXCOLUMN/root.getNodeHeight();

		for (int n = 0; n < numExternalNodes; n++)
		{
			umbrella[n] = false;
		}

		position[0] = 1;
		for (int i = root.getChildCount()-1; i > -1; i--)
		{
			printNodeInASCII(out, root.getChild(i), 1, i, root.getChildCount());
			if (i != 0)
			{
				putCharAtLevel(out, 0, '|');
				out.println();
			}
		}
	}

	// Print branch information
	private static void branchInfo(Tree tree, PrintWriter out)
	{

		//
		// CALL PRINTASCII FIRST !!!
		//

		// check if some SE values differ from the default zero
		boolean showSE = false;
		for (int i = 0; i < numExternalNodes && showSE == false; i++)
		{
			if (tree.getExternalNode(i).getBranchLengthSE() != 0.0)
			{
				showSE = true;
			}
			if (i < numInternalNodes-1)
			{
				if (tree.getInternalNode(i).getBranchLengthSE() != 0.0)
				{
					showSE = true;
				}
			}
		}

		format.displayIntegerWhite(out, numExternalNodes);
		out.print("   Length    ");
		if (showSE) out.print("S.E.      ");
		out.print("Label     ");
		if (numInternalNodes > 1)
		{
			format.displayIntegerWhite(out, numBranches);
			out.print("        Length    ");
			if (showSE) out.print("S.E.      ");
			out.print("Label");
		}
		out.println();

		for (int i = 0; i < numExternalNodes; i++)
		{
			format.displayInteger(out, i+1, numExternalNodes);
			out.print("   ");
			format.displayDecimal(out, tree.getExternalNode(i).getBranchLength(), 5);
			out.print("   ");
			if (showSE)
			{
				format.displayDecimal(out, tree.getExternalNode(i).getBranchLengthSE(), 5);
				out.print("   ");
			}
			format.displayLabel(out, tree.getExternalNode(i).getIdentifier().getName(), 10);

			if (i < numInternalNodes-1)
			{
				format.multiplePrint(out, ' ', 5);
				format.displayInteger(out, i+1+numExternalNodes, numBranches);
				out.print("   ");
				format.displayDecimal(out, tree.getInternalNode(i).getBranchLength(), 5);
				out.print("   ");
				if (showSE)
				{
					format.displayDecimal(out, tree.getInternalNode(i).getBranchLengthSE(), 5);
					out.print("   ");
				}
				format.displayLabel(out, tree.getInternalNode(i).getIdentifier().getName(), 10);
			}

			out.println();
		}
	}


	// Print height information
	private static void heightInfo(Tree tree, PrintWriter out)
	{
		//
		// CALL PRINTASCII FIRST
		//

		if (tree.getRoot().getNodeHeight() == 0.0) {
			NodeUtils.lengths2Heights(tree.getRoot());
		}

		// check if some SE values differ from the default zero
		//boolean showSE = false;
		//for (int i = 0; i < numInternalNodes && showSE == false; i++)
		//{
		//	if (tree.getInternalNode(i).getNodeHeightSE() != 0.0)
		//	{
		//		showSE = true;
		//	}
		//}

		format.displayIntegerWhite(out, numExternalNodes);
		out.print("   Height    ");
		format.displayIntegerWhite(out, numBranches);
		out.print("        Height    ");
		//if (showSE) out.print("S.E.");

		out.println();

		for (int i = 0; i < numExternalNodes; i++)
		{
			format.displayInteger(out, i+1, numExternalNodes);
			out.print("   ");
			format.displayDecimal(out, tree.getExternalNode(i).getNodeHeight(), 7);
			out.print("   ");

			if (i < numInternalNodes)
			{
				format.multiplePrint(out, ' ', 5);

				if (i == numInternalNodes-1)
				{
					out.print("R");
					format.multiplePrint(out, ' ', Integer.toString(numBranches).length()-1);
				}
				else
				{
					format.displayInteger(out, i+1+numExternalNodes, numBranches);
				}

				out.print("   ");
				format.displayDecimal(out, tree.getInternalNode(i).getNodeHeight(), 7);
				out.print("   ");
				//if (showSE)
				//{
				//	format.displayDecimal(out, tree.getInternalNode(i).getNodeHeightSE(), 7);
				//}
			}

			out.println();
		}
	}

	private static void printNodeInASCII(PrintWriter out, Node node, int level, int m, int maxm)
	{
		position[level] = (int) (node.getBranchLength()*proportion);

		if (position[level] < minLength)
		{
			position[level] = minLength;
		}

		if (node.isLeaf()) // external branch
		{
			if (m == maxm-1)
			{
				umbrella[level-1] = true;
			}

			printlnNodeWithNumberAndLabel(out, node, level);

			if (m == 0)
			{
				umbrella[level-1] = false;
			}
		}
		else // internal branch
		{
			for (int n = node.getChildCount()-1; n > -1; n--)
			{
				printNodeInASCII(out, node.getChild(n), level+1, n, node.getChildCount());

				if (m == maxm-1 && n == node.getChildCount()/2)
				{
					umbrella[level-1] = true;
				}

				if (n != 0)
				{
					if (n == node.getChildCount()/2)
					{
						printlnNodeWithNumberAndLabel(out, node, level);
					}
					else
					{
						for (int i = 0; i < level+1; i++)
						{
							if (umbrella[i])
							{
								putCharAtLevel(out, i, '|');
							}
							else
							{
								putCharAtLevel(out, i, ' ');
							}
						}
						out.println();
					}
				}

				if (m == 0 && n == node.getChildCount()/2)
				{
					umbrella[level-1] = false;
				}
			}
		}
	}

	private static void printlnNodeWithNumberAndLabel(PrintWriter out, Node node, int level)
	{
		for (int i = 0; i < level-1; i++)
		{
			if (umbrella[i])
			{
				putCharAtLevel(out, i, '|');
			}
			else
			{
				putCharAtLevel(out, i, ' ');
			}
		}

		putCharAtLevel(out, level-1, '+');

		int branchNumber;
		if (node.isLeaf())
		{
			branchNumber = node.getNumber()+1;
		}
		else
		{
			branchNumber = node.getNumber()+1+numExternalNodes;
		}

		String numberAsString = Integer.toString(branchNumber);

		int numDashs = position[level]-numberAsString.length();
		for (int i = 0; i < numDashs; i++)
		{
			out.print('-');
		}
		out.print(numberAsString);

		if (node.isLeaf())
		{
			out.println(" " + node.getIdentifier());
		}
		else
		{
			if (!node.getIdentifier().equals(Identifier.ANONYMOUS))
			{
				out.print("(" + node.getIdentifier() + ")");
			}
			out.println();
		}
	}

	private static void putCharAtLevel(PrintWriter out, int level, char c)
	{
		int n = position[level]-1;
		for (int i = 0; i < n; i++)
		{
			out.print(' ');
		}
		out.print(c);
	}

	/**
	 * make given node the root node
	 *
	 * @param node new root node
	 */
	private static void reroot(Node node)
	{
		if (node.isRoot() || node.isLeaf())
		{
			return;
		}

		if (!node.getParent().isRoot())
		{
			reroot(node.getParent());
		}

		// Now the parent of node is root

		if (node.getParent().getChildCount() < 3)
		{
			// Rerooting not possible
			return;
		}

		// Exchange branch label, length et cetera
		NodeUtils.exchangeInfo(node.getParent(), node);

		// Rearrange topology
		Node parent = node.getParent();
		NodeUtils.removeChild(parent, node);
		node.addChild(parent);
	}

    /**
     * Generates a tree which is identical to baseTree but has attributes (defined by attributeName)
     * at all internal nodes excluding the root node signifying (as a value between 0 and 100) the bootstrap
     * support by clade (that is the proportion of replicates that produce the sub clade under that node).
     *
     * <p>Note: This method assumes all alternative trees have the exact same set of labels (taxa) as the baseTree.</p>
     *
     * @param attributeName The name of the attribute to store the bootstrap support value (0-100).
     * @param baseTree The reference tree whose topology is to be analyzed.
     * @param alternativeTrees An array of bootstrap replicate trees used to calculate support.
     * @return A new Tree object identical to baseTree but with bootstrap support attributes added to internal nodes.
     * @deprecated Use {@link #getReplicateCladeSupport(String, Tree, TreeGenerator, int, AlgorithmCallback)} instead.
     */
    public static final Tree getBootstrapSupportByCladeTree(String attributeName, Tree baseTree, Tree[] alternativeTrees) {
        SimpleTree result = new SimpleTree(baseTree);
        IdGroup ids = TreeUtils.getLeafIdGroup(baseTree);
        SplitSystem baseSystem = SplitUtils.getSplits(ids,baseTree);
        boolean[][] baseVector = baseSystem.getSplitVector();
        int[] supportCount = new int[baseVector.length];
        for(int i = 0 ; i < alternativeTrees.length ; i++) {
            SplitSystem alternativeSystem = SplitUtils.getSplits(ids, alternativeTrees[i]);
            for(int j = 0 ; j < baseVector.length ; j++) {
                if(alternativeSystem.hasSplit(baseVector[j])) {
                    supportCount[j]++;
                }
            }
        }
        for(int i = 0 ; i < supportCount.length ; i++) {
            int support = (int)(supportCount[i]*100/(double)alternativeTrees.length);
            result.setAttribute(
                    result.getInternalNode(i),
                    attributeName,
                    new Integer(support)
            );
        }
        return result;
    }

    /**
     * Generates a tree which is identical to baseTree but has attributes (defined by attributeName)
     * at all internal nodes excluding the root node signifying (as a value between 0 and 100) the replicate
     * support by clade (that is the proportion of replicates that produce the sub clade under that node).
     * This method uses a {@code TreeGenerator} to process replicates sequentially, which can save memory
     * compared to loading all replicate trees into an array.
     *
     * @param attributeName The name attached to the attribute which holds the clade support value.
     * @param baseTree The base tree whose splits are checked for support.
     * @param treeGenerator The source of the replicate trees. For bootstrap analysis, this would typically be an iterator that builds trees based on bootstrap alignments.
     * @param numberOfReplicates The total number of replicates to extract from the TreeGenerator for
     * use in calculating clade support (excluding the base tree).
     * @param callback An AlgorithmCallback object for monitoring progress and allowing early stopping.
     * @return A new Tree object identical to baseTree but with replicate support attributes added to internal nodes. Returns the original baseTree with no annotation if the callback requests an early stop.
     * @see pal.gui.TreePainter#BOOTSTRAP_ATTRIBUTE_NAME
     */
    public static final Tree getReplicateCladeSupport(final String attributeName, final Tree baseTree, final TreeGenerator treeGenerator, final int numberOfReplicates, final AlgorithmCallback callback) {
        SimpleTree result = new SimpleTree(baseTree);
        IdGroup ids = TreeUtils.getLeafIdGroup(baseTree);
        SplitSystem baseSystem = SplitUtils.getSplits(ids,baseTree);
        boolean[][] baseVector = baseSystem.getSplitVector();
        int[] supportCount = new int[baseVector.length];
        for(int i = 0 ; i < numberOfReplicates ; i++) {
            Tree replicateTree = treeGenerator.getNextTree(
                    AlgorithmCallback.Utils.getSubCallback(callback,"Replicate:"+i,i/(double)(numberOfReplicates+1),(i+1)/(double)(numberOfReplicates+1))
            );
            if(callback.isPleaseStop()) {
                return baseTree;
            }
            SplitSystem alternativeSystem =    SplitUtils.getSplits(ids,replicateTree);
            for(int j = 0 ; j < baseVector.length ; j++) {
                if(alternativeSystem.hasSplit(baseVector[j])) {       supportCount[j]++;       }
            }
        }
        for(int i = 0 ; i < supportCount.length ; i++) {
            int support = (int)(supportCount[i]*100/(double)numberOfReplicates);
            result.setAttribute(
                    result.getInternalNode(i),
                    attributeName,
                    new Integer(support)
            );
        }
        return result;
    }

    /**
     * Creates a new tree where the leaf labels are redefined (re-indexed) based on a mapping,
     * assuming that the base tree's labels are numbered integers.
     *
     * <ol>
     * <li> If the base label is not a parseable number, the new label is the original label.</li>
     * <li> If the base label is a number, it is treated as an index (offset by the minimum index found)
     * to look up the new label in the provided set of identifiers ({@code ids}).</li>
     * </ol>
     *
     * @param baseTree The base tree to be relabelled.
     * @param ids The set of identifiers used for relabeling the numbered leaves.
     * @return A new relabelled Tree object, or the input baseTree if no numbered leaves were found or changed.
     */
    public static final Tree getNumberRelabelledTree(Tree baseTree, IdGroup ids) {
        LabelMapping lm = new LabelMapping();
        int minIndex = Integer.MAX_VALUE;
        for(int i = 0 ; i < baseTree.getIdCount() ; i++) {
            String treeLabel = baseTree.getIdentifier(i).getName();
            try {
                int number = Integer.parseInt(treeLabel);
                if(number<minIndex) {
                    minIndex = number;
                }
            }catch(NumberFormatException e) {  }
        }
        int changes = 0;
        for(int i = 0 ; i < baseTree.getIdCount() ; i++) {
            String treeLabel = baseTree.getIdentifier(i).getName();
            try {
                int number = Integer.parseInt(treeLabel)-minIndex;
                if(number<ids.getIdCount()) {
                    lm.addMapping(treeLabel,ids.getIdentifier(number).getName());
                    changes++;
                }
            }catch(NumberFormatException e) {

            }
        }
        if(changes==0) { return baseTree; }
        return new SimpleTree(baseTree,lm);
    }
}
