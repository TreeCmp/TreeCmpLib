// Comparator.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.util;

/**
 * interface for an object that can compare other objects for the
 * purposes of ordering them.
 * This interface is analogous to the Comparator interface in
 * Java 1.2 and higher, and it should be superceded by the collections
 * framework when PAL is moved to 1.2 or higher.
 *
 * @version $Id: Comparator.java,v 1.2 2001/07/13 14:39:13 korbinian Exp $
 *
 * @author Alexei Drummond
 */
public interface Comparator
{
    /**
     * Compares two objects to establish their relative ordering.
     * This method defines the **total ordering** imposed by this Comparator.
     *
     * @param o1 The first object to be compared.
     * @param o2 The second object to be compared.
     * @return A negative integer if the first object (o1) is considered "less than" the second (o2),
     * a positive integer if o1 is "greater than" o2, and zero if o1 and o2 are considered equal
     * in terms of this defined ordering.
     */
    int compare(Object o1, Object o2);

    /**
     * Indicates whether some other object is "equal to" this one, based on the definition
     * of equality established by this Comparator.
     *
     * @param o1 The first object to test for equality.
     * @param o2 The second object to test for equality.
     * @return true if the two specified objects are considered equal by this Comparator; false otherwise.
     */
    boolean equals(Object o1, Object o2);
}
