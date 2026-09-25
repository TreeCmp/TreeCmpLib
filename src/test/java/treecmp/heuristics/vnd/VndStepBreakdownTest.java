package treecmp.heuristics.vnd;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristic;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristic;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic;
import treecmp.metrics.topological.acc.RFIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Weryfikacja rzeczywistej liczby wykonanych kroków per operator w VND (RF Inc)")
public class VndStepBreakdownTest {

    @Test
    @Disabled
    void testVndActualMoveCountBreakdown() {
        int n = 30;
        int pairsToTest = 10;

        Map<String, Integer> moveCounts = new HashMap<>();
        moveCounts.put("NNI", 0);
        moveCounts.put("2sECR", 0);
        moveCounts.put("3sECR", 0);
        moveCounts.put("uSPR", 0);
        moveCounts.put("uTBR", 0);

        VndStepListener stepCounter = new VndStepListener() {
            @Override public void onStart(String name, Tree t, double d) {}
            @Override public void onFinish(double d) {}

            @Override
            public void onStep(String neighborhoodName, List<Tree> trajectory, double newDist, Tree targetTree) {
                String lower = neighborhoodName.toLowerCase();
                String key = "Inne";
                if (lower.contains("nni")) key = "NNI";
                else if (lower.contains("2secr") || lower.contains("ecr2")) key = "2sECR";
                else if (lower.contains("3secr") || lower.contains("ecr3")) key = "3sECR";
                else if (lower.contains("spr")) key = "uSPR";
                else if (lower.contains("tbr")) key = "uTBR";

                moveCounts.put(key, moveCounts.getOrDefault(key, 0) + trajectory.size());
            }
        };

        for (int p = 0; p < pairsToTest; p++) {
            Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(n, 1000L + p);
            Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(n, 9000L + p);
            if (t1 instanceof SimpleTree) ((SimpleTree) t1).createNodeList();
            if (t2 instanceof SimpleTree) ((SimpleTree) t2).createNodeList();
            TreeUtils.computeParentPointers(t1.getRoot());
            TreeUtils.computeParentPointers(t2.getRoot());

            RFIncrementalMetric metric = new RFIncrementalMetric();

            List<IncrementalHeuristicBaseMetric> neighborhoods = List.of(
                    new NniIncrementalHeuristic(metric, "RF"),
                    new Ecr2IncrementalHeuristic(metric, "RF"),
                    new Ecr3IncrementalHeuristic(metric, "RF"),
                    new UsprIncrementalHeuristicMetric(metric, "RF"),
                    new UtbrIncrementalHeuristic(metric, "RF")
            );

            NniVndIncrementalHeuristic vnd = new NniVndIncrementalHeuristic(
                    neighborhoods,
                    null,
                    "RF",
                    stepCounter
            );

            vnd.getDistance(t1, t2);
        }

        int totalMoves = moveCounts.values().stream().mapToInt(Integer::intValue).sum();

        System.out.println("==========================================================");
        System.out.println("RZECZYWISTA STRUKTURA WYKONANYCH RUCHÓW (MOVE BREAKDOWN):");
        System.out.println("==========================================================");
        moveCounts.forEach((k, v) -> {
            double pct = totalMoves > 0 ? (v * 100.0 / totalMoves) : 0;
            System.out.printf("%-8s : %4d ruchów (%5.1f%%)%n", k, v, pct);
        });
        System.out.printf("ŁĄCZNIE  : %4d ruchów%n", totalMoves);
        System.out.println("==========================================================");

        assertTrue(moveCounts.get("3sECR") > 0, "3sECR musi wykonywać ruchy w VND dla metryki RF!");
    }
}