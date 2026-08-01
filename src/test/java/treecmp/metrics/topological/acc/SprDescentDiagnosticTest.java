package treecmp.debug;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.spr.UsprHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.metrics.topological.MatchingTripletMetric;
import treecmp.metrics.topological.acc.M3IncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.List;

/**
 * Narzędzie diagnostyczne śledzące krok po kroku trajektorię schodzenia SPR
 * dla metryki klasycznej (Classic) oraz przyrostowej (Incremental).
 */
public class SprDescentDiagnosticTest {

    public static void main(String[] args) {
        int treeSize = 10; // Zacznijmy od małego drzewa N=10, aby łatwo przeanalizować output
        long seedT1 = 12345L;
        long seedT2 = 67890L;

        Tree t1Classic = TestTreeFactory.randomUnrootedBinaryTree(treeSize, seedT1);
        Tree t1Incr    = TestTreeFactory.randomUnrootedBinaryTree(treeSize, seedT1);
        Tree t2        = TestTreeFactory.randomUnrootedBinaryTree(treeSize, seedT2);

        assignNumbers(t1Classic);
        assignNumbers(t1Incr);
        assignNumbers(t2);

        UsprHeuristicMetric classicMetric = new UsprHeuristicMetric(new MatchingTripletMetric(), "M3");
        UsprIncrementalHeuristicMetric incrMetric = new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), "M3");

        System.out.println("=".repeat(85));
        System.out.printf(" DIAGNOSTYKA TRAJEKTORII SCHODZENIA uSPR (M3) | ROZMIAR DRZEWA: %d%n", treeSize);
        System.out.println("=".repeat(85));

        double initialDist = classicMetric.evaluateInitialDistance(t1Classic, t2);
        System.out.printf("Początkowy dystans M3 między T1 a T2: %.1f%n%n", initialDist);

        // Uruchamiamy pełny przebieg, aby nagrać trajektorię wewnątrz obiektów
        double classicResult = classicMetric.getDistance(t1Classic, t2);
        double incrResult    = incrMetric.getDistance(t1Incr, t2);

        List<Tree> classicTrajectory = classicMetric.getLastOptimumTrajectory(t1Classic);
        List<Tree> incrTrajectory    = incrMetric.getLastOptimumTrajectory(t1Incr);

        System.out.println("WYNIKI SUMARYCZNE:");
        System.out.printf(" -> Classic Full-Run     : %s (Kroków: %d)%n", formatResult(classicResult), classicTrajectory.size() - 1);
        System.out.printf(" -> Incremental Full-Run : %s (Kroków: %d)%n%n", formatResult(incrResult), incrTrajectory.size() - 1);

        System.out.println("-".repeat(85));
        System.out.printf("%-6s | %-35s | %-35s%n", "Krok", "CLASSIC (Dystans po kroku)", "INCREMENTAL (Dystans po kroku)");
        System.out.println("-".repeat(85));

        int maxSteps = Math.max(classicTrajectory.size(), incrTrajectory.size());
        for (int i = 0; i < maxSteps; i++) {
            String classicStr = (i < classicTrajectory.size())
                    ? String.format("Dystans: %.1f", classicMetric.evaluateInitialDistance(classicTrajectory.get(i), t2))
                    : "[Koniec schodzenia]";

            String incrStr = (i < incrTrajectory.size())
                    ? String.format("Dystans: %.1f", classicMetric.evaluateInitialDistance(incrTrajectory.get(i), t2))
                    : "[Koniec schodzenia / Utknięcie]";

            boolean divergence = !classicStr.equals(incrStr);
            String marker = divergence ? " <-- ROZBIEŻNOŚĆ!" : "";

            System.out.printf("%-6d | %-35s | %-35s %s%n", i, classicStr, incrStr, marker);
            if (divergence) break; // Zatrzymujemy się na pierwszym punkcie rozwidlenia
        }
        System.out.println("=".repeat(85));
    }

    private static void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    private static String formatResult(double res) {
        return (res == Double.POSITIVE_INFINITY) ? "INFINITY (Minimum lokalne > 0)" : String.format("%.2f (Sukces)", res);
    }
}