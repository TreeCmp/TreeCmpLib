// MolecularClockLikelihoodModel.java
//
// (c) 1999-2004 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.eval;

/**
 * <p>Title: MolecularClockLikelihoodModel </p>
 * <p>Description: An interface to objects that can be used for calculating likelihood estimates when a molecular clock is assumed (and therefore knowledge of the relative temporal order of events) </p>
 * @author Matthew Goode
 * @version 1.0
 * <p>History<br>
 *  <ul>
 *    <li>27/5/2004 Created </li>
 *  </ul>
 * </p>
 */
import pal.misc.*;

public interface MolecularClockLikelihoodModel {
	/**
   * The External calculator does not maintain any state and is approapriate for
   * calculation where a store is provided
   */
    public static interface External {

        /**
         * Extend the conditional probabilities for a single descendent along a branch.
         *
         * @param topBaseHeight The height at the top of the branch.
         * @param bottomBaseHeight The height at the bottom of the branch.
         * @param centerPattern The pattern information at this branch.
         * @param descendentConditionalProbabilities The store for descendant conditional probabilities.
         */
        public void calculateSingleDescendentExtendedConditionals(
                double topBaseHeight, double bottomBaseHeight,
                PatternInfo centerPattern,
                ConditionalProbabilityStore descendentConditionalProbabilities
        );

        /**
         * Extend the conditional probabilities for a single ascendent along a branch directly.
         *
         * @param topBaseHeight The height at the top of the branch.
         * @param bottomBaseHeight The height at the bottom of the branch.
         * @param centerPattern The pattern information at this branch.
         * @param ascendentConditionalProbabilityProbabilities The store for ascendent conditional probabilities.
         */
        public void calculateSingleAscendentExtendedConditionalsDirect(
                double topBaseHeight, double bottomBaseHeight,
                PatternInfo centerPattern,
                ConditionalProbabilityStore ascendentConditionalProbabilityProbabilities
        );

        /**
         * Extend the conditional probabilities for a single ascendent along a branch indirectly.
         *
         * @param topBaseHeight The height at the top of the branch.
         * @param bottomBaseHeight The height at the bottom of the branch.
         * @param centerPattern The pattern information at this branch.
         * @param baseAscendentConditionalProbabilityProbabilities The base conditional probabilities to extend.
         * @param resultConditionalProbabilityProbabilities The store to hold the resulting extended probabilities.
         */
        public void calculateSingleAscendentExtendedConditionalsIndirect(
                double topBaseHeight, double bottomBaseHeight,
                PatternInfo centerPattern,
                ConditionalProbabilityStore baseAscendentConditionalProbabilityProbabilities,
                ConditionalProbabilityStore resultConditionalProbabilityProbabilities
        );

        /**
         * Calculate the extended conditional probabilities for a node given left and right children.
         *
         * @param topBaseHeight The height at the top of the branch.
         * @param bottomBaseHeight The height at the bottom of the branch.
         * @param centerPattern The pattern information at this branch.
         * @param leftConditionalProbabilities Left child conditional probabilities (not modified).
         * @param rightConditionalProbabilities Right child conditional probabilities (not modified).
         * @param resultStore Store to hold the resulting extended probabilities.
         */
        public void calculateExtendedConditionals(
                double topBaseHeight, double bottomBaseHeight,
                PatternInfo centerPattern,
                ConditionalProbabilityStore leftConditionalProbabilities,
                ConditionalProbabilityStore rightConditionalProbabilities,
                ConditionalProbabilityStore resultStore
        );

        /**
         * Calculate the log-likelihood given two extended subtrees at the root.
         *
         * @param rootHeight The height at the root.
         * @param centerPattern Pattern information at this node.
         * @param leftConditionalProbabilitiesStore Conditional probabilities from the left subtree.
         * @param rightConditionalProbabilitiesStore Conditional probabilities from the right subtree.
         * @return The log-likelihood at this node.
         */
        public double calculateLogLikelihood(
                double rootHeight, PatternInfo centerPattern,
                ConditionalProbabilityStore leftConditionalProbabilitiesStore,
                ConditionalProbabilityStore rightConditionalProbabilitiesStore
        );

        /**
         * Calculate the log-likelihood for a non-root node.
         *
         * @param nodeHeight The height of the node for likelihood computation.
         * @param centerPattern Pattern information at this node. Left child is assumed ascendent, right child descendent.
         * @param ascendentConditionalProbabilitiesStore Ascendent conditional probabilities (extended to nodeHeight).
         * @param descendentConditionalProbabilitiesStore Descendent conditional probabilities (extended to nodeHeight).
         * @return The log-likelihood at this node.
         */
        public double calculateLogLikelihoodNonRoot(
                double nodeHeight, PatternInfo centerPattern,
                ConditionalProbabilityStore ascendentConditionalProbabilitiesStore,
                ConditionalProbabilityStore descendentConditionalProbabilitiesStore
        );

        /**
         * Calculate the log-likelihood given conditional probabilities at a single node.
         *
         * @param rootHeight The height of the node for likelihood computation.
         * @param centerPattern Pattern information at this node.
         * @param conditionalProbabilitiesStore The store of conditional probabilities.
         * @return The log-likelihood at this node.
         */
        public double calculateLogLikelihoodSingle(
                double rootHeight, PatternInfo centerPattern,
                ConditionalProbabilityStore conditionalProbabilitiesStore
        );

		public SiteDetails calculateSiteDetails(
                double rootHeight, PatternInfo centerPattern,
                ConditionalProbabilityStore leftConditionalProbabilitiesStore,
                ConditionalProbabilityStore rightConditionalProbabilitiesStore );

		public void calculateFlatConditionals(
                double rootHeight, PatternInfo centerPattern,
                ConditionalProbabilityStore leftConditionalProbabilitiesStore,
                ConditionalProbabilityStore rightConditionalProbabilitiesStore,
                ConditionalProbabilityStore resultConditionalProbabilitiesStore );
  } //End of class External

  /**
   * The Internal calculator may maintain state and is approapriate permanent attachment
   * to internal nodes of the tree structure
   */
    /**
     * Internal calculator interface for likelihood computations within a tree.
     * Implementations may maintain internal state and are allowed to overwrite certain
     * conditional probability stores in specific circumstances.
     */
    public static interface Internal {

        /**
         * Calculate extended conditional probabilities for a node.
         *
         * @param topBaseHeight The height at the top of the branch.
         * @param bottomBaseHeight The height at the bottom of the branch.
         * @param centerPattern Pattern information at this node.
         * @param leftConditionalProbabilityProbabilities Left child probabilities (may be overwritten).
         * @param rightConditionalProbabilityProbabilities Right child probabilities (may be overwritten).
         * @return Resulting conditional probabilities.
         * Note: Left and right stores should not be used after this call.
         */
        public ConditionalProbabilityStore calculateExtendedConditionals(
                double topBaseHeight, double bottomBaseHeight,
                PatternInfo centerPattern,
                ConditionalProbabilityStore leftConditionalProbabilityProbabilities,
                ConditionalProbabilityStore rightConditionalProbabilityProbabilities
        );

        /**
         * Extend left and right conditionals and then calculate flat conditionals.
         *
         * @param topBaseHeight The height at the top of the branch.
         * @param bottomBaseHeight The height at the bottom of the branch.
         * @param centerPattern Pattern information at this node.
         * @param leftConditionalProbabilityProbabilities Left child probabilities (may be overwritten).
         * @param rightConditionalProbabilityProbabilities Right child probabilities (may be overwritten).
         * @return Resulting conditional probabilities.
         * Note: Left and right stores should not be used after this call.
         */
        public ConditionalProbabilityStore calculatePostExtendedFlatConditionals(
                double topBaseHeight, double bottomBaseHeight,
                PatternInfo centerPattern,
                ConditionalProbabilityStore leftConditionalProbabilityProbabilities,
                ConditionalProbabilityStore rightConditionalProbabilityProbabilities
        );

        /**
         * Calculate ascendent conditional probabilities for a node.
         *
         * @param topBaseHeight    the upper height of the branch
         * @param bottomBaseHeight the lower height of the branch
         * @param centerPattern    the site pattern information at this node
         * @param ascendentConditionalProbabilityProbabilities conditional probabilities from the ascendent (parent) side
         * @param otherConditionalProbabilityProbabilities conditional probabilities from the other (child/sibling) side
         * @return the calculated ascendent conditional probability store
         */
        public ConditionalProbabilityStore calculateAscendentExtendedConditionals(
                double topBaseHeight, double bottomBaseHeight,
                PatternInfo centerPattern,
                ConditionalProbabilityStore ascendentConditionalProbabilityProbabilities,
                ConditionalProbabilityStore otherConditionalProbabilityProbabilities
        );

        /**
         * Calculate ascendent flat conditional probabilities for a node.
         *
         * @param centerPattern    the site pattern information at this node
         * @param ascendentConditionalProbabilityProbabilities conditional probabilities from the ascendent (parent) side
         * @param otherConditionalProbabilityProbabilities conditional probabilities from the other (child/sibling) side
         * @return the calculated ascendent flat conditional probability store
         */
        public ConditionalProbabilityStore calculateAscendentFlatConditionals(
                PatternInfo centerPattern,
                ConditionalProbabilityStore ascendentConditionalProbabilityProbabilities,
                ConditionalProbabilityStore otherConditionalProbabilityProbabilities
        );

        /**
         * Calculate flat conditional probabilities for a node given left and right children.
         *
         * @param centerPattern                     the site pattern information at this node
         * @param leftConditionalProbabilityProbabilities  conditional probabilities from the left child
         * @param rightConditionalProbabilityProbabilities conditional probabilities from the right child
         * @return the calculated flat conditional probability store
         */
        public ConditionalProbabilityStore calculateFlatConditionals(
                PatternInfo centerPattern,
                ConditionalProbabilityStore leftConditionalProbabilityProbabilities,
                ConditionalProbabilityStore rightConditionalProbabilityProbabilities
        );
    }

    /**
     * Leaf node interface for conditional probability calculations across a single branch.
     * Can support ambiguous characters or quick implementations depending on the calculator.
     */
    public static interface Leaf {

        /**
         * Calculate extended conditional probabilities for this leaf along a branch.
         *
         * @param topHeight    the upper height of the branch
         * @param bottomHeight the lower height of the branch
         * @return the calculated extended conditional probability store
         */
        public ConditionalProbabilityStore calculateExtendedConditionals(double topHeight, double bottomHeight);

        /**
         * Calculate flat conditional probabilities for this leaf at a given height.
         *
         * @param height the height at which to calculate probabilities
         * @return the calculated flat conditional probability store
         */
        public ConditionalProbabilityStore calculateFlatConditionals(double height);
    }
    /**
     * Interface for simulating sequences along branches.
     */
    public static interface Simulator {

        /**
         * Generate a new sequence based on a base sequence along a branch.
         *
         * @param baseSequence   the starting sequence
         * @param topBaseHeight  the upper height of the branch
         * @param bottomBaseHeight the lower height of the branch
         * @return a newly generated sequence
         */
        public int[] getSimulated(int[] baseSequence, double topBaseHeight, double bottomBaseHeight);

        /**
         * Write simulated sequence into provided array.
         *
         * @param baseSequence   the starting sequence
         * @param topBaseHeight  the upper height of the branch
         * @param bottomBaseHeight the lower height of the branch
         * @param newSequence    the array into which the simulated sequence will be written
         */
        public void simulated(int[] baseSequence, double topBaseHeight, double bottomBaseHeight, int[] newSequence);

        /**
         * Generate a sequence at the root of the tree.
         *
         * @param sampleHeight the height at which to generate the root sequence
         * @return the generated root sequence
         */
        public int[] generateRoot(double sampleHeight);
    }
    /**
     * Converts between branch heights and expected substitution distances.
     */
    public static interface HeightConverter {

        /**
         * Get expected substitution height given a base node height.
         *
         * @param baseHeight the raw height of the node (usually in coalescent or chronological units)
         * @return expected substitution height corresponding to the given base height
         */
        public double getExpectedSubstitutionHeight(double baseHeight);

        /**
         * Get expected substitution distance between two heights.
         *
         * @param lower the lower height (closer to the tips)
         * @param upper the upper height (closer to the root)
         * @return expected substitution distance between lower and upper
         */
        public double getExpectedSubstitutionDistance(double lower, double upper);
    }

    /**
     * Factory and access interface for creating various likelihood calculator components.
     */
    public static interface Instance extends java.io.Serializable {

        /**
         * Returns a summary of the substitution model.
         *
         * @return human-readable summary string for the substitution model
         */
        public String getSubstitutionModelSummary();

        /**
         * Create a new leaf object for a given pattern and height converter.
         *
         * @param converter a {@link HeightConverter} used to translate between branch heights and substitution distances
         * @param pattern the pattern information associated with the leaf
         * @param patternStateMatchup an array mapping alignment states to model states
         * @return a new {@link Leaf} object configured for the given parameters
         */
        public Leaf createNewLeaf(HeightConverter converter, PatternInfo pattern, int[] patternStateMatchup);

        /**
         * Create a new external calculator object.
         *
         * @param converter a {@link HeightConverter} used to translate between branch heights and substitution distances
         * @return a new {@link External} calculator
         */
        public External createNewExternal(HeightConverter converter);

        /**
         * Create a new internal calculator object.
         *
         * @param converter a {@link HeightConverter} used to translate between branch heights and substitution distances
         * @return a new {@link Internal} calculator
         */
        public Internal createNewInternal(HeightConverter converter);

        /**
         * Create a conditional probability store appropriate for leaf or internal nodes.
         *
         * @param isForLeaf true if the store should be suitable for a leaf node, false for internal nodes
         * @return a {@link ConditionalProbabilityStore} appropriate for the specified node type
         */
        public ConditionalProbabilityStore createAppropriateConditionalProbabilityStore(boolean isForLeaf);

        /**
         * Access object for model parameters.
         *
         * @return a {@link NeoParameterized} interface to query and update model parameters
         */
        public NeoParameterized getParameterAccess();
    }

}