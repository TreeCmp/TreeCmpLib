// SplitSystem.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.tree;

import java.io.*;

import pal.misc.*;

/**
 * data structure for a set of splits 
 *
 * @version $Id: SplitSystem.java,v 1.3 2001/07/13 14:39:13 korbinian Exp $
 *
 * @author Korbinian Strimmer
 */
public class SplitSystem
{
	//
	// Public stuff
	//

	/**
	 * @param idGroup  sequence labels
	 * @param size     number of splits
	 */
	public SplitSystem(IdGroup idGroup, int size)
	{
		this.idGroup = idGroup;
		
		labelCount = idGroup.getIdCount();
		splitCount = size;
		
		splits = new boolean[splitCount][labelCount];
	}

    /**
     * Returns the total number of splits (rows) stored in this system.
     *
     * @return The number of splits.
     */
    public int getSplitCount()
    {
        return splitCount;
    }

    /**
     * Returns the number of labels (taxa or leaves) represented in each split (column count).
     *
     * @return The number of labels (taxa) in the system.
     */
    public int getLabelCount()
    {
        return labelCount;
    }

    /**
     * Returns the 2D boolean array representing the entire split system.
     * The array is organized as {@code [split_index][label_index]}.
     *
     * @return The boolean 2D array where {@code true} indicates a label belongs to one side of the split.
     */
    public boolean[][] getSplitVector()
    {
        return splits;
    }

    /**
     * Returns the boolean array for a single split at the specified index.
     *
     * @param i The zero-based index of the split to retrieve.
     * @return The boolean array representing the partition of labels for the split.
     */
    public boolean[] getSplit(int i)
    {
        return splits[i];
    }


    /**
     * Returns the IdGroup object containing the identifiers (labels) corresponding to the columns in the split array.
     *
     * @return The IdGroup associated with this split system.
     */
    public IdGroup getIdGroup()
    {
        return idGroup;
    }

    /**
     * Tests whether a given split is already contained in this split system.
     * This comparison assumes the input split array uses the same leaf order as the internal system.
     *
     * @param split The boolean array representing the split (partition) to test for existence.
     * @return {@code true} if an identical split is found in the system; otherwise, {@code false}.
     */
    public boolean hasSplit(boolean[] split)
    {
        for (int i = 0; i < splitCount; i++)
        {
            if (SplitUtils.isSame(split, splits[i])) return true;
        }

        return false;
    }


    /**
     * Returns a string representation of the split system, listing the labels followed by a matrix
     * visualization of the splits (where '*' indicates membership to the split's partition and '.' indicates exclusion).
     *
     * @return A string containing the formatted split system.
     */
    public String toString()
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        for (int i = 0; i < labelCount; i++)
        {
            pw.println(idGroup.getIdentifier(i));
        }
        pw.println();


        for (int i = 0; i < splitCount; i++)
        {
            for (int j = 0; j < labelCount; j++)
            {
                if (splits[i][j] == true)
                    pw.print('*');
                else
                    pw.print('.');
            }

            pw.println();
        }

        return sw.toString();
    }
	
	//
	// Private stuff
	//
	
	private int labelCount, splitCount;
	private IdGroup idGroup;
	private boolean[][] splits;
}
