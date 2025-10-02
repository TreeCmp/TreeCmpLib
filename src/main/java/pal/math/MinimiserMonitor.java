// MultivariateMonitor.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.math;
import java.io.*;
/**
 * interface for a classes that wish to monitor the progress of a Minimiser
 *
 * @author Matthew Goode
 */

public interface MinimiserMonitor {
	/**
	 * Inform monitor of current progress (as a number between 0 and 1), or -1 to reset
     *
     * @param progress a number between {@code 0.0} and {@code 1.0} representing progress,
     *                 or {@code -1.0} to reset the progress indicator
     */
	public void updateProgress(double progress);

	/**
	 * Inform monitor of a new minimum, along with the current arguments. Monitors should NOT
	 * change the supplied array of parameterValues!
	 * This should be called in the same thread as the minimisation so that beingOptimized may be accessed
	 * within this call with out worry of conflicting with the optimisation process!
     *
     * @param value the minimum value found
     * @param parameterValues the argument values corresponding to this minimum
     * @param beingOptimized the function being optimized
     */
	public void newMinimum(double value, double[] parameterValues, MultivariateFunction beingOptimized);
//=====================================================================
//=====================================================================

	public static class Utils {

		/**
		 * Creates a MinimiserMonitor that outputs current minimum to a print stream
         *
         * @param output the print writer to which results will be written
         * @return a monitor that writes progress and results to {@code output}
         */
		public static final MinimiserMonitor createSimpleMonitor(PrintWriter output) {
			return new Simple(output);
		}
		/**
		 * Creates a monitor such that all information sent to monitor is based on two sub monitors
         *
         * @param a the first monitor
         * @param b the second monitor
         * @return a monitor that sends updates to both {@code a} and {@code b}
         */
		public static final MinimiserMonitor createSplitMonitor(MinimiserMonitor a, MinimiserMonitor b) {
			return new Split(a,b);
		}

		/**
		 * Creates a MinimiserMonitor that outputs current minimum to a System.out
         *
         * @return a monitor writing results to standard output
         */
		public static final MinimiserMonitor createSystemOuptutMonitor() {
			return SystemOutput.INSTANCE;
		}
		/**
		 * Creates a MinimiserMonitor that outputs current minimum to a System.err
         *
         * @return a monitor writing results to standard error
         */
		public static final MinimiserMonitor createSystemErrorMonitor() {
			return SystemError.INSTANCE;
		}
		/**
		 * Creates a MinimiserMonitor that Stores output (use toString() to access current results)
         *
         * @return a monitor storing results as a string
         */
		public static final MinimiserMonitor createStringMonitor() {
			return new StringMonitor();
		}
		/**
		 * Creates a MinimiserMonitor that looses all output
         *
         * @return a monitor that discards all information
         */
		public static final MinimiserMonitor createNullMonitor() {
			return NullMonitor.INSTANCE;
		}

		//=============================================================
		private static final class StringMonitor implements MinimiserMonitor {
			private final StringWriter sw_;
			private final PrintWriter pw_;
			public StringMonitor() {
				this.sw_ = new StringWriter();
				this.pw_ = new PrintWriter(sw_,true);
			}
			public void updateProgress(double progress) {
				pw_.println("Update Progress:"+progress);
			}
			public void newMinimum(double value, double[] parameterValues, MultivariateFunction beingOptimized) {
				pw_.println("New Minimum:"+value);
			}
			public String toString() {
				return sw_.toString();
			}
		}
		//=============================================================
		private static final class NullMonitor implements MinimiserMonitor {
			public static final MinimiserMonitor INSTANCE = new NullMonitor();
			public NullMonitor() {   }
			public void updateProgress(double progress) {}
			public void newMinimum(double value, double[] parameterValues, MultivariateFunction beingOptimized) {}
			public String toString() { return "Null Monitor";   }
		}


		private static class Split implements MinimiserMonitor {
			private final MinimiserMonitor a_;
			private final MinimiserMonitor b_;

			public Split(MinimiserMonitor a, MinimiserMonitor b) {
				this.a_ = a; this.b_ = b;
			}
			public void updateProgress(double progress) {
				a_.updateProgress(progress);
				b_.updateProgress(progress);
			}
			public void newMinimum(double value, double[] parameterValues, MultivariateFunction mf) {
				a_.newMinimum(value,parameterValues,mf);
				b_.newMinimum(value,parameterValues,mf);
			}
		}
		private static class Simple implements MinimiserMonitor {
			PrintWriter output_;
			Simple(PrintWriter output) {
				this.output_ = output;
			}
			public void updateProgress(double progress) {		}
			public void newMinimum(double value, double[] parameterValues, MultivariateFunction mf) {
				output_.println("New Minimum:"+value);
			}
		}
		private static class SystemOutput implements MinimiserMonitor {
			static final SystemOutput INSTANCE = new SystemOutput();
			public void updateProgress(double progress) {		}
			public void newMinimum(double value, double[] parameterValues, MultivariateFunction mf) {
				System.out.println("New Minimum:"+value);
			}
		}
		private static class SystemError implements MinimiserMonitor {
			static final SystemError INSTANCE = new SystemError();
			public void updateProgress(double progress) {		}
			public void newMinimum(double value, double[] parameterValues, MultivariateFunction mf) {
				System.err.println("New Minimum:"+value);
			}
		}
	}
}