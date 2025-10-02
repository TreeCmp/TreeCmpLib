// NeoParameterized.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.misc;

import java.io.*;


/**
 * interface for class with (optimizable) parameters. A replacement for the Parameterized interface with
 * it's irritating updating of one parameter at a time
 *
 * @version $Id: NeoParameterized.java,v 1.1 2004/08/02 05:22:04 matt Exp $
 *
 * @author Korbinian Strimmer, Matthew Goode
 */
public interface NeoParameterized {
	/**
	 * get number of parameters
	 *
	 * @return number of parameters
	 */
	public int getNumberOfParameters();

	/**
	 * set model parameter
	 *
	 * @param parameters the array holding the parameters
	 * @param startIndex the index into the array that the related parameters start at
	 */
	public void setParameters(double[] parameters, int startIndex);

    /**
     * Retrieves model parameters and stores them into the provided array starting at the specified index.
     *
     * @param parameterStore the array where parameters will be stored
     * @param startIndex the starting index in the array to store parameters
     */
    public void getParameters(double[] parameterStore, int startIndex);

    /**
     * Returns the lower bound for the n-th parameter.
     *
     * @param n the index of the parameter
     * @return the lower limit of the specified parameter
     */
    public double getLowerLimit(int n);

    /**
     * Returns the upper bound for the n-th parameter.
     *
     * @param n the index of the parameter
     * @return the upper limit of the specified parameter
     */
    public double getUpperLimit(int n);

    /**
     * Retrieves the default values of parameters and stores them into the provided array starting at the specified index.
     *
     * @param store the array where default values will be stored
     * @param startIndex the starting index in the array to store default values
     */
    public void getDefaultValues(double[] store, int startIndex);

}
