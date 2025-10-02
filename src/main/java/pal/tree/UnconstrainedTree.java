// UnconstrainedTree.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.tree;

import pal.misc.*;


/**
 * provides parameter interface to an unconstrained tree
 * (parameters are all available branch lengths)
 *
 * @version $Id: UnconstrainedTree.java,v 1.13 2004/04/25 22:53:14 matt Exp $
 *
 * @author Korbinian Strimmer
 * @author Alexei Drummond
 */
public class UnconstrainedTree extends ParameterizedTree.ParameterizedTreeBase  implements ParameterizedTree
{
	//
	// Public stuff
	//

    /**
     * Constructs a new {@code UnconstrainedTree} object by wrapping an existing {@code Tree} object.
     * This new object provides an interface suitable for working with the tree's parameters (all branch lengths)
     * in an unconstrained manner, typically used in optimization contexts.
     *
     * @param t The source {@code Tree} object whose topology and branch lengths will be used as the base.
     * @throws IllegalArgumentException If the root node of the input tree has fewer than three children.
     * This is required for representing a correctly unrooted tree or an unconstrained optimization problem.
     */
    public UnconstrainedTree(Tree t)
    {
        setBaseTree(t);

        if (getRoot().getChildCount() < 3)
        {
            throw new IllegalArgumentException(
                    "The root node must have at least three childs!");
        }

        // set default values
        for (int i = 0; i < getNumParameters(); i++)
        {
            setParameter(getDefaultValue(i), i);
        }
    }

	protected UnconstrainedTree(UnconstrainedTree toCopy) {
		super(toCopy);
	}
	// interface Parameterized

	public int getNumParameters()
	{
		return getInternalNodeCount()+getExternalNodeCount()-1;
	}

	public void setParameter(double param, int n)
	{
		if (n < getExternalNodeCount())
		{
			getExternalNode(n).setBranchLength(param);
		}
		else
		{
			getInternalNode(n-getExternalNodeCount()).setBranchLength(param);
		}
	}
	public String getParameterizationInfo() {
		return "Unconstrained tree";
	}
	public double getParameter(int n)
	{
		if (n < getExternalNodeCount())
		{
			return getExternalNode(n).getBranchLength();
		}
		else
		{
			return getInternalNode(n-getExternalNodeCount()).getBranchLength();
		}
	}

	public void setParameterSE(double paramSE, int n)
	{
		if (n < getExternalNodeCount())
		{
			getExternalNode(n).setBranchLengthSE(paramSE);
		}
		else
		{
			getInternalNode(n-getExternalNodeCount()).setBranchLengthSE(paramSE);
		}
	}

	public double getLowerLimit(int n)
	{
		return BranchLimits.MINARC;
	}

	public double getUpperLimit(int n)
	{
		return BranchLimits.MAXARC;
	}

	public double getDefaultValue(int n)
	{
		return BranchLimits.DEFAULT_LENGTH;
	}

	public Tree getCopy() {
		return new UnconstrainedTree(this);
	}
// ===========================================================================
// ===== Static stuff =======

    /**
     * Obtains a {@code ParameterizedTree.Factory} instance specifically configured
     * for generating {@code UnconstrainedTree} objects.
     *
     * <p>Note: This Factory implementation automatically processes input trees
     * by converting standard "rooted" topologies (with a bifurcating root)
     * into "unrooted" representations (with a multifurcating root, typically a trifurcation),
     * making them suitable for unconstrained parameter optimization.</p>
     *
     * @return A {@code ParameterizedTree.Factory} instance capable of creating {@code UnconstrainedTree} objects.
     */
    public static final ParameterizedTree.Factory getParameterizedTreeFactory() {
        return TreeFactory.DEFAULT_INSTANCE;
    }

	private static class TreeFactory implements ParameterizedTree.Factory {
		public static final ParameterizedTree.Factory DEFAULT_INSTANCE = new TreeFactory();
		/**
		 * Automatically unroots rooted trees!
		 */
		public ParameterizedTree generateNewTree(Tree base) {
			if(base.getRoot().getChildCount()==2) {
				base = new TreeManipulator(base).getUnrootedTree();
			}
			return new UnconstrainedTree(base);
		}
	}
}
