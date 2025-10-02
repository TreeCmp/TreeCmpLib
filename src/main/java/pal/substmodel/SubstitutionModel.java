// SubstitutionModel.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.substmodel;

import pal.misc.*;
import pal.math.*;
import pal.datatype.*;

import java.io.*;


/**
 * <b>model of sequence substitution (rate matrix + rate variation)</b>.
 * provides a convenient interface for the computation of transition probabilities
 *
 * @version $Id: SubstitutionModel.java,v 1.33 2004/05/19 04:05:21 matt Exp $
 *
 * @author Alexei Drummond
 * @author Matthew Goode
 */
public interface SubstitutionModel extends Parameterized, Report, java.io.Serializable {

	public DataType getDataType();
	public int getNumberOfTransitionCategories();
	public double getTransitionCategoryProbability(int category);
	/**
	 * @return all the category probabilites for each category respectively.
	 * Note: Applications should not alter the returned array in
	 * any way!
	 */
	public double[] getTransitionCategoryProbabilities();
    /**
     * Calculates and stores the transition probabilities for all transition groups at the specified branch length.
     * The resulting table is organized as [transition_group][from][to].
     *
     * @param branchLength The evolutionary distance (time or branch length) over which to calculate probabilities.
     * @param tableStore The 3D array where the probabilities will be stored, organized as [transition_group][from][to].
     */
    public void getTransitionProbabilities(double branchLength, double[][][] tableStore);

    /**
     * Calculates and stores the transposed transition probabilities for all transition groups at the specified branch length.
     * The resulting table is organized as [transition_group][to][from].
     *
     * @param branchLength The evolutionary distance (time or branch length) over which to calculate probabilities.
     * @param tableStore The 3D array where the probabilities will be stored, organized as [transition_group][to][from].
     */
    public void getTransitionProbabilitiesTranspose(double branchLength, double[][][] tableStore);

    /**
     * Calculates and stores the transition probabilities for a single category and all transition groups at the specified branch length.
     * The resulting table is organized as [transition_group][from][to].
     *
     * @param branchLength The evolutionary distance (time or branch length) over which to calculate probabilities.
     * @param category The specific category index for which to calculate probabilities.
     * @param tableStore The 2D array where the probabilities will be stored. Note: The organization is [from][to] for the selected category.
     */
    public void getTransitionProbabilities(double branchLength, int category, double[][] tableStore);

    /**
     * Calculates and stores the transposed transition probabilities for a single category and all transition groups at the specified branch length.
     * The resulting table is organized as [transition_group][to][from].
     *
     * @param branchLength The evolutionary distance (time or branch length) over which to calculate probabilities.
     * @param category The specific category index for which to calculate probabilities.
     * @param tableStore The 2D array where the probabilities will be stored. Note: The organization is [to][from] for the selected category.
     */
    public void getTransitionProbabilitiesTranspose(double branchLength, int category, double[][] tableStore);

    /**
     * Returns a double array of the related equilibrium frequencies for all states.
     * Callers should not alter the returned array as it may be used internally by the model.
     *
     * @return A double array containing the equilibrium frequencies.
     */
    public double[] getEquilibriumFrequencies();

    /**
     * Adds a PalObjectListener to be notified of changes to the model's parameters or structure.
     *
     * @param l The PalObjectListener to add.
     */
    public void addPalObjectListener(PalObjectListener l);

    /**
     * Removes a PalObjectListener so it is no longer notified of changes.
     *
     * @param l The PalObjectListener to remove.
     */
    public void removePalObjectListener(PalObjectListener l);

    /**
     * Returns hints used for orthogonalization or specific optimizations, if available.
     *
     * @return An OrthogonalHints object, or {@code null} if no hints are available.
     */
    public OrthogonalHints getOrthogonalHints();

	public Object clone();


	//===========================
	//===== Utils
	/**
	 * A small Utility class for things relating to Substitution Models in general
	 */
	public static class Utils {
		public static final double[][][] generateTransitionProbabilityTables(SubstitutionModel model) {
			int numberOfStates = model.getDataType().getNumStates();
			return new double[model.getNumberOfTransitionCategories()][numberOfStates][numberOfStates];
		}

        /**
         * Creates a substitution model based on a single rate matrix.
         * This model has only one transition category, and thus no independent distribution access
         * (as there is no distribution across transition categories).
         *
         * @param rm The underlying rate matrix (RateMatrix) for the single transition category.
         * @return A new SubstitutionModel object with a single rate category.
         */
        public static final SubstitutionModel createSubstitutionModel(RateMatrix rm) {
            return new SimpleSubstitutionModel(rm);
        }

        /**
         * Creates a substitution model based on a specified rate matrix, data type, and equilibrium frequencies.
         * This model has only one transition category.
         *
         * @param rm The underlying rate matrix (NeoRateMatrix).
         * @param dt The data type (DataType) handled by the model.
         * @param equilibriumFrequencies An array of equilibrium frequencies for the states.
         * @return A new SingleClassSubstitutionModel object.
         */
        public static final SubstitutionModel createSubstitutionModel(NeoRateMatrix rm, DataType dt, double[] equilibriumFrequencies) {
            return new SingleClassSubstitutionModel(rm,dt, equilibriumFrequencies);
        }

        /**
         * Creates a substitution model based on a rate matrix and a rate distribution.
         * The model will have as many transition categories as there are rate categories in the rate distribution.
         * There is no independent distribution access (as rate distributions typically link rate and probability).
         *
         * @param rm The underlying rate matrix (RateMatrix).
         * @param rd The rate distribution (RateDistribution) which defines the categories and their probabilities.
         * @return A new RateDistributionSubstitutionModel object.
         */
        public static final SubstitutionModel createSubstitutionModel(RateMatrix rm, RateDistribution rd) {
            return new RateDistributionSubstitutionModel(rm,rd);
        }

        /**
         * Creates a substitution model based on a rate matrix and a rate distribution, with an option to parameterize the distribution.
         * The model will have as many transition categories as there are rate categories in the rate distribution.
         * There is no independent distribution access (as rate distributions typically link rate and probability).
         *
         * @param rm The underlying rate matrix (RateMatrix).
         * @param rd The rate distribution (RateDistribution) which defines the categories and their probabilities.
         * @param parameteriseDistribution If {@code true}, the distribution parameters are included as part of the substitution model parameters. If {@code false}, the distribution parameters are set from the substitution model's point of view.
         * @return A new RateDistributionSubstitutionModel object.
         */
        public static final SubstitutionModel createSubstitutionModel(RateMatrix rm, RateDistribution rd, boolean parameteriseDistribution) {
            return new RateDistributionSubstitutionModel(rm,rd,parameteriseDistribution);
        }



//======== Private Inner classes
//==============================
		//========= SimpleSubstitutionModel ===============
		//=================================================
		private static class SimpleSubstitutionModel extends Parameterized.ParameterizedUser implements SubstitutionModel {
			private RateMatrix matrixBase_;

			private static final long serialVersionUID = 3054360219040005677L;

			private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
				out.writeByte(1); //Version number
				out.writeObject(matrixBase_);
			}

			private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException{
				byte version = in.readByte();
				switch(version) {
					default : {
						matrixBase_ = (RateMatrix)in.readObject();
						setParameterizedBase(matrixBase_);
						break;
					}
				}
			}


			private SimpleSubstitutionModel(SimpleSubstitutionModel toCopy) {
				this.matrixBase_ = (RateMatrix)toCopy.matrixBase_.clone();
				setParameterizedBase(matrixBase_);
			}

			public SimpleSubstitutionModel(RateMatrix base) {
				super(base);
				this.matrixBase_ = base;
			}

			public DataType getDataType() {
				return matrixBase_.getDataType();
			}
			public int getNumberOfTransitionCategories() {
				return 1;
			}
			public double getTransitionCategoryProbability(int category) {
				return 1;
			}
			public double[] getTransitionCategoryProbabilities() {
				return new double[] { 1 };
			}
			public double[] getEquilibriumFrequencies() {	return matrixBase_.getEquilibriumFrequencies();			}

			public void getTransitionProbabilities(double branchLength, double[][][] store) {
				matrixBase_.setDistance(branchLength);
				matrixBase_.getTransitionProbabilities(store[0]);
			}
			public void getTransitionProbabilities(double branchLength, int category, double[][] store) {
				matrixBase_.setDistance(branchLength);
				matrixBase_.getTransitionProbabilities(store);
			}
			public void getTransitionProbabilitiesTranspose(double branchLength, double[][][] store) {
				matrixBase_.setDistanceTranspose(branchLength);
				matrixBase_.getTransitionProbabilities(store[0]);
			}
			public void getTransitionProbabilitiesTranspose(double branchLength, int category, double[][] store) {
				matrixBase_.setDistanceTranspose(branchLength);
				matrixBase_.getTransitionProbabilities(store);
			}
			public void addPalObjectListener(PalObjectListener l) {
				matrixBase_.addPalObjectListener(l);
			}
			public void removePalObjectListener(PalObjectListener l) {
				matrixBase_.removePalObjectListener(l);
			}
			public OrthogonalHints getOrthogonalHints() {		return null; 	 }

			// interface Report
			public void report(PrintWriter out) {
				matrixBase_.report(out);
			}
			public String toString() {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw,true);
				report(pw);
				return "Simple Substitution Model:\n"+sw.toString();
			}
			public Object clone() {
				return new SimpleSubstitutionModel(this);
			}
			public SubstitutionModel getCopy() {
				return new SimpleSubstitutionModel(this);
			}
		}
		//========= SimpleSubstitutionModel ===============


		//============ RateDistributionSubstitutionModel ===================
		//======================================
		private static class RateDistributionSubstitutionModel extends Parameterized.ParameterizedUser implements SubstitutionModel {
			private RateMatrix matrixBase_;
			private RateDistribution distribution_;
			private int numberOfDistributionCategories_;
			private boolean parameteriseDistribution_;

			private static final long serialVersionUID = -3530291767049646272L;

			private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
				out.writeByte(2); //Version number
				out.writeObject(matrixBase_);
				out.writeObject(distribution_);
				out.writeBoolean(parameteriseDistribution_);
			}

			private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException{
				byte version = in.readByte();
				switch(version) {
					default : {
						matrixBase_ = (RateMatrix)in.readObject();
						distribution_ = (RateDistribution)in.readObject();
						numberOfDistributionCategories_ = distribution_.getNumberOfRates();
						parameteriseDistribution_ = in.readBoolean();
						setParameterizedBase(parameteriseDistribution_ ? Parameterized.Utils.combine(new Parameterized[] {matrixBase_,distribution_}) : matrixBase_);
						break;
					}
					case 1 : {
						matrixBase_ = (RateMatrix)in.readObject();
						distribution_ = (RateDistribution)in.readObject();
						numberOfDistributionCategories_ = distribution_.getNumberOfRates();
						parameteriseDistribution_ = true;
						setParameterizedBase(parameteriseDistribution_ ? Parameterized.Utils.combine(new Parameterized[] {matrixBase_,distribution_}) : matrixBase_);
						break;
					}
				}
			}


			private RateDistributionSubstitutionModel(RateDistributionSubstitutionModel toCopy ) {
				this.matrixBase_ = (RateMatrix)toCopy.matrixBase_.clone();
				this.distribution_ = (RateDistribution)toCopy.distribution_.clone();
				this.parameteriseDistribution_ = toCopy.parameteriseDistribution_;
				this.numberOfDistributionCategories_ = distribution_.getNumberOfRates();
				setParameterizedBase(parameteriseDistribution_ ? Parameterized.Utils.combine(new Parameterized[] {matrixBase_,distribution_}) : matrixBase_);
			}
			public RateDistributionSubstitutionModel( RateMatrix base, RateDistribution distribution) {
				this(base,distribution,true);
			}
			public RateDistributionSubstitutionModel( RateMatrix base, RateDistribution distribution, boolean parameteriseDistribution) {
				super(parameteriseDistribution ? Parameterized.Utils.combine(new Parameterized[] {base,distribution}) : base);
				this.matrixBase_ = base;
				this.parameteriseDistribution_ = parameteriseDistribution;
				this.distribution_ = distribution;
				this.numberOfDistributionCategories_ = distribution_.getNumberOfRates();
			}

			public double[] getTransitionCategoryProbabilities() {
				return distribution_.probability;
			}
			public DataType getDataType() {
				return matrixBase_.getDataType();
			}
			public int getNumberOfTransitionCategories() {
				return distribution_.getNumberOfRates();
			}
			public double getTransitionCategoryProbability(int category) {
				return distribution_.probability[category];
			}
			public double[] getEquilibriumFrequencies() {
				return matrixBase_.getEquilibriumFrequencies();
			}
			public void getTransitionProbabilities(double branchLength, double[][][] store) {
				for(int i = 0 ; i < numberOfDistributionCategories_ ; i++) {
					matrixBase_.setDistance(branchLength*distribution_.rate[i]);
					matrixBase_.getTransitionProbabilities(store[i]);
				}

			}
			public void getTransitionProbabilities(double branchLength, int category, double[][] store) {
				matrixBase_.setDistance(branchLength*distribution_.rate[category]);
				matrixBase_.getTransitionProbabilities(store);
			}
			public void getTransitionProbabilitiesTranspose(double branchLength, double[][][] store) {
				for(int i = 0 ; i < numberOfDistributionCategories_ ; i++) {
					matrixBase_.setDistanceTranspose(branchLength*distribution_.rate[i]);
					matrixBase_.getTransitionProbabilities(store[i]);
				}
			}
			public void getTransitionProbabilitiesTranspose(double branchLength, int category, double[][] store) {
				matrixBase_.setDistanceTranspose(branchLength*distribution_.rate[category]);
				matrixBase_.getTransitionProbabilities(store);
			}

			public void addPalObjectListener(PalObjectListener l) {
				matrixBase_.addPalObjectListener(l);
				distribution_.addPalObjectListener(l);
			}
			public void removePalObjectListener(PalObjectListener l) {
				matrixBase_.removePalObjectListener(l);
				distribution_.removePalObjectListener(l);
			}
			public OrthogonalHints getOrthogonalHints() {		return null; 	 }

			public boolean isParameterBaseIncludingDistribution() { return parameteriseDistribution_; }

			// interface Report
			public void report(PrintWriter out) {
				matrixBase_.report(out);
				out.println();
				distribution_.report(out);
			}

			public String toString() {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw,true);
				report(pw);
				return "Substitution Model (with Rate Distribution):\n"+sw.toString();
			}
			public Object clone() {
				return new RateDistributionSubstitutionModel(this);
			}
			public SubstitutionModel getCopy() {
				return new RateDistributionSubstitutionModel(this);
			}
		}
	}
}
