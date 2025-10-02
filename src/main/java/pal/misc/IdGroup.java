// IdGroup.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.misc;

/**
 * An indexed group of identifiers. For example of group of taxa
 * related by a phylogenetic tree.
 * <BR><B>NOTE:</B> Was called Taxa but not general enough.
 *
 * @version $Id: IdGroup.java,v 1.10 2002/10/27 05:46:28 matt Exp $
 *
 * @author Alexei Drummond
 */
public interface IdGroup extends java.io.Serializable {

    /**
     * Returns the number of identifiers in this group.
     *
     * @return the number of identifiers.
     */
    int getIdCount();

    /**
     * Returns the identifier at the specified index.
     *
     * @param i the index of the identifier to return.
     * @return the Identifier at the given index.
     * @throws IndexOutOfBoundsException if the index is out of range.
     */
    Identifier getIdentifier(int i);

    /**
     * Sets the identifier at the specified index.
     *
     * @param i  the index at which to set the identifier.
     * @param id the Identifier to set.
     * @throws IndexOutOfBoundsException if the index is out of range.
     */
    void setIdentifier(int i, Identifier id);

    /**
     * Returns the index of the identifier with the given name.
     *
     * @param name the name of the identifier to search for.
     * @return the index of the identifier with the given name, or -1 if not found.
     */
    int whichIdNumber(String name);

// ============================================================================
// =================== Utility Class for IdGroup stuff ========================
// ============================================================================
	public static final class Utils{
    /**
     * Checks if all identifiers in the <i>sub</i> group are contained within the <i>full</i> group.
     *
     * @param sub the {@link IdGroup} to check for containment
     * @param full the {@link IdGroup} that should contain all identifiers from {@code sub}
     * @return {@code true} if every identifier in {@code sub} is also in {@code full}, {@code false} otherwise
     */
    public static final boolean isContainedWithin(IdGroup sub, IdGroup full) {
        for (int i = 0; i < sub.getIdCount(); i++) {
            boolean found = false;
            Identifier subID = sub.getIdentifier(i);
            for (int j = 0; j < full.getIdCount(); j++) {
                Identifier fullID = full.getIdentifier(j);
                if (fullID.equals(subID)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if two {@link IdGroup} objects contain exactly the same identifiers, ignoring order.
     * Equality is based on {@link Identifier#equals(Object)}.
     *
     * @param id1 the first {@link IdGroup}
     * @param id2 the second {@link IdGroup}
     * @return {@code true} if both groups contain exactly the same identifiers, {@code false} otherwise
     */
    public static final boolean isEqualIgnoringOrder(IdGroup id1, IdGroup id2) {
        return isContainedWithin(id1, id2) && isContainedWithin(id2, id1);
    }

    /**
     * Finds the index of a given identifier name in an {@link IdGroup}.
     *
     * @param group the {@link IdGroup} to search
     * @param s the name of the identifier to find
     * @return the index of the identifier with name {@code s}, or {@code -1} if not found
     */
    public static final int whichIdNumber(IdGroup group, String s) {
        for (int i = 0; i < group.getIdCount(); i++) {
            if (s.equals(group.getIdentifier(i).getName())) {
                return i;
            }
        }
        return -1;
    }

}
}
