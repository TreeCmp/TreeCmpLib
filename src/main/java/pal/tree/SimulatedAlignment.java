// SimulatedAlignment.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.tree;

import pal.datatype.*;
import pal.substmodel.*;
import pal.alignment.*;
import pal.math.*;
import pal.misc.*;
import pal.util.AlgorithmCallback;


/**
 * generates an artificial data set
 *
 * @version $Id: SimulatedAlignment.java,v 1.19 2003/03/23 00:21:33 matt Exp $
 *
 * @author Korbinian Strimmer
 * @author Alexei Drummond
 */
public class SimulatedAlignment extends AbstractAlignment
{
	//
	// Public stuff
	//


	//
	// Private stuff
	//

	private Tree tree;
	private SubstitutionModel model;
	private double[] cumFreqs;
	private int[] rateAtSite;
	private double[] cumRateProbs;
	private int numStates;
	private byte[][] stateData;
	private MersenneTwisterFast rng;

		//
	// Serialization
	//


	//private static final long serialVersionUID = -5197800047652332969L;

	//serialver -classpath ./classes pal.tree.SimulatedAlignment
	private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
		out.writeByte(1); //Version number
		out.writeObject(tree);
		out.writeObject(model);
		out.writeObject(cumFreqs);
		out.writeObject(rateAtSite);
		out.writeObject(cumRateProbs);
		out.writeObject(stateData);
	}

	private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException{
		byte version = in.readByte();
		switch(version) {
			default : {
				tree = (Tree)in.readObject();
				model = (SubstitutionModel)in.readObject();
				cumFreqs = (double[])in.readObject();
				rateAtSite = (int[])in.readObject();
				cumRateProbs = (double[])in.readObject();
				stateData = (byte[][])in.readObject();
				numStates = getDataType().getNumStates();
				rng = new MersenneTwisterFast();
				break;
			}
		}
	}

	/**
	 * Inititalisation
	 *
	 * @param sites number of sites
	 * @param t     tree relating the sequences
	 * @param m     model of evolution
	 */
	public SimulatedAlignment(int sites, Tree t, SubstitutionModel m) {
		rng = new MersenneTwisterFast();
		setDataType(m.getDataType());
		numStates = getDataType().getNumStates();
		model = m;

		tree = t;
		tree.createNodeList();

		numSeqs = tree.getExternalNodeCount();
		numSites = sites;
		idGroup = new SimpleIdGroup(numSeqs);

		for (int i = 0; i < numSeqs; i++)
		{
			idGroup.setIdentifier(i, tree.getExternalNode(i).getIdentifier());
		}

		stateData = new byte[numSeqs][numSites];

		for (int i = 0; i < tree.getExternalNodeCount(); i++)
		{
			tree.getExternalNode(i).setSequence(stateData[i]);
		}
		for (int i = 0; i < tree.getInternalNodeCount()-1; i++)
		{
			tree.getInternalNode(i).setSequence(new byte[numSites]);
		}


		rateAtSite = new int[numSites];
		cumFreqs = new double[numStates];
		cumRateProbs = new double[m.getNumberOfTransitionCategories()];
	}


	// Implementation of abstract Alignment method

	/** sequence alignment at (sequence, site) */
	public char getData(int seq, int site)
	{
		return getChar(stateData[seq][site]);
	}


	/** generate new artificial data set (random root sequence) */
	public void simulate() {
		simulate(makeRandomRootSequence());
	}

    /**
     * Generates a new artificial character data set (simulated sequence alignment)
     * starting with a randomly determined root sequence.
     *
     * @param givenRootSequence The root sequence provided as a {@code String}. The method converts this string into an array of byte states based on the model's data type.
     */
    public void simulate(String givenRootSequence)  {
        simulate(DataType.Utils.getByteStates(givenRootSequence, model.getDataType()));
    }

    /**
     * Generates a new artificial character data set (simulated sequence alignment)
     * starting with the specified sequence for the root node.
     *
     * <p>The simulation involves assigning new site-specific rate categories, then traversing the tree
     * in pre-order to determine the mutated sequence at each descendant node using transition probability tables.</p>
     *
     * @param rootSeq The root sequence as an array of byte states (indices corresponding to the model's states).
     * @throws IllegalArgumentException If the provided root sequence contains illegal state indices (i.e., less than 0 or greater than or equal to the number of states).
     */
    public void simulate(byte[] rootSeq)
    {
        double[][][] transitionStore = SubstitutionModel.Utils.generateTransitionProbabilityTables(model);
        // Check root sequence
        for (int i = 0; i < numSites; i++)
        {
            if (rootSeq[i] >= numStates || rootSeq[i] < 0)
            {
                throw new IllegalArgumentException("Root sequence contains illegal state (?,-, etc.)");
            }
        }

        tree.getInternalNode(tree.getInternalNodeCount()-1).setSequence(rootSeq);

        // Assign new rate categories
        assignRates();

        // Visit all nodes except root
        Node node = NodeUtils.preorderSuccessor(tree.getRoot());
        do
        {
            determineMutatedSequence(node,transitionStore);
            node = NodeUtils.preorderSuccessor(node);
        }
        while (node != tree.getRoot());
    }

	private void determineMutatedSequence(Node node, double[][][] transitionStore)
	{
		if (node.isRoot()) throw new IllegalArgumentException("Root node not allowed");

		model.getTransitionProbabilities(node.getBranchLength(),transitionStore);

		byte[] oldS = node.getParent().getSequence();
		byte[] newS = node.getSequence();

		for (int i = 0; i < numSites; i++)
		{
			double[] freqs = transitionStore[rateAtSite[i]][oldS[i]];
			cumFreqs[0] = freqs[0];
			for (int j = 1; j < numStates; j++)
			{
				cumFreqs[j] = cumFreqs[j-1] + freqs[j];
			}

			newS[i] = (byte) randomChoice(cumFreqs);
		}
	}

	private byte[] makeRandomRootSequence()	{
		double[] frequencies = model.getEquilibriumFrequencies();
		cumFreqs[0] = frequencies[0];
		for (int i = 1; i < numStates; i++)	{
			cumFreqs[i] = cumFreqs[i-1] + frequencies[i];
		}
		byte[] rootSequence = new byte[numSites];
		for (int i = 0; i < numSites; i++)
		{
			rootSequence[i] = (byte) randomChoice(cumFreqs);
		}
		return rootSequence;
	}

	private void assignRates()	{
		double[] categoryProbabilities = model.getTransitionCategoryProbabilities();

		cumRateProbs[0] = categoryProbabilities[0];
		for (int i = 1; i < categoryProbabilities.length ; i++)	{
			cumRateProbs[i] = cumRateProbs[i-1] + categoryProbabilities[i];
		}

		for (int i = 0; i < numSites; i++) {
			rateAtSite[i] = randomChoice(cumRateProbs);
		}


	}

	// Chooses one category if a cumulative probability distribution is given
	private int randomChoice(double[] cf)
	{

		double rnd = rng.nextDouble();

		int s;
		if (rnd <= cf[0])
		{
			s = 0;
		}
		else
		{
			for (s = 1; s < cf.length; s++)
			{
				if (rnd <= cf[s] && rnd > cf[s-1])
				{
					break;
				}
			}
		}

		return s;
	}
// ============================================================================
// SimulatedAlignment.Factory
    /**
     * A utility class that acts as a factory to generate {@code SimulatedAlignment} objects
     * based on a specified sequence length and a substitution model.
     */
    public static final class Factory {
        private int sequenceLength_;
        private SubstitutionModel model_;

        /**
         * Constructs a {@code Factory} instance ready to produce simulated alignments.
         *
         * @param sequenceLength The desired length of the sequences in the generated alignments.
         * @param model The {@code SubstitutionModel} that defines the simulation process (e.g., nucleotide or amino acid rates).
         * @throws IllegalArgumentException if the provided sequence length is less than 1.
         */
        public Factory(int sequenceLength, SubstitutionModel model) {
            if(sequenceLength<1) {
                throw new IllegalArgumentException("Invalid sequence length:"+sequenceLength);
            }
            this.sequenceLength_ = sequenceLength;
            this.model_ = model;
        }

        /**
         * Generates a single simulated alignment based on the input tree topology and branch lengths.
         *
         * @param tree The {@code Tree} object, whose branch lengths are used to simulate evolution.
         * Note: Branch lengths must be set in units of **expected substitutions** (or units must be {@code UNKNOWN}).
         * @return A new {@code SimulatedAlignment} object.
         * @throws IllegalArgumentException if the tree's units are neither {@code Units.EXPECTED_SUBSTITUTIONS} nor {@code Units.UNKNOWN}.
         */
        public final SimulatedAlignment generateAlignment(final Tree tree) {
            if(
                    (tree.getUnits()!=Units.EXPECTED_SUBSTITUTIONS)&&
                            (tree.getUnits()!=Units.UNKNOWN)
            ) {
                throw new IllegalArgumentException("Tree units must be Expected Substitutions (or reluctantly Unknown)");
            }
            //System.out.println("Simulating:"+model_);
            SimulatedAlignment sa = new SimulatedAlignment(sequenceLength_,tree,model_);
            sa.simulate();
            return sa;
        }

        /**
         * Generates an array of simulated alignments based on a corresponding array of input trees.
         * This method supports monitoring progress and premature stopping via a callback mechanism.
         *
         * @param trees An array of {@code Tree} objects, each with branch lengths set in units of **expected substitutions** (or {@code UNKNOWN}).
         * @param callback An {@code AlgorithmCallback} for monitoring progress and allowing the process to be prematurely stopped.
         * @return An array of generated {@code SimulatedAlignment} objects. If the {@code callback} indicates stopping, a truncated array containing alignments generated so far is returned.
         * @throws IllegalArgumentException if any of the trees' units are neither {@code Units.EXPECTED_SUBSTITUTIONS} nor {@code Units.UNKNOWN}.
         */
        public final SimulatedAlignment[] generateAlignments(final Tree[] trees, final AlgorithmCallback callback) {
            SimulatedAlignment[] as = new SimulatedAlignment[trees.length];
            for(int i = 0 ; i < trees.length ; i++) {
                if(callback.isPleaseStop()) {
                    SimulatedAlignment[] partial = new SimulatedAlignment[i];
                    System.arraycopy(as,0,partial,0,i);
                    return partial;
                }
                as[i] = generateAlignment(trees[i]);
                as[i].simulate();
                callback.updateProgress(i/(double)trees.length);
            }
            callback.clearProgress();
            return as;
        }
    }
}
