// CharacterAlignment.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.alignment;

import java.io.*;

import pal.misc.*;

/**
 *  This interface is designed to hold quantitative character states.
 *  Each trait (a quantitative character) has two sets of
 * labels.  One is the traitName, and the second is the environmentName.  Obviously any
 * descriptor could be placed in these two labels, however for printing purposes
 * traitName is printed first.  Double.NaN is assumed to be the missing value.
 *
 * @version $Id: CharacterAlignment.java,v 1.2 2001/09/02 13:19:41 korbinian Exp $
 *
 * @author Ed Buckler
 */

public interface CharacterAlignment extends Serializable, IdGroup, Report,
	TableReport {
      double MISSING=Double.NaN;

    /**
     * Returns the name of the trait corresponding to the given trait index.
     *
     * @param trait the trait index.
     * @return the name of the trait as a String.
     */
    String getTraitName(int trait);

    /**
     * Returns the name of the environment corresponding to the given trait index.
     *
     * @param trait the trait index.
     * @return the environment name as a String.
     */
    String getEnvironmentName(int trait);

    /**
     * Returns the value of a specific trait for a given sequence (taxon).
     *
     * @param seq   the sequence (taxon) index.
     * @param trait the trait index.
     * @return the trait value as a double.
     */
    double getTrait(int seq, int trait);

    /**
     * Returns the number of sequences (taxa) in this alignment or trait dataset.
     *
     * @return the number of sequences as an integer.
     */
    int getSequenceCount();

    /**
     * Returns the number of traits for each sequence in this alignment or dataset.
     *
     * @return the number of traits as an integer.
     */
    int getTraitCount();
}

