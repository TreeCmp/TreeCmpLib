package treecmp.heuristics.nni.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.nni.NniUtils;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.List;

public class NniIncrementalHeuristic extends IncrementalHeuristicBaseMetric {

    private final NniUtils nniUtils;
    private final String metricShortName;
    private final IncrementalMetric primaryMetric; // Opcjonalny filtr (np. RF)

    protected List<TreeMove> tiedMoves = new ArrayList<>();
    protected double currentPrimaryBestDist;

    // 1. Konstruktor podstawowy (bez filtra)
    public NniIncrementalHeuristic(IncrementalMetric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    // 2. Konstruktor rozszerzony (z filtrem)
    public NniIncrementalHeuristic(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(metric.isRooted(), metric);
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
        this.nniUtils = new NniUtils(!metric.isRooted());
    }

    private void checkImprovementWithTies(double currentDist, TreeMove move) {
        if (currentDist < this.currentPrimaryBestDist) {
            this.currentPrimaryBestDist = currentDist;
            this.tiedMoves.clear();
            this.tiedMoves.add(move);
        } else if (currentDist == this.currentPrimaryBestDist && currentDist != Double.POSITIVE_INFINITY) {
            this.tiedMoves.add(move);
        }
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;

        this.tiedMoves.clear();
        this.currentPrimaryBestDist = Double.POSITIVE_INFINITY;

        exploreNniRecursive(currentTree.getRoot(), activeMetric);
    }

    private void exploreNniRecursive(Node parent, IncrementalMetric activeMetric) {
        if (parent.isLeaf()) return;

        for (int i = 0; i < parent.getChildCount(); i++) {
            Node child = parent.getChild(i);
            if (child.isLeaf()) continue;

            Node sibling = findSibling(parent, child);
            if (sibling != null && child.getChildCount() >= 2) {
                Node g1 = child.getChild(0);
                Node g2 = child.getChild(1);

                // --- SĄSIAD 1 ---
                NniMove move1 = new NniMove(g1, sibling);
                double dist1 = activeMetric.applyNni(move1);
                checkImprovementWithTies(dist1, move1);
                activeMetric.undoNni(move1);

                // --- SĄSIAD 2 ---
                NniMove move2 = new NniMove(g2, sibling);
                double dist2 = activeMetric.applyNni(move2);
                checkImprovementWithTies(dist2, move2);
                activeMetric.undoNni(move2);
            }

            exploreNniRecursive(child, activeMetric);
        }
    }

    @Override
    protected double commitMoveToMetric(TreeMove move) {
        // Metoda wymagana przez klasę bazową.
        // W NNI commity są obsługiwane bezpośrednio w zoptymalizowanej pętli getDistance.
        if (move instanceof NniMove) {
            double dist = this.incMetric.applyNni((NniMove) move);
            this.incMetric.commit();
            return dist;
        }
        return this.incMetric.getCurrentDistance();
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof NniMove) {
            return nniUtils.applyPhysicalMove(tree, (NniMove) move);
        }
        return tree;
    }

    private Node findSibling(Node parent, Node child) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            Node n = parent.getChild(i);
            if (n != child) return n;
        }
        return null;
    }

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        Tree currentTree = tree1;
        this.improved = true;
        int totalSteps = 0;
        int maxSteps = 1000;

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

            if (!this.tiedMoves.isEmpty() && this.currentPrimaryBestDist < currentDist) {
                TreeMove bestMove = null;

                // Brak remisów lub brak filtra -> bierzemy pierwszy lepszy ruch
                if (primaryMetric == null || tiedMoves.size() == 1) {
                    bestMove = tiedMoves.get(0);
                }
                // TIE-BREAKER: Sprawdzamy remisy ciężką metryką wirtualnie (O(1))
                else {
                    double bestHeavyDist = Double.POSITIVE_INFINITY;
                    for (TreeMove tm : tiedMoves) {
                        NniMove nniM = (NniMove) tm;

                        double heavyDist = this.incMetric.applyNni(nniM);
                        if (heavyDist < bestHeavyDist) {
                            bestHeavyDist = heavyDist;
                            bestMove = nniM;
                        }
                        this.incMetric.undoNni(nniM); // Czyste wycofanie bez dotykania wskaźników fizycznego drzewa!
                    }
                }

                if (bestMove != null) {
                    NniMove finalMove = (NniMove) bestMove;

                    // Aktualizacja matematyki dla filtra
                    currentDist = activeMetric.applyNni(finalMove);
                    activeMetric.commit();

                    // Jeśli mamy filtr, musimy też utrzymać zsynchronizowany stan w ciężkiej metryce
                    if (primaryMetric != null) {
                        this.incMetric.applyNni(finalMove);
                        this.incMetric.commit();
                    }

                    // Dopiero teraz faktycznie modyfikujemy drzewo pod kolejną iterację Walkera
                    currentTree = applyPhysicalMove(currentTree, finalMove);
                    totalSteps++;
                    this.improved = true;
                }
            }
        }
        return (currentDist == 0) ? (double) totalSteps : Double.POSITIVE_INFINITY;
    }

    @Override
    public double performLocalDescent(Tree startTree, Tree targetTree) {
        Tree currentTree = new pal.tree.SimpleTree(startTree);
        if (currentTree instanceof pal.tree.SimpleTree) {
            ((pal.tree.SimpleTree) currentTree).createNodeList();
        }

        this.improved = true;
        this.accumulatedNniCost = 0.0;
        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;

        activeMetric.initCalculationState(currentTree, targetTree);
        double currentDist = activeMetric.getCurrentDistance();

        if (currentDist == 0) {
            this.lastOptimumTree = currentTree;
            return 0.0;
        }

        int maxSteps = 1000;
        int steps = 0;

        while (this.improved && currentDist > 0 && steps < maxSteps) {
            this.improved = false;
            searchNeighborhood(currentTree);

            if (!this.tiedMoves.isEmpty() && this.currentPrimaryBestDist < currentDist) {
                treecmp.heuristics.moves.TreeMove bestMove = this.tiedMoves.get(0);

                if (bestMove != null) {
                    this.accumulatedNniCost += bestMove.getNniEquivalentCost();
                    this.lastOptimumMove = bestMove;

                    currentTree = applyPhysicalMove(currentTree, bestMove);
                    pal.tree.TreeUtils.computeParentPointers(currentTree.getRoot());
                    activeMetric.initCalculationState(currentTree, targetTree);
                    double newDist = activeMetric.getCurrentDistance();

                    if (newDist >= currentDist) {
                        break;
                    }

                    currentDist = newDist;
                    this.improved = true;
                    steps++;
                }
            }
        }

        this.lastOptimumTree = currentTree;
        return currentDist;
    }

    @Override
    public String getName() {
        return "NNI_IncrementalHeuristic_" + metricShortName;
    }
}