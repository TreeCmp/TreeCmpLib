// FormattedOutput.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.io;

import java.io.*;
import java.text.*;
import java.util.*;
import java.math.*;

/**
 * tools to simplify formatted output to a stream
 *
 * @version $Id: FormattedOutput.java,v 1.17 2003/04/03 05:55:53 matt Exp $
 *
 * @author Korbinian Strimmer
 * @author Alexei Drummond
 */
public class FormattedOutput implements Serializable
{
	//
	// Public stuff
	//

    /**
     * Returns the singleton instance of {@link FormattedOutput}.
     * <p>
     * This class uses the singleton pattern, so only one instance can exist.
     * The constructor is private to enforce this.
     * This method is synchronized to ensure thread safety.
     *
     * @return the singleton instance of {@link FormattedOutput}
     */
    public synchronized static FormattedOutput getInstance() {
        if (singleton == null) {
            singleton = new FormattedOutput();
        }
        return singleton;
    }

	/**
	 * print decimal number with a prespecified number
	 * of digits after the point
	 *
	 * @param out output stream
	 * @param number to be printed
	 * @param width number of fraction digits
	 *
	 * @return length of the string printed
	 */
	public int displayDecimal(PrintWriter out, double number, int width)
	{
		String s = getDecimalString(number, width);

		out.print(s);

		return s.length();
	}

    /**
     * Returns a decimal string representation of a number with a specified
     * number of fraction digits.
     * <p>
     * The output string will have exactly {@code width} digits after the
     * decimal point. The method is synchronized to ensure thread safety
     * when using the internal {@link java.text.NumberFormat} instance.
     *
     * @param number the number to format
     * @param width the number of digits to display after the decimal point
     * @return a string representation of {@code number} with {@code width} decimal places
     */
    public synchronized String getDecimalString(double number, int width) {
        nf.setMinimumFractionDigits(width);
        nf.setMaximumFractionDigits(width);

        return nf.format(number);
    }

	private static final double round(double number, int sf) {
		double decimals = Math.floor(Math.log(number) / Math.log(10.0));
		double power =  Math.pow(10, decimals - sf+1);
		number /= power;
		number = Math.round(number);
		number *= power;
		return number;
	}
	public String getSFString(double[] numbers, int sf, String delimiter) {
		StringBuffer sb = new StringBuffer();
		for(int i = 0 ; i < numbers.length ; i++) {
			sb.append(getSFString(numbers[i],sf));
			if(i!=numbers.length-1) {
				sb.append(delimiter);
			}
		}
		return sb.toString();
	}
	public String getSFString(double number, int sf) {
		//This seems to give the correct number of sf
		int decimals = (int)Math.ceil(Math.log(number)/Math.log(10));
		double power = Math.pow(10,sf-decimals);
		double result = (Math.round(number*power)/power);
		if(Double.isInfinite(result)||Double.isNaN(result)) {
			return String.valueOf(number);
		}
		return String.valueOf(result);
		/*double decimals = Math.floor(Math.log(number) / Math.log(10.0));
		double power =  Math.pow(10, decimals - sf);
		number /= power;
		number = Math.round(number);
		number *= power;
		return "" + number;*/
	}


	/**
	 * print label with a prespecified length
	 * (label will be shortened or spaces will introduced, if necessary)
	 *
	 * @param out output stream
	 * @param label label to be printed
	 * @param width desired length
	 */
	public void displayLabel(PrintWriter out, String label, int width)
	{
		int len = label.length();

		if (len == width)
		{
			// Print as is
			out.print(label);
		}
		else if (len < width)
		{
			// fill rest with spaces
			out.print(label);
			multiplePrint(out, ' ', width - len);
		}
		else
		{
			// Print first width characters
			for (int i = 0; i < width; i++)
			{
				out.print(label.charAt(i));
			}
		}
	}

	/**
	 * print integer, aligned to a reference number,
	 * (introducing space at the left side)
	 *
	 * @param out output stream
	 * @param num number to be printed
	 * @param maxNum reference number
	 */
	public void displayInteger(PrintWriter out, int num, int maxNum)
	{
		int lenNum = Integer.toString(num).length();
		int lenMaxNum = Integer.toString(maxNum).length();

		if (lenNum < lenMaxNum)
		{
			multiplePrint(out, ' ', lenMaxNum - lenNum);
		}
		out.print(num);
	}

	/**
	 * print whitespace of length of a string displaying a given integer
	 *
	 * @param out stream
	 * @param maxNum number
	 */
	public void displayIntegerWhite(PrintWriter out, int maxNum)
	{
		int lenMaxNum = Integer.toString(maxNum).length();

		multiplePrint(out, ' ', lenMaxNum);
	}

	/**
	 * repeatedly print a character
	 *
	 * @param out output stream
	 * @param c   character
	 * @param num number of repeats
	 */
	public void multiplePrint(PrintWriter out, char c, int num)
	{
		for (int i = 0; i < num; i++)
		{
			out.print(c);
		}
	}

	/**
	 * returns of string of a given length of a single character.
	 *
	 * @param size length of the string required
	 * @param c   character
     * @return a string of length {@code size} consisting of the character {@code c}
     */
	public static String space(int size, char c) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < size; i++) {
			sb.append(c);
		}
		return new String(sb);
	}

	//
	// Private stuff
	//

	// private constructor
	private FormattedOutput()
	{
		nf = NumberFormat.getInstance(Locale.UK);
		nf.setGroupingUsed(false);
	}

	private static FormattedOutput singleton = null;

	private NumberFormat nf;
}

