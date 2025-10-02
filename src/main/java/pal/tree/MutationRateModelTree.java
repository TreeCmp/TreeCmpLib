// MutationRateModelTree.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)
//

package pal.tree;

import pal.misc.*;
import pal.mep.*;
import pal.math.OrthogonalHints;
import pal.math.OrderEnumerator;


import pal.util.*;

/**
 * Provides parameter interface to any clock-like tree with
 * serially sampled tips (parameters are the minimal node height differences
 * at each internal node). Any mutation rate model can be used. <P>
 * @see pal.mep.MutationRateModel
 *
 * @version $Id: MutationRateModelTree.java,v 1.19 2003/06/04 03:17:52 matt Exp $
 *
 * @author Alexei Drummond
 */
public class MutationRateModelTree extends ParameterizedTree.ParameterizedTreeBase implements OrthogonalHints, ParameterizedTree {

	//
	// Public stuff
	//

	TimeOrderCharacterData tocd = null;
	MutationRateModel model = null;
	int numParameters;
	double maxRelativeHeight_ = BranchLimits.MAXARC;

	//
	// Private stuff
	//

	private double[] parameter;
	private double lnL = 0.0;

	private final static double MIN_MU = 1e-12;
	private final static double MIN_DELTA = 1e-12;
    /**
     * Constructs a clock-like tree interface based on a provided tree, time data, and mutation rate model.
     * The parameters of this object include the minimal node height differences at each internal node,
     * and the parameters of the mutation rate model are included by default.
     *
     * <p>
     * <em>This parameterisation of a clock-tree, ensuring that
     * all parameters are independent of each other is due to
     * Andrew Rambaut (personal communication).</em>
     * </p>
     *
     * @param t The base {@code Tree} to be parameterized.
     * @param tocd The {@code TimeOrderCharacterData} containing the sampling times needed for clock-like analysis.
     * @param model The {@code MutationRateModel} defining the evolutionary rate and its parameters.
     */
    public MutationRateModelTree(Tree t, TimeOrderCharacterData tocd, MutationRateModel model)  {
        this(t,tocd, model,true);
    }

    /**
     * Constructs a clock-like tree interface based on a provided tree, time data, and mutation rate model,
     * with an option to include the model's parameters in the tree's parameter set.
     * The tree parameters represent the minimal node height differences at each internal node.
     *
     * <p>
     * <em>This parameterisation of a clock-tree, ensuring that
     * all parameters are independent of each other is due to
     * Andrew Rambaut (personal communication).</em>
     * </p>
     *
     * @param t The base {@code Tree} to be parameterized.
     * @param tocd The {@code TimeOrderCharacterData} containing the sampling times needed for clock-like analysis.
     * @param model The {@code MutationRateModel} defining the evolutionary rate and its parameters.
     * @param includeModelParameters If {@code true}, the parameters of the {@code MutationRateModel} are included in this object's parameter list.
     * @throws RuntimeException if the root node has fewer than two children or if the {@code tocd} does not contain time information.
     */
    public MutationRateModelTree(Tree t, TimeOrderCharacterData tocd, MutationRateModel model, boolean includeModelParameters)  {

        setBaseTree(t);

        this.tocd = tocd;
        this.model = model;

        if (t.getRoot().getChildCount() < 2) {
            throw new RuntimeException(
                    "The root node must have at least two childs!");
        }

        NodeUtils.heights2Lengths(getRoot());

        numParameters = getInternalNodeCount();
        if(includeModelParameters) {numParameters+= model.getNumParameters(); }

        if (!tocd.hasTimes()) {
            throw new RuntimeException("Must have times!");
        }

        parameter = new double[getInternalNodeCount()];
        heights2parameters();
    }

    /**
     * Cloning constructor that performs a deep copy of the tree structure and a clone of the mutation rate model.
     *
     * @param toCopy The {@code MutationRateModelTree} instance to be cloned.
     */
    protected MutationRateModelTree(MutationRateModelTree toCopy ){
        this.tocd = toCopy.tocd;
        this.model = (MutationRateModel)toCopy.model.clone();
        this.parameter = pal.misc.Utils.getCopy(toCopy.parameter);
        this.lnL = toCopy.lnL;
        this.numParameters = toCopy.numParameters;
        parameters2Heights();
        NodeUtils.heights2Lengths(getRoot());
    }

    /**
     * Sets the maximum allowed relative height difference between an ancestor node and its latest descendant.
     * This value is typically used to constrain tree heights during optimization.
     *
     * @param value The new maximum relative height value (default is often around 1.0, based on {@code BranchLimits.MAX_ARC}).
     */
    public void setMaxRelativeHeight(double value) {
        this.maxRelativeHeight_ = value;
    }
    // interface Parameterized

	public int getNumParameters() {
		return numParameters;
	}

	public void setParameter(double param, int n) {

		if (n < getInternalNodeCount()) {
			parameter[n] = param;
		} else model.setParameter(param, n - getInternalNodeCount());

		// call this parameter2Heights
		parameters2Heights();
		NodeUtils.heights2Lengths(getRoot());
	}

	public double getParameter(int n) {
		if (n < getInternalNodeCount()) {
			return parameter[n];
		} else {
			return model.getParameter(n - getInternalNodeCount());
		}
	}

	/**
	 * Returns lower limit of parameter estimate.
	 */
	public double getLowerLimit(int n) {
		if (n < getInternalNodeCount()) {
			return BranchLimits.MINARC;
		} else {
			return model.getLowerLimit(n - getInternalNodeCount());
		}
	}

	public double getDefaultValue(int n) {
		if (n < getInternalNodeCount()) {
			return BranchLimits.DEFAULT_LENGTH;
		} else {
			return model.getDefaultValue(n - getInternalNodeCount());
		}
	}

	public void setParameterSE(double paramSE, int n) {
		if (n < getInternalNodeCount()) {
			return ; //Todo
		} else {
			model.setParameterSE(paramSE, n - getInternalNodeCount());
		}
	}
	public double getUpperLimit(int n) {
		if (n < getInternalNodeCount()) {
			return maxRelativeHeight_;
		} else {
			return model.getUpperLimit(n - getInternalNodeCount());
		}
	}

	public String getParameterizationInfo() {
		return "Mutation Rate Model based tree ("+model.toSingleLine()+")";
	}

    /**
     * Returns the underlying mutation rate model.
     *
     * @return The {@code MutationRateModel} instance associated with this object.
     */
    public MutationRateModel getMutationRateModel() {
        return model;
    }

	protected void parameters2Heights() {
		// nodes have been stored by a post-order traversal

		int index;

		for (int i = 0; i < getExternalNodeCount(); i++) {

			index = tocd.whichIdNumber(getExternalNode(i).getIdentifier().getName());
			//System.err.println(index + ":" + i);

			getExternalNode(i).setNodeHeight(model.getExpectedSubstitutions(tocd.getTime(index)));
		}

		// this could be more efficient
		for (int i = 0; i < getInternalNodeCount(); i++) {
			Node node = getInternalNode(i);
			node.setNodeHeight(parameter[i] + NodeUtils.findLargestChild(node));
		}
	}

	protected void heights2parameters() {
		for (int i = 0; i < getInternalNodeCount(); i++) {
			Node node = getInternalNode(i);
			parameter[i] = node.getNodeHeight()-NodeUtils.findLargestChild(node);
		}

		// need to convert heights to model parameters somehow!
	}

	public void setLnL(double lnL) {
		this.lnL = lnL;
	}

	public double getLnL() {
		return lnL;
	}

	public OrthogonalHints getOrthogonalHints() {
		if(model.getNumParameters()==0) {
			return this;
		}
		OrthogonalHints modelHints = model.getOrthogonalHints();
		if(modelHints!=null) {
			return OrthogonalHints.Utils.getCombined(this,parameter.length,modelHints,model.getNumParameters());
		}
		return OrthogonalHints.Utils.getCombined(
			this,parameter.length,
			OrthogonalHints.Utils.getNull(),
			model.getNumParameters());
	}
	// ===================== OrthogonalHints stuff ======================

	public OrderEnumerator getSuggestedOrdering(OrderEnumerator defaultOrdering) { return defaultOrdering; }

	public int getInternalParameterBoundaries(int parameter, double[] storage) {
		Node n = getInternalNode(parameter);

		if(n.isRoot()) { return 0; }

		int count = 0;
		Node p = n.getParent();
		Node current = n;
		double offset = 0;
		double baseLine = NodeUtils.findLargestChild(n);

		while(p!=null) {
			Node max = null;
			int numberOfChildren =  p.getChildCount();
			//Find Maximum
			double maxHeight = Double.NEGATIVE_INFINITY;
			double realMaxHeight = Double.NEGATIVE_INFINITY;
			for(int i = 0 ; i < numberOfChildren ; i++){
				Node c = p.getChild(i);
				//We ignore the target node!
				double nh = c.getNodeHeight();
				if(c!=n&&maxHeight<nh) { maxHeight = nh;max = c;	}
				if(realMaxHeight<nh) { realMaxHeight = nh; }
			}
			double value = maxHeight-offset-baseLine;
			if(value>maxRelativeHeight_) {
				break;
			}
			if(value>0&&max!=current) {
				//System.out.println("MH:"+maxHeight+"    OFS:"+offset+"   BL:"+baseLine);
				if(count==storage.length) {
					return -1;
				}
				if(count==0||value>storage[count-1]) {
					storage[count++] = value;
				}
			}
			//The offset is to take into account how much we are "pushing"
			offset+=p.getNodeHeight()-realMaxHeight;
			current = p;
			p = p.getParent();

		}
		return count;

	}

	// ===================== End of OrthogonalHints stuff ======================

	public Tree getCopy() {
		return new MutationRateModelTree(this);
	}
	public Object clone() {
		return getCopy();
	}
// ===========================================================================
// ===== Static stuff =======

    /**
     * Obtains a {@code ParameterizedTree.Factory} instance capable of generating
     * {@code Unconstrained} trees (trees whose branch lengths are treated as free parameters).
     *
     * @param rateModel The {@code MutationRateModel.Factory} used to create the rate model component of the parameterized tree.
     * @param tocd The {@code TimeOrderCharacterData} containing the sampling times or ordinals, used by the generated factory.
     * @return A {@code ParameterizedTree.Factory} object specifically configured to generate unconstrained trees.
     */
    public static final ParameterizedTree.Factory getParameterizedTreeFactory(MutationRateModel.Factory rateModel, TimeOrderCharacterData tocd) {
        return new TreeFactory(rateModel,tocd);
    }

	private static class TreeFactory implements ParameterizedTree.Factory {
		MutationRateModel.Factory rateModel_;
		TimeOrderCharacterData tocd_;
		public TreeFactory(MutationRateModel.Factory rateModel, TimeOrderCharacterData tocd) {
			this.rateModel_ = rateModel;
			this.tocd_ = tocd;
		}
		public ParameterizedTree generateNewTree(Tree base) {
			return new MutationRateModelTree(base, tocd_, rateModel_.generateNewModel(),true);
		}
	}
}
