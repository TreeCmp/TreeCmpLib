// Identifier.java
//
// (c) 1999-2000 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.misc;

import java.io.*;
import java.util.*;
import pal.util.Comparable;

/**
 * An identifier for some sampled data. This will most often be
 * for example, the accession number of a DNA sequence, or the
 * taxonomic name that the sequence represents, et cetera.
 *
 * @version $Id: Identifier.java,v 1.9 2002/11/25 05:40:54 matt Exp $
 *
 * @author Alexei Drummond
 */


public class Identifier implements Serializable,
					 pal.util.Comparable, Nameable {

	private String name = null;

	private static final long serialVersionUID=-7873729831795750538L;

    /**
     * Custom serialization method used when writing this object's state to an
     * {@code ObjectOutputStream} for versioning control.
     *
     * <p>This method manually writes a version byte and then sequentially writes
     * the internal fields (specifically {@code name}) for persistence, overriding
     * the default serialization mechanism.
     *
     * @param out The stream to write the object state to.
     * @throws java.io.IOException If an I/O error occurs during writing to the stream.
     */
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
		out.writeByte(1); //Version number
		out.writeObject(name);
	}

    /**
     * Custom deserialization method used when reading this object from an
     * {@code ObjectInputStream} for versioning control.
     *
     * <p>This method reads a version byte and uses a switch statement to handle different
     * serialization formats, ensuring backward compatibility. Currently, it only handles
     * one version by reading the `name` field.
     *
     * @param in The stream to read the object from.
     * @throws java.io.IOException If an I/O error occurs during reading from the stream.
     * @throws ClassNotFoundException If the class of a serialized object (like `name`) cannot be found.
     */
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException{
		byte version = in.readByte();
		switch(version) {
			default : {
				name = (String)in.readObject();
				break;
			}
		}
	}
	public static Identifier ANONYMOUS = new Identifier("");

		public Identifier() {}

		public Identifier(String name) {
	setName(name);
		}

		public String toString() {
	return getName();
		}

		// implements Comparable interface

		public int compareTo(Object c) {

	return getName().compareTo(((Identifier)c).getName());
		}

		public boolean equals(Object c) {

	if (c instanceof Identifier) {
			return getName().equals(((Identifier)c).getName());
	} else return false;
		}

		// implements Nameable interface

		public String getName() {
	return name;
		}

		public void setName(String s) {
	name = s;
		}
    /**
     * Translates an array of {@link Identifier} objects into an array of their names.
     *
     * @param ids the array of identifiers
     * @return an array of strings corresponding to the names of the identifiers
     */
    public final static String[] getNames(Identifier[] ids) {
        String[] names = new String[ids.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = ids[i].getName();
        }
        return names;
    }

    /**
     * Translates an array of {@link Identifier} objects into an array of their names,
     * optionally skipping a particular identifier by index.
     *
     * @param ids the array of identifiers
     * @param toIgnore the index of the identifier to ignore; if less than 0 or greater than or equal to {@code ids.length}, no element is ignored
     * @return an array of strings corresponding to the names of the identifiers, excluding the ignored one if applicable
     */
    public final static String[] getNames(Identifier[] ids, int toIgnore) {
        if (toIgnore < 0 || toIgnore >= ids.length) {
            return getNames(ids);
        }
        String[] names = new String[ids.length - 1];
        int index = 0;
        for (int i = 0; i < ids.length; i++) {
            if (i != toIgnore) {
                names[index] = ids[i].getName();
                index++;
            }
        }
        return names;
    }
    /**
     * Translates an array of strings into an array of {@link Identifier} objects.
     *
     * @param names the array of strings to convert
     * @return an array of {@link Identifier} objects corresponding to the input names
     */
    public final static Identifier[] getIdentifiers(String[] names) {
        Identifier[] ids = new Identifier[names.length];
        for (int i = 0; i < names.length; i++) {
            ids[i] = new Identifier(names[i]);
        }
        return ids;
    }

    /**
     * Translates an {@link IdGroup} into an array of {@link Identifier} objects.
     *
     * @param idGroup the {@link IdGroup} to convert
     * @return an array of {@link Identifier} objects from the given {@link IdGroup}
     */
    public final static Identifier[] getIdentifiers(IdGroup idGroup) {
        Identifier[] ids = new Identifier[idGroup.getIdCount()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = idGroup.getIdentifier(i);
        }
        return ids;
    }

    /**
     * Translates an {@link IdGroup} into an array of strings representing the names of the identifiers.
     *
     * @param ids the {@link IdGroup} to convert
     * @return an array of strings containing the names of all identifiers in the group
     */
    public final static String[] getNames(IdGroup ids) {
        String[] names = new String[ids.getIdCount()];
        for (int i = 0; i < names.length; i++) {
            names[i] = ids.getIdentifier(i).getName();
        }
        return names;
    }

    /**
     * Translates an {@link IdGroup} into an array of strings representing the names of the identifiers,
     * optionally skipping a particular identifier.
     *
     * @param ids the {@link IdGroup} to convert
     * @param toIgnore the index of the identifier to ignore; if less than 0 or greater than or equal to the group size, no element is ignored
     * @return an array of strings containing the names of identifiers, excluding the ignored one if applicable
     */
    public final static String[] getNames(IdGroup ids, int toIgnore) {
        if (toIgnore < 0 || toIgnore >= ids.getIdCount()) {
            return getNames(ids);
        }
        int numberOfIDS = ids.getIdCount();
        String[] names = new String[numberOfIDS - 1];
        int index = 0;
        for (int i = 0; i < numberOfIDS; i++) {
            if (i != toIgnore) {
                names[index] = ids.getIdentifier(i).getName();
                index++;
            }
        }
        return names;
    }

    /**
     * Translates an {@link IdGroup} into an array of strings representing the names of the identifiers,
     * optionally skipping multiple identifiers.
     *
     * @param ids the {@link IdGroup} to convert
     * @param toIgnore an array of indexes of identifiers to ignore; can be unsorted; if {@code null}, no elements are ignored
     * @return an array of strings containing the names of identifiers, excluding the ignored ones if applicable
     */
    public final static String[] getNames(IdGroup ids, int[] toIgnore) {
        if (toIgnore == null) {
            return getNames(ids);
        }

        int numberOfIDS = ids.getIdCount();
        Vector<String> names = new Vector<>(numberOfIDS);

        for (int i = 0; i < numberOfIDS; i++) {
            boolean ignore = false;
            for (int j = 0; j < toIgnore.length; j++) {
                if (toIgnore[j] == i) {
                    ignore = true;
                    break;
                }
            }
            if (!ignore) {
                names.add(ids.getIdentifier(i).getName());
            }
        }
        String[] namesFinal = new String[names.size()];
        names.copyInto(namesFinal);
        return namesFinal;
    }
}

