/** This file is part of GTP, a program for computing the geodesic distance between phylogenetic trees,
 * and sturmMean, a program for computing the Frechet mean between phylogenetic trees.
    Copyright (C) 2008-2012  Megan Owen, Scott Provan

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */


package polyAlg;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Vector;
import java.util.Arrays;

import distanceAlg1.PhyloTreeEdge;
import distanceAlg1.RatioSequence;

public class Tools {

    /**
     * Truncates a double-precision floating-point number to a specified number of decimal places.
     *
     * <p>This operation discards all digits after the p-th decimal place, without rounding.
     *
     * @param d The double value to be truncated.
     * @param p The number of decimal places to keep.
     * @return The truncated double value.
     */
    public static double truncate (double d, int p) {
        return Math.floor(d * Math.pow(10,p)) / Math.pow(10,p);
    }

	// XXX:  Should be using BigDecimal
	public static double round(double d, int p) {
		return ((double) Math.round(d * Math.pow(10,p)))/ Math.pow(10,p);
	}

    /**
     * Rounds the number {@code d} to have {@code p} significant digits.
     *
     * <p>This method uses the {@code BigDecimal} class with {@code RoundingMode.HALF_EVEN} (Banker's rounding)
     * to achieve the specified precision in terms of significant figures.
     *
     * @param d The double value to be rounded.
     * @param p The desired number of significant digits (precision).
     * @return The double value rounded to {@code p} significant digits.
     */
    public static double roundSigDigits(double d, int p) {
        BigDecimal bd = new BigDecimal(d);
        bd = bd.round(new MathContext(p,RoundingMode.HALF_EVEN));
        return bd.doubleValue();
    }
	
	public static boolean[][] getIncidenceMatrix(Vector<PhyloTreeEdge> edges1, Vector<PhyloTreeEdge> edges2) {
		boolean[][] incidenceMatrix = new boolean[edges1.size()][edges2.size()];
		
		for (int i = 0; i < edges1.size(); i++) {
			
			for (int j = 0; j < edges2.size(); j++) {
				if (edges1.get(i).crosses(edges2.get(j))) {
					incidenceMatrix[i][j] = true;
				}
				else {
					incidenceMatrix[i][j] = false;
				}
			}
		}
		return incidenceMatrix;
	}
	public static Vector<PhyloTreeEdge> myVectorClonePhyloTreeEdge(Vector<PhyloTreeEdge> v) { 
		if (v == null) {
			return null;
		}
		
		Vector<PhyloTreeEdge> newV = new Vector<PhyloTreeEdge>();
		for (int i = 0; i < v.size(); i++) {
			if (v.get(i) == null) {
				newV.add(null);
			}
			else {
				newV.add(v.get(i).clone());
			}
		}
		return newV;
	}
	
	public static Vector<RatioSequence> myVectorCloneRatioSequence(Vector<RatioSequence> v) {
		if (v == null) {
			return null;
		}
		
		Vector<RatioSequence> newV = new Vector<RatioSequence>();
		for (int i = 0; i < v.size(); i++) {
			if (v.get(i) == null) {
				newV.add(null);
			}
			else {
				newV.add(v.get(i).clone());
			}
		}
		return newV;
	}
	public static Vector<String> myVectorCloneString(Vector<String> v) {
		if (v == null) {
			return null;
		}
		
		Vector<String> newV = new Vector<String>();
		for (int i = 0; i < v.size(); i++) {
			if (v.get(i) == null) {
				newV.add(null);
			}
			else {
				newV.add(new String(v.get(i)));
			}
		}
		return newV;
	}

    /**
     * Removes all the {@code PhyloTreeEdge} objects from the vector `v` where the split value is 0,
     * as determined by the edge being "empty" (i.e., {@code edge.isEmpty()} returns true).
     *
     * @param v The vector of {@code PhyloTreeEdge} objects to modify.
     * @return The modified vector {@code v} with empty edges removed.
     */
    public static Vector<PhyloTreeEdge> deleteEmptyEdges(Vector<PhyloTreeEdge> v) {
        int k = 0;
        while (k < v.size()) {
//        System.out.println("v.get(k) is " + v.get(k));
            if (v.get(k).isEmpty()) {
                v.remove(k);
            }
            else {
                k++;
            }
        }
        return v;
    }

    /**
     * Normalizes the coordinate vector `coords` to have a Euclidean length (norm) of 1.
     *
     * <p>If the input vector has a length of 0 (i.e., it's the zero vector), a new vector of the same
     * dimension containing all 0s is returned.
     *
     * @param coords The array of doubles representing the coordinate vector.
     * @return A new array of doubles representing the normalized vector.
     */
    public static double[] normalize(double[] coords) {
        // Compute the length of coords.
        double length = 0;
        for (int i = 0; i < coords.length; i++) {
            length = length + Math.pow(coords[i],2);
        }
        length = Math.sqrt(length);

        // if the vector was all zeros, return the vector.
        if (length == 0) {
            double[] newCoords = new double[coords.length];
            Arrays.fill(newCoords,0.0);
            return newCoords;
        }

        return scaleBy(coords,1/length);
    }

    /**
     * Multiplies the coordinates in `coords` by the scalar value `a`.
     *
     * @param coords The array of doubles representing the coordinate vector.
     * @param a The scalar value to multiply the coordinates by.
     * @return A new array of doubles representing the scaled vector.
     */
    public static double[] scaleBy(double[] coords, double a) {
        double[] newCoords = new double[coords.length];
        for (int i = 0; i < coords.length; i++) {
            newCoords[i] = coords[i]*a;
        }
        return newCoords;
    }

    /**
     * Converts a double array into a single string with its entries separated by spaces.
     *
     * @param array The double array to convert.
     * @return A string containing the elements of the array separated by spaces.
     */
    public static String doubleArray2String(double[] array) {
        String s = "";
        for (double e: array) {
            s = s + e + " ";
        }
        // remove the last space
        s = s.substring(0,s.length()-1);
        return s;
    }
}
