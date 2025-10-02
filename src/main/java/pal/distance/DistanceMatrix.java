// DistanceMatrix.java
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
 * storage for pairwise distance matrices.<p>
 *
 * features:
 * - printing in in PHYLIP format,
 * - computation of (weighted) squared distance to other distance matrix
 * - Fills in all of array...
 *
 * @version $Id: DistanceMatrix.java,v 1.11 2003/07/20 02:36:08 matt Exp $
 *
 * @author Korbinian Strimmer
 * @author Alexei Drummond
 */
public class DistanceMatrix implements Serializable, IdGroup
{

	//
	// Private stuff
	//

	/** sequence identifiers */
	private IdGroup idGroup;

	/** distances [seq1][seq2] */
	private double[][] distance = null;

	static final long serialVersionUID = 4725925229860707633L;

    /** I like doing things my self!
     * Custom serialization method used when writing this object to an
     * {@code ObjectOutputStream}.
     *
     * <p>This implementation manually writes a version number and then delegates
     * the serialization of the critical fields (`idGroup` and `distance`) to the stream.
     *
     * @param out The stream to write the object to.
     * @throws java.io.IOException If an I/O error occurs during writing to the stream.
     */
	private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
		out.writeByte(1); //Version number
		out.writeObject(idGroup);
		out.writeObject(distance);
	}
	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException{
		byte version = in.readByte();
		switch(version) {
			default : {
				idGroup = (IdGroup)in.readObject();
				distance = (double[][])in.readObject();
			}
		}
	}



    /**
     * Default constructor.
     */
    public DistanceMatrix() { }

    /**
     * Constructs a DistanceMatrix with the given distances and identifiers.
     *
     * @param distance a 2D array of distances
     * @param idGroup the group of identifiers corresponding to the distances
     */
    public DistanceMatrix(double[][] distance, IdGroup idGroup) {
        super();
        this.distance = distance;
        this.idGroup = idGroup;
    }

    /**
     * Constructs a DistanceMatrix by cloning the distances of another DistanceMatrix,
     * but using the same IdGroup.
     *
     * @param dm the DistanceMatrix to copy
     */
    public DistanceMatrix(DistanceMatrix dm) {
        distance = pal.misc.Utils.getCopy(dm.distance);
        idGroup = dm.getIdGroup();
    }

    /**
     * Constructs a DistanceMatrix by cloning distances from another DistanceMatrix
     * but only for the identifiers in the given subset.
     *
     * @param dm the DistanceMatrix to copy from
     * @param subset the subset of identifiers to include in the new matrix
     */
    public DistanceMatrix(DistanceMatrix dm, IdGroup subset) {
        int index1, index2;

        distance = new double[subset.getIdCount()][subset.getIdCount()];
        for (int i = 0; i < distance.length; i++) {
            index1 = dm.whichIdNumber(subset.getIdentifier(i).getName());

            for (int j = 0; j < i; j++) {
                index2 = dm.whichIdNumber(subset.getIdentifier(j).getName());
                distance[i][j] = dm.distance[index1][index2];
                distance[j][i] = distance[i][j];
            }
        }
        idGroup = subset;
    }

    /**
     * Prints the distance matrix in PHYLIP format to the given PrintWriter.
     *
     * @param out the PrintWriter to write the output to
     */
    public void printPHYLIP(PrintWriter out) {
        // PHYLIP header line
        out.println("  " + distance.length);
        FormattedOutput format = FormattedOutput.getInstance();

        for (int i = 0; i < distance.length; i++) {
            format.displayLabel(out, idGroup.getIdentifier(i).getName(), 10);
            out.print("      ");

            for (int j = 0; j < distance.length; j++) {
                if (j % 6 == 0 && j != 0) {
                    out.println();
                    out.print("                ");
                }
                out.print("  ");
                format.displayDecimal(out, distance[i][j], 5);
            }
            out.println();
        }
    }

    /**
     * Returns a string representation of the distance matrix in PHYLIP format.
     *
     * @return a String containing the distance matrix
     */
    public String toString() {
        StringWriter sw = new StringWriter();
        printPHYLIP(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Computes the weighted or unweighted squared distance to another distance matrix.
     *
     * @param mat the other DistanceMatrix to compare to
     * @param weighted if true, use Fitch-Margoliash weighting; if false, use Cavalli-Sforza-Edwards weighting
     * @return the squared distance between this matrix and the given matrix
     */
    public double squaredDistance(DistanceMatrix mat, boolean weighted) {
        double sum = 0;
        for (int i = 0; i < distance.length - 1; i++) {
            for (int j = i + 1; j < distance.length; j++) {
                double diff = distance[i][j] - mat.distance[i][j];
                double weight = weighted ? 1.0 / (distance[i][j] * distance[i][j]) : 1.0;
                sum += weight * diff * diff;
            }
        }
        return 2.0 * sum; // only half the matrix counted
    }

    /**
     * Computes the absolute distance to another distance matrix.
     *
     * @param mat the other DistanceMatrix to compare to
     * @return the sum of absolute differences between this matrix and the given matrix
     */
    public double absoluteDistance(DistanceMatrix mat) {
        double sum = 0;
        for (int i = 0; i < distance.length - 1; i++) {
            for (int j = i + 1; j < distance.length; j++) {
                sum += Math.abs(distance[i][j] - mat.distance[i][j]);
            }
        }
        return 2.0 * sum; // only half the matrix counted
    }

    /**
     * Returns the number of rows (or columns) of the distance matrix.
     *
     * @return the size of the distance matrix
     */
    public int getSize() {
        return distance.length;
    }

    /**
     * Returns a cloned 2D array of distances. The returned array can be modified freely.
     *
     * @return a cloned 2D array of distances
     */
    public final double[][] getClonedDistances() {
        return pal.misc.Utils.getCopy(distance);
    }

    /**
     * Returns the 2D array of distances as currently stored.
     *
     * @return a copy of the distances array
     */
    protected final double[][] getDistances() {
        return pal.misc.Utils.getCopy(distance);
    }

    /**
     * Returns the distance between two elements in the matrix.
     *
     * @param row the row index
     * @param col the column index
     * @return the distance between row and column
     */
    public final double getDistance(final int row, final int col) {
        return distance[row][col];
    }

    /**
     * Sets the distance between two elements, updating both upper and lower triangles.
     *
     * @param i the first index
     * @param j the second index
     * @param dist the distance to set
     */
    public void setDistance(int i, int j, double dist) {
        distance[i][j] = distance[j][i] = dist;
    }

    /**
     * Adds a delta to the distance between two elements, updating both upper and lower triangles.
     *
     * @param i the first index
     * @param j the second index
     * @param delta the value to add to the distance
     */
    public void addDistance(int i, int j, double delta) {
        distance[i][j] += delta;
        distance[j][i] += delta;
    }

    /**
     * Computes the mean pairwise distance of this matrix (excluding diagonal elements).
     *
     * @return the mean of all pairwise distances
     */
    public double meanDistance() {
        double dist = 0.0;
        int count = 0;
        for (int i = 0; i < distance.length; i++) {
            for (int j = 0; j < distance[i].length; j++) {
                if (i != j) {
                    dist += distance[i][j];
                    count++;
                }
            }
        }
        return dist / (double) count;
    }

	//IdGroup interface
	public Identifier getIdentifier(int i) {return idGroup.getIdentifier(i);}
	public void setIdentifier(int i, Identifier ident) { idGroup.setIdentifier(i, ident); }
	public int getIdCount() { return idGroup.getIdCount(); }
	public int whichIdNumber(String name) { return idGroup.whichIdNumber(name); }

    /**
     * Returns the IdGroup of this distance matrix.
     *
     * @deprecated distance matrix now implements IdGroup directly
     * @return the IdGroup associated with this matrix
     */
    public IdGroup getIdGroup() {
        return idGroup;
    }

    /**
     * Tests whether this distance matrix is symmetric.
     * <p>A matrix is symmetric if distance[i][i] == 0 and distance[i][j] == distance[j][i] for all i, j.</p>
     *
     * @return true if the matrix is symmetric, false otherwise
     */
    public boolean isSymmetric() {
        for (int i = 0; i < distance.length; i++) {
            if (distance[i][i] != 0) return false;
        }
        for (int i = 0; i < distance.length - 1; i++) {
            for (int j = i + 1; j < distance.length; j++) {
                if (distance[i][j] != distance[j][i]) return false;
            }
        }
        return true;
    }

    /**
     * Returns the index of the element closest to the specified element, excluding any specified elements.
     *
     * @param fromID the name of the element from which to find the closest
     * @param exclusion array of names to exclude from consideration; may be null
     * @return the index of the closest element, or -1 if fromID is not a valid name
     */
    public int getClosestIndex(String fromID, String[] exclusion) {
        int index = whichIdNumber(fromID);
        if (index < 0) { return -1; }

        int[] exclusionIndexes;
        if (exclusion == null) {
            exclusionIndexes = null;
        } else {
            exclusionIndexes = new int[exclusion.length];
            for (int i = 0; i < exclusion.length; i++) {
                exclusionIndexes[i] = whichIdNumber(exclusion[i]);
            }
        }
        return getClosestIndex(index, exclusionIndexes);
    }

    /**
     * Checks whether a value is contained in a set of integers.
     *
     * @param value the value to check
     * @param set the array of integers; may be null
     * @return true if value is in set, false otherwise
     */
    private final boolean isIn(int value, int[] set) {
        if (set == null) { return false; }
        for (int i = 0; i < set.length; i++) {
            if (set[i] == value) { return true; }
        }
        return false;
    }

    /**
     * Returns the index of the element closest to the specified element, excluding any specified indexes.
     *
     * @param fromIndex the index of the element from which to find the closest
     * @param exclusion array of indexes to exclude from consideration; may be null
     * @return the index of the closest element, or -1 if none found
     */
    public int getClosestIndex(int fromIndex, int[] exclusion) {
        double min = Double.POSITIVE_INFINITY;
        int index = -1;
        for (int i = 0; i < distance.length; i++) {
            if (i != fromIndex && !isIn(i, exclusion)) {
                double d = distance[fromIndex][i];
                if (d < min) {
                    min = d;
                    index = i;
                }
            }
        }
        return index;
    }

	protected final void setIdGroup(IdGroup base) {
		this.idGroup = new SimpleIdGroup(base);
	}
	protected final void setDistances(double[][] matrix) {
		this.distance = matrix;
	}
}
