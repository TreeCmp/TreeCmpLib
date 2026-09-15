package treecmp.heuristics.tbr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Weryfikacja poprawności 1-krokowego TBR/uTBR vs klasyczne TbrUtils/UTbrUtils")
public class TbrIncrementalCorrectnessTest {

    private static final double EPSILON = 1e-9;

    private static Stream<Arguments> provideTestParameters() {
        String[] metrics = {"RF", "RFC", "MS", "MC", "MP", "M3"};
        // Dla TBR O(N^3) małe rozmiary N pozwalają na błyskawiczne testy jednostkowe w CI/CD
        int[] treeSizes = {6, 8, 10};
        long[][] seedPairs = {
                {12345L, 67890L},
                {42L, 999L}
        };

        List<Arguments> arguments = new ArrayList<>();
        for (String metric : metrics) {
            for (int size : treeSizes) {
                for (long[] seeds : seedPairs) {
                    arguments.add(Arguments.of(metric, size, seeds[0], seeds[1]));
                }
            }
        }
        return arguments.stream();
    }

    private static void assignNumbers(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
    }

    @ParameterizedTest(name = "Metryka: {0} | n={1} | seeds=[{2}, {3}]")
    @MethodSource("provideTestParameters")
    @DisplayName("Porównanie minimalnej odległości w otoczeniu TBR/uTBR (Incr vs Classic)")
    void testSingleStepBestNeighborMatch(String metricName, int treeSize, long seed1, long seed2) throws Exception {
        boolean isRooted;
        Metric classicMetric;
        IncrementalHeuristicBaseMetric incrementalMetric;

        switch (metricName) {
            case "RF":
                isRooted = false;
                classicMetric = new RFMetric();
                incrementalMetric = new UtbrIncrementalHeuristic(new RFIncrementalMetric(), "RF");
                break;
            case "RFC":
                isRooted = true;
                classicMetric = new RFClusterMetric();
                incrementalMetric = new TbrIncrementalHeuristic(new RFClusterIncrementalMetric(), "RFC");
                break;
            case "MS":
                isRooted = false;
                classicMetric = new MatchingSplitMetric();
                incrementalMetric = new UtbrIncrementalHeuristic(new MSIncrementalMetric(), "MS");
                break;
            case "MC":
                isRooted = true;
                classicMetric = new MatchingClusterMetric();
                incrementalMetric = new TbrIncrementalHeuristic(new MCIncrementalMetric(), "MC");
                break;
            case "MP":
                isRooted = true;
                classicMetric = new MatchingPairMetric();
                incrementalMetric = new TbrIncrementalHeuristic(new MPIncrementalMetric(), "MP");
                break;
            case "M3":
                isRooted = false;
                classicMetric = new MatchingTripletMetric();
                incrementalMetric = new UtbrIncrementalHeuristic(new M3IncrementalMetric(), "M3");
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metricName);
        }

        Tree t1, t2, t1ForIncr;
        if (isRooted) {
            t1 = TestTreeFactory.randomRootedBinaryTree(treeSize, seed1);
            t2 = TestTreeFactory.randomRootedBinaryTree(treeSize, seed2);
            t1ForIncr = TestTreeFactory.randomRootedBinaryTree(treeSize, seed1);
        } else {
            t1 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, seed1);
            t2 = TestTreeFactory.randomUnrootedBinaryTree(treeSize, seed2);
            t1ForIncr = TestTreeFactory.randomUnrootedBinaryTree(treeSize, seed1);
        }

        assignNumbers(t1);
        assignNumbers(t2);
        assignNumbers(t1ForIncr);

        // 1. Obliczenie najlepszego sąsiada inkrementalnie (Steepest Descent 1 krok)
        double distIncr = incrementalMetric.evaluateSingleStep(t1ForIncr, t2);

        // 2. Przeszukanie pełnego otoczenia klasycznie przez orakularne TbrUtils / UTbrUtils
        final double[] bestClassicDist = {Double.POSITIVE_INFINITY};
        final int[] neighborCount = {0};
        final String[] bestClassicMove = {"None"};

        if (isRooted) {
            TbrUtils tbrUtils = new TbrUtils();
            List<Node> allNodes = getAllNodes(t1);
            for (Node prune : allNodes) {
                if (prune.isRoot() || prune.getParent() == null) continue;
                List<Node> rerootNodes = new ArrayList<>();
                collectSubtree(prune, rerootNodes);
                List<Node> targetNodes = new ArrayList<>();
                collectOutside(t1.getRoot(), prune, targetNodes);

                for (Node reroot : rerootNodes) {
                    for (Node target : targetNodes) {
                        if (reroot == prune && target == prune.getParent()) continue;
                        if (tbrUtils.isValidTbrMove(prune, reroot, target)) {
                            Tree neighbor = tbrUtils.createTbrTree(t1, prune, reroot, target);
                            if (neighbor != null) {
                                assignNumbers(neighbor);
                                neighborCount[0]++;
                                double d = classicMetric.getDistance(neighbor, t2);
                                if (d < bestClassicDist[0]) {
                                    bestClassicDist[0] = d;
                                    bestClassicMove[0] = String.format("prune=%s, reroot=%s, target=%s",
                                            formatNode(prune), formatNode(reroot), formatNode(target));
                                }
                            }
                        }
                    }
                }
            }
        } else {
            UTbrUtils utbrUtils = new UTbrUtils();
            List<Node> allNodes = getAllNodes(t1);
            for (Node prune : allNodes) {
                if (prune.isRoot() || prune.getParent() == null) continue;
                List<Node> rerootNodes = new ArrayList<>();
                collectSubtree(prune, rerootNodes);
                List<Node> targetNodes = new ArrayList<>();
                collectOutside(t1.getRoot(), prune, targetNodes);

                for (Node reroot : rerootNodes) {
                    for (Node target : targetNodes) {
                        if (reroot == prune && target == prune.getParent()) continue;
                        if (utbrUtils.isValidUtbrMove(prune, reroot, target)) {
                            Tree neighbor = utbrUtils.createUtbrTree(t1, prune, reroot, target);
                            if (neighbor != null) {
                                assignNumbers(neighbor);
                                neighborCount[0]++;
                                double d = classicMetric.getDistance(neighbor, t2);
                                if (d < bestClassicDist[0]) {
                                    bestClassicDist[0] = d;
                                    bestClassicMove[0] = String.format("prune=%s, reroot=%s, target=%s",
                                            formatNode(prune), formatNode(reroot), formatNode(target));
                                }
                            }
                        }
                    }
                }
            }
        }

        assertTrue(neighborCount[0] > 0, "Otoczenie TBR nie powinno być puste");

        // =========================================================================
        // DIAGNOSTYKA ANOMALII TBR (DUMP DANYCH PRZY NIEZGODNOŚCI)
        // =========================================================================
        if (Math.abs(bestClassicDist[0] - distIncr) > EPSILON) {
            System.err.println("\n================================================================================");
            System.err.println("                      !!! TBR MISMATCH DIAGNOSTIC DUMP !!!");
            System.err.println("================================================================================");
            System.err.printf("Metric: %s | n=%d | seed1=%dL | seed2=%dL%n", metricName, treeSize, seed1, seed2);
            System.err.println("Tree T1 Newick: " + t1);
            System.err.println("Tree T2 Newick: " + t2);
            System.err.printf("Classic Best Dist: %.6f (Move: %s)%n", bestClassicDist[0], bestClassicMove[0]);
            System.err.printf("Incr    Best Dist: %.6f%n", distIncr);
            System.err.println("--------------------------------------------------------------------------------");

            if (isRooted && "MC".equals(metricName)) {
                System.err.println("Skanowanie walkerem w poszukiwaniu ruchu dającego zaniżony dystans (10.0)...");
                treecmp.metrics.topological.acc.MCIncrementalMetric diagMetric = new treecmp.metrics.topological.acc.MCIncrementalMetric();
                diagMetric.initCalculationState(t1ForIncr, t2);
                treecmp.heuristics.tbr.TbrUtils diagTbrUtils = new treecmp.heuristics.tbr.TbrUtils();

                final boolean[] printed = {false};

                new treecmp.heuristics.tbr.acc.IncrementalTbrWalker().walk(t1ForIncr, diagMetric, (d, p, r, tgt) -> {
                    if (printed[0]) {
                        return; // Pomijamy kolejne wywołania po pierwszym wydrukowaniu
                    }

                    if (Math.abs(d - distIncr) < EPSILON) {
                        System.err.printf("[ZANIŻONY WYNIK] IncrDist=%.4f na ruchu: prune=%s, reroot=%s, target=%s%n",
                                d, formatNode(p), formatNode(r), formatNode(tgt));

                        Tree physTree = diagTbrUtils.createTbrTree(t1ForIncr, p, r, tgt);
                        if (physTree != null) {
                            assignNumbers(physTree);
                            double realDist = 0;
                            try {
                                realDist = classicMetric.getDistance(physTree, t2);
                            } catch (TreeCmpException e) {
                                throw new RuntimeException(e);
                            }
                            System.err.printf("   -> RZECZYWISTY dystans tego drzewa do T2 (Classic): %.4f%n", realDist);
                            System.err.println("   -> Wygenerowane drzewo: " + physTree);

                            // === AUTOMATYCZNY ZRZUT DANYCH ===
                            diagMetric.printDiagnosticDump(physTree);
                            printed[0] = true; // Zaznaczamy, że zrzut został zrobiony
                        } else {
                            System.err.println("   -> createTbrTree zwróciło NULL!");
                        }
                    }
                });
            }
            System.err.println("================================================================================\n");
        }

        // 3. Weryfikacja tożsamości znalezionego optimum lokalnego
        assertEquals(bestClassicDist[0], distIncr, EPSILON,
                String.format("Niezgodność w TBR (%s) dla n=%d! Classic=%.6f vs Incr=%.6f",
                        metricName, treeSize, bestClassicDist[0], distIncr));
    }

    private String formatNode(Node n) {
        if (n == null) return "null";
        return n.isLeaf() ? n.getIdentifier().getName() : ("int#" + n.getNumber());
    }

    private static List<Node> getAllNodes(Tree tree) {
        List<Node> list = new ArrayList<>();
        collectSubtree(tree.getRoot(), list);
        return list;
    }

    private static void collectSubtree(Node n, List<Node> list) {
        list.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collectSubtree(n.getChild(i), list);
    }

    private static void collectOutside(Node curr, Node exclude, List<Node> list) {
        if (curr == exclude) return;
        list.add(curr);
        for (int i = 0; i < curr.getChildCount(); i++) collectOutside(curr.getChild(i), exclude, list);
    }
}