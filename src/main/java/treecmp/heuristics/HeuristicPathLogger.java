package treecmp.heuristics;

import pal.tree.Tree;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class HeuristicPathLogger {

    /**
     * Konwertuje obiekt Tree na natywny format Newick jako String.
     */
    public static String treeToNewick(Tree tree) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pal.tree.TreeUtils.printNH(tree, pw);
        pw.flush();
        return sw.toString().trim();
    }

    /**
     * Inicjuje nowy plik logu z nagłówkiem i zapisuje drzewo startowe.
     */
    public static void startNewLog(String filepath, String testName, Tree startTree, double initialDistance) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filepath, false))) {
            writer.write("========================================================\n");
            writer.write("ŚCIEŻKA HEURYSTYKI: " + testName + "\n");
            writer.write("========================================================\n");
            writer.write("KROK 0 [START] - Dystans: " + String.format("%.4f", initialDistance) + "\n");
            writer.write(treeToNewick(startTree) + "\n\n");
        } catch (IOException e) {
            System.err.println("Błąd inicjalizacji logu: " + e.getMessage());
        }
    }

    /**
     * Dopisuje kolejny udany krok (znalezionego lepszego sąsiada) do pliku.
     */
    public static void logStep(String filepath, int stepNumber, String moveType, Tree currentTree, double currentDistance) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filepath, true))) {
            writer.write("KROK " + stepNumber + " [" + moveType + "] - Dystans: " + String.format("%.4f", currentDistance) + "\n");
            writer.write(treeToNewick(currentTree) + "\n\n");
        } catch (IOException e) {
            System.err.println("Błąd zapisu kroku: " + e.getMessage());
        }
    }

    /**
     * Zamyka log podsumowaniem.
     */
    public static void finishLog(String filepath, int totalSteps, double finalDistance) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filepath, true))) {
            writer.write("========================================================\n");
            writer.write("ZAKOŃCZONO. Wykonano kroków: " + totalSteps + " | Końcowy dystans: " + String.format("%.4f", finalDistance) + "\n");
            writer.write("========================================================\n");
        } catch (IOException e) {
            System.err.println("Błąd finalizacji logu: " + e.getMessage());
        }
    }
}