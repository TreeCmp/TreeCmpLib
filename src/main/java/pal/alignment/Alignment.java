// Alignment.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.alignment;

import java.io.*;

import pal.datatype.*;
import pal.misc.*;


/**
 * interface for any alignment data.
 *
 * @version $Id: Alignment.java,v 1.15 2003/03/23 00:12:57 matt Exp $
 *
 * @author Alexei Drummond
 * @author Korbinian Strimmer
 * Note: Removed setDataType(), setFrequencies(), and getFrequencies() from interface
 * As they seem better placed in concrete implementations only and should not be a requirement of an alignment, or in the case of Frequencies, can be
 * found using Alignment.Utils.estimateFrequencies(Alignment)
 */
public interface Alignment extends Serializable, IdGroup
{
	//
	// Public stuff
	//

	/** character used to designate gaps */
	static char GAP = DataType.PRIMARY_SUGGESTED_GAP_CHARACTER;

	/** character used to designate unknown characters */
	static char UNKNOWN = DataType.UNKNOWN_CHARACTER;
	/** character used to designate unknown characters */
	static String UNKNOWN_TLA = DataType.UNKNOWN_TLA;

	/**
	 * A three letter acronym version of a gap.
	 */
	static String GAP_TLA = ""+GAP+GAP+GAP;

	/** Characters that might be used as gaps */
	static String GAPS = "_-?.";

	// Abstract method

    /**
     * Returns the character at the given sequence and site in the alignment.
     *
     * @param seq  the index of the sequence.
     * @param site the index of the site within the sequence.
     * @return the character at the specified sequence and site.
     * @throws IndexOutOfBoundsException if the sequence or site index is out of range.
     */
    char getData(int seq, int site);

    /**
     * Returns the number of sites for each sequence in this alignment.
     *
     * @return the number of sites.
     */
    int getSiteCount();

    /**
     * Returns the number of sequences in this alignment.
     *
     * @return the number of sequences.
     */
    int getSequenceCount();

    /**
     * Returns the DataType of this alignment.
     *
     * @return the DataType of the alignment.
     */
    DataType getDataType();

    /**
     * Returns a string representation of a single sequence in the alignment,
     * including gap characters.
     *
     * @param sequence the index of the sequence to return.
     * @return the aligned sequence as a string.
     * @throws IndexOutOfBoundsException if the sequence index is out of range.
     */
    String getAlignedSequenceString(int sequence);

}
