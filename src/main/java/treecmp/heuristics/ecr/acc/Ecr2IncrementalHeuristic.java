package treecmp.heuristics.ecr.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.Ecr2Move;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR;
import treecmp.metrics.IncrementalMetric;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class Ecr2IncrementalHeuristic extends IncrementalHeuristicBaseMetric {

    private final SubtreeEcr2Utils ecr2Utils;
    private final String metricShortName;
    protected IncrementalMetric primaryMetric; // Opcjonalny filtr (np. RF)

    // 1. Podstawowy konstruktor (bez filtra)
    public Ecr2IncrementalHeuristic(IncrementalMetric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    // 2. Rozszerzony konstruktor (z filtrem RF)
    public Ecr2IncrementalHeuristic(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(metric.isRooted(), metric);
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
        this.ecr2Utils = new SubtreeEcr2Utils(!metric.isRooted());
    }


    @Override
    protected void searchNeighborhood(Tree currentTree) {
        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;

        this.tiedMoves.clear();
        this.bestDist = Double.POSITIVE_INFINITY;

        int intNum = currentTree.getInternalNodeCount();

        for (int i = 0; i < intNum; i++) {
            Node node = currentTree.getInternalNode(i);

            if (node != currentTree.getRoot()) {
                Node c = node.getParent();
                if (c != null && !c.isLeaf()) {
                    Node p = c.getParent();
                    if (p != null && !p.isLeaf()) {
                        Node[] bounds = new Node[]{ getOtherChild(p, c), getOtherChild(c, node), node.getChild(0), node.getChild(1) };
                        evaluateEcr2Cluster(p, c, node, bounds, false, activeMetric);
                    }
                }
            }

            List<Node> intChildren = new ArrayList<>();
            for (int j = 0; j < node.getChildCount(); j++) if (!node.getChild(j).isLeaf()) intChildren.add(node.getChild(j));
            if (intChildren.size() >= 2) {
                for (int a = 0; a < intChildren.size(); a++) {
                    for (int b = a + 1; b < intChildren.size(); b++) {
                        Node m1 = intChildren.get(a); Node m2 = intChildren.get(b);
                        Node[] bounds = new Node[]{m1.getChild(0), m1.getChild(1), m2.getChild(0), m2.getChild(1)};
                        evaluateEcr2Cluster(node, m1, m2, bounds, true, activeMetric);
                    }
                }
            }
        }
    }

    private void evaluateEcr2Cluster(Node top, Node m1, Node m2, Node[] bounds, boolean isFork, IncrementalMetric activeMetric) {
        for (TopologyTemplate2sECR template : SubtreeEcr2Utils.getTemplates()) {
            if (template.isFork == isFork && Arrays.equals(template.indices, new int[]{0, 1, 2, 3})) continue;
            double dist = activeMetric.evaluate2sEcrMove(top, m1, m2, bounds, template);
            checkImprovementWithTies(dist, new Ecr2Move(top, m1, m2, bounds, template));
        }
    }

    private Node getOtherChild(Node parent, Node exclude) {
        for (int i = 0; i < parent.getChildCount(); i++) if (parent.getChild(i) != exclude) return parent.getChild(i);
        return null;
    }

    @Override protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        return ecr2Utils.applyPhysicalMove(tree, (Ecr2Move) move);
    }

    @Override protected double commitMoveToMetric(TreeMove move) {
        Ecr2Move m = (Ecr2Move) move;
        return this.incMetric.commit2sEcrMove(m.top, m.m1, m.m2, m.boundarySubtrees, m.template);
    }

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        Tree currentTree = tree1;
        this.improved = true;
        int totalSteps = 0;
        int maxSteps = 1000; // Zabezpieczenie przed pętlą

        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;

        // Inicjalizacja stanów metryk na startowym drzewie
        activeMetric.initCalculationState(currentTree, tree2);
        if (primaryMetric != null) {
            this.incMetric.initCalculationState(currentTree, tree2);
        }

        double currentDist = activeMetric.getCurrentDistance();

        while (this.improved && currentDist > 0 && totalSteps < maxSteps) {
            this.improved = false;

            searchNeighborhood(currentTree);

            if (!this.tiedMoves.isEmpty() && this.bestDist < currentDist) {
                TreeMove bestMove = null;

                // Brak remisów lub brak filtra -> bierzemy pierwszy lepszy ruch
                if (primaryMetric == null || tiedMoves.size() == 1) {
                    bestMove = tiedMoves.get(0);
                }
                // TIE-BREAKER: Sprawdzamy remisy ciężką metryką wirtualnie
                else {
                    double bestHeavyDist = Double.POSITIVE_INFINITY;
                    for (TreeMove tm : tiedMoves) {
                        Ecr2Move em = (Ecr2Move) tm;
                        // Oceniamy wirtualnie bez zapisu
                        double heavyDist = this.incMetric.evaluate2sEcrMove(em.top, em.m1, em.m2, em.boundarySubtrees, em.template);
                        if (heavyDist < bestHeavyDist) {
                            bestHeavyDist = heavyDist;
                            bestMove = em;
                        }
                    }
                }

                if (bestMove != null) {
                    Ecr2Move finalMove = (Ecr2Move) bestMove;

                    // Fizyczny commit na aktywnym filtrze
                    currentDist = activeMetric.commit2sEcrMove(finalMove.top, finalMove.m1, finalMove.m2, finalMove.boundarySubtrees, finalMove.template);
                    activeMetric.commit();

                    // Synchronizacja stanu w metryce ciężkiej (jeśli używamy filtra)
                    if (primaryMetric != null) {
                        this.incMetric.commit2sEcrMove(finalMove.top, finalMove.m1, finalMove.m2, finalMove.boundarySubtrees, finalMove.template);
                        this.incMetric.commit();
                    }

                    currentTree = applyPhysicalMove(currentTree, finalMove);
                    totalSteps++;
                    this.improved = true;
                }
            }
        }
        return (currentDist == 0) ? (double) totalSteps : Double.POSITIVE_INFINITY;
    }

    @Override public String getName() { return "2sECR_IncrementalHeuristic_" + metricShortName; }
}