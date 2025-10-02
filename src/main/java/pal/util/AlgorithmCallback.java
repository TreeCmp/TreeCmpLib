// AlgorithmCallback.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.util;

/**
 * An AlgorithmCallback is an interface for any algorithm that wishes to communicate back with
 * it's caller while running (eg, to check if should stop)
 * To be intergrated into the use of algorithms that take substantial ammounts of time.
 * <b>History </b>
 *  <ul>
 *   <li> 14/10/2003 - Added PrintWriter stuff </li>
 *  </ul>
 * @version $Id: AlgorithmCallback.java,v 1.4 2004/04/25 22:53:15 matt Exp $
 * @author Matthew Goode
 */
import java.io.*;

public interface AlgorithmCallback {
    /**
     * Checks whether the algorithm should terminate execution prematurely.
     * This method is called intermittently by the running algorithm to respect external control requests.
     *
     * @return {@code true} if the algorithm should cease processing immediately, {@code false} otherwise.
     * Note: Implementations should also check if the algorithm has already produced a valid output before stopping and return {@code false} if output is available.
     */
    public boolean isPleaseStop();
    /**
     * Updates the calling environment with the current progress of the algorithm.
     *
     * @param progress A double value representing the current completion status, expected to be between 0.0 (start) and 1.0 (complete).
     */
    public void updateProgress(double progress);
    /**
     * Resets or clears the current progress display (e.g., resets a progress bar to zero).
     */
    public void clearProgress();
    /**
     * Informs the caller or user interface of the current operational status or phase of the algorithm.
     *
     * @param statusString A descriptive string detailing the current activity or status.
     */
    public void updateStatus(String statusString);
	// ==========================================================================
	// ==== Static utility class
	/**
	 * A Utility class that provides some simple implementations of AlgorithmCallback
	 * that can be used for manipulating callback results
	 */
	public static final class Utils {
		/**
		 * @return an AlgorithmCallback object that never says it is time to stop,
		 * and ignores all status/progress calls
		 */
		public static final AlgorithmCallback getNullCallback() {
			return NullCallback.INSTANCE;
		}
		/**
		 * Construct an algorithm callback that redirects status reports to a print writer
		 * @param pw A print writer object to direct status reports to
		 * @return An algorithm callback
		 */
		public static final AlgorithmCallback getPrintWriterCallback(PrintWriter pw) {
			return new PrintWriterCallback(pw);
		}

		public static final AlgorithmCallback getSystemOutCallback() {
		  return new PrintWriterCallback(new PrintWriter(System.out));
		}
        /**
         * Creates a specialized {@code AlgorithmCallback} object (a sub-callback) that delegates
         * its progress and status updates to a parent callback, adjusting the values proportionally.
         * This is typically used when a complex algorithm divides its work into multiple sub-tasks
         * and needs to map the sub-task's 0-100% completion to a specific segment of the parent task's
         * progress bar (e.g., mapping 0.0-1.0 to the parent's 0.2 to 0.5 range).
         *
         * @param parent The primary {@code AlgorithmCallback} object that will receive the translated progress and status updates.
         * @param id A string identifier or prefix to be prepended to status messages sent by the sub-callback, providing context about the sub-task.
         * @param minProgress The minimum progress value (between 0.0 and 1.0) on the parent callback that corresponds to the sub-callback's start (0.0).
         * @param maxProgress The maximum progress value (between 0.0 and 1.0) on the parent callback that corresponds to the sub-callback's completion (1.0).
         * @return An {@code AlgorithmCallback} object that wraps the parent and translates progress updates.
         */
        public static final AlgorithmCallback getSubCallback(AlgorithmCallback parent, String id, double minProgress, double maxProgress) {
            return new SubCallback(parent,id,minProgress,maxProgress);
        }
		// - - - - - - - - - - - - - - - - - - - - - - - - - - - -- -  -- - - - - -
		private static final class NullCallback implements AlgorithmCallback {
			static final AlgorithmCallback INSTANCE = new NullCallback();
			public void updateStatus(String statusString) {}
			public boolean isPleaseStop() { return false; }
			public void updateProgress(double progress) {}
			public void clearProgress() {}
		}
		// - - - - - - -- - - -- - - - - -- - - - - - - -- - - - -- - - - - -- - -
		private static final class PrintWriterCallback implements AlgorithmCallback {
			private final PrintWriter pw_;
			public PrintWriterCallback(PrintWriter pw) {
				this.pw_ = pw;
			}
			public void updateStatus(String statusString) { pw_.println("Status:"+statusString); }
			public void log(Object logInfo){ pw_.println("Log:"+logInfo); }
			public void logNNL(Object logInfo){  pw_.print(logInfo);}
			public void debug(Object logInfo) {pw_.println("Debug:"+logInfo); }
			public boolean isPleaseStop() { return false; }
			public void updateProgress(double progress) {pw_.println("Progress:"+progress);}
			public void clearProgress() {pw_.println("Clear Progress"); }
		}
		// - - - - - - -- - - -- - - - - -- - - - - - - -- - - - -- - - - - -- - -
		private static final class SubCallback implements AlgorithmCallback {
			private final String id_;
			private final double minProgress_;
			private final double progressRange_;
			private final AlgorithmCallback parent_;
			public SubCallback(AlgorithmCallback parent, String id, double minProgress, double maxProgress) {
				this.id_ = id;
				this.minProgress_ = minProgress;
				this.progressRange_ = maxProgress - minProgress;
				this.parent_ = parent;
			}
			public void updateStatus(String statusString) {		parent_.updateStatus(id_+statusString);			}
			public boolean isPleaseStop() { return parent_.isPleaseStop(); }
			public void updateProgress(double progress) {
				if(progress>=0&&progress<=1) {
					parent_.updateProgress(progressRange_*progress+minProgress_);
				} else {
					System.out.println("Warning: strange usage of progress:"+progress);
					Thread.dumpStack();
				}
			}
			public void clearProgress() {	parent_.clearProgress(); }
		}
	}
}
