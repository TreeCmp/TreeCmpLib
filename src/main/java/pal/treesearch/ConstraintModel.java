// ConstraintModel.java
//
// (c) 1999-2004 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.treesearch;

/**
 * <p>Title: ConstraintModel </p>
 * <p>Description: </p>
 * @author Matthew Goode
 * @version 1.0
 */

import pal.eval.*;
import pal.misc.*;

public interface ConstraintModel {
    /**
     * Inquires about the clock constraint grouping assigned to a set of leaves.
     * This grouping is used to enforce a strict molecular clock (or a similar rate constraint)
     * across subsets of the tree's taxa.
     *
     * @param leafLabelSet An array of strings representing the labels (taxa) of the leaves to be checked.
     * @return The {@code GroupManager} object defining the clock constraint grouping for the leaves,
     * or {@code null} if the set of leaves is unconstrained (free) or no explicit grouping applies.
     */
    public GroupManager getGlobalClockConstraintGrouping(String[] leafLabelSet);

    /**
     * Obtains the permanent clade constraint sets. These sets define which leaf labels
     * must always form a monophyletic clade (subtree) during tree building (e.g., random generation)
     * and tree searching algorithms.
     *
     * @param allLabelSet The complete set of all leaf labels (taxa) present in the tree.
     * @return An array of string arrays, where each inner array represents a set of labels
     * that must perpetually form a single, distinct clade.
     */
    public String[][] getCladeConstraints(String[] allLabelSet);
    public UnconstrainedLikelihoodModel.Leaf createNewFreeLeaf(int[] patternStateMatchup, int numberOfPatterns);
	public UnconstrainedLikelihoodModel.External createNewFreeExternal();
    public UnconstrainedLikelihoodModel.Internal createNewFreeInternal();
	public ConditionalProbabilityStore createAppropriateConditionalProbabilityStore(  boolean isForLeaf );
	public NeoParameterized getGlobalParameterAccess();
	public String getRateModelSummary();

// ===================================================================================================

	public static interface GroupManager {
		public double getLeafBaseHeight(String leafLabel);
		public double getBaseHeight(double originalExpectSubstitutionHeight);
		public double getExpectedSubstitutionHeight(double baseHeight);

		public int getBaseHeightUnits();

		public void initialiseParameters(String[] leafNames, double[] leafHeights );

		public NeoParameterized getAllGroupRelatedParameterAccess();
		public NeoParameterized getPrimaryGroupRelatedParameterAccess();
		public NeoParameterized getSecondaryGroupRelatedParameterAccess();

		public MolecularClockLikelihoodModel.Leaf createNewClockLeaf(PatternInfo pattern, int[] patternStateMatchup);
		public MolecularClockLikelihoodModel.External createNewClockExternal();
		public MolecularClockLikelihoodModel.Internal createNewClockInternal();
	}
}