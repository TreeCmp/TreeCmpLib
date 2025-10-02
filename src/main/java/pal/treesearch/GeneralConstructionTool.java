// GeneralConstructionTool.java
//
// (c) 1999-2004 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.treesearch;

/**
 * <p>Title: GeneralConstructionTool</p>
 * <p>Description: </p>
 * @author Matthew Goode
 * @version 1.0
 */
import java.util.*;

import pal.alignment.*;
import pal.datatype.*;
import pal.eval.*;
import pal.misc.*;
import pal.tree.*;

public class GeneralConstructionTool {

	private final String[] names_;
	private final int[][] sequences_;

	private final int numberOfStates_;
//	private final int numberOfCategories_;
	private final int numberOfSites_;

	private final DataType dataType_;

	private int nextConnectionIndex_ = 0;

	private final ArrayList allUNodes_ = new ArrayList();

	private final ConstraintModel constraints_;

	private ConditionalProbabilityStore tempConditionals_ = null;

	private final UnconstrainedLikelihoodModel.External freeCalcExternal_;

    /**
     * Constructs a new {@code GeneralConstructionTool}, initializing it with the provided constraints and sequence data.
     * This tool is responsible for managing the structure and data required for phylogenetic calculations
     * under various constraint models (e.g., molecular clock).
     *
     * @param constraints The {@code ConstraintModel} defining the topological, branch length, and rate constraints for the analysis.
     * @param alignment The base {@code Alignment} containing the sequence data, from which the number of sites, states, and sequences are extracted.
     */
    public GeneralConstructionTool(ConstraintModel constraints, Alignment alignment) {
        this.constraints_ = constraints;
        this.dataType_ = alignment.getDataType();

        this.numberOfSites_ = alignment.getSiteCount();
        this.numberOfStates_ = dataType_.getNumStates();

        this.names_ = Identifier.getNames(alignment);
        this.sequences_ = pal.alignment.AlignmentUtils.getAlignedStates( alignment,numberOfStates_ );

        freeCalcExternal_ = constraints_.createNewFreeExternal();
    } //End of constructor

    /**
     * Creates and returns an appropriate {@code FreeNode} (either a leaf or an internal node)
     * based on the characteristics of the normal PAL node peer.
     *
     * This method determines whether to create a standard {@code FreeInternalNode} or a specialized
     * {@code PivotNode} based on whether the node's associated clade is under a global clock constraint.
     *
     * @param peer The normal PAL Node structure acting as the blueprint for the new FreeNode.
     * @param parent The {@code FreeBranch} that connects to this new node (the branch toward the base/root).
     * @param store The constraint manager store, used to retrieve specific constraint groups for PivotNodes.
     * @return A concrete implementation of {@code FreeNode} (either a leaf, standard internal, or pivot node).
     * @throws IllegalArgumentException If a leaf node is found to be incorrectly constrained, suggesting a mismatch between topology and the constraint model.
     */
    public FreeNode createFreeNode(Node peer, FreeBranch parent, GeneralConstraintGroupManager.Store store) {
        if(peer.isLeaf()) {
            String name = peer.getIdentifier().getName();
            int[] sequence = getSequence(name);
            final String[] leafLabelSet = new String[] { name };
            if(constraints_.getGlobalClockConstraintGrouping(leafLabelSet)!=null) {
                //Could make this a warning...
                throw new IllegalArgumentException("Being forced to treat node '"+name+"' as unconstrained when constrained (probably a result of incorrectly structured topology");
            }
            return new FreeLeafNode(parent, name,this);
        } else {
            String[] leafLabelSet = getLeafLabelSet(peer);
            ConstraintModel.GroupManager grouping = constraints_.getGlobalClockConstraintGrouping(leafLabelSet);
            if(grouping==null) {
                return new FreeInternalNode(peer,parent,this,store);
            } else {
                return new PivotNode(peer,parent,this,store.getConstraintGroupManager(grouping),store);
            }
        }
    }

	public RootAccess createRootAccess(Node baseTree, GeneralConstraintGroupManager.Store store) {
		String[] allLeaves = getLeafLabelSet(baseTree);
		ConstraintModel.GroupManager grouping = constraints_.getGlobalClockConstraintGrouping(allLeaves);
		if(grouping==null) {
			return new FreeBranch(baseTree,this,store);
		} else {
			return new PivotNode(baseTree,this,store.getConstraintGroupManager(grouping),store);
		}
	}

    /**
     * Creates and returns an appropriate {@code ConstrainedNode} (either a leaf or an internal node)
     * based on the characteristics of the normal PAL node peer and the applied constraint model.
     *
     * This method ensures that the created node is correctly initialized within the constraints
     * defined by the {@code GeneralConstraintGroupManager}.
     *
     * @param peer The normal PAL Node structure acting as the blueprint for the new ConstrainedNode.
     * @param parent The {@code ParentableConstrainedNode} that serves as the parent in the constrained structure.
     * @param store The store managing general constraint groups.
     * @param groupManager The constraint group manager specific to the current part of the tree being built.
     * @return A concrete implementation of {@code ConstrainedNode} (either a leaf or an internal node).
     * @throws IllegalArgumentException If a leaf node is found to be unconstrained when it should be constrained, suggesting an incorrectly structured topology for the constraint model.
     * @throws RuntimeException If the implementation encounters an unsupported scenario, such as moving from a constrained structure to an unconstrained one.
     */
    public ConstrainedNode createConstrainedNode(Node peer, ParentableConstrainedNode parent, GeneralConstraintGroupManager.Store store, GeneralConstraintGroupManager groupManager) {
        ConstraintModel.GroupManager parentGroup = groupManager.getRelatedGroup();
        if(peer.isLeaf()) {
            String name = peer.getIdentifier().getName();
            int[] sequence = getSequence(name);
            String[] leafLabelSet = new String[] { name };
            ConstraintModel.GroupManager grouping = constraints_.getGlobalClockConstraintGrouping(leafLabelSet);
            if(grouping==null) {
                //Could make this a warning...
                throw new IllegalArgumentException("Being forced to treat node '"+name+"' as constrained when unconstrained (probably a result of incorrectly structured topology");
            }
            return new ConstrainedLeafNode(parent,peer,parentGroup.getLeafBaseHeight(name), this, parentGroup);
        } else {
            String[] leafLabelSet = getLeafLabelSet(peer);
            ConstraintModel.GroupManager grouping = constraints_.getGlobalClockConstraintGrouping(leafLabelSet);
            if(grouping==null) {
                throw new RuntimeException("Not implemented - cannont handle the constrained moving to unconstrained case yet!");
            } else {
                return new ConstrainedInternalNode(peer, parent,this,store,groupManager);
            }
        }
    }

// ================================================================================================================
	// - - - - -

	public PatternInfo constructFreshPatternInfo(boolean binaryPattern) {
		return new PatternInfo(numberOfSites_,binaryPattern);
	}

	public final ConditionalProbabilityStore obtainTempConditionalProbabilityStore() {
		if(tempConditionals_==null) {
			tempConditionals_ = newConditionalProbabilityStore(false);
		}
		return tempConditionals_;
	}

	public final ConditionalProbabilityStore newConditionalProbabilityStore(boolean isForLeaf) {
	  return constraints_.createAppropriateConditionalProbabilityStore( isForLeaf );
	}

	public final int allocateNextConnectionIndex() {	return nextConnectionIndex_++;		}
// - - - - -
	public UnconstrainedLikelihoodModel.Internal allocateNewFreeInternalCalculator() {
	  return constraints_.createNewFreeInternal();
	}
// - - - - -
	public UnconstrainedLikelihoodModel.External obtainFreeExternalCalculator() {
		if(freeCalcExternal_!=null) {	return freeCalcExternal_;	}
		throw new RuntimeException("No free calculator");
	}
// - - - - -
// - - - - -
	public UnconstrainedLikelihoodModel.Leaf createNewFreeLeafCalculator(int[] patternStateMatchup, int numberOfPatterns) {
		return constraints_.createNewFreeLeaf(patternStateMatchup,numberOfPatterns);
	}

	public int build(PatternInfo beingBuilt, PatternInfo left, PatternInfo right) {
		return beingBuilt.build(left,right,numberOfSites_);
	}

	public DataType getDataType() { return dataType_; }
	public final int getNumberOfSites() { return numberOfSites_; }
	public int getNumberOfStates() { return numberOfStates_; }

	/**
	 * Get the sequence data for a particular OTU
	 * @param name The name of the OTU
	 * @return the sequence data stored as integer values
	 * @throws IllegalArgumentException if no such OTU with given name
	 */
	public int[] getSequence(String name) {
		if(sequences_==null) {	return null;	}
		for(int i = 0 ; i < names_.length ; i++) {
			if(name.equals(names_[i])) {	return sequences_[i];	}
		}
		throw new IllegalArgumentException("Unknown sequence:"+name);
	}

	/**
	 * A horibly inefficient way of doing things. Finds the leaf index for all leaves from the tree
	 * defined by the PAL node. Returns -1 if more than one index.
	 * @param peer the root of the sub tree
	 * @return the common leaf index, of -1 if no common leaf index
	 * Note: assumes bificating tree
	 */
	public String[] getLeafLabelSet(Node peer) {
		ArrayList al = new ArrayList();
		getLeafLabelSet(peer,al);
		String[] result = new String[al.size()];
		al.toArray(result);
		return result;
	}

	public void getLeafLabelSet(Node peer, ArrayList al) {
		if(peer.isLeaf()) {
			al.add(peer.getIdentifier().getName());
		} else {
			getLeafLabelSet(peer.getChild(0), al);
			getLeafLabelSet(peer.getChild(1), al);
		}
	}


}
