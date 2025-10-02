// LHCalculator.java
//
// (c) 1999-2003 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.eval;

/**
 * <p>Title: LHCalculator </p>
 * <p>Description: An LHCalculator object must be treated as a stateful, single threaded object that can be used
 * for calculating components in an overall likelihood calculation. </p>
 * <p>History<br>
 *  <ul>
 *    <li>25/10/2003 Added leaf handling interface </li>
 *    <li>30/3/2004 Changed certain methods to more intelligent ones (relating to posterior distribution of sites). Added abstract External class.
 *  </ul>
 * </p>
 * @author Matthew Goode
 * @version 1.0
 * Note: needs to have the use of the word likelihood altered in certain cases (to conditional probability)
 *
 */
import pal.datatype.*;
import pal.substmodel.*;

public interface LHCalculator {
  /**
   * The External calculator does not maintain any state and is approapriate for
   * calculation where a store is provided
   */
  public static interface External extends java.io.Serializable {
    /**
     *
     * @param centerPattern the pattern information
     * @param leftConditionalProbabilities Implementations must not overwrite or change
     * @param rightConditionalProbabilities Implementations must not overwrite or change
     * @param resultStore Where to stick the created categoryPatternState information
     * Note: calls to getLastConditionalProbabilities() does not have to be valid after call this method
     */
    public void calculateFlat( PatternInfo centerPattern, ConditionalProbabilityStore leftConditionalProbabilities, ConditionalProbabilityStore rightConditionalProbabilities, ConditionalProbabilityStore resultStore );

      /**
       * Computes the extended conditional probabilities for a given branch distance.
       * <p>This method combines information from left and right child nodes to populate
       * the result store with the conditional probabilities for each category and pattern state.</p>
       *
       * @param distance the branch length separating the parent from its children
       * @param model the {@link SubstitutionModel} describing evolutionary changes
       * @param centerPattern the {@link PatternInfo} representing the site pattern at the parent node
       * @param leftConditionalProbabilities the conditional probabilities from the left child; implementations must not overwrite or modify this
       * @param rightConditionalProbabilities the conditional probabilities from the right child; implementations must not overwrite or modify this
       * @param resultStore the {@link ConditionalProbabilityStore} where the computed conditional probabilities will be stored
       */
    public void calculateExtended( double distance, SubstitutionModel model,
                                   PatternInfo centerPattern,
                                   ConditionalProbabilityStore
                                   leftConditionalProbabilities,
                                   ConditionalProbabilityStore
                                   rightConditionalProbabilities,
                                   ConditionalProbabilityStore resultStore );

      /**
       * Extend the conditional probabilities back in time for a single node over a given distance, using the specified substitution model.
       * This version directly modifies the given conditional probabilities.
       *
       * @param distance the evolutionary distance (branch length) to extend
       * @param model the {@link SubstitutionModel} used for the extension
       * @param numberOfPatterns the number of site patterns
       * @param conditionalProbabilities the {@link ConditionalProbabilityStore} to be extended (modified in place)
       */
      public void calculateSingleExtendedDirect(double distance, SubstitutionModel model,
                                                int numberOfPatterns,
                                                ConditionalProbabilityStore conditionalProbabilities);

      /**
       * Extend the conditional probabilities back in time for a single node over a given distance, using the specified substitution model.
       * This version reads from a base store and writes results into a separate store.
       *
       * @param distance the evolutionary distance (branch length) to extend
       * @param model the {@link SubstitutionModel} used for the extension
       * @param numberOfPatterns the number of site patterns
       * @param baseConditionalProbabilities the {@link ConditionalProbabilityStore} containing the base probabilities to extend
       * @param resultConditionalProbabilities the {@link ConditionalProbabilityStore} where the extended probabilities will be stored
       */
      public void calculateSingleExtendedIndirect(double distance, SubstitutionModel model,
                                                  int numberOfPatterns,
                                                  ConditionalProbabilityStore baseConditionalProbabilities,
                                                  ConditionalProbabilityStore resultConditionalProbabilities);

      /**
       * Calculate the log-likelihood given two subtrees (left and right) and their flat (unextended) likelihood probabilities.
       *
       * @param distance the branch length separating the parent from its children
       * @param model the {@link SubstitutionModel} describing evolutionary changes
       * @param centerPattern the {@link PatternInfo} representing the site pattern at the parent node
       * @param leftFlatConditionalProbabilities the flat conditional probabilities from the left child
       * @param rightFlatConditionalProbabilities the flat conditional probabilities from the right child
       * @param tempStore a temporary store that may be used internally during computation
       * @return the log-likelihood of the parent node given its children
       */
      public double calculateLogLikelihood(double distance, SubstitutionModel model,
                                           PatternInfo centerPattern,
                                           ConditionalProbabilityStore leftFlatConditionalProbabilities,
                                           ConditionalProbabilityStore rightFlatConditionalProbabilities,
                                           ConditionalProbabilityStore tempStore);

      /**
       * Calculate the log-likelihood given two subtrees (left and right) and their extended likelihood probabilities.
       *
       * @param model the {@link SubstitutionModel} describing evolutionary changes
       * @param centerPattern the {@link PatternInfo} representing the site pattern at the parent node
       * @param leftConditionalProbabilities the extended conditional probabilities from the left child
       * @param rightConditionalProbabilities the extended conditional probabilities from the right child
       * @return the log-likelihood of the parent node given its children
       */
      public double calculateLogLikelihood(SubstitutionModel model, PatternInfo centerPattern,
                                           ConditionalProbabilityStore leftConditionalProbabilities,
                                           ConditionalProbabilityStore rightConditionalProbabilities);

      /**
       * Calculate the log-likelihood given the conditional probabilities at the root.
       *
       * @param model the {@link SubstitutionModel} used
       * @param patternWeights the weights of each site pattern
       * @param numberOfPatterns the number of site patterns
       * @param conditionalProbabilityStore the conditional probabilities at the root
       * @return the log-likelihood of the root given its conditional probabilities
       */
      public double calculateLogLikelihoodSingle(SubstitutionModel model, int[] patternWeights, int numberOfPatterns,
                                                 ConditionalProbabilityStore conditionalProbabilityStore);

      /**
       * Calculate the conditional probabilities of each pattern for each category for a rooted node.
       *
       * @param model the {@link SubstitutionModel} used
       * @param centerPattern the {@link PatternInfo} representing the site pattern at the parent node
       * @param leftConditionalProbabilitiesStore the conditional probabilities from the left child
       * @param rightConditionalProbabilitiesStore the conditional probabilities from the right child
       * @return a {@link SiteDetails} object containing a matrix [category][pattern] representing the site probabilities under each category (not multiplied by category probability or pattern weights)
       */
      public SiteDetails calculateSiteDetailsRooted(SubstitutionModel model,
                                                    PatternInfo centerPattern,
                                                    ConditionalProbabilityStore leftConditionalProbabilitiesStore,
                                                    ConditionalProbabilityStore rightConditionalProbabilitiesStore);

      /**
       * Calculate the conditional probabilities of each pattern for each category for an unrooted node.
       *
       * @param distance the branch length between the two nodes
       * @param model the {@link SubstitutionModel} used
       * @param centerPattern the {@link PatternInfo} representing the site pattern at the parent node
       * @param leftConditionalProbabilitiesStore the conditional probabilities from the left child
       * @param rightConditionalProbabilitiesStore the conditional probabilities from the right child
       * @param tempStore a temporary store for intermediate computation
       * @return a {@link SiteDetails} object containing a matrix [category][pattern] representing the site probabilities under each category (not multiplied by category probability or pattern weights)
       */
      public SiteDetails calculateSiteDetailsUnrooted(double distance, SubstitutionModel model,
                                                      PatternInfo centerPattern,
                                                      ConditionalProbabilityStore leftConditionalProbabilitiesStore,
                                                      ConditionalProbabilityStore rightConditionalProbabilitiesStore,
                                                      ConditionalProbabilityStore tempStore);
  } //End of class External
// =================================================================================================
// ================= Internal ======================================================================
// =================================================================================================
  /**
   * The Internal calculator may maintain state and is approapriate permanent attachment
   * to internal nodes of the tree structure
   */
  public static interface Internal {
    /**
     * calculate flat probability information (not extended over a branch).
     * @param centerPattern the pattern information
     * @param leftConditionalProbabilities Implementations should be allowed to overwrite in certain cases
     * @param rightConditionalProbabilities Implementations should be allowed to overwrite in certain cases
     * @return true if results built from cached information
     * Note: An assumption may be made that after a call to this method the leftConditionals and rightConditionals are not used again!
     */
    public ConditionalProbabilityStore calculateFlat( PatternInfo centerPattern, ConditionalProbabilityStore leftConditionalProbabilities, ConditionalProbabilityStore rightConditionalProbabilities );

      /**
       * Extends the conditional probabilities back in time by a given distance using a substitution model.
       *
       * @param distance the evolutionary distance to extend by
       * @param model the substitution model to use for the extension
       * @param centerPattern the pattern information for the central node
       * @param leftConditionalProbabilities the conditional probabilities for the left subtree
       * @param rightConditionalProbabilities the conditional probabilities for the right subtree
       * @param modelChangedSinceLastCall true if the substitution model has changed since the last call on this object; false otherwise
       * @return a {@link ConditionalProbabilityStore} containing the resulting conditional probabilities
       * Note: after calling this method, it is assumed that the left and right conditional probability stores are no longer used
       */
      public ConditionalProbabilityStore calculateExtended(
              double distance,
              SubstitutionModel model,
              PatternInfo centerPattern,
              final ConditionalProbabilityStore leftConditionalProbabilities,
              final ConditionalProbabilityStore rightConditionalProbabilities,
              boolean modelChangedSinceLastCall
      );

      /**
       * Computes post-extended flat conditional probabilities for a given distance and model.
       *
       * @param distance the evolutionary distance to extend by
       * @param model the substitution model to use
       * @param centerPattern the pattern information for the central node
       * @param leftConditionalProbabilities the conditional probabilities for the left subtree
       * @param rightConditionalProbabilities the conditional probabilities for the right subtree
       * @param modelChangedSinceLastCall true if the substitution model has changed since the last call on this object; false otherwise
       * @return a {@link ConditionalProbabilityStore} containing the resulting flat conditional probabilities
       * Note: after calling this method, it is assumed that the left and right conditional probability stores are no longer used
       */
      public ConditionalProbabilityStore calculatePostExtendedFlat(
              double distance,
              SubstitutionModel model,
              PatternInfo centerPattern,
              final ConditionalProbabilityStore leftConditionalProbabilities,
              final ConditionalProbabilityStore rightConditionalProbabilities,
              boolean modelChangedSinceLastCall
      );

  } //End of Internal

// =================================================================================================
// ================= Leaf ==========================================================================
// =================================================================================================
	/**
	 * A LHCalculator.Leaf object is attached to each leaf node and can be used to calculated conditional probabilities across the related branch.
	 * Allows for quick implementations as well as implementations that cope correctly with ambiguous characters
	 * Note: Should not be made serializable!
	 */
	public static interface Leaf {
		public ConditionalProbabilityStore getFlatConditionalProbabilities();
		public ConditionalProbabilityStore getExtendedConditionalProbabilities( double distance, SubstitutionModel model, boolean modelChanged);
		/**
		 * Create a new Leaf calculator that has exactly the same properties as this one (but is different such that it may be used independently)
		 * @return a copy of this leaf calculator
		 */
		public Leaf getCopy();
	}

  public static interface Factory extends java.io.Serializable  {
    public Generator createSeries( int numberOfCategories, DataType dt );
  }

  public static interface Generator extends java.io.Serializable  {
		/**
		 * Create anew leaf calculator
		 * @param patternStateMatchup The sequence as reduced to patterns. This should just be one state per pattern.
		 * For example given a sequence [ 0, 1,0,1,3,0] a patternMatchup may be [0,1,3] (the first element is the first
		 * pattern, which is state 0, the second element is the second pattern which is 1, and the third element is the
		 * third pattern (novel pattern) which is state 3)
		 * @param numberOfPatterns The number of patterns in the patternStateMatchup array
		 * @return a leaf calculator object
		 */
    public Leaf createNewLeaf(int[] patternStateMatchup, int numberOfPatterns);

		public Leaf createNewLeaf(int[] patternStateMatchup, int numberOfPatterns, Generator parentGenerator );

		public External createNewExternal();

    public Internal createNewInternal();

		public boolean isAllowCaching();

      /**
       * Creates a new {@link External} conditional probability object.
       * Primarily used by high-accuracy likelihood calculators.
       *
       * @param parentGenerator a reference to the encompassing {@link Generator} that may
       *                        impose its own choices on the creation of {@link ConditionalProbabilityStore}s
       * @return a newly created {@link External} object
       * @throws IllegalArgumentException if this generator does not allow being a subservient generator
       */
      public External createNewExternal(Generator parentGenerator) throws IllegalArgumentException;

      /**
       * Creates a new {@link Internal} conditional probability object.
       * Primarily used by high-accuracy likelihood calculators.
       *
       * @param parentGenerator a reference to the encompassing {@link Generator} that may
       *                        impose its own choices on the creation of {@link ConditionalProbabilityStore}s
       * @return a newly created {@link Internal} object
       * @throws IllegalArgumentException if this generator does not allow being a subservient generator
       */
      public Internal createNewInternal(Generator parentGenerator) throws IllegalArgumentException;

      public ConditionalProbabilityStore createAppropriateConditionalProbabilityStore( boolean isForLeaf );

  }
// ======================================================================================
	public abstract class AbstractExternal {
	 		public final SiteDetails calculateSiteDetailsUnrooted(double distance,
      SubstitutionModel model,
      PatternInfo centerPattern,
      ConditionalProbabilityStore leftFlatConditionalProbabilities,
      ConditionalProbabilityStore rightFlatConditionalProbabilities,
      ConditionalProbabilityStore tempStore
			) {
			double[][] store = new double[model.getNumberOfTransitionCategories()][centerPattern.getNumberOfPatterns()];
			calculateCategoryPatternProbabilities(distance, model, centerPattern, leftFlatConditionalProbabilities,rightFlatConditionalProbabilities,tempStore, store);
		  double[] siteLikelihoods =
			  calculateSiteLikelihoods(
				  store,
					model.getTransitionCategoryProbabilities(),
					model.getNumberOfTransitionCategories(),
					centerPattern.getSitePatternMatchup(),centerPattern.getNumberOfSites()
				);
			return
			  SiteDetails.Utils.create(
				  store,false,model,
					centerPattern.getNumberOfPatterns(),
					centerPattern.getSitePatternMatchup(),
					centerPattern.getNumberOfSites(),
					siteLikelihoods
			  );
		}
		private final double[] calculateSiteLikelihoods( double[][] conditionals, final double[] catProbabilities, int numberOfCategories, int[] sitePatternMatchup,  int numberOfSites) {
		  final double[] siteLikeihoods = new double[numberOfSites];
			for(int site= 0 ; site < numberOfSites ; site++) {
				double total = 0;
				int pattern = sitePatternMatchup[site];
				for(int cat = 0 ; cat < numberOfCategories ; cat++) {
				  total+=catProbabilities[cat]*conditionals[cat][pattern];
				}
				siteLikeihoods[site] = total;
			}
			return siteLikeihoods;
		}
	  public final SiteDetails calculateSiteDetailsRooted(SubstitutionModel model,
      PatternInfo centerPattern,
      ConditionalProbabilityStore leftConditionalProbabilitiesStore,
      ConditionalProbabilityStore rightConditionalProbabilitiesStore) {
		  double[][] store = new double[model.getNumberOfTransitionCategories()][centerPattern.getNumberOfPatterns()];
			calculateCategoryPatternProbabilities(model,centerPattern,leftConditionalProbabilitiesStore,rightConditionalProbabilitiesStore,store);
			final  double[] siteLikelihoods =
			  calculateSiteLikelihoods(
				  store,
					model.getTransitionCategoryProbabilities(),
					model.getNumberOfTransitionCategories(),
					centerPattern.getSitePatternMatchup(),centerPattern.getNumberOfSites()
				);
			return SiteDetails.Utils.create(store,false,model,centerPattern.getNumberOfPatterns(),centerPattern.getSitePatternMatchup(),centerPattern.getNumberOfSites(),siteLikelihoods);
		}
		protected abstract void calculateCategoryPatternProbabilities(
				double distance, SubstitutionModel model, PatternInfo centerPattern,
        ConditionalProbabilityStore leftFlatConditionalProbabilities,
        ConditionalProbabilityStore rightFlatConditionalProbabilities,
        ConditionalProbabilityStore tempStore,
        double[][] categoryPatternLogLikelihoodStore
      );

    protected abstract void calculateCategoryPatternProbabilities(
				SubstitutionModel model, PatternInfo centerPattern,
        ConditionalProbabilityStore leftConditionalProbabilities,
        ConditionalProbabilityStore rightConditionalProbabilities,
        double[][] categoryPatternLikelihoodStore
			);
	}
}