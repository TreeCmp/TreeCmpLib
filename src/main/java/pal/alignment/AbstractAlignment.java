// AbstractAlignment.java
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
 * abstract base class for any alignment data.
 *
 * @version $Id: AbstractAlignment.java,v 1.7 2003/03/23 00:12:57 matt Exp $
 *
 * @author Alexei Drummond
 * @author Korbinian Strimmer
 */
abstract public class AbstractAlignment implements Alignment, Serializable, IdGroup, Report
{
	//
	// Public stuff
	//

	//
	// Protected stuff
	//
	/** number of sequences */
	protected int numSeqs;

	/** length of each sequence */
	protected int numSites;

	/** sequence identifiers */
	protected IdGroup idGroup;

	/** data type */
	private DataType dataType;

	//
	// Serialization code
	//

	private static final long serialVersionUID = -5197800047652332969L;

	//serialver -classpath ./classes pal.alignment.AbstractAlignment
	private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
		out.writeByte(2); //Version number
		out.writeInt(numSeqs);
		out.writeInt(numSites);
		out.writeObject(idGroup);
		out.writeObject(dataType);
	}

	private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException{
		byte version = in.readByte();
		switch(version) {
			case 1 : {
				numSeqs = in.readInt();
				numSites = in.readInt();
				idGroup = (IdGroup)in.readObject();
				double[] frequencyDummy = (double[])in.readObject();
				dataType = (DataType)in.readObject();
				break;
			}
			default : {
				numSeqs = in.readInt();
				numSites = in.readInt();
				idGroup = (IdGroup)in.readObject();
				dataType = (DataType)in.readObject();
				break;
			}
		}
	}

	public AbstractAlignment(){	}

	// Abstract method

	/** sequence alignment at (sequence, site) */
	abstract public char getData(int seq, int site);

    /**
     * Returns true if there is a gap at the given sequence and site position.
     *
     * @param seq  the index of the sequence.
     * @param site the index of the site within the sequence.
     * @return true if the character at the given position is a gap, false otherwise.
     * @throws IndexOutOfBoundsException if seq or site is out of range.
     */
    public boolean isGap(int seq, int site) {
        return dataType.isGapChar(getData(seq, site));
    }

    /**
     * Guess and set the data type for this alignment based on its content.
     */
    public void guessDataType() {
        dataType = AlignmentUtils.getSuitableInstance(this);
    }

    /**
     * Returns the character corresponding to a given state according to the data type.
     *
     * @param state the state index.
     * @return the character representing the state.
     */
    protected final char getChar(int state) {
        return dataType.getChar(state);
    }

    /**
     * Returns the state index corresponding to a given character according to the data type.
     *
     * @param c the character.
     * @return the state index representing the character.
     */
    protected final int getState(char c) {
        return dataType.getState(c);
    }

    /**
     * Checks if the given state represents an unknown value according to the data type.
     *
     * @param state the state index.
     * @return true if the state is unknown, false otherwise.
     */
    protected final boolean isUnknownState(int state) {
        return dataType.isUnknownState(state);
    }

    /**
     * Returns the data type of this alignment.
     *
     * @return the DataType of the alignment.
     */
    public final DataType getDataType() {
        return dataType;
    }

    /**
     * Sets the data type of this alignment.
     *
     * @param d the DataType to set.
     */
    public final void setDataType(DataType d) {
        dataType = d;
    }

    /**
     * Returns a string representation of this alignment.
     *
     * @return a string representing the alignment.
     */
    public String toString() {
        StringWriter sw = new StringWriter();
        AlignmentUtils.print(this, new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Reports the alignment using the provided PrintWriter.
     *
     * @param out the PrintWriter to write the report to.
     */
    public void report(PrintWriter out) {
        AlignmentUtils.report(this, out);
    }

    /**
     * Fills a matrix with the state indices of each character in the alignment.
     * Gaps are represented by -1.
     *
     * @return a 2D array [numSequences][numSites] with state indices.
     */
    public int[][] getStates() {
        int[][] indices = new int[numSeqs][numSites];
        for (int i = 0; i < numSeqs; i++) {
            for (int j = 0; j < numSites; j++) {
                indices[i][j] = dataType.getState(getData(i, j));
                if (indices[i][j] >= dataType.getNumStates()) {
                    indices[i][j] = -1;
                }
            }
        }
        return indices;
    }

    /**
     * Returns the number of sites (columns) in this alignment.
     *
     * @return the number of sites.
     */
    public final int getLength() {
        return numSites;
    }

    /**
     * Returns the number of sequences in this alignment.
     *
     * @return the number of sequences.
     */
    public final int getSequenceCount() {
        return numSeqs;
    }

    /**
     * Returns the number of sites for each sequence in this alignment.
     *
     * @return the number of sites.
     */
    public final int getSiteCount() {
        return numSites;
    }

    /**
     * Returns a string representing a single sequence (including gaps) from this alignment.
     *
     * @param seq the index of the sequence.
     * @return the aligned sequence as a string.
     * @throws IndexOutOfBoundsException if seq is out of range.
     */
    public String getAlignedSequenceString(int seq) {
        char[] data = new char[numSites];
        for (int i = 0; i < numSites; i++) {
            data[i] = getData(seq, i);
        }
        return new String(data);
    }

    //IdGroup interface
	public Identifier getIdentifier(int i) {return idGroup.getIdentifier(i);}
	public void setIdentifier(int i, Identifier ident) { idGroup.setIdentifier(i, ident); }
	public int getIdCount() { return idGroup.getIdCount(); }
	public int whichIdNumber(String name) { return idGroup.whichIdNumber(name); }

}
