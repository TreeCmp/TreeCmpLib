// MultiLocusAnnotatedAlignment.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.alignment;

import pal.datatype.*;
import pal.misc.*;

/**
 * MultiLocusAnnotatedAlignment is an extension of the SimpleAlignment that includes Annotation, and is designed for multiple
 * loci. Separate annotation information is stored for each site. This would be good for SNP/SSR type data,
 * but it would be inefficient for single gene data.
 *
 * @version $Id:
 * @author Ed Buckler
 */
public class MultiLocusAnnotatedAlignment extends SimpleAlignment implements AnnotationAlignment {
    /** used to designate position along chromosome */
    public float chromosomePosition[];
    /** used to designate chromosome */
    public int chromosome[];
    /** used to designate weighted position; accounts for gaps */
    public float weightedPosition[];
    /** used to designate position; does not account for gaps */
    public int locusPosition[];
    /** used to designate position type */
    public char positionType[];
    /** used to designate locus name */
    public String locusName[];
    /** provides datatype for each locus separately */
    public DataType[] siteDataType = null;

    /**
     * Basic constructor.
     * @param a the base alignment to copy
     */
    public MultiLocusAnnotatedAlignment(Alignment a) {
        super(a);
        initMatrices();
    }

    /**
     * Null constructor.
     */
    public MultiLocusAnnotatedAlignment() {
    }

    /**
     * Clone constructor for Annotated alignment.
     * @param a the annotated alignment to clone
     */
    public MultiLocusAnnotatedAlignment(AnnotationAlignment a) {
        super(a);
        initMatrices();
        for (int i = 0; i < getSiteCount(); i++) {
            chromosomePosition[i] = a.getChromosomePosition(i);
            chromosome[i] = a.getChromosome(i);
            locusName[i] = a.getLocusName(i);
            weightedPosition[i] = a.getWeightedLocusPosition(i);
            positionType[i] = a.getPositionType(i);
        }
    }

    /**
     * Constructor with identifiers and sequences.
     * @param ids the identifiers for each sequence
     * @param sequences the sequences
     * @param gaps the gap character(s)
     * @param dt the datatype
     */
    public MultiLocusAnnotatedAlignment(Identifier[] ids, String[] sequences, String gaps, DataType dt) {
        super(ids, sequences, gaps, dt);
        initMatrices();
    }

    /**
     * Constructor with IdGroup and sequences.
     * @param group the IdGroup
     * @param sequences the sequences
     * @param dt the datatype
     */
    public MultiLocusAnnotatedAlignment(IdGroup group, String[] sequences, DataType dt) {
        super(group, sequences, dt);
        initMatrices();
    }

    /**
     * Constructor with IdGroup, sequences and gaps.
     * @param group the IdGroup
     * @param sequences the sequences
     * @param gaps the gap character(s)
     * @param dt the datatype
     */
    public MultiLocusAnnotatedAlignment(IdGroup group, String[] sequences, String gaps, DataType dt) {
        super(group, sequences, gaps, dt);
        initMatrices();
    }

    /**
     * Constructor that subsets the alignment based on a new IdGroup.
     * @param a the annotated alignment to subset
     * @param newGroup the new IdGroup to subset by
     */
    public MultiLocusAnnotatedAlignment(AnnotationAlignment a, IdGroup newGroup) {
        sequences = new String[newGroup.getIdCount()];
        for (int i = 0; i < newGroup.getIdCount(); i++) {
            int oldI = a.whichIdNumber(newGroup.getIdentifier(i).getName());
            sequences[i] = a.getAlignedSequenceString(oldI);
        }
        init(newGroup, sequences);
        initMatrices();
        weightedPosition = new float[numSites];
        positionType = new char[numSites];
        for (int i = 0; i < numSites; i++) {
            chromosomePosition[i] = a.getChromosomePosition(i);
            chromosome[i] = a.getChromosome(i);
            locusName[i] = a.getLocusName(i);
            weightedPosition[i] = a.getWeightedLocusPosition(i);
            positionType[i] = a.getPositionType(i);
        }
    }

    /**
     * Initializes the matrices for chromosome and locus information.
     */
    protected void initMatrices() {
        chromosomePosition = new float[getSiteCount()];
        chromosome = new int[getSiteCount()];
        locusName = new String[getSiteCount()];
        locusPosition = new int[getSiteCount()];
        weightedPosition = new float[getSiteCount()];
        positionType = new char[getSiteCount()];
    }

    /**
     * Initializes sequences and estimates frequencies.
     * @param group the IdGroup for the alignment
     * @param sequences the aligned sequences
     */
    protected void init(IdGroup group, String[] sequences) {
        numSeqs = sequences.length;
        numSites = sequences[0].length();

        this.sequences = sequences;
        idGroup = group;

        AlignmentUtils.estimateFrequencies(this);
    }

    /**
     * Returns the position along chromosome.
     * @param site the site index
     * @return the chromosome position at the given site
     */
    public float getChromosomePosition(int site) { return chromosomePosition[site]; }

    /**
     * Sets the position along chromosome.
     * @param position the chromosome position
     * @param site the site index
     */
    public void setChromosomePosition(float position, int site) { this.chromosomePosition[site] = position; }

    /**
     * Returns the chromosome index.
     * @param site the site index
     * @return the chromosome at the given site
     */
    public int getChromosome(int site) { return chromosome[site]; }

    /**
     * Sets the chromosome index.
     * @param chromosome the chromosome index
     * @param site the site index
     */
    public void setChromosome(int chromosome, int site) { this.chromosome[site] = chromosome; }

    /**
     * Returns the weighted position along the gene (handles gaps).
     * @param site the site index
     * @return the weighted locus position at the given site
     */
    public float getWeightedLocusPosition(int site) { return weightedPosition[site]; }

    /**
     * Sets the weighted position along the gene.
     * @param site the site index
     * @param weightedPos the weighted position to set
     */
    public void setWeightedLocusPosition(int site, float weightedPos) { weightedPosition[site] = weightedPos; }

    /**
     * Returns the position along the locus (ignores gaps).
     * @param site the site index
     * @return the locus position at the given site
     */
    public int getLocusPosition(int site) { return locusPosition[site]; }

    /**
     * Sets the position within the locus.
     * @param position the locus position to set
     * @param site the site index
     */
    public void setLocusPosition(int position, int site) { locusPosition[site] = position; }

    /**
     * Returns the position type (e.g., I=intron, E=exon, P=promoter, 1=first codon, etc.).
     * @param site the site index
     * @return the position type at the given site
     */
    public char getPositionType(int site) { return positionType[site]; }

    /**
     * Sets the position type (e.g., I=intron, E=exon, P=promoter, 1=first codon, etc.).
     * @param site the site index
     * @param posType the position type to set
     */
    public void setPositionType(int site, char posType) { positionType[site] = posType; }

    /**
     * Returns the locus name.
     * @param site the site index
     * @return the locus name at the given site
     */
    public String getLocusName(int site) { return locusName[site]; }

    /**
     * Sets the locus name.
     * @param locusName the locus name to set
     * @param site the site index
     */
    public void setLocusName(String locusName, int site) { this.locusName[site] = locusName; }

    /**
     * Returns the DataType of the alignment at a given site.
     * @param site the site index
     * @return the DataType for the site
     */
    public DataType getDataType(int site) {
        if (siteDataType == null) {
            return getDataType();
        } else {
            return siteDataType[site];
        }
    }
}
