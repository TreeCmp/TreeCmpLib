/** This file is part of sturmMean, a program for computing the 
 * Frechet mean between phylogenetic trees using the geodesic distance.
    Copyright (C) 2008 -2012  Megan Owen, Scott Provan

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

package distanceAlg1;

import java.text.DecimalFormat;
import java.util.Arrays;

public class EdgeAttribute {
    double[] vect;

    public static final double TOLERANCE = 0.00000001;  // compares double values up to TOLERANCE (currently 8 decimal places)

    public EdgeAttribute() {
        this.vect = null;
    }

    public EdgeAttribute(double[] vect) {
        this.vect = vect;
    }

    public EdgeAttribute(String s) {
        try {
            // if s is a vector
            if ((s.charAt(0) == '[') && (s.charAt(s.length() - 1) == ']')) {
                // remove [ and ]
                s = s.substring(1, s.length() - 1);
                // split the string at ','
                String[] elements = s.split(" ");
                this.vect = new double[elements.length];
                // convert vector element strings into doubles
                for (int i = 0; i < elements.length; i++) {
                    vect[i] = Double.parseDouble(elements[i]);
                }
            }
            // else s is just a double (vector of length 1)
            // (or s is neither, but then will be caught by the NumberFormatException)
            else {
                this.vect = new double[1];
                vect[0] = Double.parseDouble(s);
            }
        } catch (NumberFormatException e) {
            System.err.println("Error creating new edge attribute: input string does not have double where expected or bracket problem: " + e.getMessage());
            System.exit(1);
        }
    }


    public void setEdgeAttribute(EdgeAttribute attrib) {
        this.vect = attrib.vect;
    }

    public EdgeAttribute clone() {
        return new EdgeAttribute(Arrays.copyOf(vect, vect.length));
    }

    public String toString() {
        if (vect == null) {
            return "";
        }

        // 10 decimals
        DecimalFormat df = new DecimalFormat("#0.##########");
        if (vect.length == 1) {
            return df.format(vect[0]);
        }

        String str = "[" + df.format(vect[0]);
        for (int i = 1; i < vect.length; i++) {
            str = str + " " + df.format(vect[i]);
        }
        return str + "]";
    }

    // TODO:  only set up to handle the attribute being a vector
    @Override
    public boolean equals(Object e) {
        if (e == null) {
            return false;
        }
        if (this == e) {
            return true;
        }

        if (!(e instanceof EdgeAttribute)) {
            return false;
        }

        // we cannot just use Arrays.equal, since we need to compare the double values with a tolerance.
        for (int i = 0; i < vect.length; i++) {
            if (Math.abs(vect[i] - ((EdgeAttribute) e).vect[i]) > TOLERANCE) {
                return false;
            }
        }

        return true;
    }

    /**
     * Computes the L2 norm (Euclidean norm) of this attribute.
     *
     * @return the L2 norm of the attribute
     */
    public double norm() {
        if (vect == null) {
            return 0.0;
        }

        double norm = 0;
        for (int i = 0; i < vect.length; i++) {
            norm += Math.pow(vect[i], 2);
        }
        return Math.sqrt(norm);
    }

    /**
     * Computes the difference between two EdgeAttribute objects by subtracting the second from the first.
     *
     * @param a1 the first EdgeAttribute
     * @param a2 the second EdgeAttribute
     * @return a new EdgeAttribute representing the difference, or null if both are null
     */
    public static EdgeAttribute difference(EdgeAttribute a1, EdgeAttribute a2) {
        if (a1 == null && a2 == null) {
            System.out.println("Calculating difference between two null edge attributes; returning null");
            return null;
        }
        if (a1 == null) return a2;
        if (a2 == null) return a1;
        if (a1.vect == null) return a2;
        if (a2.vect == null) return a1;
        if (a1.vect.length != a2.vect.length) {
            System.err.println("Error: vectors have different lengths: " + a1 + " and " + a2);
            System.exit(1);
        }

        int diffLength = a1.vect.length;
        EdgeAttribute diff = new EdgeAttribute();
        diff.vect = new double[diffLength];
        for (int i = 0; i < diffLength; i++) {
            diff.vect[i] = a1.vect[i] - a2.vect[i];
        }
        return diff;
    }

    /**
     * Adds another EdgeAttribute to this one element-wise.
     *
     * @param a the EdgeAttribute to add
     */
    public void add(EdgeAttribute a) {
        if (a.vect == null) return;
        if (this.vect.length != a.vect.length) {
            System.err.println("Error: vectors have different lengths: " + this + " and " + a);
            System.exit(1);
        }
        for (int i = 0; i < this.vect.length; i++) {
            this.vect[i] += a.vect[i];
        }
    }

    /**
     * Multiplies two EdgeAttributes element-wise.
     *
     * @param a1 the first EdgeAttribute
     * @param a2 the second EdgeAttribute
     * @return a new EdgeAttribute representing the element-wise product
     */
    public static EdgeAttribute product(EdgeAttribute a1, EdgeAttribute a2) {
        if (a1.vect == null || a2.vect == null) return null;
        if (a1.vect.length != a2.vect.length) {
            System.err.println("Error: vectors have different lengths: " + a1 + " and " + a2);
            System.exit(1);
        }

        int length = a1.vect.length;
        EdgeAttribute prod = new EdgeAttribute();
        prod.vect = new double[length];
        for (int i = 0; i < length; i++) {
            prod.vect[i] = a1.vect[i] * a2.vect[i];
        }
        return prod;
    }

    /**
     * Computes the sum of all elements in this attribute's vector.
     *
     * @return the sum of elements
     */
    public double sumOfAttributeVector() {
        double sum = 0;
        for (int i = 0; i < vect.length; sum += vect[i++]) {
        }
        return sum;
    }

    /**
     * Finds the specified point on the line between start and target attributes.
     * Position should be between 0 (start) and 1 (target). Null can be used to compute distance to/from zero attribute.
     *
     * @param start    the starting EdgeAttribute
     * @param target   the target EdgeAttribute
     * @param position the relative position between 0 and 1
     * @return the interpolated EdgeAttribute at the specified position
     */
    public static EdgeAttribute weightedPairAverage(EdgeAttribute start, EdgeAttribute target, double position) {
        if (start == null && target == null) {
            System.out.println("Calculating point between two null edge attributes; returning null");
            return null;
        }
        if (start == null) start = EdgeAttribute.zeroAttribute(target.size());
        if (target == null) target = EdgeAttribute.zeroAttribute(start.size());
        if (start.vect.length != target.vect.length) {
            System.err.println("Error: vectors have different lengths: " + start + " and " + target);
            System.exit(1);
        }
        if (position < 0 || position > 1) {
            System.err.println("Error: position must be between 0 and 1, got " + position);
        }
        if (start.equals(target)) return start;

        EdgeAttribute point = new EdgeAttribute(new double[start.vect.length]);
        for (int i = 0; i < start.vect.length; i++) {
            point.vect[i] = (1 - position) * start.vect[i] + position * target.vect[i];
        }
        return point;
    }

    /**
     * Scales each element of this attribute by a factor.
     *
     * @param a the scaling factor
     */
    public void scaleBy(double a) {
        for (int i = 0; i < vect.length; i++) {
            vect[i] *= a;
        }
    }

    /**
     * Returns the size (length) of this EdgeAttribute's vector.
     *
     * @return the length of vect
     */
    public int size() {
        return vect == null ? 0 : vect.length;
    }

    /**
     * Ensures the single element in vect is positive if vect has length 1.
     */
    public void ensurePositive() {
        if (vect.length == 1) vect[0] = Math.abs(vect[0]);
    }

    /**
     * Sets the single element to zero if negative. Does nothing if vect has length &gt; 1.
     */
    public void ensureNonNegative() {
        if (vect.length == 1 && vect[0] < 0) vect[0] = 0;
    }

    /**
     * Returns an EdgeAttribute of the given size with all elements set to zero.
     *
     * @param size the length of the vector
     * @return a new EdgeAttribute with all zeros
     */
    public static EdgeAttribute zeroAttribute(int size) {
        if (size < 1) {
            System.err.println("Error: invalid size " + size);
            System.exit(1);
        }
        EdgeAttribute zero = new EdgeAttribute(new double[size]);
        Arrays.fill(zero.vect, 0.0);
        return zero;
    }

    /**
     * Returns the value at the given position in vect.
     *
     * @param position index of the element
     * @return the value at the specified position
     */
    public double get(int position) {
        return vect[position];
    }
}
