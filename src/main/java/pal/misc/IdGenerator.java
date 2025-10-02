// IdGenerator.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.misc;

import java.math.*;
import pal.io.*;

/**
 * Generates IdGroup objects given certain parameters. 
 * 
 * @version $Id: IdGenerator.java,v 1.3 2001/07/13 14:39:13 korbinian Exp $
 *
 * @author Alexei Drummond
 */
public class IdGenerator {

    /**
     * Generates a group of unique {@link Identifier} objects numbered from zero.
     * Each identifier is a zero-padded string according to the total size.
     *
     * @param size the number of identifiers to generate
     * @return an {@link IdGroup} containing {@code size} unique identifiers named "000", "001", ..., up to {@code size-1} with zero-padding
     */
    public static IdGroup createIdGroup(int size) {

        int width = (int) Math.ceil(Math.log(size) / Math.log(10.0));

        IdGroup idGroup = new SimpleIdGroup(size);

        String name;
        for (int i = 0; i < size; i++) {
            name = Integer.toString(i);
            name = FormattedOutput.space(width - name.length(), '0') + name;
            idGroup.setIdentifier(i, new Identifier(name));
        }

        return idGroup;
    }
}

