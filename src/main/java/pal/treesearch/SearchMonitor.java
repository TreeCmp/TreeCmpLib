// SearchMonitor.java
//
// (c) 1999-2003 PAL Development Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.treesearch;

/**
 * <p>Title: SearchMonitor </p>
 * <p>Description: A class that receives information about the state of the search </p>
 * @author Matthew Goode
 * @version 1.0
 */

public interface SearchMonitor {
    /**
     * Notifies the listener that a single step or iteration of the tree search algorithm has been completed.
     * This is a critical callback used to report progress, monitor convergence, and potentially trigger subsequent actions.
     *
     * @param logLikelihood The log likelihood value achieved by the tree topology and parameters at the completion of this step.
     * Note: When this method returns, the state of the tree object should be stable, making it safe to access tree search methods (e.g., for exporting or building a PAL tree representation).
     */
    public void searchStepComplete(double logLikelihood);
	public static final class Utils {
	  public static final SearchMonitor createNullMonitor() {
		  return Null.INSTANCE;
		}
		private static final class Null implements SearchMonitor {
			public static final SearchMonitor INSTANCE = new Null();
		  private Null() { }
			public void searchStepComplete(double logLikelihood) {}
		}
	}
}