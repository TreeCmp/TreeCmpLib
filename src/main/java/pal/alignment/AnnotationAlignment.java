// AnnotationAlignment.java
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
 *  The AnnotationAlignment interface is designed to provide annotation for an alignment.
 *  This annotation can
 *  include information on chromosomal location, site positions, names of loci, and the
 *  type of position (exon, intron, etc.)  This interface also permits multiple datatypes per
 *  alignment.
 *
 * @version $Id: AnnotationAlignment.java,v 1.2 2001/09/02 13:19:41 korbinian Exp $
 *
 * @author Ed Buckler
 */
public interface AnnotationAlignment extends Alignment, Report {

    /**
     * Returns the physical or genetic position of a site along the chromosome.
     *
     * @param site the site index.
     * @return the position along the chromosome as a float.
     */
    float getChromosomePosition(int site);

    /**
     * Returns the chromosome number where the given site is located.
     *
     * @param site the site index.
     * @return the chromosome number.
     */
    int getChromosome(int site);

    /**
     * Returns the weighted position along the locus, taking into account gaps in the alignment.
     *
     * @param site the site index.
     * @return the weighted position along the locus as a float.
     */
    float getWeightedLocusPosition(int site);

    /**
     * Returns the position of the site along the locus, ignoring any gaps.
     *
     * @param site the site index.
     * @return the position along the locus as an integer.
     */
    int getLocusPosition(int site);

    /**
     * Returns the type of the site, e.g., I=intron, E=exon, P=promoter, 1=first codon position, 2=second, 3=third, etc.
     *
     * @param site the site index.
     * @return a character representing the site type.
     */
    char getPositionType(int site);

    /**
     * Returns the name of the locus to which the site belongs.
     *
     * @param site the site index.
     * @return the locus name as a String.
     */
    String getLocusName(int site);

    /**
     * Returns the data type associated with a specific site. This allows for site-specific data types
     * in complex alignments where different sites may use different data representations.
     *
     * @param site the site index.
     * @return the DataType object for the site.
     */
    DataType getDataType(int site);

    /**
     * Generates a report of the alignment, including sequence and site information.
     *
     * @param out a PrintWriter to which the report will be written.
     */
    void report(PrintWriter out);
}

