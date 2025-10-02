// GeneralOptimisable.java
//
// (c) 1999-2004 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.treesearch;

/**
 * <p>Title: GeneralOptimisable </p>
 * <p>Description: </p>
 * @author Matthew Goode
 * @version 1.0
 */
import pal.math.*;

public interface GeneralOptimisable {
	public int getNumberOfOptimisationTypes();

    /**
     * Executes a one-dimensional optimization process, typically to find the optimal branch length
     * that maximizes the log likelihood (or minimizes the negative log likelihood).
     *
     * @param optimisationType An integer code specifying the type of optimization to perform (e.g., branch length, node height).
     * @param minimiser The single dimensional minimization tool (e.g., Golden Section Search or Brent's method) used to find the optimal parameter value.
     * @param tool The construction tool used to calculate the log likelihood for a given parameter value.
     * @param fracDigits The desired number of fractional digits of precision to which the optimization process should converge.
     * @return The optimized log likelihood value achieved after convergence, or a value greater than 0 if no optimization occurred or if the minimization failed to find a lower value.
     */
    public double optimise(int optimisationType, UnivariateMinimum minimiser, GeneralConstructionTool tool, int fracDigits);
}