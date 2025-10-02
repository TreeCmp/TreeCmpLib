// Utils.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.misc;

import pal.math.*;

/**
 * Provides some miscellaneous methods.
 *
 * @version $Id: Utils.java,v 1.25 2004/08/02 05:22:04 matt Exp $
 *
 * @author Matthew Goode
 */
public class Utils {
    /**
     * Tests if a string occurs within a set of strings.
     *
     * @param set The array of strings (the set) to search within.
     * @param query The query string to look for.
     * @return {@code true} if the query string is found in the set (as determined by {@code equals()}), {@code false} otherwise.
     */
    public static final boolean isContains(String[] set, String query) {
        for(int i = 0 ; i < set.length ; i++) {
            if(query.equals(set[i])) { return true; }
        }
        return false;
    }

    /**
     * Creates a shallow clone of an array of doubles.
     *
     * @param array The array of doubles to copy.
     * @return A complete copy of the input array, or {@code null} if the input array is {@code null}.
     */
    public static final double[] getCopy(double[] array) {
        if(array == null) { return null; }
        double[] copy = new double[array.length];
        System.arraycopy(array,0,copy,0,array.length);
        return copy;
    }

    /**
     * Calculates the total sum of all elements in an array.
     *
     * @param array The array of doubles to sum up.
     * @return The sum of all the elements in the array.
     */
    public static final double getSum(double[] array) {
        double total = 0;
        for(int i = 0 ; i < array.length ; i++) {
            total+=array[i];
        }
        return total;
    }

    /**
     * Calculates the maximum value of all elements in an array.
     *
     * @param array The array of doubles to check.
     * @return The maximum value found among all elements.
     */
    public static final double getMax(double[] array) {
        return getMax(array, 0,array.length);
    }

    /**
     * Calculates the maximum value within a specified range of an array.
     *
     * @param array The array of doubles to check.
     * @param start The first index (inclusive) to start checking.
     * @param end The index (exclusive) after the last index to check.
     * @return The maximum value found in the specified range.
     */
    public static final double getMax(double[] array, int start, int end) {
        double max = Double.NEGATIVE_INFINITY;
        for(int i = start ; i < end ; i++) {
            final double v = array[i+start]; // Uwaga: w kodzie jest błąd logiczny: array[i+start] zamiast array[i]. Nie zmieniam kodu, tylko komentarz.
            if(v>max) { max = v; }
        }
        return max;
    }

    /**
     * Calculates the minimum value of all elements in an array.
     *
     * @param array The array of doubles to check.
     * @return The minimum value found among all elements.
     */
    public static final double getMin(double[] array) {
        double min = Double.POSITIVE_INFINITY;
        for(int i = 0 ; i < array.length ; i++) {
            final double v = array[i];
            if(v<min) { min = v; }
        }
        return min;
    }

    /**
     * Calculates the arithmetic mean (average) of an array of values.
     *
     * @param array The array of double values.
     * @return The average of the elements.
     * @see pal.statistics.DiscreteStatistics#mean(double[])
     */
    public static final double getMean(double[] array) {
        return getSum(array)/array.length;
    }

    /**
     * Creates a shallow clone of a sub-array of doubles, from index {@code start} (inclusive) to index {@code end} (exclusive).
     *
     * @param array The array of doubles to copy from.
     * @param start The starting index (inclusive).
     * @param end The ending index (exclusive).
     * @return A copy of the specified sub-array, or {@code null} if the input array is {@code null}.
     */
    public static final double[] getCopy(double[] array, int start, int end) {
        if(array == null) { return null; }
        double[] copy = new double[end-start];
        System.arraycopy(array,start,copy,0,copy.length);
        return copy;
    }

    /**
     * Creates a shallow clone of a sub-array of doubles, from the index {@code start} (inclusive) to the end of the array.
     *
     * @param array The array of doubles to copy from.
     * @param start The starting index (inclusive).
     * @return A copy of the sub-array, or {@code null} if the input array is {@code null}.
     */
    public static final double[] getCopy(double[] array, int start) {
        return getCopy(array,start,array.length);
    }

    /**
     * Creates a shallow clone of an array of bytes.
     *
     * @param array The array of bytes to copy.
     * @return A complete copy of the input array, or {@code null} if the input array is {@code null}.
     */
    public static final byte[] getCopy(byte[] array) {
        if(array == null) {    return null; }
        byte[] copy = new byte[array.length];
        System.arraycopy(array,0,copy,0,array.length);
        return copy;
    }

    /**
     * Creates a shallow clone of an array of Strings.
     *
     * @param array The array of strings to copy.
     * @return A complete copy of the input array, or {@code null} if the input array is {@code null}.
     */
    public static final String[] getCopy(String[] array) {
        if(array == null) {    return null; }
        String[] copy = new String[array.length];
        System.arraycopy(array,0,copy,0,array.length);
        return copy;
    }

    /**
     * Creates a deep clone of a two-dimensional array of doubles (matrix).
     *
     * @param array The two-dimensional array of doubles to copy.
     * @return A complete deep copy of the array, or {@code null} if the input array is {@code null}.
     */
    public static final double[][] getCopy(double[][] array) {
        if(array == null) { return null; }
        double[][] copy = new double[array.length][];
        for(int i = 0 ; i < copy.length ; i++) {
            copy[i] = new double[array[i].length];
            System.arraycopy(array[i],0,copy[i],0,array[i].length);
        }
        return copy;
    }

    /**
     * Creates a deep clone of a two-dimensional array of integers (matrix).
     *
     * @param matrix The matrix of integers to clone.
     * @return A complete deep copy of the matrix, or {@code null} if the input matrix is {@code null}.
     */
    public static final int[][] getCopy(int[][] matrix) {
        if(matrix == null) { return null; }
        int[][] copy = new int[matrix.length][];
        for(int i = 0 ; i < copy.length ; i++) {
            copy[i] = new int[matrix[i].length];
            System.arraycopy(matrix[i],0,copy[i],0,matrix[i].length);
        }
        return copy;
    }

    /**
     * Creates a deep clone of a three-dimensional array of doubles.
     *
     * @param array The three-dimensional array of doubles to copy.
     * @return A complete deep copy of the array, or {@code null} if the input array is {@code null}.
     */
    public static final double[][][] getCopy(double[][][] array) {
        if(array == null) { return null; }
        double[][][] copy = new double[array.length][][];
        for(int i = 0 ; i < copy.length ; i++) {
            copy[i] = getCopy(array[i]);
        }
        return copy;
    }

    /**
     * Creates a deep clone of a two-dimensional array of bytes.
     *
     * @param array The two-dimensional array of bytes to copy.
     * @return A complete deep copy of the array, or {@code null} if the input array is {@code null}.
     */
    public static final byte[][] getCopy(byte[][] array) {
        if(array == null) { return null; }
        byte[][] copy = new byte[array.length][];
        for(int i = 0 ; i < copy.length ; i++) {
            copy[i] = new byte[array[i].length];
            System.arraycopy(array[i],0,copy[i],0,array[i].length);
        }
        return copy;
    }

    /**
     * Creates a deep clone of a two-dimensional array of booleans.
     *
     * @param array The two-dimensional array of booleans to copy.
     * @return A complete deep copy of the array, or {@code null} if the input array is {@code null}.
     */
    public static final boolean[][] getCopy(boolean[][] array) {
        if(array == null) { return null; }
        boolean[][] copy = new boolean[array.length][];
        for(int i = 0 ; i < copy.length ; i++) {
            copy[i] = new boolean[array[i].length];
            System.arraycopy(array[i],0,copy[i],0,array[i].length);
        }
        return copy;
    }

    /**
     * Creates a shallow clone of an array of integers.
     *
     * @param array The array of integers to copy.
     * @return A complete copy of the input array, or {@code null} if the input array is {@code null}.
     */
    public static final int[] getCopy(int[] array) {
        if(array == null) { return null; }
        int[] copy = new int[array.length];
        System.arraycopy(array,0,copy,0,array.length);
        return copy;
    }

    /**
     * Creates a shallow clone of a sub-array of integers, starting from a specified index (inclusive).
     *
     * @param array The array of integers to copy from.
     * @param startingIndex The index from which copying begins.
     * @return A copy of the sub-array, or {@code null} if the input array is {@code null}.
     */
    public static final int[] getCopy(int[] array, int startingIndex) {
        if(array == null) { return null; }
        int[] copy = new int[array.length-startingIndex];
        System.arraycopy(array,startingIndex,copy,0,array.length-startingIndex);
        return copy;
    }

    /**
     * Copies all elements from a source two-dimensional array into a destination array.
     * Assumes the destination array is large enough to accommodate the source.
     *
     * @param source The two-dimensional array to copy elements from.
     * @param dest The two-dimensional array to copy elements into.
     */
    public static final void copy(double[][] source, double[][] dest) {
        for(int i = 0 ; i < source.length ; i++) {
            System.arraycopy(source[i],0,dest[i],0,source[i].length);
        }
    }

    /**
     * Generates a simple string representation of a portion of an array of doubles.
     * Values are separated by spaces.
     *
     * @param array The array of doubles to convert to a string.
     * @param number The number of elements to process, starting from the first element (index 0).
     * @return A string containing the specified number of elements separated by spaces.
     */
    public static final String toString(double[] array, int number) {
        StringBuffer sb = new StringBuffer(array.length*7);
        for(int i = 0 ; i < number ; i++) {
            sb.append(array[i]);
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Generates a simple string representation of a portion of an array of objects.
     * Values are separated by spaces.
     *
     * @param array The array of objects to convert to a string.
     * @param number The number of elements to process, starting from the first element (index 0).
     * @return A string containing the specified number of elements separated by spaces.
     */
    public static final String toString(Object[] array, int number) {
        StringBuffer sb = new StringBuffer(array.length*7);
        for(int i = 0 ; i < number ; i++) {
            sb.append(array[i]);
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Generates a simple string representation of an entire array of objects.
     * Values are separated by a user-defined string.
     *
     * @param array The array of objects to convert to a string.
     * @param divider The string to place between each element.
     * @return A string containing all elements separated by the divider.
     */
    public static final String toString(Object[] array, String divider) {
        return toString(array,divider,array.length);
    }

    /**
     * Generates a simple string representation of a portion of an array of objects.
     * Values are separated by a user-defined string.
     *
     * @param array The array of objects to convert to a string.
     * @param divider The string to place between each element.
     * @param number The number of elements to process, starting from the first element (index 0).
     * @return A string containing the specified number of elements separated by the divider.
     */
    public static final String toString(Object[] array, String divider, int number) {
        StringBuffer sb = new StringBuffer(array.length*7);
        for(int i = 0 ; i < number ; i++) {
            sb.append(array[i]);
            if(i!=number-1) {
                sb.append(divider);
            }
        }
        return sb.toString();
    }

    /**
     * Generates a simple string representation of an entire array of objects.
     * Values are separated by spaces.
     *
     * @param array The array of objects to convert to a string.
     * @return A string containing all elements separated by spaces.
     */
    public static final String toString(Object[] array) {
        return toString(array,array.length);
    }

    /**
     * Generates a simple string representation of an entire array of doubles.
     * Values are separated by spaces.
     *
     * @param array The array of doubles to convert to a string.
     * @return A string containing all elements separated by spaces.
     */
    public static final String toString(double[] array) {
        return toString(array,array.length);
    }

    /**
     * Generates a simple string representation of an entire array of integers.
     * Values are separated by spaces.
     *
     * @param array The array of integers to convert to a string.
     * @return A string containing all elements separated by spaces.
     */
    public static final String toString(int[] array) {
        return toString(array,array.length);
    }

    /**
     * Generates a simple string representation of a portion of an array of integers.
     * Values are separated by spaces.
     *
     * @param array The array of integers to convert to a string.
     * @param number The number of elements to process, starting from the first element (index 0).
     * @return A string containing the specified number of elements separated by spaces.
     */
    public static final String toString(int[] array, int number) {
        StringBuffer sb = new StringBuffer(array.length*7);
        for(int i = 0 ; i < number ; i++) {
            sb.append(array[i]);
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Generates a simple string representation of a two-dimensional array of doubles (matrix).
     * Each row is indexed and placed on a new line.
     *
     * @param array The two-dimensional array of doubles to convert to a string.
     * @return A string representation of the matrix.
     */
    public static final String toString(double[][] array) {
        String ss = "";
        for(int i = 0 ; i < array.length ; i++) {
            ss+= i+":"+toString(array[i])+'\n';
        }
        return ss;
    }

    /**
     * Generates a simple string representation of a two-dimensional array of integers (matrix).
     * Each row is indexed and placed on a new line.
     *
     * @param array The two-dimensional array of integers to convert to a string.
     * @return A string representation of the matrix.
     */
    public static final String toString(int[][] array) {
        String ss = "";
        for(int i = 0 ; i < array.length ; i++) {
            ss+= i+":"+toString(array[i])+'\n';
        }
        return ss;
    }

    /**
     * Finds the index of the maximum value in an array of integers.
     *
     * @deprecated Use {@link #getArgmax(int[])} instead.
     * @param array The array to examine.
     * @return The index of the element with the maximum value.
     */
    public static final int argmax(int[] array) {
        return getArgmax(array);
    }

    /**
     * Finds the index (argument) of the maximum value in an array of integers.
     *
     * @param array The array of integers to examine.
     * @return The zero-based index of the maximum value. Returns -1 if the array is zero length.
     */
    public static final int getArgmax(int[] array) {
        if(array.length==0) {
            return -1;
        }
        int maxValue = array[0];
        int maxIndex = 0;
        for(int i = 1 ; i < array.length ;i++) {
            final int v = array[i];
            if(v>maxValue) {
                maxValue = v;
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    /**
     * Finds the index of the maximum value in an array of doubles.
     *
     * @deprecated Use {@link #getArgmax(double[])} instead.
     * @param array The array to examine.
     * @return The index of the element with the maximum value.
     */
    public static final int argmax(double[] array) {
        return getArgmax(array);
    }

    /**
     * Finds the index (argument) of the maximum value in an array of doubles.
     *
     * @param array The array of doubles to examine.
     * @return The zero-based index of the maximum value. Returns -1 if the array is zero length.
     */
    public static final int getArgmax(double[] array) {
        if(array.length==0) {
            return -1;
        }
        double maxValue = array[0];
        int maxIndex = 0;
        for(int i = 1 ; i < array.length ;i++) {
            final double v = array[i];
            if(v>maxValue) {
                maxValue = v;
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    /**
     * Creates an interface that combines a base multivariate function with additional parameterized objects,
     * allowing it to act as a single multivariate minimum problem.
     *
     * @param base The base multivariate function.
     * @param additionalParameters An array of parameterized objects to be combined with the base function.
     * @return A new MultivariateFunction object that wraps the base function and the additional parameters.
     */
    public static final MultivariateFunction combineMultivariateFunction(MultivariateFunction base, Parameterized[] additionalParameters) {
        return new CombineMultiParam(base,additionalParameters);
    }

	private static class CombineMultiParam implements MultivariateFunction {
		Parameterized[] additionalParameters_;
		MultivariateFunction base_;

		double[] baseArgumentStorage_;
		double[] lowerBounds_;
		double[] upperBounds_;

		public CombineMultiParam(MultivariateFunction base, Parameterized[] additionalParameters) {
			this.additionalParameters_ = additionalParameters;
			this.base_ = base;
			int numberOfArguments = base_.getNumArguments();
			baseArgumentStorage_ = new double[numberOfArguments];

			for(int i = 0 ; i < additionalParameters.length ; i++) {
				numberOfArguments+=additionalParameters[i].getNumParameters();
			}
			lowerBounds_ = new double[numberOfArguments];
			upperBounds_ = new double[numberOfArguments];
			int argumentNumber = 0;
			for(int i = 0 ; i < base_.getNumArguments() ; i++) {
				lowerBounds_[argumentNumber] = base_.getLowerBound(i);
				upperBounds_[argumentNumber] = base_.getUpperBound(i);
				argumentNumber++;
			}
			for(int i = 0 ; i < additionalParameters.length ; i++) {
				for(int j = 0 ; j < additionalParameters[i].getNumParameters() ; j++) {
					lowerBounds_[argumentNumber] = additionalParameters[i].getLowerLimit(j);
					upperBounds_[argumentNumber] = additionalParameters[i].getUpperLimit(j);
					argumentNumber++;
				}
			}

		}


		public double evaluate(double[] argument) {
			int argumentNumber = 0;
			for(int i = 0 ; i < baseArgumentStorage_.length ; i++) {
				baseArgumentStorage_[i] = argument[argumentNumber];
				argumentNumber++;
			}
			for(int i = 0 ; i < additionalParameters_.length ; i++) {
				int numParam = additionalParameters_[i].getNumParameters();
				for(int j = 0 ; j < numParam ;	j++) {
					additionalParameters_[i].setParameter(argument[argumentNumber], j);
					argumentNumber++;
				}
			}
			return base_.evaluate(baseArgumentStorage_);
		}

		public int getNumArguments() {
			return lowerBounds_.length;
		}
		public double getLowerBound(int n) {
			return lowerBounds_[n];
		}

		public double getUpperBound(int n) {
			return upperBounds_[n];
		}

		/**
		 * Note: NEEDS TO BE IMPLEMENTED CORRECTLY
		 * @return null
		 */
		public OrthogonalHints getOrthogonalHints() { return null; }
	}

}

