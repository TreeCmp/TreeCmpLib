// UnivariateFunction.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.math;


/**
 * interface for a function of one variable
 *
 * @author Korbinian Strimmer
 */
public interface UnivariateFunction
{
    /**
     * Computes the value of the function for a given argument.
     *
     * @param argument the input value at which to evaluate the function
     * @return the value of the function at the specified argument
     */
    double evaluate(double argument);
	
	/**
	 * get lower bound of argument
	 *
	 * @return lower bound
	 */
	double getLowerBound();
	
	/**
	 * get upper bound of argument
	 *
	 * @return upper bound
	 */
	double getUpperBound();
}
