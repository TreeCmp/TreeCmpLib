// SimpleSummarizable.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

/**
 * A simple implementation of a Summarizable object. Can be used to provide a
 * summarizable object based upon another summarizable object but with the
 * minimum storage requirement (see SimpleSummarizable(Summarizable) constructor.
 *
 *
 * @version $Id: SimpleSummarizable.java,v 1.2 2001/07/13 14:39:13 korbinian Exp $
 *
 * @author Matthew Goode
 */
package pal.misc;

public class SimpleSummarizable implements Summarizable, java.io.Serializable {
	private String[] types_;
	private double[] values_;

    /**
     * Simple constructor.
     * Note: The arrays of types and values should be of the same length.
     *
     * @param types  array of summary types
     * @param values array of corresponding values
     */
    public SimpleSummarizable(String[] types, double[] values) {
        this.types_ = types;
        this.values_ = values;
    }

    /**
     * Imitation constructor - creates a new SimpleSummarizable object
     * based on another Summarizable object with minimum memory requirements.
     * No reference to the original object is maintained.
     *
     * @param toImitate the Summarizable object to imitate; the new object
     *                  will contain the same types and values as toImitate
     *                  at the time of construction
     */
    public SimpleSummarizable(Summarizable toImitate) {
        this.types_ = toImitate.getSummaryTypes();
        this.values_ = new double[this.types_.length];
        for (int i = 0; i < values_.length; i++) {
            this.values_[i] = toImitate.getSummaryValue(i);
        }
    }

    public String[] getSummaryTypes() {
		return this.types_;
	}

	public double getSummaryValue(int type) {
   	return this.values_[type];
	}

} 
