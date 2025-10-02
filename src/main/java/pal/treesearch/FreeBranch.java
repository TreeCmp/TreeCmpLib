// FreeBranch.java
//
// (c) 1999-2004 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.treesearch;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * @author Matthew Goode
 * @version 1.0
 */
import java.util.*;

import pal.eval.*;
import pal.math.*;
import pal.tree.*;

public final class FreeBranch implements RootAccess, GeneralOptimisable  {

	private FreeNode leftNode_;
	private FreeNode rightNode_;
	private double branchLength_;
	private final PatternInfo centerPattern_;
	private boolean centerPatternValid_;

	private final OptimisationHandler optimisationHandler_;

	private final int index_;

	private FreeNode markLeftNode_ = null;
	private FreeNode markRightNode_ = null;
	private double markBranchLength_;

	private Object annotation_ = null;

    /**
     * The starting constructor for building the {@code FreeBranch} structure from the root of a given PAL tree.
     * This constructor assumes the input node represents the root, combining its two children's branch lengths
     * to form the initial branch length.
     *
     * @param n The normal PAL Node structure to base this branch on (expected to be the root).
     * @param tool The construction tool used to aid in building the branch and node objects and allocating connection indices.
     * @param store The store managing general constraint groups, used during node creation.
     * @throws IllegalArgumentException If the base tree node is not bifurcating (does not have exactly two children).
     */
    public FreeBranch(Node n,  GeneralConstructionTool tool, GeneralConstraintGroupManager.Store store) {
        if(n.getChildCount()!=2) {
            throw new IllegalArgumentException("Base tree must be bificating");
        }
        this.index_ = tool.allocateNextConnectionIndex();
        Node l = n.getChild(0);
        Node r = n.getChild(1);
        this.branchLength_ = l.getBranchLength()+r.getBranchLength();

        leftNode_ = tool.createFreeNode(l, this,store);
        rightNode_ = tool.createFreeNode(r, this,store);

        this.centerPattern_ = new PatternInfo(tool.getNumberOfSites(),true);
        this.centerPatternValid_ = false;
        this.optimisationHandler_ = new OptimisationHandler(tool);
    }
    /**
     * The continuing recursion constructor used for building a subtree of the {@code FreeBranch} structure.
     * This constructor is typically called recursively for non-root nodes of the source PAL tree.
     *
     * @param n The PAL Node structure to base the sub-branch on. Its branch length becomes the new {@code FreeBranch} length.
     * @param parent The parent {@code FreeNode} that connects this branch (representing the subtree in the other direction).
     * @param tool The construction tool used to aid in building the branch and node objects and allocating connection indices.
     * @param store The store managing general constraint groups, used during node creation.
     */
    public FreeBranch(Node n, FreeNode parent, GeneralConstructionTool tool, GeneralConstraintGroupManager.Store store) {
        this.index_ = tool.allocateNextConnectionIndex();
        this.branchLength_ = n.getBranchLength();
        this.rightNode_ = parent;
        this.leftNode_ = tool.createFreeNode(n,this,store);
        this.centerPattern_ = new PatternInfo(tool.getNumberOfSites(),true);
        this.centerPatternValid_ = false;
        this.optimisationHandler_ = new OptimisationHandler(tool);

    }

    /**
     * A generic constructor for creating a new {@code FreeBranch} that connects two already defined {@code FreeNode} objects.
     *
     * @param left The left node to be connected.
     * @param right The right node to be connected.
     * @param branchLength The length of the connection (branch).
     * @param tool The construction tool used to allocate a unique connection index for this branch.
     */
    public FreeBranch(FreeNode left, FreeNode right, double branchLength, GeneralConstructionTool tool) {
        this.index_ = tool.allocateNextConnectionIndex();
        this.branchLength_ = branchLength;
        this.rightNode_ = right;
        this.leftNode_ = left;
        this.centerPattern_ = new PatternInfo(tool.getNumberOfSites(),true);
        this.centerPatternValid_ = false;
        this.optimisationHandler_ = new OptimisationHandler(tool);
    }

    /**
     * Sets an arbitrary annotation object to be associated with this branch.
     *
     * @param annotation The object to store as annotation.
     */
    public void setAnnotation(Object annotation) {    this.annotation_ = annotation;      }
    /**
     * Retrieves the "left" {@code FreeNode} connected by this branch.
     *
     * @return The left {@code FreeNode} of this connection.
     */
    public final FreeNode getLeft() { return leftNode_; }
    /**
     * Retrieves the "right" {@code FreeNode} connected by this branch.
     *
     * @return The right {@code FreeNode} of this connection.
     */
    public final FreeNode getRight() { return rightNode_; }

    /**
     * Marks the current state of the branch by storing the current branch length and references
     * to the left and right nodes. This state can later be restored using {@code undoToMark()}.
     */
    public final void mark() {
        this.markBranchLength_ = branchLength_;
        this.markLeftNode_ = leftNode_;       this.markRightNode_ = rightNode_;
    }
    /**
     * Retrieves the {@code PatternInfo} object associated with the left node, relative to this connection.
     *
     * @param tool The construction tool used to retrieve the pattern information if necessary.
     * @return The pattern info object for the left node.
     */
    public final PatternInfo getLeftPatternInfo(GeneralConstructionTool tool) {    return leftNode_.getPatternInfo(tool, this);   }
    /**
     * Retrieves the {@code PatternInfo} object associated with the right node, relative to this connection.
     *
     * @param tool The construction tool used to retrieve the pattern information if necessary.
     * @return The pattern info object for the right node.
     */
    public final PatternInfo getRightPatternInfo(GeneralConstructionTool tool) {    return rightNode_.getPatternInfo(tool, this);  }
	public final PatternInfo getPatternInfo(GeneralConstructionTool tool, FreeNode caller) {
	  if(caller==leftNode_) {
		  return rightNode_.getPatternInfo(tool,this);
		}
		if(caller==rightNode_) {
		  return leftNode_.getPatternInfo(tool,this);
		}
		throw new IllegalArgumentException("Unknown caller!");
	}
    /**
     * Retrieves the combined {@code PatternInfo} across this connection (branch).
     * This information is used when this branch is temporarily treated as the
     * "root" during the likelihood calculation, combining the data from both the
     * left and right subtrees.
     *
     * @param tool The construction tool, which is used to build the pattern if it is not yet valid (cached).
     * @return The combined {@code PatternInfo} object for this branch's connection.
     */
    public final PatternInfo getCenterPatternInfo(GeneralConstructionTool tool) {
        if(!centerPatternValid_) {
            tool.build(centerPattern_, getLeftPatternInfo(tool),getRightPatternInfo(tool));
            centerPatternValid_ = true;
        }
        return centerPattern_;
    }

	public final void undoToMark() {
		if(markLeftNode_==null) {
			throw new RuntimeException("Assertion error : undo to mark when no mark made");
		}
		this.branchLength_ = markBranchLength_;
		this.leftNode_ = markLeftNode_;
		this.rightNode_ = markRightNode_;
	}

	public String toString() {
		return "("+leftNode_+", "+rightNode_+")";
	}
	public boolean hasConnection(FreeBranch c, FreeNode caller) {
		if(c==this) { return true; }
		if(caller==leftNode_) {
			return rightNode_.hasConnection(c,this);
		}
		if(caller==rightNode_) {
			return leftNode_.hasConnection(c,this);
		}
		throw new IllegalArgumentException("Unknown caller");
	}

	/**
	 * @return the "left" connection of the left node
	 */
	public FreeBranch getLeftLeftBranch() {	return leftNode_.getLeftBranch(this);	}
	/**
	 * @return the "right" connection of the left node
	 */
	public FreeBranch getLeftRightBranch() {	return leftNode_.getRightBranch(this);	}
	/**
	 * @return the "left" connection of the right node
	 */
	public FreeBranch getRightLeftBranch() {	return rightNode_.getLeftBranch(this);	}
	/**
	 * @return the "right" connection of the left node
	 */
	public FreeBranch getRightRightBranch() {		return rightNode_.getRightBranch(this);		}

    /**
     * Attaches this branch to a specified {@code attachmentPoint} branch in the tree,
     * effectively performing a topological change (likely a Subtree Pruning and Regrafting operation).
     *
     * The method prunes the subtree currently attached via this branch and re-grafts it at the new attachment point.
     *
     * @param attachmentPoint The existing {@code FreeBranch} in the tree where this branch (and its connected subtree) will be attached.
     * @param store An array of size 3 (or more) used internally to store references to the branches involved in the topological change
     * ({@code this}, the redundant branch, and {@code attachmentPoint}) before fixing up connections on the {@code used} node.
     * @return The {@code FreeBranch} that was structurally left behind after the operation,
     * which allows the calling code to potentially undo this operation by reattaching to it. Returns {@code null} if the operation fails,
     * specifically if the branch is already directly connected to the attachment point.
     * @throws IllegalArgumentException If an internal assertion about the branch structure fails.
     * @throws RuntimeException If an internal assertion about the extractable node fails.
     */
    public FreeBranch attachTo(FreeBranch attachmentPoint, FreeBranch[] store) {

        final FreeNode used = (leftNode_.hasConnection(attachmentPoint, this) ? leftNode_ : rightNode_ );
        if(used.hasDirectConnection(attachmentPoint)) {
            return null;
        }
        final FreeBranch redundant = used.extract(this);
        final FreeBranch reattachment;
        final FreeBranch leftUsed = used.getLeftBranch(this);
        final FreeBranch rightUsed = used.getRightBranch(this);

        if(leftUsed==redundant) {
            reattachment = rightUsed;
        } else if(rightUsed == redundant) {
            reattachment = leftUsed;
        } else {
            throw new IllegalArgumentException("Assertion error");
        }
        if(redundant==null) {
            throw new RuntimeException("Assertion error : I should be able to extract from one of my nodes!");
        }

        FreeNode attachmentOldRight = attachmentPoint.rightNode_;
        //We will attach the old right to redundant, and move in the used node to the attachment point
        attachmentPoint.swapNode(attachmentOldRight,used);
        redundant.swapNode(redundant.getOther(used),attachmentOldRight);

        //Fix up old right to have correct attachments
        attachmentOldRight.swapConnection(attachmentPoint,redundant);

        //c.swapNode();
        //Fix up the used connections
        store[0] = this;
        store[1] = redundant;
        store[2] = attachmentPoint;
        used.setConnectingBranches(store,3);

        return reattachment;
    }
	public Node buildPALNodeBase() {
		Node[] children = new Node[] {
			leftNode_.buildPALNodeBase(branchLength_/2,this),
			rightNode_.buildPALNodeBase(branchLength_/2,this)
		};
		return NodeFactory.createNode(children);
	}
	public Node buildPALNodeES() {
		Node[] children = new Node[] {
			leftNode_.buildPALNodeES(branchLength_/2,this),
			rightNode_.buildPALNodeES(branchLength_/2,this)
		};
		Node n = NodeFactory.createNode(children);
		NodeUtils.lengths2Heights(n);
		return  n;
	}

	public Node buildPALNodeBase(FreeNode caller) {
		if(leftNode_==caller) {		return rightNode_.buildPALNodeBase(branchLength_,this);		}
		if(rightNode_==caller) {	return leftNode_.buildPALNodeBase(branchLength_,this);		}
		throw new IllegalArgumentException("Unknown caller!");
	}

	public Node buildPALNodeES(FreeNode caller) {
		if(leftNode_==caller) {		return rightNode_.buildPALNodeES(branchLength_,this);		}
		if(rightNode_==caller) {	return leftNode_.buildPALNodeES(branchLength_,this);		}
		throw new IllegalArgumentException("Unknown caller!");
	}

	/**
	 * @return -1 if null
	 */
	private final static int getIndex(FreeBranch c) {
		if(c==null) { return -1;}
		return c.index_;
	}

    /**
     * Sets the left and right {@code FreeNode} objects connected by this branch.
     * This method directly assigns the node references and **does nothing to update**
     * the internal connection information within the assigned nodes themselves,
     * potentially leaving the overall tree structure in an inconsistent state.
     *
     * @param left The {@code FreeNode} to set as the left connection.
     * @param right The {@code FreeNode} to set as the right connection.
     * @deprecated This method is low-level and inherently unsafe; use higher-level topological methods to maintain tree consistency.
     */
    public void setNodes(FreeNode left, FreeNode right) {
        this.leftNode_ = left;    this.rightNode_ = right;
    }
    /**
     * Replaces one connected {@code FreeNode} with another {@code FreeNode} on this branch.
     *
     * <p>Note: This method only updates the reference held by this branch (the parent).
     * It **does not change the connection information** stored within the nodes themselves,
     * leaving the tree in an inconsistent state. Higher-level methods must be called immediately
     * to fix the connections on the replaced and replacement nodes.</p>
     *
     * @param nodeToReplace The currently connected {@code FreeNode} that is to be replaced (must be either left or right node).
     * @param replacement The new {@code FreeNode} that will take the place of the old node.
     * @throws RuntimeException If the {@code nodeToReplace} is not one of the nodes currently connected by this branch.
     */
    public void swapNode(FreeNode nodeToReplace, FreeNode replacement) {
        if(nodeToReplace==leftNode_) {
            leftNode_ = replacement;
        } else if(nodeToReplace==rightNode_) {
            rightNode_ = replacement;
        } else {
            throw new RuntimeException("Unknown node to replace");
        }
    }
	public final ConditionalProbabilityStore getLeftFlatConditionalProbabilities( GeneralConstructionTool tool ) {
		return leftNode_.getFlatConditionalProbabilities(this,tool);
	}
	public final ConditionalProbabilityStore getRightFlatConditionalProbabilities( GeneralConstructionTool tool) {
		return rightNode_.getFlatConditionalProbabilities(this,tool);
	}

	//Branch Length stuff
	public final double getBranchLength() { return branchLength_; }
	public final void setBranchLength(double x) { this.branchLength_ = x; }

	public String toString(FreeNode caller) {
		if(caller==leftNode_) {
			return rightNode_.toString(this);
		}
		if(caller!=rightNode_) {
			throw new RuntimeException("Unknown caller");
		}
		return leftNode_.toString(this);
	}
	public void testLikelihood( GeneralConstructionTool tool) {
		testLikelihood(null,tool);
	}
	public void testLikelihood(FreeNode caller, GeneralConstructionTool tool) {
		System.out.println("Test Free Branch:"+calculateLogLikelihood( tool));

		if(caller!=leftNode_) {		leftNode_.testLikelihood(this,tool);		}
		if(caller!=rightNode_){		rightNode_.testLikelihood(this,tool);		}
	}

	public ConditionalProbabilityStore getExtendedConditionalProbabilities(  FreeNode caller, GeneralConstructionTool tool) {
		FreeNode other = getOther(caller);
		return other.getExtendedConditionalProbabilities(branchLength_,  this,tool);
	}
	public ConditionalProbabilityStore getExtendedConditionalProbabilities(  FreeNode caller, UnconstrainedLikelihoodModel.External externalCalculator, ConditionalProbabilityStore extendedStore, GeneralConstructionTool tool) {
		FreeNode other = getOther(caller);
		return other.getExtendedConditionalProbabilities(branchLength_,this, externalCalculator, extendedStore,tool);
	}

	public final int getNumberOfOptimisationTypes() { return 1; }
	public double optimise(int optimisationType, UnivariateMinimum minimiser, GeneralConstructionTool tool, int fracDigits) {
		ConditionalProbabilityStore leftFlat = getLeftFlatConditionalProbabilities(tool);
		ConditionalProbabilityStore rightFlat = getRightFlatConditionalProbabilities(tool);
		optimisationHandler_.setup(leftFlat,rightFlat,getCenterPatternInfo(tool),branchLength_, fracDigits,tool.obtainTempConditionalProbabilityStore());
		optimisationHandler_.optimise(minimiser);
		this.branchLength_ = optimisationHandler_.getBranchLength();
		return optimisationHandler_.getLogLikelihood();
	}


	public void getAllComponents(ArrayList store, Class componentType) {	getAllComponents(store,componentType, null);	}

	public void getAllComponents(ArrayList store, Class componentType, FreeNode caller) {
		if(componentType.isAssignableFrom(getClass())) { store.add(this);		}
		if(caller!=leftNode_) {	leftNode_.getAllComponents(store,componentType, this);	}
		if(caller!=rightNode_) { rightNode_.getAllComponents(store,componentType,this);	}
	}

	public void getCenterPatternInfo(GeneralConstructionTool tool, PatternInfo store) {
		PatternInfo left = leftNode_.getPatternInfo(tool, this);
		PatternInfo right = rightNode_.getPatternInfo(tool, this);
		tool.build(store, left,right);
	}

	public FreeNode getOther(FreeNode caller) {
		if(leftNode_==caller) {
			return rightNode_;
		}
		if(rightNode_==caller) {
			return leftNode_;
		}
		throw new RuntimeException("Unknown caller!");
	}

	public final void doNNI(MersenneTwisterFast r) {
		doNNI(r.nextBoolean(),r.nextBoolean());
	}
    /**
     * Performs a single step of the Nearest Neighbour Interchange (NNI) operation
     * on the two subtrees connected by this branch. NNI swaps two subtrees that are
     * "nearest neighbours" to explore different tree topologies.
     *
     * <p>The operation swaps the specified connection from the left node with the specified connection from the right node
     * across this central branch.</p>
     *
     * @param leftSwapLeft If {@code true}, the NNI swap involves the left-most branch of the left node; otherwise, it involves the right-most branch.
     * @param rightSwapLeft If {@code true}, the NNI swap involves the left-most branch of the right node; otherwise, it involves the right-most branch.
     * @return {@code true} if the NNI operation was successfully performed (the swap occurred), {@code false} if one of the specified branches to swap was {@code null} and the operation could not be completed.
     * Note: This method only modifies the tree's topology by swapping connections. **It does not automatically trigger the reconstruction or invalidation of cached likelihood patterns (Conditional Probability Stores).**
     */
    public boolean doNNI(boolean leftSwapLeft, boolean rightSwapLeft) {
        FreeBranch first = leftSwapLeft ? leftNode_.getLeftBranch(this) : leftNode_.getRightBranch(this);
        if(first==null) {
            return false;
        }
        FreeBranch second = rightSwapLeft ? rightNode_.getLeftBranch(this) : rightNode_.getRightBranch(this);
        if(second==null) {
            return false;
        }
        leftNode_.swapConnection(first,rightNode_,second);
        return true;
    }
	public double calculateLogLikelihood(  GeneralConstructionTool tool) {
		UnconstrainedLikelihoodModel.External calculator = tool.obtainFreeExternalCalculator();
		PatternInfo pi = getCenterPatternInfo(tool);
		final ConditionalProbabilityStore leftConditionalProbabilityProbabilties =
			leftNode_.getFlatConditionalProbabilities( this,tool);
		final ConditionalProbabilityStore rightConditionalProbabilityProbabilties =
			rightNode_.getExtendedConditionalProbabilities(branchLength_, this,tool);
		return calculator.calculateLogLikelihood(pi, leftConditionalProbabilityProbabilties,rightConditionalProbabilityProbabilties);
	}
	public double calculateLogLikelihood2(GeneralConstructionTool tool) {
	  UnconstrainedLikelihoodModel.External calculator = tool.obtainFreeExternalCalculator();
		PatternInfo pi = getCenterPatternInfo(tool);
	  final ConditionalProbabilityStore left = leftNode_.getFlatConditionalProbabilities( this,tool);
	  final ConditionalProbabilityStore right = rightNode_.getFlatConditionalProbabilities(   this,tool);
	  return calculator.calculateLogLikelihood(branchLength_, pi, left,right,tool.newConditionalProbabilityStore(false));
	}
	public SiteDetails calculateSiteDetails(UnconstrainedLikelihoodModel.External calculator, GeneralConstructionTool tool) {
		PatternInfo pi = getCenterPatternInfo(tool);
		final ConditionalProbabilityStore left = leftNode_.getFlatConditionalProbabilities( this,tool);
		final ConditionalProbabilityStore right = rightNode_.getFlatConditionalProbabilities(  this,tool);
		return calculator.calculateSiteDetailsUnrooted(branchLength_, pi,left,right,tool.newConditionalProbabilityStore(false));
	}
	// ================================================================================================
	private static final class OptimisationHandler implements UnivariateFunction {
		private ConditionalProbabilityStore leftFlatConditionals_;
		private ConditionalProbabilityStore rightFlatConditionals_;
		private ConditionalProbabilityStore tempConditionals_;
		private PatternInfo centerPattern_;
		private final UnconstrainedLikelihoodModel.External external_;
		private double branchLength_;
		private double logLikelihood_;
		private int fracDigits_;

		public OptimisationHandler(GeneralConstructionTool tool) {
			this.external_ = tool.obtainFreeExternalCalculator();
		}
		public void setup(ConditionalProbabilityStore leftFlatConditionals, ConditionalProbabilityStore rightFlatConditionals, PatternInfo centerPattern, double branchLength, int fracDigits, ConditionalProbabilityStore tempConditionals) {
		  this.leftFlatConditionals_ = leftFlatConditionals;
			this.tempConditionals_ = tempConditionals;
			this.rightFlatConditionals_ = rightFlatConditionals;
			this.centerPattern_ = centerPattern;
			this.branchLength_ = branchLength;
			this.fracDigits_ = fracDigits;
		}
		public void optimise(UnivariateMinimum minimiser) {
			minimiser.findMinimum(branchLength_,this,fracDigits_);
		  this.branchLength_ = minimiser.minx;
			this.logLikelihood_ = -minimiser.fminx;
		}

		public double evaluate(double argument) {
			return -external_.calculateLogLikelihood(argument,centerPattern_,leftFlatConditionals_,rightFlatConditionals_, tempConditionals_);
		}

	  public double getLowerBound() { return 0; }

	  public double getUpperBound() { return 1; }
		public double getLogLikelihood() { return logLikelihood_; }
		public double getBranchLength() { return branchLength_; }
	}
}