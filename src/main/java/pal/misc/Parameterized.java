// Parameterized.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.misc;

import java.io.*;


/**
 * interface for class with (optimizable) parameters
 *
 * @version $Id: Parameterized.java,v 1.13 2004/08/15 03:00:37 matt Exp $
 *
 * @author Korbinian Strimmer
 */
public interface Parameterized
{
	/**
	 * get number of parameters
	 *
	 * @return number of parameters
	 */
	int getNumParameters();

	/**
	 * set model parameter
	 *
	 * @param param  parameter value
	 * @param n  parameter number
	 */
	void setParameter(double param, int n);

	/**
	 * get model parameter
	 *
	 * @param n  parameter number
	 *
	 * @return parameter value
	 */
	double getParameter(int n);


	/**
	 * set standard errors for model parameter
	 *
	 * @param paramSE  standard error of parameter value
	 * @param n parameter number
	 */
	void setParameterSE(double paramSE, int n);


	/**
	 * get lower parameter limit
	 *
	 * @param n parameter number
	 *
	 * @return lower bound
	 */
	double getLowerLimit(int n);

	/**
	 * get upper parameter limit
	 *
	 * @param n parameter number
	 *
	 * @return upper bound
	 */
	double getUpperLimit(int n);


	/**
	 * get default value of parameter
	 *
	 * @param n parameter number
	 *
	 * @return default value
	 */
	double getDefaultValue(int n);

// ============================================================================

	/**
	 * A Utility class for using as the superclass to subclasses which work by adding functionality to
	 * a general Parameterized object (the base Parameterized object)
	 */
	abstract public static class ParameterizedUser extends PalObjectListener.EventGenerator {
		private Parameterized base_;

		protected ParameterizedUser(Parameterized base) {
			this.base_ = base;
		}

        /**
         * Default constructor.
         * <p>
         * Subclasses should call {@link #setParameterizedBase(Parameterized)} at some point
         * if using this constructor, otherwise the object will not function correctly.
         */
        protected ParameterizedUser() {}

        /**
         * Sets the base parameterized object for this user.
         * <p>
         * This allows the user class to delegate parameter operations to the provided base.
         *
         * @param base the {@link Parameterized} object that will serve as the underlying parameter handler
         */
        protected void setParameterizedBase(Parameterized base) {
            this.base_ = base;
        }

		public final int getNumParameters() { return base_.getNumParameters(); }

		public final void setParameter(double param, int n) {
			base_.setParameter(param,n);
			fireParametersChangedEvent();
		}
		protected double getRandomParameterValue(int parameter) {
			double min = base_.getLowerLimit(parameter);
			double max = base_.getUpperLimit(parameter);
			return Math.random()*(max-min)+min;
		}
		protected void randomiseParameters() {
			final int numberParameters = base_.getNumParameters();
			for(int i = 0 ; i < numberParameters ; i++) {
				base_.setParameter(getRandomParameterValue(i),i);
			}
		}
		public final double getParameter(int n) { return base_.getParameter(n); }

		public final void setParameterSE(double paramSE, int n) { base_.setParameterSE(paramSE,n);  }

		public final double getLowerLimit(int n) {	return base_.getLowerLimit(n);	}

		public final double getUpperLimit(int n) { return base_.getUpperLimit(n); }
		public final double getDefaultValue(int n) { return base_.getDefaultValue(n); }
		/**
		 * Obtain a fresh array containing the current parameter values
		 * @return a newly created double array of the parameter values
		 */
		public final double[] getAllParameters() {
		  double[] parameters = new double[getNumParameters()];
			for(int i = 0 ; i < parameters.length ; i++) {
			  parameters[i] = base_.getParameter(i);
			}
			return parameters;
		}
	}


	/**
	 * A Utility class for using as the superclass to subclasses which are based on double arrays
	 */
	abstract public static class ParameterizedBase extends PalObjectListener.EventGenerator {
		private double[] parameters_;
		private double[] lowerLimits_;
		private double[] upperLimits_;
		private double[] defaultValues_;
		private double[] parametersSE_;
        /**
         * Builds a parameterized object around a set of double arrays.
         * <p>
         * Note: uses the actual passed arrays, so subclasses can maintain references
         * to the arrays, and their values will be reflected in the parameterized interface.
         *
         * @param parameters the array holding the parameter values
         * @param lowerLimits the array holding the lower limits for each parameter
         * @param upperLimits the array holding the upper limits for each parameter
         * @param defaultValues the array holding the default values for each parameter
         */
        protected ParameterizedBase(
                double[] parameters,
                double[] lowerLimits,
                double[] upperLimits,
                double[] defaultValues
        ) {
            setSource(parameters, lowerLimits, upperLimits, defaultValues);
        }

        /**
         * Builds a parameterized object around a set of double arrays, including standard errors.
         * <p>
         * Note: uses the actual passed arrays, so subclasses can maintain references
         * to the arrays, and their values will be reflected in the parameterized interface.
         *
         * @param parameters the array holding the parameter values
         * @param lowerLimits the array holding the lower limits for each parameter
         * @param upperLimits the array holding the upper limits for each parameter
         * @param defaultValues the array holding the default values for each parameter
         * @param parametersSE the array holding standard errors for each parameter
         */
        protected ParameterizedBase(
                double[] parameters,
                double[] lowerLimits,
                double[] upperLimits,
                double[] defaultValues,
                double[] parametersSE
        ) {
            setSource(parameters, lowerLimits, upperLimits, defaultValues, parametersSE);
        }

        /**
         * Constructs an empty parameterized object.
         * <p>
         * The user needs to call {@link #setSource(double[], double[], double[], double[])}
         * or {@link #setSource(double[], double[], double[], double[], double[])} at some
         * point to correctly initialize the object.
         */
        protected ParameterizedBase() { }
        /**
         * Sets the base arrays for parameters, lower limits, upper limits, and default values.
         * <p>
         * Initializes standard errors to a new array of the same length as {@code parameters}.
         *
         * @param parameters the array holding the parameter values
         * @param lowerLimits the array holding the lower limits for each parameter
         * @param upperLimits the array holding the upper limits for each parameter
         * @param defaultValues the array holding the default values for each parameter
         * @throws IllegalArgumentException if the lengths of the arrays do not match
         */
        protected void setSource(
                double[] parameters,
                double[] lowerLimits,
                double[] upperLimits,
                double[] defaultValues
        ) {
            setSource(parameters, lowerLimits, upperLimits, defaultValues, new double[parameters.length]);
        }

        /**
         * Sets the base arrays for parameters, lower limits, upper limits, default values,
         * and parameter standard errors.
         *
         * @param parameters the array holding the parameter values
         * @param lowerLimits the array holding the lower limits for each parameter
         * @param upperLimits the array holding the upper limits for each parameter
         * @param defaultValues the array holding the default values for each parameter
         * @param parametersSE the array holding the standard errors for each parameter
         * @throws IllegalArgumentException if the lengths of the arrays do not match
         */
        protected void setSource(
                double[] parameters,
                double[] lowerLimits,
                double[] upperLimits,
                double[] defaultValues,
                double[] parametersSE
        ) {
            this.parameters_ = parameters;
            this.lowerLimits_ = lowerLimits;
            this.upperLimits_ = upperLimits;
            this.defaultValues_ = defaultValues;
            this.parametersSE_ = parametersSE;
            int length = parameters_.length;
            if (lowerLimits.length != length) { sizeError(); }
            if (upperLimits.length != length) { sizeError(); }
            if (defaultValues.length != length) { sizeError(); }
            if (parametersSE.length != length) { sizeError(); }
        }
		private final void sizeError() {
			throw new IllegalArgumentException("All arrays do not match in size");
		}

		public int getNumParameters() { return parameters_.length; }

		public void setParameter(double param, int n) {
			this.parameters_[n] = param;
			fireParametersChangedEvent();
		}

		public double getParameter(int n) { return this.parameters_[n]; }

		public void setParameterSE(double paramSE, int n) { this.parametersSE_[n] = paramSE;  }

		public double getLowerLimit(int n) {	return lowerLimits_[n];	}
		public double getUpperLimit(int n) { return upperLimits_[n]; }
		public double getDefaultValue(int n) { return defaultValues_[n]; }
		protected double[] getParametersSE() { return parametersSE_; }
	}

	/**
	 * NullParameterized Object
	 * Can be used by subclasses to implement parameterized without actually having paramters
	 */
	abstract public static class Null implements Parameterized {
		protected Null() { }
		public int getNumParameters() { return 0; }
		private double error() { throw new RuntimeException("Assertion error : should not be called as no parameters!"); }
		public void setParameter(double param, int n) { error(); }
		public double getParameter(int n) { return error(); }
		public void setParameterSE(double paramSE, int n) { error(); }

		public double getLowerLimit(int n) {	return error();	}
		public double getUpperLimit(int n) { return error(); }
		public double getDefaultValue(int n) { return error(); }
	}

// ============================================================================
// ============ Utils ==========================================================
    public static final class Utils {
        /**
         * Returns the current parameter values of the given {@link Parameterized} object.
         *
         * @param source the {@link Parameterized} object to extract parameters from
         * @return an array of doubles representing the current parameter values
         */
        public static final double[] getParameters(Parameterized source) {
            double[] params = new double[source.getNumParameters()];
            for (int i = 0; i < params.length; i++) {
                params[i] = source.getParameter(i);
            }
            return params;
        }

        /**
         * Returns the total number of parameters across multiple {@link Parameterized} objects.
         *
         * @param bases an array of {@link Parameterized} objects
         * @return the sum of all parameters in the given objects
         */
        public static final int getTotalNumberOfParameters(Parameterized[] bases) {
            int total = 0;
            for (int i = 0; i < bases.length; i++) {
                total += bases[i].getNumParameters();
            }
            return total;
        }

        /**
         * Sets up lookup arrays to map a flattened parameter index to the base object and
         * the parameter index within that object.
         *
         * @param bases an array of {@link Parameterized} objects
         * @param baseLookup an int array to store the base index for each parameter
         * @param parameterIndexLookup an int array to store the index within the base for each parameter
         * @param totalNumberOfParameters the total number of parameters across all bases
         */
        public final static void setupLookups(Parameterized[] bases, int[] baseLookup, int[] parameterIndexLookup, int totalNumberOfParameters) {
            int baseIndex = 0;
            int parameterIndex = 0;
            for (int i = 0; i < totalNumberOfParameters; i++) {
                while (bases[baseIndex].getNumParameters() <= parameterIndex) {
                    baseIndex++;
                    parameterIndex = 0;
                }
                baseLookup[i] = baseIndex;
                parameterIndexLookup[i] = parameterIndex;
                parameterIndex++;
            }
        }

        /**
         * Wraps given arrays of parameters and limits into a {@link Parameterized} object.
         * Changes to the {@link Parameterized} object reflect in the original arrays.
         *
         * @param parameters the current parameter values
         * @param lowerLimits the lower limits for each parameter
         * @param upperLimits the upper limits for each parameter
         * @param defaultValues the default values for each parameter
         * @return a {@link Parameterized} wrapper around the arrays
         */
        public static final Parameterized createParametizedWrapper(double[] parameters, double[] lowerLimits, double[] upperLimits, double[] defaultValues) {
            return new ParameterizedWrapper(parameters, lowerLimits, upperLimits, defaultValues);
        }

        /**
         * Wraps given arrays of parameters, limits, defaults, and standard errors into a {@link Parameterized} object.
         * Changes to the {@link Parameterized} object reflect in the original arrays.
         *
         * @param parameters the current parameter values
         * @param lowerLimits the lower limits for each parameter
         * @param upperLimits the upper limits for each parameter
         * @param defaultValues the default values for each parameter
         * @param parametersSE the standard errors for each parameter
         * @return a {@link Parameterized} wrapper around the arrays
         */
        public static final Parameterized createParametizedWrapper(double[] parameters, double[] lowerLimits, double[] upperLimits, double[] defaultValues, double[] parametersSE) {
            return new ParameterizedWrapper(parameters, lowerLimits, upperLimits, defaultValues, parametersSE);
        }

        /**
         * Combines multiple {@link Parameterized} objects into a single {@link Parameterized} object.
         *
         * @param bases an array of {@link Parameterized} objects to combine
         * @return a {@link MultiParameterized} object representing all parameters
         */
        public static final Parameterized combine(Parameterized[] bases) {
            return new MultiParameterized(bases);
        }

        /**
         * Combines two {@link Parameterized} objects into a single {@link Parameterized} object.
         * <p>
         * Note: The resulting object may not be serializable.
         *
         * @param baseOne the first {@link Parameterized} object
         * @param baseTwo the second {@link Parameterized} object
         * @return a {@link MultiParameterized} object representing both parameters
         */
        public static final Parameterized combine(Parameterized baseOne, Parameterized baseTwo) {
            return new MultiParameterized(new Parameterized[]{baseOne, baseTwo});
        }

		// ============================================================================
		// ============ ParameterizedWrapper ============================================
		private static final class ParameterizedWrapper extends ParameterizedBase implements Parameterized {
			public ParameterizedWrapper(double[] parameters, double[] lowerLimits, double[] upperLimits, double[] defaultValues) {
				super(parameters,lowerLimits,upperLimits,defaultValues);
			}
			public ParameterizedWrapper(double[] parameters,	double[] lowerLimits, double[] upperLimits,	double[] defaultValues,	double[] parametersSE) {
				super(parameters,lowerLimits,upperLimits,defaultValues, parametersSE);
			}

		}
	}
}
