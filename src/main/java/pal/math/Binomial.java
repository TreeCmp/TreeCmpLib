// Binomial.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.math;

/**
 * Binomial coefficients
 *
 * @version $Id: Binomial.java,v 1.6 2001/07/13 14:39:13 korbinian Exp $
 *
 * @author Korbinian Strimmer
 */
public class Binomial implements java.io.Serializable
{
	//
	// Public stuff
	//

    /**
     * Computes the binomial coefficient "n choose k".
     *
     * @param n the total number of items
     * @param k the number of items to choose
     * @return the binomial coefficient C(n, k)
     */
    public double choose(double n, double k) {
        n = Math.floor(n + 0.5);
        k = Math.floor(k + 0.5);

        double lchoose = GammaFunction.lnGamma(n + 1.0) -
                GammaFunction.lnGamma(k + 1.0) -
                GammaFunction.lnGamma(n - k + 1.0);

        return Math.floor(Math.exp(lchoose) + 0.5);
    }

    /**
     * Returns the precomputed value of n choose 2.
     *
     * @param n the value of n
     * @return the value of C(n, 2)
     */
    public double getNChoose2(int n) {
        return nChoose2[n];
    }

    /**
     * Sets the maximum value for precomputation of n choose 2 and fills the array.
     *
     * @param max the maximum n for which n choose 2 should be precomputed
     */
    public void setMax(int max) {
        if (nChoose2 == null) {
            precalculate(max);
        } else if (max >= nChoose2.length) {
            precalculate(Math.max(nChoose2.length * 2, max));
        }
    }

	//
	// private stuff
	//

	private double[] nChoose2 = null;

    /**
     * Pre-calculates the values of n choose 2 for all integers from 0 to n.
     * These values are stored in the nChoose2 array for fast lookup.
     *
     * @param n the maximum number of lineages for which n choose 2 should be computed
     */
    private void precalculate(int n) {
        nChoose2 = new double[n + 1];

        for (int i = 0; i <= n; i++) {
            nChoose2[i] = ((double) (i * (i - 1))) * 0.5;
        }
    }
}
