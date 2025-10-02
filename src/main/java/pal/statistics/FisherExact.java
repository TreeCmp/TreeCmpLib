// ContigencyTable.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.statistics;

/**
 * This does a one tail fisher exact test.
 * It uses an array of factorials initialized at the beginning to provide speed.
 * There could be better ways to do this.
 *
 * @version $Id: FisherExact.java,v 1
 *
 * @author Ed Buckler
 */

public class FisherExact {
  private double[] f;
  int maxSize;


    /**
   * constructor for FisherExact table
   *
   * @param maxSize is the maximum sum that will be encountered by the table (a+b+c+d)
   */
  public FisherExact(int maxSize) {
    this.maxSize=maxSize;
    double cf=1.0;
    f=new double[maxSize+1];
    f[0]=0.0;
    for(int i=1; i<=this.maxSize; i++)
      {f[i]=f[i-1]+Math.log(i);}
  }

    /**
     * Calculates the P-value for a specific state based on cell counts in a 2x2 contingency matrix.
     *
     * @param a The count in the top-left cell of the 2x2 matrix.
     * @param b The count in the top-right cell of the 2x2 matrix.
     * @param c The count in the bottom-left cell of the 2x2 matrix.
     * @param d The count in the bottom-right cell of the 2x2 matrix.
     * @return The calculated P-value, or {@code Double.NaN} if the total count {@code n} exceeds {@code maxSize}.
     */
  public final double getP(int a, int b, int c, int d) {
    int n=a+b+c+d;
    if(n>maxSize)
      {return Double.NaN;}
    double p;
    p=(f[a+b]+f[c+d]+f[a+c]+f[b+d])-(f[a]+f[b]+f[c]+f[d]+f[n]);
    return Math.exp(p);
  }

    /**
     * Calculates the one-tailed P-value for the Fisher's Exact Test.
     * This sums the probabilities of the current 2x2 matrix and all matrices
     * that are more extreme in the observed direction.
     *
     * @param a The count in the top-left cell of the 2x2 matrix.
     * @param b The count in the top-right cell of the 2x2 matrix.
     * @param c The count in the bottom-left cell of the 2x2 matrix.
     * @param d The count in the bottom-right cell of the 2x2 matrix.
     * @return The calculated one-tailed P-value, or {@code Double.NaN} if the total count {@code n} exceeds {@code maxSize}.
     */
  public final double getCumlativeP(int a, int b, int c, int d) {
    int min,i;
    int n=a+b+c+d;
    if(n>maxSize)
      {return Double.NaN;}
    double p=0;
    p+=getP(a, b, c, d);
    if((a*d)>=(b*c))
      {min=(c<b)?c:b;
      for(i=0; i<min; i++)
        {p+=getP(++a, --b, --c, ++d);}
      }
    if((a*d)<(b*c))
      {min=(a<d)?a:d;
      for(i=0; i<min; i++)
        {p+=getP(--a, ++b, ++c, --d);}
      }
    return p;
  }

}