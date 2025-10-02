// AbstractAlignment.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.alignment;

import pal.datatype.*;

/**
 * an extension of the IndelAlignment that includes annotation.  This should only extract
 * indels from a single locus.
 *
 * @version $Id:
 *
 * @author Ed Buckler
 */
public class IndelAnnotatedAlignment extends IndelAlignment implements AnnotationAlignment{
	/** used to designate position along chromosome */
	 public float chromosomePosition;
	 /** used to designate chromosome */
	 public int chromosome;
	 /** used to designate weighted position; accounts for gaps */
	 public float weightedLocusPosition[];
	 /** used to designate position; do not account for gaps */
	 public int locusPosition[];
	 /** used to designate position Type */
	 public char positionType[];
	 /** used to designate locus name */
	 public String locusName;

	/**
	 * Basic constructor.  All annotation is based off the first site in the AnnotationAlignment.
	 * This Alignment should not span multiple loci.
     *
     * @param a        the source {@link AnnotationAlignment} containing both
     *                 sequence alignment and site annotations
	 * @param anchored sets to score anchored indels as same position
	 */
	public IndelAnnotatedAlignment(AnnotationAlignment a, boolean anchored) {
		super((Alignment)a,anchored);
		setDataType(new NumericDataType());
		chromosomePosition=a.getChromosomePosition(0);
		chromosome=a.getChromosome(0);
		locusName=a.getLocusName(0);
		locusPosition=new int[numSites];
		weightedLocusPosition=new float[numSites];
		positionType=new char[numSites];
		IndelPosition ip;
		for(int i=0; i<numSites; i++)
			{ip=getIndelPosition(i);
			locusPosition[i]=ip.start;  //the start of the indel is used for the position
			weightedLocusPosition[i]=a.getWeightedLocusPosition(ip.start);
			positionType[i]=a.getPositionType(ip.start);
			}
	}

    /**
     * Returns the physical position of this alignment on the chromosome.
     *
     * @param site the site index
     * @return the chromosome position at the given site
     */
    public float getChromosomePosition(int site) {
        return chromosomePosition;
    }

    /**
     * Sets the chromosome position for this alignment (overrides default).
     *
     * @param position the new chromosome position
     */
    public void setChromosomePosition(float position) {
        this.chromosomePosition = position;
    }

    /**
     * Returns the chromosome index for this alignment.
     *
     * @param site the site index
     * @return the chromosome index
     */
    public int getChromosome(int site) {
        return chromosome;
    }

    /**
     * Sets the chromosome index for this alignment.
     *
     * @param chromosome the chromosome index to set
     */
    public void setChromosome(int chromosome) {
        this.chromosome = chromosome;
    }

    /**
     * Returns the weighted locus position at the given site (accounts for gaps in the alignment).
     *
     * @param site the site index
     * @return the weighted locus position
     */
    public float getWeightedLocusPosition(int site) {
        return weightedLocusPosition[site];
    }

    /**
     * Returns the unweighted locus position at the given site (ignores gaps).
     *
     * @param site the site index
     * @return the unweighted locus position
     */
    public int getLocusPosition(int site) {
        return locusPosition[site];
    }

    /**
     * Returns the position type at the given site.
     * Examples: I=intron, E=exon, P=promoter, 1=first codon position, etc.
     *
     * @param site the site index
     * @return the position type code
     */
    public char getPositionType(int site) {
        return positionType[site];
    }

    /**
     * Returns the locus name for this alignment.
     *
     * @param site the site index
     * @return the name of the locus
     */
    public String getLocusName(int site) {
        return locusName;
    }

    /**
     * Sets the locus name for this alignment.
     *
     * @param locusName the name to set for the locus
     */
    public void setLocusName(String locusName) {
        this.locusName = locusName;
    }

    /**
     * Returns the {@link DataType} of the alignment at the given site.
     *
     * @param site the site index
     * @return the data type
     */
    public DataType getDataType(int site) {
        return getDataType();
    }
}