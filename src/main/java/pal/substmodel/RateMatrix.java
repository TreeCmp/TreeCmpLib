// RateMatrix.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.substmodel;

import pal.misc.*;
import pal.io.*;
import pal.datatype.*;
import pal.mep.*;
import pal.math.OrthogonalHints;

import java.io.*;


/**
 * abstract base class for all rate matrices
 *
 * @version $Id: RateMatrix.java,v 1.34 2003/11/13 04:05:39 matt Exp $
 *
 * @author Korbinian Strimmer
 * @author Alexei Drummond
 * @author Matthew Goode
 */
public interface RateMatrix
	extends NamedParameterized, Report, Cloneable, Serializable{

	/**
	 * get numerical code describing the data type
	 *
	 * @return integer code identifying a data type
	 */
	int getTypeID();

	/**
	 * get numerical code describing the model type
	 *
	 * @return integer code identifying a substitution model
	 */
	int getModelID();

	/**
	 * @return a short unique human-readable identifier for this rate matrix.
	 */
	String getUniqueName();

	/**
	 * @return the dimension of this rate matrix.
	 */
	int getDimension();

	/**
	 * @return stationary frequencies (sum = 1.0)
	 */
	double[] getEquilibriumFrequencies();

    /**
     * Returns the stationary (equilibrium) frequency for the specified state.
     * The sum of all equilibrium frequencies across all states equals 1.0.
     * This method is the preferred method for infrequent use.
     *
     * @param i The index of the state for which the equilibrium frequency is requested.
     * @return The stationary frequency for the ith state.
     */
    double getEquilibriumFrequency(int i);

    /**
     * Gets the data type handled by this rate matrix (e.g., DNA, Amino Acids).
     *
     * @return The DataType of this rate matrix.
     */
    DataType getDataType();

    /**
     * Returns the relative rate matrix, where the first index represents the 'from' state
     * and the second index represents the 'to' state (transition: from 1st index to 2nd index).
     *
     * @return The 2D array representing the relative rate matrix.
     * @deprecated Try not to use this method directly, as it exposes internal representation.
     */
    double[][] getRelativeRates();

    /**
     * Returns the probability of transitioning from one state to another over the currently set distance.
     *
     * @param fromState The index of the starting state.
     * @param toState The index of the resulting state.
     * @return The transition probability P(toState | fromState) over the current distance.
     */
    double getTransitionProbability(int fromState, int toState);

    /**
     * A utility method designed for speed; it transfers the pre-calculated transition probability
     * information quickly into the provided storage matrix.
     *
     * @param probabilityStore The 2D array where the transition probabilities will be stored (usually [from][to]).
     */
    void getTransitionProbabilities(double[][] probabilityStore);

    /**
     * Sets the evolutionary distance (such as time or branch length) used for calculating
     * the transition probabilities.
     * Note: This method may trigger computationally intensive steps.
     *
     * @param distance The evolutionary distance to be used.
     */
    void setDistance(double distance);

    /**
     * Sets the evolutionary distance (such as time or branch length) used for calculating
     * the transition probabilities.
     * Note: The resulting transition probabilities calculated and stored internally will be in reverse
     * order (i.e., transposed, [to][from] instead of the standard [from][to]).
     *
     * @param distance The evolutionary distance to be used.
     */
    void setDistanceTranspose(double distance);

    /**
     * Adds a listener to be notified of changes to the model (e.g., parameter changes).
     * Only the {@code parametersChanged} method of the listener will generally be called.
     *
     * @param pol The PalObjectListener to add.
     */
    void addPalObjectListener(PalObjectListener pol);

    /**
     * Removes a listener so it is no longer notified of changes to the model.
     *
     * @param pol The PalObjectListener to remove.
     */
    void removePalObjectListener(PalObjectListener pol);

	/**
	 * @return an orthogonal hints object for orthogonal optimisation (may return null for no hints)
	 */
	OrthogonalHints getOrthogonalHints();

	// interface Report (remains abstract)

	// interface Parameterized (remains abstract)

	Object clone();

	public double setParametersNoScale(double[] parameters);
	public void scale(double scaleValue);

}
