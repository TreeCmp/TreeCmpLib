// ReadDistanceMatrix.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.distance;

import java.io.*;

import pal.io.*;
import pal.misc.*;


/**
 * reads pairwise distance matrices in PHYLIP format
 *  (full matrix)
 *
 * @version $Id: ReadDistanceMatrix.java,v 1.4 2002/12/05 04:27:28 matt Exp $
 *
 * @author Korbinian Strimmer
 * @author Alexei Drummond
 */
public class ReadDistanceMatrix extends DistanceMatrix
{
	//
	// Public stuff
	//

    /**
     * Constructs a ReadDistanceMatrix by reading distances from a character stream.
     *
     * @param input a PushbackReader providing the distance matrix data
     * @throws DistanceParseException if there is an error parsing the distance matrix
     */
    public ReadDistanceMatrix(PushbackReader input) throws DistanceParseException {
        readSquare(input);
    }

    /**
     * Constructs a ReadDistanceMatrix by reading distances from a file.
     *
     * @param file the path to the file containing the distance matrix
     * @throws DistanceParseException if there is an error parsing the distance matrix
     * @throws IOException if an I/O error occurs while reading the file
     */
    public ReadDistanceMatrix(String file) throws DistanceParseException, IOException {
        PushbackReader input = InputSource.openFile(file);
        readSquare(input);
        input.close();
    }


    //
	// Private stuff
	//

	// Read square matrix
	private void readSquare(PushbackReader in)
		throws DistanceParseException
	{
		FormattedInput fi = FormattedInput.getInstance();
		try
		{
			// Parse PHYLIP header line
			int numSeqs = fi.readInt(in);
			fi.nextLine(in);

			// Read distance and sequence names
			double[][] distance = new double[numSeqs][numSeqs];
			IdGroup idGroup = new SimpleIdGroup(numSeqs);
			for (int i = 0; i < numSeqs; i++)
			{
				idGroup.setIdentifier(i, new Identifier(fi.readLabel(in, 10)));
				for (int j = 0; j < numSeqs; j++)
				{
					distance[i][j] = fi.readDouble(in);
				}
				fi.nextLine(in);
			}
			setIdGroup(idGroup);
			setDistances(distance);
		}
			catch (IOException e)
			{
				throw new DistanceParseException("IO error");
			}
			catch (NumberFormatException e)
			{
				throw new DistanceParseException("Number format error");
			}
	}
}
