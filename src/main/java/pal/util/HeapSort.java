// HeapSort.java
//
// (c) 1999-2001 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)


package pal.util;

import pal.math.*;

import java.util.*;

/**
 * sorts numbers and comparable objects by treating contents of array as a binary tree.
 * KNOWN BUGS: There is a horrible amount of code duplication here!
 *
 * @version $Id: HeapSort.java,v 1.11 2003/03/23 00:34:23 matt Exp $
 *
 * @author Alexei Drummond
 * @author Korbinian Strimmer
 */
public class HeapSort {

	//
	// Public stuff
	//

    /**
     * Sortuje tablicę indeksów na podstawie wartości w towarzyszącym obiekcie {@code Vector}
     * zawierającym obiekty porównywalne. Indeksy są tak uporządkowane, aby odzwierciedlały
     * rosnący porządek elementów w wektorze.
     *
     * @param array Obiekt {@code Vector} zawierający obiekty porównywalne, których porządek ma być odzwierciedlony przez indeksy.
     * @param indices Tablica liczb całkowitych, która zostanie wypełniona początkowymi indeksami (0 do n-1), a następnie posortowana.
     */
    public static void sort(Vector array, int[] indices) {

        // ensures we are starting with valid indices
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }

        int temp;
        int j, n = array.size();

        // turn input array into a heap
        for (j = n/2; j > 0; j--) {
            adjust(array, indices, j, n);
        }

        // remove largest elements and put them at the end
        // of the unsorted region until you are finished
        for (j = n-1; j > 0; j--) {
            temp = indices[0];
            indices[0] = indices[j];
            indices[j] = temp;
            adjust(array, indices, 1, j);
        }
    }

    /**
     * Sortuje obiekt {@code Vector} zawierający obiekty porównywalne bezpośrednio w kolejności rosnącej,
     * modyfikując wektor w miejscu (in place).
     *
     * @param array Obiekt {@code Vector} z obiektami porównywalnymi do posortowania.
     */
    public static void sort(Vector array) {

        Object temp;
        int j, n = array.size();

        // turn input array into a heap
        for (j = n/2; j > 0; j--) {
            adjust(array, j, n);
        }

        // remove largest elements and put them at the end
        // of the unsorted region until you are finished
        for (j = n-1; j > 0; j--) {
            temp = array.elementAt(0);
            array.setElementAt(array.elementAt(j), 0);
            array.setElementAt(temp, j);
            adjust(array, 1, j);
        }
    }

    /**
     * Sortuje standardową tablicę obiektów implementujących {@code pal.util.Comparable}
     * bezpośrednio w kolejności rosnącej, modyfikując tablicę w miejscu (in place).
     *
     * @param array Tablica obiektów porównywalnych do posortowania.
     */
    public static void sort(pal.util.Comparable[] array) {

        pal.util.Comparable temp;
        int j, n = array.length;

        // turn input array into a heap
        for (j = n/2; j > 0; j--) {
            adjust(array, j, n);
        }

        // remove largest elements and put them at the end
        // of the unsorted region until you are finished
        for (j = n-1; j > 0; j--) {
            temp = array[0];
            array[0] = array[j];
            array[j] = temp;
            adjust(array, 1, j);
        }
    }

    /**
     * Sortuje standardową tablicę obiektów w kolejności rosnącej, używając dostarczonego obiektu {@code Comparator}.
     *
     * @param array Tablica obiektów do posortowania.
     * @param c Obiekt {@code Comparator}, który definiuje relację porządkowania między obiektami.
     */
    public static void sort(Object[] array, Comparator c) {

        Object temp;
        int j, n = array.length;

        // turn input array into a heap
        for (j = n/2; j > 0; j--) {
            adjust(array, c, j, n);
        }

        // remove largest elements and put them at the end
        // of the unsorted region until you are finished
        for (j = n-1; j > 0; j--) {
            temp = array[0];
            array[0] = array[j];
            array[j] = temp;
            adjust(array, c, 1, j);
        }
    }

    /**
     * Tworzy posortowaną kopię wejściowej tablicy wartości double, pozostawiając oryginalną tablicę bez zmian.
     *
     * @param array Tablica wartości double do skopiowania i posortowania.
     * @return Nowa tablica wartości double posortowana w kolejności rosnącej.
     */
    public static final double[] getSorted(double[] array) {
        double[] copy = new double[array.length];
        System.arraycopy(array,0,copy,0,copy.length);
        sort(copy);
        return copy;
    }

    /**
     * Sortuje tablicę wartości {@code double} bezpośrednio w kolejności rosnącej,
     * modyfikując tablicę w miejscu (in place).
     *
     * @param array Tablica wartości double do posortowania.
     */
    public static void sort(double[] array) {
        double temp;
        int j, n = array.length;

        // turn input array into a heap
        for (j = n/2; j > 0; j--) {
            adjust(array, j, n);
        }

        // remove largest elements and put them at the end
        // of the unsorted region until you are finished
        for (j = n-1; j > 0; j--) {
            temp = array[0];
            array[0] = array[j];
            array[j] = temp;
            adjust(array, 1, j);
        }
    }

    /**
     * Sortuje tablicę wartości {@code double} w kolejności rosnącej na podstawie
     * **wartości bezwzględnej** każdego elementu, modyfikując tablicę w miejscu.
     *
     * @param array Tablica wartości double do posortowania według wartości bezwzględnej.
     */
    public static void sortAbs(double[] array) {

        double temp;
        int j, n = array.length;

        // turn input array into a heap
        for (j = n/2; j > 0; j--) {
            adjustAbs(array, j, n);
        }

        // remove largest elements and put them at the end
        // of the unsorted region until you are finished
        for (j = n-1; j > 0; j--) {
            temp = array[0];
            array[0] = array[j];
            array[j] = temp;
            adjustAbs(array, 1, j);
        }
    }

    /**
     * Sortuje tablicę indeksów na podstawie wartości w towarzyszącej tablicy wartości double.
     * Indeksy są tak uporządkowane, aby odzwierciedlały rosnący porządek wartości double.
     *
     * @param array Tablica wartości double, których porządek ma być odzwierciedlony przez indeksy.
     * @param indices Tablica liczb całkowitych, która zostanie wypełniona początkowymi indeksami (0 do n-1), a następnie posortowana.
     */
    public static void sort(double[] array, int[] indices)
    {

        // ensures we are starting with valid indices
        for (int i = 0; i < indices.length; i++)
        {
            indices[i] = i;
        }

        int temp;
        int j, n = array.length;

        // turn input array into a heap
        for (j = n/2; j > 0; j--) {
            adjust(array, indices, j, n);
        }

        // remove largest elements and put them at the end
        // of the unsorted region until you are finished
        for (j = n-1; j > 0; j--) {
            temp = indices[0];
            indices[0] = indices[j];
            indices[j] = temp;
            adjust(array, indices, 1, j);
        }
    }

    /**
     * Uruchamia test implementacji algorytmu Heapsort
     * na różnych typach tablic i indeksów.
     *
     * @param args Argumenty wiersza poleceń (nieużywane).
     */
    public static void main(String[] args) {

        MersenneTwisterFast m = new MersenneTwisterFast();

        int testSize = 100;

        // test array of Comparable objects

        pal.util.ComparableDouble[] test = new pal.util.ComparableDouble[testSize];

        for (int i = 0; i < test.length; i++) {
            test[i] = new pal.util.ComparableDouble(m.nextInt(testSize * 10));
        }

        sort(test);
        for (int i = 0; i < test.length; i++) {
            System.out.print(test[i] + " ");
        }
        System.out.println();

        // test index to Vector of Comparable objects

        Vector testv = new Vector();
        int[] indices = new int[testSize];

        for (int i = 0; i < testSize; i++) {
            testv.addElement(new pal.util.ComparableDouble(m.nextInt(testSize * 10)));
        }

        sort(testv, indices);
        for (int i = 0; i < test.length; i++) {
            System.out.print(testv.elementAt(indices[i]) + " ");
        }
        System.out.println();


        // test index to array of doubles

        double[] testd = new double[testSize];
        //int[] indices = new int[testSize];

        for (int i = 0; i < testSize; i++) // KOD ZOSTAŁ POPRAWIONY
        {
            testd[i] = m.nextInt(testSize * 10);
        }

        sort(testd, indices);
        for (int i = 0; i < test.length; i++)
        {
            System.out.print(testd[indices[i]] + " ");
        }
        System.out.println();

    }

// PRIVATE STUFF

    /**
     * Wykonuje operację "sift down" lub "heapify" w celu przywrócenia właściwości kopca
     * na segmencie tablicy indeksów, bazując na wartościach w towarzyszącym obiekcie {@code Vector}
     * zawierającym obiekty porównywalne.
     *
     * Zakłada, że segment od {@code indices[lower+1]} do {@code indices[upper]} jest
     * już w formie kopca, a następnie koryguje segment od {@code indices[lower]} do {@code indices[upper]}.
     *
     * @param array Obiekt {@code Vector} zawierający obiekty, których wartości określają porządek kopca.
     * @param indices Tablica liczb całkowitych (indeksów) manipulowana w celu utrzymania struktury kopca.
     * @param lower Indeks 1-bazowy reprezentujący korzeń (pod)kopca, który ma zostać skorygowany.
     * @param upper Indeks 1-bazowy reprezentujący koniec zakresu kopca.
     */
    private static void adjust(Vector array, int[] indices, int lower, int upper) {

        int j, k;
        int temp;

        j = lower;
        k = lower * 2;

        while (k <= upper) {
            if ((k < upper) && (((pal.util.Comparable)array.elementAt(indices[k-1])).compareTo(array.elementAt(indices[k])) < 0)) {
                k += 1;
            }
            if (((pal.util.Comparable)array.elementAt(indices[j-1])).compareTo(array.elementAt(indices[k-1])) < 0) {
                temp = indices[j-1];
                indices[j-1] = indices[k-1];
                indices[k-1] = temp;
            }
            j = k;
            k *= 2;
        }
    }

    /**
     * Wykonuje operację "sift down" lub "heapify" w celu przywrócenia właściwości kopca
     * na segmencie obiektu {@code Vector} zawierającego obiekty porównywalne.
     *
     * Zakłada, że segment od {@code array[lower+1]} do {@code array[upper]} jest
     * już w formie kopca, a następnie koryguje segment od {@code array[lower]} do {@code array[upper]}.
     *
     * @param array Obiekt {@code Vector} zawierający obiekty porównywalne, który ma zostać skorygowany.
     * @param lower Indeks 1-bazowy reprezentujący korzeń (pod)kopca, który ma zostać skorygowany.
     * @param upper Indeks 1-bazowy reprezentujący koniec zakresu kopca.
     */
    private static void adjust(Vector array, int lower, int upper) {

        int j, k;
        Object temp;

        j = lower;
        k = lower * 2;

        while (k <= upper) {
            if ((k < upper) && (((pal.util.Comparable)array.elementAt(k-1)).compareTo(array.elementAt(k)) < 0)) {
                k += 1;
            }
            if (((pal.util.Comparable)array.elementAt(j-1)).compareTo(array.elementAt(k-1)) < 0) {
                temp = array.elementAt(j-1);
                array.setElementAt(array.elementAt(k-1), j-1);
                array.setElementAt(temp, k-1);
            }
            j = k;
            k *= 2;
        }
    }

    /**
     * Wykonuje operację "sift down" lub "heapify" w celu przywrócenia właściwości kopca
     * na segmencie tablicy obiektów implementujących {@code pal.util.Comparable}.
     *
     * Zakłada, że segment od {@code array[lower+1]} do {@code array[upper]} jest
     * już w formie kopca, a następnie koryguje segment od {@code array[lower]} do {@code array[upper]}.
     *
     * @param array Tablica obiektów porównywalnych do skorygowania.
     * @param lower Indeks 1-bazowy reprezentujący korzeń (pod)kopca, który ma zostać skorygowany.
     * @param upper Indeks 1-bazowy reprezentujący koniec zakresu kopca.
     */
    private static void adjust(pal.util.Comparable[] array, int lower, int upper) {

        int j, k;
        pal.util.Comparable temp;

        j = lower;
        k = lower * 2;

        while (k <= upper) {
            if ((k < upper) && (array[k-1].compareTo(array[k]) < 0)) {
                k += 1;
            }
            if (array[j-1].compareTo(array[k-1]) < 0) {
                temp = array[j-1];
                array[j-1] = array[k-1];
                array[k-1] = temp;
            }
            j = k;
            k *= 2;
        }
    }

    /**
     * Wykonuje operację "sift down" lub "heapify" w celu przywrócenia właściwości kopca
     * na segmencie tablicy obiektów generycznych, używając zewnętrznego obiektu {@code Comparator}
     * do określenia porządku.
     *
     * Zakłada, że segment od {@code array[lower+1]} do {@code array[upper]} jest
     * już w formie kopca, a następnie koryguje segment od {@code array[lower]} do {@code array[upper]}.
     *
     * @param array Tablica obiektów do skorygowania.
     * @param c Obiekt {@code Comparator} używany do porównywania elementów i utrzymywania porządku kopca.
     * @param lower Indeks 1-bazowy reprezentujący korzeń (pod)kopca, który ma zostać skorygowany.
     * @param upper Indeks 1-bazowy reprezentujący koniec zakresu kopca.
     */
    private static void adjust(Object[] array, Comparator c, int lower, int upper) {

        int j, k;
        Object temp;

        j = lower;
        k = lower * 2;

        while (k <= upper) {
            if ((k < upper) && (c.compare(array[k-1], array[k]) < 0)) {
                k += 1;
            }
            if (c.compare(array[j-1], array[k-1]) < 0) {
                temp = array[j-1];
                array[j-1] = array[k-1];
                array[k-1] = temp;
            }
            j = k;
            k *= 2;
        }
    }

    /**
     * Wykonuje operację "sift down" lub "heapify" w celu przywrócenia właściwości kopca
     * na segmencie tablicy wartości {@code double}.
     *
     * Zakłada, że segment od {@code array[lower+1]} do {@code array[upper]} jest
     * już w formie kopca, a następnie koryguje segment od {@code array[lower]} do {@code array[upper]}.
     *
     * @param array Tablica wartości double do skorygowania.
     * @param lower Indeks 1-bazowy reprezentujący korzeń (pod)kopca, który ma zostać skorygowany.
     * @param upper Indeks 1-bazowy reprezentujący koniec zakresu kopca.
     */
    private static void adjust(double[] array, int lower, int upper) {

        int j, k;
        double temp;

        j = lower;
        k = lower * 2;

        while (k <= upper) {
            if ((k < upper) && (array[k-1] < array[k])) {
                k += 1;
            }
            if (array[j-1] < array[k-1]) {
                temp = array[j-1];
                array[j-1] = array[k-1];
                array[k-1] = temp;
            }
            j = k;
            k *= 2;
        }
    }
    /**
     * Wykonuje operację "sift down" lub "heapify" w celu przywrócenia właściwości kopca
     * na segmencie tablicy wartości {@code double}, porządkując elementy na podstawie ich **wartości bezwzględnej**.
     *
     * Zakłada, że segment od {@code array[lower+1]} do {@code array[upper]} jest
     * już w formie kopca, a następnie koryguje segment od {@code array[lower]} do {@code array[upper]}.
     *
     * @param array Tablica wartości double do skorygowania.
     * @param lower Indeks 1-bazowy reprezentujący korzeń (pod)kopca, który ma zostać skorygowany.
     * @param upper Indeks 1-bazowy reprezentujący koniec zakresu kopca.
     */
    private static void adjustAbs(double[] array, int lower, int upper) {

        int j, k;
        double temp;

        j = lower;
        k = lower * 2;

        while (k <= upper) {
            if ((k < upper) && (Math.abs(array[k-1]) < Math.abs(array[k]))) {
                k += 1;
            }
            if (Math.abs(array[j-1]) < Math.abs(array[k-1])) {
                temp = array[j-1];
                array[j-1] = array[k-1];
                array[k-1] = temp;
            }
            j = k;
            k *= 2;
        }
    }

    /**
     * Wykonuje operację "sift down" lub "heapify" w celu przywrócenia właściwości kopca
     * na segmencie tablicy indeksów, bazując na wartościach w towarzyszącej tablicy wartości double.
     *
     * Zakłada, że segment od {@code indices[lower+1]} do {@code indices[upper]} jest
     * już w formie kopca, a następnie koryguje segment od {@code indices[lower]} do {@code indices[upper]}.
     *
     * @param array Tablica wartości double, których wartości określają porządek kopca.
     * @param indices Tablica liczb całkowitych (indeksów) manipulowana w celu utrzymania struktury kopca.
     * @param lower Indeks 1-bazowy reprezentujący korzeń (pod)kopca, który ma zostać skorygowany.
     * @param upper Indeks 1-bazowy reprezentujący koniec zakresu kopca.
     */
    private static void adjust(double[] array, int[] indices, int lower, int upper) {

        int j, k;
        int temp;

        j = lower;
        k = lower * 2;

        while (k <= upper)
        {
            if ((k < upper) && (array[indices[k-1]] < array[indices[k]]))
            {
                k += 1;
            }
            if (array[indices[j-1]] < array[indices[k-1]])
            {
                temp = indices[j-1];
                indices[j-1] = indices[k-1];
                indices[k-1] = temp;
            }
            j = k;
            k *= 2;
        }
    }
}

