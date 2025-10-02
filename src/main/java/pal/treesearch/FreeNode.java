// FreeNode.java
//
// (c) 1999-2004 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.treesearch;

/**
 * <p>Title: FreeNode </p>
 * <p>Description: </p>
 * @author Matthew Goode
 * @version 1.0
 */
import java.util.*;

import pal.eval.*;
import pal.tree.*;


public interface FreeNode extends GeneralTreeComponent {
	public PatternInfo getPatternInfo(GeneralConstructionTool tool, FreeBranch caller);
	public boolean hasConnection(FreeBranch c, FreeBranch caller);
	public FreeBranch getLeftBranch(FreeBranch caller);
	public FreeBranch getRightBranch(FreeBranch caller);

//	/**
//	 * Recurse to all neighbours but caller
//	 * @return the maximum number of patterns from any neighbour
//	 */
//	public int rebuildPattern( GeneralConstructionTool tool, FreeBranch caller, boolean firstPass);
//
//	public int rebuildPattern(GeneralConstructionTool tool);

	public void getAllComponents(ArrayList store, Class componentType, FreeBranch connection);

	public void testLikelihood(FreeBranch caller, GeneralConstructionTool tool);

//	/**
//	 * This should only be called by another leaf node on the other end of the connection.
//	 * In this case we don't have to do much (tree is two node tree)
//	 */
//	public int redirectRebuildPattern(GeneralConstructionTool tool);

//	public ConditionalProbabilityStore getLeftExtendedConditionalProbabilities(FreeBranch callingConnection, UnconstrainedLikelihoodModel.External external, ConditionalProbabilityStore resultStore);
//	public ConditionalProbabilityStore getRightExtendedConditionalProbabilities( FreeBranch callingConnection, UnconstrainedLikelihoodModel.External external, ConditionalProbabilityStore resultStore);

    /**
     * Retrieves the {@code PatternInfo} object for the subtree connected by the branch
     * that is considered "left" relative to the {@code caller} branch at this node.
     * This effectively calculates the likelihood pattern looking out of the node, away from the caller.
     *
     * @param tool The construction tool used to build the pattern information.
     * @param caller The {@code FreeBranch} that initiated the call to this node (the branch towards the "parent" in the current calculation context).
     * @return The {@code PatternInfo} for the relative left subtree, or {@code null} if no such branch exists (e.g., in a leaf node or an unrooted tree's base node).
     */
    public PatternInfo getLeftPatternInfo(GeneralConstructionTool tool, FreeBranch caller);

    /**
     * Retrieves the {@code PatternInfo} object for the subtree connected by the branch
     * that is considered "right" relative to the {@code caller} branch at this node.
     * This effectively calculates the likelihood pattern looking out of the node, away from the caller.
     *
     * @param tool The construction tool used to build the pattern information.
     * @param caller The {@code FreeBranch} that initiated the call to this node (the branch towards the "parent" in the current calculation context).
     * @return The {@code PatternInfo} for the relative right subtree, or {@code null} if no such branch exists.
     */
    public PatternInfo getRightPatternInfo(GeneralConstructionTool tool, FreeBranch caller);
	public ConditionalProbabilityStore getExtendedConditionalProbabilities( double distance, FreeBranch callingConnection, GeneralConstructionTool tool);
	public ConditionalProbabilityStore getExtendedConditionalProbabilities( double distance, FreeBranch callingConnection, UnconstrainedLikelihoodModel.External external, ConditionalProbabilityStore resultStore, GeneralConstructionTool tool);

    /**
     * Instructs this node to extract itself from the tree structure by bypassing two of its connections that are not the caller branch.
     * This operation is typically performed as the "prune" step in algorithms like Subtree Pruning and Regrafting (SPR).
     *
     * One of the two non-caller connections will be made redundant and its two connected nodes will be re-connected.
     *
     * @param caller The {@code FreeBranch} that initiated the call (the connection leading toward the current root/base of the calculation context).
     * @return The {@code FreeBranch} that became redundant (bypassed) after the extraction, or {@code null} if this node cannot be extracted (e.g., if it is a leaf node or has fewer than three connections).
     */
    public FreeBranch extract(FreeBranch caller);


	public Node buildPALNodeES(double branchLength_,FreeBranch caller);
	public Node buildPALNodeBase(double branchLength_,FreeBranch caller);
	public ConditionalProbabilityStore getFlatConditionalProbabilities(FreeBranch caller, GeneralConstructionTool tool);
	public String toString(FreeBranch caller);
	public void setConnectingBranches(FreeBranch[] store, int number);
	public boolean hasDirectConnection(FreeBranch query);

    /**
     * Swaps a specified branch connected to this node with a new branch.
     * This is a low-level operation that only updates the internal list of connections
     * within *this* node.
     *
     * @param original The existing {@code FreeBranch} currently connected to this node that should be replaced.
     * @param newConnection The new {@code FreeBranch} that will take the place of the original branch in this node's connection list.
     * Note: This method only swaps the references in this node's connection list and **does not update** the connection information in the {@code original} or {@code newConnection} branches themselves.
     */
    public void swapConnection(FreeBranch original,FreeBranch newConnection);

    /**
     * Performs a complex connection swap operation, typically used to implement a Nearest Neighbour Interchange (NNI)
     * or similar topological move, ensuring the integrity of the local tree structure.
     * This method replaces an {@code original} branch connected to this node and also ensures that the new connection
     * correctly links to a specified {@code nodeToReplace} within the structure of the {@code newConnection}.
     *
     * @param original The existing {@code FreeBranch} currently connected to this node that should be replaced.
     * @param nodeToReplace The {@code FreeNode} that the {@code newConnection} should be linked to in the context of this node.
     * @param newConnection The {@code FreeBranch} that will replace the {@code original} branch and link to {@code nodeToReplace}.
     * Note: This method is designed to preserve local tree integrity by updating the necessary references in both this node and the branches involved in the swap.
     */
    public void swapConnection(FreeBranch original, FreeNode nodeToReplace, FreeBranch newConnection);
}