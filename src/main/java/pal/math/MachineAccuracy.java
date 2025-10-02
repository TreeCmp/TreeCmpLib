package pal.math;

/**
 * Utility class for determining machine accuracy and performing
 * precision-related comparisons.
 *
 * @version $Id: MachineAccuracy.java,v 1.4 2001/09/09 22:17:11 alexi Exp $
 * @author Korbinian Strimmer
 * @author Alexei Drummond
 */
public class MachineAccuracy {

    /** Machine epsilon: the smallest number such that 1 + EPSILON != 1 */
    public static final double EPSILON = 2.220446049250313E-16;

    /** Square root of EPSILON, useful for relative comparisons */
    public static final double SQRT_EPSILON = 1.4901161193847656E-8;

    /** Fourth root of EPSILON, less strict tolerance */
    public static final double SQRT_SQRT_EPSILON = 1.220703125E-4;

    /**
     * Compute machine epsilon from scratch.
     * @return computed machine epsilon
     */
    public static double computeEpsilon() {
        double eps = 1.0;

        while (eps + 1.0 != 1.0) {
            eps /= 2.0;
        }
        eps *= 2.0;

        return eps;
    }

    /**
     * Checks if two doubles are effectively the same within
     * a relative tolerance of SQRT_EPSILON.
     *
     * @param a first number
     * @param b second number
     * @return true if relative difference |a/b - 1| &lt;= SQRT_EPSILON
     */
    public static boolean same(double a, double b) {
        return Math.abs((a / b) - 1.0) <= SQRT_EPSILON;
    }
}
