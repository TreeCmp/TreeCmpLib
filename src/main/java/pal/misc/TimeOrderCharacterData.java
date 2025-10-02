// TimeOrderCharacterData.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.misc;

import java.io.*;
import pal.util.*;
import pal.math.*;

/**
 * Character data that expresses an order through time.
 *
 * @version $Id: TimeOrderCharacterData.java,v 1.21 2004/01/15 01:18:32 matt Exp $
 *
 * @author Alexei Drummond
 */
public class TimeOrderCharacterData implements Serializable, BranchLimits, UnitsProvider, IdGroup {

	//
	// Protected Stuff
	//

	/** Order of times */
	protected int[] timeOrdinals = null; //Is serialized

	/** Actual times of each sample */
	protected double[] times = null; //Is serialized

	/** the identifier group */
	protected IdGroup taxa; //Is serialized

	protected int units = Units.GENERATIONS; //Is serialized
	protected SubgroupHandler[] subgroups_;

	//
	// PRIVATE STUFF
	//

	/** Name of this character data */
	private String name = "Time/order character data";	//Is serialized

	//
	// Serialization code
	//
	private static final long serialVersionUID= 7672390862755080486L;

	//serialver -classpath ./classes pal.misc.TimeOrderCharacterData
	private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
		out.writeByte(2); //Version number
		out.writeObject(timeOrdinals);
		out.writeObject(times);
		out.writeObject(taxa);
		out.writeInt(units);
		out.writeObject(name);
		out.writeObject(subgroups_);
	}

	private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException{
		byte version = in.readByte();
		switch(version) {
			case 1 : {
				timeOrdinals = (int[])in.readObject();
				times = (double[])in.readObject();
				taxa = (IdGroup)in.readObject();
				units = in.readInt();
				name = (String)in.readObject();
				break;
			}
			default : {
				timeOrdinals = (int[])in.readObject();
				times = (double[])in.readObject();
				taxa = (IdGroup)in.readObject();
				units = in.readInt();
				name = (String)in.readObject();
				subgroups_ = (SubgroupHandler[])in.readObject();
				break;
			}
		}
	}

	/**
	 * Parameterless constructor for superclasses.
	 */
	protected TimeOrderCharacterData() {}

	/**
		 * Clones a TimeOrderCharacterData object
		 * but the identifier positions match that of base (ie whichIdNumber(Name) returns the same as for base)
		 */
	private TimeOrderCharacterData(TimeOrderCharacterData toCopy, IdGroup base) {
		int size = toCopy.getIdCount();
		this.timeOrdinals = new int[size];
		final boolean hasTimes = toCopy.hasTimes();
		this.times = hasTimes ? new double[size] : null;
		for(int i = 0 ; i < size ; i++) {
			String name = toCopy.getIdentifier(i).getName();
			int baseLocation = base.whichIdNumber(name);
			if(baseLocation<0) {
				throw new IllegalArgumentException("Base does not contain:"+name);
			}
			this.timeOrdinals[baseLocation] = toCopy.timeOrdinals[i];
			if(hasTimes) {
				this.times[baseLocation] = toCopy.times[i];
			}
		}
		this.subgroups_ = SubgroupHandler.getCopy(toCopy.subgroups_,toCopy,base);
		this.units = toCopy.units;
		this.taxa = new SimpleIdGroup(base);
	}

	/**
	 * Constructor taking only IdGroup.
	 * Beware! This constructor does not initialize
	 * any time ordinals or times.
	 * @param taxa the taxa that this time data relates to.
	 * @param units the units of the times.
	 */
	public TimeOrderCharacterData(IdGroup taxa, int units) {
		this(taxa, units, false);
	}

	/**
	 * Constructor taking only IdGroup.
	 * @param taxa the taxa that this time data relates to.
	 * @param units the units of the times.
	 * @param contemp if true, all times are set to zero, else
	 * times are not set.
	 */
	public TimeOrderCharacterData(IdGroup taxa, int units, boolean contemp) {
		this.taxa = taxa;
		this.units = units;

		if (contemp) {
			double[] times = new double[taxa.getIdCount()];
			setTimes(times, units);
		}
	}

	/**
	 * Constructs a TimeOrderCharacterData with a number of
	 * equal-sized, evenly-spaced sampling times.
	 * @param numSeqsPerSample the number of taxa/sequences per sample time.
	 * @param numSamples the number of sample times.
	 * @param timeBetweenSamples the time between each pair of consecutive samples.
	 * @param units the units in which the times are expressed.
	 */
	public TimeOrderCharacterData(int numSeqsPerSample, int numSamples,
		double timeBetweenSamples, int units) {

		int n = numSeqsPerSample * numSamples;

		taxa = IdGenerator.createIdGroup(n);

		// create times and ordinals
		timeOrdinals = new int[taxa.getIdCount()];
		times = new double[taxa.getIdCount()];

		int index = 0;
		for (int i = 0; i < numSamples; i++) {
			for (int j = 0; j < numSeqsPerSample; j++) {
				times[index] = timeBetweenSamples * (double)i;
				timeOrdinals[index] = i;
				index += 1;
			}
		}

		this.units = units;
	}

    /**
     * Returns a clone of the specified TimeOrderCharacterData.
     *
     * @param tocd The TimeOrderCharacterData object to be cloned.
     * @return A new TimeOrderCharacterData object that is a clone (subset of itself).
     */
    public static TimeOrderCharacterData clone(TimeOrderCharacterData tocd) {
        return tocd.subset(tocd);
    }

    /**
     * Extracts a subset of a TimeOrderCharacterData based on the provided identifiers.
     *
     * @param staxa The IdGroup defining the subset of identifiers (taxa) to extract.
     * @return A new TimeOrderCharacterData object containing only the data for the specified identifiers.
     */
    public TimeOrderCharacterData subset(IdGroup staxa) {

        TimeOrderCharacterData subset =
                new TimeOrderCharacterData(staxa, getUnits());

        subset.timeOrdinals = new int[staxa.getIdCount()];
        if (hasTimes()) {
            subset.times = new double[staxa.getIdCount()];
        }

        for (int i = 0; i < subset.timeOrdinals.length; i++) {
            int index = taxa.whichIdNumber(staxa.getIdentifier(i).getName());
            subset.timeOrdinals[i] = timeOrdinals[index];

            if (hasTimes()) {
                subset.times[i] = times[index];
            }
        }
        return subset;
    }

    /**
     * Returns the unit type for the time data.
     *
     * @return The unit type, represented as an integer.
     */
    public int getUnits() {
        return units;
    }

    /**
     * Defines multiple subgroups.
     *
     * @param subgroups An array of integer arrays. Each inner array holds the zero-based indexes
     * of the members that form a particular subgroup.
     */
    public final void setSubgroups(final int[][] subgroups) {
        this.subgroups_ = SubgroupHandler.create(subgroups);
    }

    /**
     * Defines a single subgroup.
     *
     * @param subgroup An array of zero-based indexes of the members that form the subgroup.
     */
    public final void setSubgroup(final int[] subgroup) {
        this.subgroups_ = SubgroupHandler.create(subgroup);
    }

    /**
     * Defines a single subgroup using member names.
     *
     * @param subgroup An array of names (String) that represent the members of the subgroup.
     * Nonexistent members are ignored.
     */
    public final void setSubgroup(final String[] subgroup) {
        this.subgroups_ = SubgroupHandler.create(this, subgroup);
    }

    /**
     * Defines multiple subgroups using member names.
     *
     * @param subgroups An array of String arrays. Each inner array holds the names of the members
     * for a particular subgroup. Nonexistent members are ignored.
     * Note: members can appear in more than one subgroup.
     */
    public final void setSubgroups(final String[][] subgroups) {
        this.subgroups_ = SubgroupHandler.create(this, subgroups);
    }

    /**
     * Checks if any subgroups have been defined.
     *
     * @return {@code true} if subgroups have been defined, {@code false} otherwise.
     */
    public final boolean hasSubgroups() {
        return this.subgroups_!=null;
    }

    /**
     * Returns the total number of defined subgroups.
     *
     * @return The number of defined subgroups, or 0 if none are defined.
     */
    public final int getNumberOfSubgroups() {
        return (subgroups_==null? 0 : subgroups_.length);
    }

    /**
     * Creates a new TimeOrderCharacterData object which is a subset corresponding to a specified subgroup.
     * Note: Different subgroups may contain the same members.
     *
     * @param subgroupNumber The zero-based index of the subgroup to create the subset from.
     * @return A new TimeOrderCharacterData object representing the specified subgroup.
     */
    public final TimeOrderCharacterData createSubgroup(int subgroupNumber) {
        return this.subgroups_[subgroupNumber].generateNewTOCD(this);
    }
	public final Identifier[] getSubgroupMembers(int subgroupNumber) {
		return this.subgroups_[subgroupNumber].getSubgroupMembers(this);
	}


    /**
     * Sets the time values and calculates the corresponding time ordinals automatically.
     *
     * @param times An array of double values representing the time for each member.
     * @param units An integer representing the unit of time (e.g., years, days).
     */
    public void setTimes(double[] times, int units) {
        setTimes(times, units, true);
    }

    /**
     * Sets the time values with an option to recalculate time ordinals.
     *
     * @param times An array of double values representing the time for each member.
     * @param units An integer representing the unit of time (e.g., years, days).
     * @param recalculateOrdinals {@code true} if the time ordinals should be recalculated from the new times; {@code false} otherwise.
     */
    public void setTimes(double[] times, int units, boolean recalculateOrdinals) {
        this.times = times;
        this.units = units;
        if (recalculateOrdinals) {
            setOrdinalsFromTimes();
        }
    }

	public TimeOrderCharacterData scale(double rate, int newUnits) {
		TimeOrderCharacterData scaled = clone(this);
		scaled.units = newUnits;
		for (int i = 0; i < times.length; i++) {
			scaled.times[i] = times[i] * rate;
		}
		return scaled;
	}
    /**
     * Sets the time ordinals for this object.
     *
     * @param ordinals An array of integers representing the time ordinal for each member.
     */
    public void setOrdinals(int[] ordinals) {
        timeOrdinals = ordinals;
    }

    /**
     * Calculates and returns the maximum time value present in the data.
     *
     * @return The maximum time value.
     * @throws RuntimeException If the time data has not been set (i.e., {@code times} is {@code null}).
     */
    public double getMaximumTime() {
        if(times==null) {
            throw new RuntimeException("Error: getMaximumTime() called with no times");
        }
        double max = times[0];
        for(int i = 1 ; i < times.length ; i++) {
            if(times[i]>max) {
                max = times[i];
            }
        }
        return max;
    }

    /**
     * Calculates and returns the minimum time value present in the data.
     *
     * @return The minimum time value.
     * @throws RuntimeException If the time data has not been set (i.e., {@code times} is {@code null}).
     */
    public double getMinimumTime() {
        if(times==null) {
            throw new RuntimeException("Error: getMinimumTime() called with no times");
        }
        double min = times[0];
        for(int i = 1 ; i < times.length ; i++) {
            if(times[i]<min) {
                min = times[i];
            }
        }
        return min;
    }

    /**
     * Gets the time ordinals array.
     *
     * @return The array of time ordinals.
     */
    public int[] getOrdinals() {
        return timeOrdinals;
    }

    /**
     * Returns a copy of the time values in the form of an array.
     *
     * @return A new array containing a copy of the time values.
     */
    public double[] getCopyOfTimes() {
        double[] copyTimes = new double[times.length];
        System.arraycopy(times, 0, copyTimes, 0, times.length);
        return copyTimes;
    }

    /**
     * Creates a new TimeOrderCharacterData object with the same properties as this one,
     * but with the identifier positions reordered to match those of the specified base group.
     *
     * @param base The IdGroup that defines the desired order of identifiers.
     * @return A new reordered TimeOrderCharacterData object.
     * @throws IllegalArgumentException If the base IDs do not match the IDs of this object.
     */
    public TimeOrderCharacterData getReordered(IdGroup base) {
        return new TimeOrderCharacterData(this,base);
    }

    /**
     * Removes the time character data by setting the internal time array to {@code null}.
     */
    public void removeTimes() {
        times = null;
    }

    /**
     * Given an array of rates between samples, produces a new TimeOrderCharacterData object
     * where times are calculated based on Expected Substitutions.
     *
     * @param sampleRates An array of rates corresponding to the samples in order.
     * @return A new TimeOrderCharacterData object timed by Expected Substitutions.
     */
    public TimeOrderCharacterData generateExpectedSubsitutionsTimedTOCD(double[] sampleRates) {
        TimeOrderCharacterData result = new TimeOrderCharacterData(this,Units.EXPECTED_SUBSTITUTIONS);
        double[] times = new double[getIdCount()];
        for(int i = 0 ; i < times.length ; i++) {
            int sample = getTimeOrdinal(i);
            double total = 0;
            //Yes, I know this is inefficient but it's too late in the afternoon for me.
            for(int es = 0; es<sample ; es++) {    total+=sampleRates[es];    }
            times[i] = total;
        }
        result.setTimes(times,Units.EXPECTED_SUBSTITUTIONS);
        return result;
    }

    /**
     * Creates a new TimeOrderCharacterData object that uses a dummy time scale.
     * The time value for each member is set to match its sample number (ordinal).
     * The units are set to {@code Units.UNKNOWN}.
     *
     * @param sampleRates This parameter is not used in the calculation but is kept for API compatibility.
     * @return A new TimeOrderCharacterData object with dummy times (time = ordinal).
     */
    public TimeOrderCharacterData generateDummyTimedTOCD(double[] sampleRates) {
        TimeOrderCharacterData dummy = new TimeOrderCharacterData(this,Units.EXPECTED_SUBSTITUTIONS);
        double[] times = new double[getIdCount()];
        for(int i = 0 ; i < times.length ; i++) {
            times[i] = getTimeOrdinal(i);
        }
        dummy.setTimes(times, Units.UNKNOWN);
        return dummy;
    }

    /**
     * Sets the time ordinals (and optionally times) from another TimeOrderCharacterData object
     * by matching member names.
     *
     * @param tocd The TimeOrderCharacterData object to take ordinals (and times) from.
     */
    public void setOrdinals(TimeOrderCharacterData tocd) {
        setOrdinals(tocd, null, false);
    }

    /**
     * Sets both the time values and time ordinals from another TimeOrderCharacterData object
     * by matching member names.
     *
     * @param tocd The TimeOrderCharacterData object to take times and ordinals from.
     */
    public void setTimesAndOrdinals(TimeOrderCharacterData tocd) {
        setOrdinals(tocd, null, true);
    }

    /**
     * Sets the time ordinals (and optionally times) from another TimeOrderCharacterData object.
     * Ordinals and times are selected by matching member names between the two objects.
     *
     * @param tocd The TimeOrderCharacterData object to take ordinals and times from.
     * @param standard The IdGroup to use as the standard for matching indices in the given {@code tocd}. If {@code null}, {@code tocd} is used.
     * @param doTimes If {@code true}, times are also set; otherwise, only ordinals are set.
     */
    public void setOrdinals(TimeOrderCharacterData tocd, IdGroup standard, boolean doTimes) {

        if (timeOrdinals == null) {
            timeOrdinals = new int[taxa.getIdCount()];
        }

        if (doTimes && tocd.hasTimes()) {
            times = new double[taxa.getIdCount()];
        }

        if (standard == null) {
            standard = tocd;
        }

        for (int i = 0; i < taxa.getIdCount(); i++) {

            String name = taxa.getIdentifier(i).getName();
            int index = standard.whichIdNumber(name);
            if (index == -1) {
                System.err.println("Identifiers don't match!");
                System.err.println("Trying to find: '" + name + "' in:");
                System.err.println(standard);
                //System.exit(1);
            }

            timeOrdinals[i] = tocd.getTimeOrdinal(index);
            if (doTimes && tocd.hasTimes()) {
                times[i] = tocd.getTime(index);
            }
        }
    }

	private final boolean equal(double a, double b, double epsilon) {
		return(Math.abs(a-b)<epsilon);
	}

	private void setOrdinalsFromTimes() {

		int[] indices = new int[times.length];
		timeOrdinals = new int[times.length];
		HeapSort.sort(times, indices);

		int ordinal = 0;
		int lastIndex = 0;
		timeOrdinals[indices[0]] = ordinal;

		for (int i = 1; i < indices.length; i++) {
			if (Math.abs(times[indices[i]] - times[indices[lastIndex]]) <= ABSTOL) {
				// this time is still within the tolerated error
			} else {
				// this is definitely a new time
				lastIndex = i;
				ordinal += 1;
			}
			timeOrdinals[indices[i]] = ordinal;
		}
	}

    /**
     * Returns the number of characters (data points) associated with each identifier.
     * This is 2 if time values exist (time + ordinal), and 1 otherwise (only ordinal).
     *
     * @return The number of characters per identifier (1 or 2).
     */
    public int getNumChars() {
        if (hasTimes()) {
            return 2;
        } else return 1;
    }

    /**
     * Returns the descriptive name assigned to this character data set.
     *
     * @return The name of this character data.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the descriptive name of this character data set.
     *
     * @param name The new name for this character data.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the time value for the identifier at the specified index.
     *
     * @param taxon The zero-based index of the identifier (taxon) of interest.
     * @return The time value for the specified identifier.
     */
    public double getTime(int taxon) {
        return times[taxon];
    }

    /**
     * Obtains the time value associated with a particular time ordinal (sample number).
     *
     * @param ordinal The time ordinal (sample number) of interest.
     * @return The time value corresponding to the input ordinal.
     * @throws IllegalArgumentException If no identifier has the specified ordinal value.
     */
    public double getOrdinalTime(int ordinal) {
        for(int i = 0 ; i < timeOrdinals.length ; i++) {
            if(timeOrdinals[i]==ordinal) {
                return times[i];
            }
        }
        throw new IllegalArgumentException("Unknown ordinal");
    }

    /**
     * Returns the time value for the identifier with the specified name.
     *
     * @param taxonName The name of the identifier (taxon) of interest.
     * @return The time value for the specified identifier.
     */
    public double getTime(String taxonName) {
        int i = whichIdNumber(taxonName);
        return times[i];
    }


    /**
     * Calculates the evolutionary "height" or distance for a taxon, given a specific rate.
     *
     * @param taxon The zero-based index of the identifier (taxon).
     * @param rate The rate used for calculation (height = time * rate).
     * @return The calculated height for the specified taxon.
     * NOTE: currently assumes times exist!
     */
    public double getHeight(int taxon, double rate) {
        return times[taxon] * rate;
    }

    /**
     * Returns the time ordinal (sample number) for the identifier at the specified index.
     *
     * @param taxon The zero-based index of the identifier (taxon) of interest.
     * @return The time ordinal for the specified identifier.
     */
    public int getTimeOrdinal(int taxon) {
        return timeOrdinals[taxon];
    }

    /**
     * Returns the time ordinal (sample number) for the identifier with the specified name.
     *
     * @param taxonName The name of the identifier (taxon) of interest.
     * @return The time ordinal for the specified identifier.
     */
    public int getTimeOrdinal(String taxonName) {
        int i = whichIdNumber(taxonName);
        return timeOrdinals[i];
    }

    /**
     * Returns the time ordinal (sample number) for the specified identifier object.
     *
     * @param taxonName The Identifier object of interest.
     * @return The time ordinal for the specified identifier.
     */
    public int getTimeOrdinal(Identifier taxonName) {
        int i = whichIdNumber(taxonName.getName());
        return timeOrdinals[i];
    }

    /**
     * Checks if time values have been set for this character data.
     *
     * @return {@code true} if time values exist (i.e., {@code times} is not {@code null}), {@code false} otherwise.
     */
    public boolean hasTimes() {
        return times != null;
    }

    /**
     * Returns an ordered array of unique time values present in this data.
     * The array is indexed by the time ordinal (sample number).
     *
     * @return An array where {@code result[ordinal] = time}.
     */
    public double[] getUniqueTimeArray() {
        int count = getOrdinalCount();
        double[] utimes = new double[count];
        for (int i = 0; i < times.length; i++) {
            utimes[getTimeOrdinal(i)] = times[i];
        }
        return utimes;
    }

    /**
     * Returns a symmetric matrix of the absolute time differences between all unique samples (ordinals).
     * A sample is defined as any set of identifiers that share the same time value.
     *
     * @return A matrix where {@code result[i][j]} is the absolute time difference between sample {@code i} and sample {@code j}.
     */
    public double[][] getUniqueTimeMatrix() {
        double[] utimes = getUniqueTimeArray();
        int count = utimes.length;
        double[][] stimes = new double[count][count];
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < count; j++) {
                stimes[i][j] = Math.abs(utimes[i] - utimes[j]);
            }
        }

        return stimes;
    }

    /**
     * A sample is defined as any set of identifiers that have the same time.
     *
     * @return The number of unique time ordinals (samples) in this data.
     * @deprecated Use {@link #getOrdinalCount()}
     */
    public int getSampleCount() {
        return getOrdinalCount();
    }

    /**
     * Returns the number of unique time ordinals (samples) in this data.
     * This is calculated as the maximum ordinal value plus one (assuming ordinals start at 0).
     *
     * @return The number of unique time ordinals (samples).
     */
    public int getOrdinalCount() {
        int max = 0;
        for (int i = 0; i < timeOrdinals.length; i++) {
            if (timeOrdinals[i] > max) max = timeOrdinals[i];
        }
        return max + 1;
    }

    /**
     * Returns a string representation of this time order character data, listing
     * each identifier along with its time and sample (ordinal) number.
     *
     * @return A formatted string representation of the data.
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();

        sb.append("Identifier\t"+ (hasTimes() ? "Times\t" : "") + "Sample\n");
        for (int i = 0; i < taxa.getIdCount(); i++) {
            sb.append(taxa.getIdentifier(i) + "\t" +
                    (hasTimes() ? getTime(i) + "\t" : "") +
                    getTimeOrdinal(i)+"\n");
        }
        return new String(sb);
    }

    /**
     * Randomly shuffles the assignment of time ordinals and time values to the identifiers
     * by permuting the data arrays. The association between a time/ordinal pair is preserved,
     * but the pair is assigned to a different identifier.
     */
    public void shuffleTimes() {
        MersenneTwisterFast mtf = new MersenneTwisterFast();

        int[] indices = mtf.shuffled(timeOrdinals.length);

        int[] newOrdinals = new int[timeOrdinals.length];
        double[] newTimes = null;
        if (hasTimes()) {
            newTimes = new double[times.length];
        }
        for (int i = 0; i < timeOrdinals.length; i++) {
            newOrdinals[i] = timeOrdinals[indices[i]];
            if (hasTimes()) { newTimes[i] = times[indices[i]]; }
        }

        timeOrdinals = newOrdinals;
        if (hasTimes()) times = newTimes;
    }

//IdGroup interface methods:

    /**
     * Returns the identifier at the specified index.
     *
     * @param i The zero-based index.
     * @return The Identifier object.
     */
    public Identifier getIdentifier(int i) {return taxa.getIdentifier(i);}

    /**
     * Sets the identifier at the specified index.
     *
     * @param i The zero-based index.
     * @param ident The new Identifier object to set.
     */
    public void setIdentifier(int i, Identifier ident) { taxa.setIdentifier(i, ident); }

    /**
     * Returns the total number of identifiers in this data set.
     *
     * @return The count of identifiers.
     */
    public int getIdCount() { return taxa.getIdCount(); }

    /**
     * Finds the index of the identifier with the given name.
     *
     * @param name The name of the identifier to search for.
     * @return The zero-based index of the identifier, or -1 if not found.
     */
    public int whichIdNumber(String name) { return taxa.whichIdNumber(name); }

    /**
     * Returns the underlying IdGroup object containing the identifiers.
     *
     * @return The IdGroup object.
     * @deprecated TimeOrderCharacterData now implements IdGroup directly, making this method redundant.
     */
    public IdGroup getIdGroup() { return taxa; }

    /**
     * A simple utility method for generating a suggested maximum mutation rate based on the minimum time difference between samples.
     * The aim is to keep the range of Expected Substitutions (mu * t) reasonable (e.g., less than 1).
     *
     * @return The suggested maximum mutation rate.
     */
    public final double getSuggestedMaximumMutationRate() {
        double[] times = getUniqueTimeArray();
        double minDiff = times[1] - times[0];
        for(int i = 2 ; i < times.length ; i++) {
            double diff = times[i]-times[i-1];
            if(diff<minDiff) { minDiff = diff; }
        }
        return 5/minDiff;
    }

// ============================================================================
// ====== SubgroupHandler =====================================================
/**
 * Handles any subgroups
 */
	private static final class SubgroupHandler implements Serializable {
		private int[] subgroupIndexes_;

		//
		// Serialization code
		//
		private static final long serialVersionUID= 341384756632221L;

		//serialver -classpath ./classes pal.misc.TimeOrderCharacterData
		private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
			out.writeByte(1); //Version number
			out.writeObject(subgroupIndexes_);
		}

		private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException{
			byte version = in.readByte();
			switch(version) {
				default : {
					subgroupIndexes_ = (int[])in.readObject();
					break;
				}
			}
		}
	// ===============
	// Constructors

		private SubgroupHandler(int[] subgroupIndexes) {
			this.subgroupIndexes_ = pal.misc.Utils.getCopy(subgroupIndexes);
		}
		private SubgroupHandler(SubgroupHandler toCopy, IdGroup oldBase, IdGroup newBase) {
			this(toCopy.subgroupIndexes_, oldBase,newBase);
		}
		private SubgroupHandler(int[] oldSubgroupIndexes, IdGroup oldBase, IdGroup newBase) {
			this.subgroupIndexes_ = new int[oldSubgroupIndexes.length];
			for(int i = 0 ; i < oldSubgroupIndexes.length ; i++) {
				String oldName = oldBase.getIdentifier(oldSubgroupIndexes[i]).getName();
				int newIndex = newBase.whichIdNumber(oldName);
				if(newIndex<0) {
					throw new IllegalArgumentException("Incompatible bases:"+oldName);
				}
				this.subgroupIndexes_[i] = newIndex;
			}
		}
		public Identifier[] getSubgroupMembers(TimeOrderCharacterData parent) {
			final int size = subgroupIndexes_.length;
			Identifier[] ids = new Identifier[size];
			for(int i = 0 ; i < size ; i++) {
				ids[i] = parent.getIdentifier(subgroupIndexes_[i]);
			}
			return ids;
		}
		public TimeOrderCharacterData generateNewTOCD(TimeOrderCharacterData parent) {
			final int size = subgroupIndexes_.length;
			Identifier[] ids = new Identifier[size];
			for(int i = 0 ; i < size ; i++) {
				ids[i] = parent.getIdentifier(subgroupIndexes_[i]);
			}
			TimeOrderCharacterData tocd = new TimeOrderCharacterData(new SimpleIdGroup(ids),parent.getUnits());

			if(parent.hasTimes()) {
				double[] times = new double[size];
				for(int i = 0 ; i < size ; i++) {
					times[i] = parent.getTime(subgroupIndexes_[i]);
				}
				tocd.setTimes(times,parent.getUnits());
			} else {
				int[] ordinals = new int[size];
				for(int i = 0 ; i < size ; i++) {
					ordinals[i] = parent.getTimeOrdinal(subgroupIndexes_[i]);
				}
				tocd.setOrdinals(ordinals);
			}
			return tocd;
		}
		public static final SubgroupHandler[] create(int[][] subgroupInfo) {
			SubgroupHandler[] handlers = new SubgroupHandler[subgroupInfo.length];
			for(int i = 0 ; i < handlers.length ; i++) {
				handlers[i] = new SubgroupHandler(subgroupInfo[i]);
			}
			return handlers;
		}
		public static final SubgroupHandler[] create(int[] subgroupInfo) {
			return new SubgroupHandler[] {
					new SubgroupHandler(subgroupInfo)
			};
		}
		private static final int[] getIndexes(final TimeOrderCharacterData parent, final String[] subgroupInfo) {
			int count = 0;
			for(int i = 0 ; i < subgroupInfo.length ; i++) {
				if(parent.whichIdNumber(subgroupInfo[i])>=0) {
					count++;
				}
			}
			final int[] indexes = new int[count];
			count = 0;
			for(int i = 0 ; i < subgroupInfo.length ; i++) {
				int parentIndex= parent.whichIdNumber(subgroupInfo[i]);
				if(parentIndex>=0) {
					indexes[count++] = parentIndex;
				}
			}
			System.out.println("Parent:"+parent);
			System.out.println("Indexes:"+pal.misc.Utils.toString(indexes));
			return indexes;
		}
		public static final SubgroupHandler[] create(TimeOrderCharacterData parent, String[][] subgroupInfo) {
			SubgroupHandler[] handlers = new SubgroupHandler[subgroupInfo.length];
			for(int i = 0 ; i < handlers.length ; i++) {
				handlers[i] = new SubgroupHandler(getIndexes(parent, subgroupInfo[i]));
			}
			return handlers;
		}
		/**
		 * Get a copy of the subgroup handlers such that the numbering is reorganised to match newbase
		 */
		public static final SubgroupHandler[] getCopy(SubgroupHandler[] toCopy, IdGroup oldBase, IdGroup newBase) {
			if(toCopy==null) {
				return null;
			}
			SubgroupHandler[] copy = new SubgroupHandler[toCopy.length];
			for(int i = 0 ; i < toCopy.length ; i++) {
				copy[i] = new SubgroupHandler(toCopy[i],oldBase,newBase);
			}
			return copy;
		}
		public static final SubgroupHandler[] create(TimeOrderCharacterData parent, String[] subgroupInfo) {
			return new SubgroupHandler[] {
					new SubgroupHandler(getIndexes(parent, subgroupInfo))
			};
		}
	}
}
