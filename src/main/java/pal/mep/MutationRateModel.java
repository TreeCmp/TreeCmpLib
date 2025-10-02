// MutationRateModel.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.mep;

import pal.math.*;
import pal.misc.*;
import pal.io.*;
import java.io.*;
import pal.eval.*;
import pal.treesearch.*;


/**
 * This abstract class contains methods that are of general use for
 * modelling mutation rate changes over time.
 *
 * @version $Id: MutationRateModel.java,v 1.12 2004/08/02 05:22:04 matt Exp $
 *
 * @author Alexei Drummond
 */
public abstract class MutationRateModel implements Units,
	Parameterized, Report, Cloneable, Serializable, Summarizable
{
	//
	// Private and protected stuff
	//

	protected FormattedOutput fo;

	/**
	 * Units in which time units are measured.
	 */
	private int units;

	private double maximumMutationRate_;


	private static final long serialVersionUID=-1755051453782951214L;

	//serialver -classpath ./classes pal.mep.ConstantMutationRate

	private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
		out.writeByte(1); //Version number
		out.writeInt(units);
		out.writeDouble(maximumMutationRate_);
	}

	private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException{
		byte version = in.readByte();
		switch(version) {
			default : {
				units = in.readInt();
				maximumMutationRate_ = in.readDouble();
				fo = FormattedOutput.getInstance();
				break;
			}
		}
	}


	protected MutationRateModel(int units, double maximumMutationRate)
	{
		setUnits(units,maximumMutationRate);
		fo = FormattedOutput.getInstance();
	}
	protected MutationRateModel(MutationRateModel toCopy) {
		this.units = toCopy.units;
		this.maximumMutationRate_ = toCopy.maximumMutationRate_;
		fo = FormattedOutput.getInstance();
	}


	public abstract Object clone();

	//
	// Because I don't like casting if I don't have to
	//
	public abstract MutationRateModel getCopy();

	//
	// functions that define a mutation rate model (left for subclass)
	//

    /**
     * Gets the mutation rate at a given time t.
     *
     * @param t the time at which to evaluate the mutation rate
     * @return the mutation rate μ(t)
     */
    public abstract double getMutationRate(double t);

    /**
     * Returns the integral of the mutation rate function from 0 to t.
     *
     * @param t the upper limit of integration
     * @return the expected number of substitutions (∫ μ(x) dx from 0 to t)
     */
    public abstract double getExpectedSubstitutions(double t);

    /**
     * Returns the time at which a given expected number of substitutions has occurred,
     * assuming we start at time 0.
     *
     * @param expectedSubs the expected number of substitutions
     * @return the time t at which the expected substitutions have occurred
     */
    public double getTime(double expectedSubs) {
        return getEndTime(expectedSubs, 0);
    }

    /**
     * Returns the end time at which a given expected number of substitutions has occurred,
     * starting from a specified start time.
     *
     * @param expectedSubs the expected number of substitutions
     * @param startTime the starting time for the calculation
     * @return the end time at which the expected substitutions have occurred
     */
    public abstract double getEndTime(double expectedSubs, double startTime);

	/**
	 * Linearly scales this mutation rate model.
	 * @param scale getExpectedSubstitutions should return scale instead of 1.0 at time t.
	 */
	public abstract void scale(double scale);

	// Parameterized and Report interface is also left for subclass

	// general functions

    /**
     * Calculates the integral of 1/μ(x) dx between start and finish.
     *
     * @param start the lower limit of integration
     * @param finish the upper limit of integration
     * @return the expected number of substitutions between start and finish
     */
    public double getExpectedSubstitutions(double start, double finish) {
        return getExpectedSubstitutions(finish) - getExpectedSubstitutions(start);
    }
    /**
     * Scales a TimeOrderCharacterData object to use EXPECTED_SUBSTITUTIONS
     * based on this MutationRateModel.
     *
     * @param toScale the TimeOrderCharacterData to scale
     * @return a new TimeOrderCharacterData scaled to EXPECTED_SUBSTITUTIONS
     * @throws IllegalArgumentException if units of this model do not match
     *         the units of the TimeOrderCharacterData object
     */
    public TimeOrderCharacterData scale(TimeOrderCharacterData toScale) {
        if(getUnits() != toScale.getUnits()) {
            throw new IllegalArgumentException(
                    "Incompatible units, expecting " + getUnits() +
                            ", found (in toScale) " + toScale.getUnits()
            );
        }
        TimeOrderCharacterData scaled = toScale.clone(toScale);
        double[] times = new double[scaled.getIdCount()];
        for (int i = 0; i < times.length; i++) {
            times[i] = getExpectedSubstitutions(scaled.getTime(i));
        }
        scaled.setTimes(times, Units.EXPECTED_SUBSTITUTIONS, false);
        return scaled;
    }

    /**
     * Sets the units of measurement.
     *
     * @param u units
     * @param maximumMutationRate the maximum allowable mutation rate for the given units
     * @throws IllegalArgumentException if units are EXPECTED_SUBSTITUTIONS
     */
    public final void setUnits(int u, double maximumMutationRate) {
        if (u == Units.EXPECTED_SUBSTITUTIONS) {
            throw new IllegalArgumentException("Units cannot be Expected Substitutions!");
        }
        units = u;
        this.maximumMutationRate_ = maximumMutationRate;
    }

    /**
     * @return the maximum mutation rate as indicated by the user
     */
    protected final double getMaximumMutationRate() {
        return maximumMutationRate_;
    }

    /**
     * Returns the units of measurement.
     *
     * @return the units
     */
    public int getUnits() {
        return units;
    }

    /**
     * Override if there is any orthogonal hint information available.
     *
     * @return an OrthogonalHints object, or null if none
     */
    public OrthogonalHints getOrthogonalHints() {
        return null;
    }

    public abstract String toSingleLine();

	public abstract Factory generateFactory();
// ===========================================================================
// ==== Factory interface
	/**
	 * An interface for objects which generate fresh MutationRAteModels
	 */
	public interface Factory {
        /**
         * Requests a new {@link MutationRateModel} instance.
         *
         * @return a newly created {@link MutationRateModel}
         */
		public MutationRateModel generateNewModel();
		public ConstraintModel buildConstraintModel(SampleInformation si, MolecularClockLikelihoodModel.Instance likelihoodModel);
	}

}
