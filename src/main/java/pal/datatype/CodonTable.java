// CodonTable.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.datatype;


/**
 * Describes a device for translating Nucleotide triplets
 * or codon indices into amino acid codes.
 * Codon Indexes (or states) are defined as in GeneralizedCodons
 *
 * @author Matthew Goode
 * @author Alexei Drummond
 *
 * Note:
 *   <ul>
 *     <li> 19 August 2003 - Added getAminoAcidStateFromIUPACStates()
 *   </ul>
 *
 * @version $Id: CodonTable.java,v 1.10 2003/09/04 03:22:34 matt Exp $
 */

public interface CodonTable extends java.io.Serializable {

	/** TypeID for Universal */
	static final int UNIVERSAL = 0;
	/** TypeID for Vertebrate Mitochondrial*/
	static final int VERTEBRATE_MITOCHONDRIAL = 1;
	/** TypeID for Yeast */
	static final int YEAST = 2;
	/** TypeID for Mold Protozoan Mitochondrial */
	static final int MOLD_PROTOZOAN_MITOCHONDRIAL = 3;
	/** TypeID for Mycoplasma */
	static final int MYCOPLASMA = 4;
	/** TypeID for Invertebrate Mitochondrial */
	static final int INVERTEBRATE_MITOCHONDRIAL = 5;
	/** TypeID for Cilate */
	static final int CILATE = 6;
	/** TypeID for Echinoderm Mitochondrial */
	static final int ECHINODERM_MITOCHONDRIAL = 7;
	/** TypeID for Euplotid Nuclear */
	static final int EUPLOTID_NUCLEAR = 8;
	/** TypeID for Ascidian Mitochondrial */
	static final int ASCIDIAN_MITOCHONDRIAL = 9;
	/** TypeID for Flatworm Mitochondrial */
	static final int FLATWORM_MITOCHONDRIAL = 10;
	/** TypeID for Blepharisma Nuclear */
	static final int BLEPHARISMA_NUCLEAR = 11;
	/** TypeID for Bacterial */
	static final int BACTERIAL = 12;
	/** TypeID for Alternative Yeast */
	static final int ALTERNATIVE_YEAST = 13;

	/**
	 * A textual version of an organism type - type is index into array
	 */
	static final String[] ORGANISM_TYPE_NAMES = {
		"Universal",
		"Vertebrate Mitochondrial",
		"Yeast",
		"Mold Protozoan Mitochondrial",
		"Mycoplasma",
		"Invertebrate Mitochondrial",
		"Cilate",
		"Echinoderm Mitochondrial",
		"Euplotid Nuclear",
		"Ascidian Mitochondrial",
		"Flatworm Mitochondrial",
		"Blepharisma Nuclear",
		"Bacterial",
		"Alternative Yeast"
	};

	/**
	 * Returns the char associated with AminoAcid represented by 'codon'
	 * Note: char is as defined by AminoAcids.java
     *
     * @param codon a char array of length 3 representing the codon
	 * @return state for '?' if codon unknown or wrong length
     * @see AminoAcids
	 */
	char getAminoAcidChar(char[] codon);

	/**
	 * Returns the state associated with AminoAcid represented by 'codon'
	 * Note: state is as defined by AminoAcids.java
     *
     * @param codon a char array of length 3 representing the codon
	 * @return '?' if codon unknown or wrong length
     * @see AminoAcids
	 */
	int getAminoAcidState(char[] codon);

    /**
     * Returns all possible codons for a given amino acid state.
     *
     * @param aminoAcidState the amino acid state
	 * @return all the possible codons for a given amino acid
	 */
	char[][] getCodonsFromAminoAcidState(int aminoAcidState);

    /**
     * Returns all possible codons for a given amino acid character.
     *
     * @param aminoAcidChar the amino acid character
	 * @return all the possible codons for a given amino acid
	 */
	char[][] getCodonsFromAminoAcidChar(char aminoAcidChar);

	/** Returns the amino acid char at the corresponding codonIndex
     *
     * @param codonIndex the codon index
     * @return the amino acid character
     */
	char getAminoAcidCharFromCodonIndex(int codonIndex);

    /** Returns the amino acid state at the corresponding codonIndex
     *
     * @param codonIndex the codon index
     * @return the amino acid state
     */
	int getAminoAcidStateFromCodonIndex(int codonIndex);

    /**
     * Returns three IUPAC nucleotide states representing the given amino acid.
     * The returned array should not be altered, and may be ambiguous.
     *
     * @param aminoAcid the amino acid state
	 * @return three IUPAC states representing the given amino acid
	 * Note: The returned array should not be altered, and implementations
	 *       should attempt to implement this as efficiently as possible
	 * Note: the returned array may not be enough to accurately reconstruct the amino acid (as it may be too ambiguous)
	*/
	int[] getIUPACStatesFromAminoAcidState(int aminoAcid);

    /**
     * Returns the nucleotide states representing the given amino acid.
     *
     * @param aminoAcid the amino acid state
     * @return an array of nucleotide states
     */
    int[] getStatesFromAminoAcidState(int aminoAcid);

    /**
     * Returns the amino acid state given an array of nucleotide states.
     * The array should have size 3.
     *
     * @param states an array of nucleotide states
	 * @return The AminoAcid states given the nucleotides states (array should be of size 3)
	 */
	int getAminoAcidStateFromStates(int[] states);

    /**
     * Returns the codon states of terminator amino acids.
     *
     * @return an array of codon states corresponding to terminator amino acids
     */
    int[] getTerminatorIndexes();

    /**
     * Returns the number of terminator amino acids.
     *
     * @return the number of terminator amino acids
     */
    int getNumberOfTerminatorIndexes();

    /**
     * Returns the type ID of this organism.
     * The type corresponds to one of the defined organism type constants.
     *
     * @return the organism type ID
     */
    int getOrganismTypeID();

    /**
     * Determines whether two codons code for the same amino acid (synonymous).
     *
     * @param codonIndexOne the index of the first codon
     * @param codonIndexTwo the index of the second codon
     * @return {@code true} if both codons map to the same amino acid, {@code false} otherwise
     */
    boolean isSynonymous(int codonIndexOne, int codonIndexTwo);

}
