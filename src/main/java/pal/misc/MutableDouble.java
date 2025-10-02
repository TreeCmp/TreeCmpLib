// MutableDouble.java
//
// (c) 1999-2004 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.misc;

import java.io.*;
import java.util.Enumeration;


import java.util.*;

public class MutableDouble implements java.io.Serializable{
	private final double defaultValue_;
	private final double minimumValue_;
	private final double maximumValue_;
	private double se_;
	private double currentValue_;

	private final String name_;

    /**
     * Constructs a MutableDouble with the given initial, default, minimum, and maximum values.
     * The default value is also used as the initial current value.
     *
     * @param initialValue the initial value (currently ignored, defaultValue is used)
     * @param defaultValue the default value
     * @param minimumValue the minimum allowed value
     * @param maximumValue the maximum allowed value
     * @param name a name associated with this value
     */
    public MutableDouble(double initialValue, double defaultValue, double minimumValue, double maximumValue, String name) {
        this.currentValue_ = defaultValue;
        this.defaultValue_ = defaultValue;
        this.minimumValue_ = minimumValue;
        this.maximumValue_ = maximumValue;
        this.name_ = name;
    }

    /**
     * Sets the current value.
     *
     * @param value the new value
     */
    public final void setValue(double value) { this.currentValue_ = value; }

    /**
     * Returns the current value.
     *
     * @return the current value
     */
    public final double getValue() { return currentValue_; }

    /**
     * Returns the minimum allowed value.
     *
     * @return the minimum value
     */
    public final double getLowerLimit() { return minimumValue_; }

    /**
     * Returns the maximum allowed value.
     *
     * @return the maximum value
     */
    public final double getUpperLimit() { return maximumValue_; }

    /**
     * Returns the default value.
     *
     * @return the default value
     */
    public final double getDefaultValue() { return defaultValue_; }

    /**
     * Returns the standard error associated with this value.
     *
     * @return the standard error
     */
    public final double getSE() { return se_; }

    /**
     * Sets the standard error.
     *
     * @param value the standard error value
     */
    public final void setSE(double value) { se_ = value; }

    /**
     * Returns the name associated with this MutableDouble.
     *
     * @return the name
     */
    public final String getName() { return name_; }

    /**
     * Returns a string representation in the format "name:currentValue".
     *
     * @return a string representing this object
     */
    public String toString() {
        return name_ + ":" + currentValue_;
    }
}
