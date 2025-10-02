// FormattedInput.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.io;

import java.io.*;
import java.text.*;
import java.util.*;


/**
 * tools to simplify formatted input from an input stream
 *
 * @version $Id: FormattedInput.java,v 1.7 2002/10/14 06:54:25 matt Exp $
 *
 * @author Korbinian Strimmer
 */
public class FormattedInput implements Serializable
{
	//
	// Public stuff
	//

    /**
     * Returns the single instance of this class.
     * <p>
     * This class implements the Singleton pattern, so there is no public constructor.
     *
     * @return the single {@link FormattedInput} instance
     */
    public static FormattedInput getInstance() {
        if (singleton == null) {
            singleton = new FormattedInput();
        }
        return singleton;
    }

    /**
     * Skips to the beginning of the next line in the given input stream.
     * <p>
     * Recognized line terminators are:
     * <ul>
     *     <li>Unix: <code>\n</code></li>
     *     <li>DOS/Windows: <code>\r\n</code></li>
     *     <li>Macintosh (classic): <code>\r</code></li>
     * </ul>
     *
     * @param in the {@link PushbackReader} to read from
     * @throws IOException if an I/O error occurs
     */
    public void nextLine(PushbackReader in) throws IOException {
        readLine(in, false);
    }

    /**
     * read a whole line
     *
     * @param in the {@link PushbackReader} to read the line from
     * @param keepWhiteSpace if {@code true}, whitespace characters are preserved;
     *                       if {@code false}, whitespace is ignored
     * @return a {@link String} containing the content of the line, without the line terminator
     * @throws IOException if an I/O error occurs while reading from the stream
     */
    public String readLine(PushbackReader in, boolean keepWhiteSpace)
            throws IOException
    {
        StringBuffer buffer = new StringBuffer();

        int EOF = -1;
        int c;

        c = in.read();
        while (c != EOF && c != '\n' && c != '\r')
        {
            if (!isWhite(c) || keepWhiteSpace)
            {
                buffer.append((char) c);
            }
            c = in.read();
        }

        if (c == '\r')
        {
            c = in.read();
            if (c != '\n')
            {
                in.unread(c);
            }
        }

        return buffer.toString();
    }

    /**
     * Skips whitespace characters in the given input stream and returns the first
     * non-whitespace character.
     *
     * @param in the {@link PushbackReader} to read from
     * @return the first non-whitespace character, or -1 (EOF) if end of stream is reached
     * @throws IOException if an I/O error occurs
     */
    public int skipWhiteSpace(PushbackReader in) throws IOException {
        int EOF = -1;
        int c;

        do {
            c = in.read();
        } while (c != EOF && isWhite(c));

        return c;
    }

    /**
     * Reads the next non-whitespace character from the input stream.
     * <p>
     * Unlike {@link #skipWhiteSpace}, this method throws an exception if the end
     * of the stream is reached.
     *
     * @param input the {@link PushbackReader} to read from
     * @return the next non-whitespace character
     * @throws IOException if an I/O error occurs or end of file is reached
     */
    public int readNextChar(PushbackReader input) throws IOException {
        int EOF = -1;

        int c = skipWhiteSpace(input);

        if (c == EOF) {
            throw new IOException("End of file/stream");
        }
        return c;
    }

    /**
     * Reads the next word from the input stream.
     * <p>
     * A word is defined as a sequence of non-whitespace characters. Leading
     * whitespace is skipped automatically. The character after the word is
     * pushed back into the stream.
     *
     * @param in the {@link PushbackReader} to read from
     * @return the next word as a {@link String}, or an empty string if no word is found
     * @throws IOException if an I/O error occurs
     */
    public String readWord(PushbackReader in) throws IOException {
        StringBuffer buffer = new StringBuffer();
        int EOF = -1;
        int c = skipWhiteSpace(in);

        while (c != EOF && !isWhite(c)) {
            buffer.append((char) c);
            c = in.read();
        }

        if (c != EOF) {
            in.unread(c);
        }

        return buffer.toString();
    }

    /**
     * Reads a sequence label from the given input stream.
     * <p>
     * A sequence label cannot contain whitespace characters (except newline or carriage return)
     * or any of the following characters: {@code :,;()[]{}}.
     * <p>
     * Newline ('\n') and carriage return ('\r') are not counted as whitespace for this purpose.
     *
     * @param in the {@link PushbackReader} to read from
     * @param maxLength the maximum allowed length of the label; if negative, any length is permitted
     * @return a {@link String} containing the label read from the stream
     * @throws IOException if an I/O error occurs
     */
    public String readLabel(PushbackReader in, int maxLength) throws IOException {
        StringBuffer buffer = new StringBuffer();

        int EOF = -1;
        int c;

        c = skipWhiteSpace(in);

        // read characters until invalid character or max length reached
        while (c != EOF && (maxLength < 0 || buffer.length() != maxLength) &&
                !((isWhite(c) && c != '\n' && c != '\r') ||
                        c == ':' || c == ',' || c == ';' ||
                        c == '(' || c == ')' ||
                        c == '[' || c == ']' ||
                        c == '{' || c == '}')) {
            if (c != '\n' && c != '\r') buffer.append((char) c);
            c = in.read();
        }

        if (c != EOF) {
            in.unread(c);
        }

        return buffer.toString();
    }

    /**
     * Reads the next number from the input stream.
     * <p>
     * A number can contain digits, a decimal point ('.'), a minus sign ('-'),
     * and an exponent indicated by 'e' or 'E'. Optionally, newline ('\n') or carriage return ('\r')
     * can be ignored when {@code ignoreNewlineCR} is {@code true}.
     *
     * @param in the {@link PushbackReader} to read from
     * @param ignoreNewlineCR if {@code true}, newline and carriage return characters are ignored as separators
     * @return a {@link String} containing the number read from the stream
     * @throws IOException if an I/O error occurs
     */
    public String readNumber(PushbackReader in, boolean ignoreNewlineCR) throws IOException {
        StringBuffer buffer = new StringBuffer();

        int EOF = -1;
        int c;

        // search for first number character
        do {
            c = in.read();
        } while (c != EOF &&
                !(c == '-' || c == '.' || Character.isDigit((char) c)));

        // search for last number character
        while (c != EOF &&
                (c == '-' || c == '.' || c == 'e' || c == 'E' || Character.isDigit((char) c)
                        || (isNewlineCR(c) && ignoreNewlineCR))) {
            if (!(isNewlineCR(c) && ignoreNewlineCR)) {
                buffer.append((char) c);
            }
            c = in.read();
        }

        if (c != EOF) {
            in.unread(c);
        }

        return buffer.toString();
    }

	/**
	 * read next number from stream and convert it to a double
	 * (newline/cr are treated as separators)
	 *
     * @param in the {@link PushbackReader} to read from
     * @return the number read, as a {@code double}
     * @throws IOException if an I/O error occurs or the end of stream is reached unexpectedly
     * @throws NumberFormatException if the number cannot be parsed as a double
     */
	public double readDouble(PushbackReader in)
		throws IOException, NumberFormatException
	{
		return readDouble(in, false);
	}

	/**
	 * read next number from stream and convert it to a double
	 *
     * @param in the {@link PushbackReader} to read from
     * @param ignoreNewlineCR if {@code true}, newline and carriage return characters are ignored as separators
     * @return the number read, as a {@code double}
     * @throws IOException if an I/O error occurs or the end of stream is reached unexpectedly
     * @throws NumberFormatException if the number cannot be parsed as a double
     */
	public double readDouble(PushbackReader in, boolean ignoreNewlineCR)
		throws IOException, NumberFormatException
	{
		String w = readNumber(in, ignoreNewlineCR);
		if (w.length() == 0)
		{
			throw new IOException("End of file/stream");
		}

		return Double.valueOf(w).doubleValue();
	}


    /**
     * Reads the next number from the stream and converts it to an int.
     * <p>
     * Newline and carriage return characters are treated as separators.
     *
     * @param in the {@link PushbackReader} to read from
     * @return the number read, as an {@code int}
     * @throws IOException if an I/O error occurs or the end of stream is reached unexpectedly
     * @throws NumberFormatException if the number cannot be parsed as an int
     */
    public int readInt(PushbackReader in)
            throws IOException, NumberFormatException
    {
        return readInt(in, false);
    }

    /**
     * Reads the next number from the stream and converts it to an int.
     *
     * @param in the {@link PushbackReader} to read from
     * @param ignoreNewlineCR if {@code true}, newline and carriage return characters are ignored as separators
     * @return the number read, as an {@code int}
     * @throws IOException if an I/O error occurs or the end of stream is reached unexpectedly
     * @throws NumberFormatException if the number cannot be parsed as an int
     */
    public int readInt(PushbackReader in, boolean ignoreNewlineCR)
            throws IOException, NumberFormatException
    {
        String w = readNumber(in, ignoreNewlineCR);
        if (w.length() == 0)
        {
            throw new IOException("End of file/stream");
        }

        return Integer.valueOf(w).intValue();
    }

    //
	// Private stuff
	//

	// private constructor
	private FormattedInput()
	{
		// Just to prevent a public constructor
	}

	private static FormattedInput singleton;

	private static boolean isWhite(int c)
	{
		return Character.isWhitespace((char) c);
	}

	private static boolean isNewlineCR(int c)
	{
		if (c == '\n' || c == 'r')
		{
			return true;
		}
		else
		{
			return false;
		}
	}
}
