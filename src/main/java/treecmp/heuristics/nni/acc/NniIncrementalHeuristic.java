package treecmp.heuristics.nni.acc;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.nni.NniUtils;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NniIncrementalHeuristic extends IncrementalHeuristicBaseMetric {

    private final NniUtils nniUtils;
    private final String metricShortName;
    private final IncrementalMetric primaryMetric; // Opcjonalny filtr (np. RF)

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

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;

        this.tiedMoves.clear();
        this.bestDist = Double.POSITIVE_INFINITY;
        this.improved = false;

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
        double remainingDist = performLocalDescent(tree1, tree2);
        return (remainingDist == 0.0) ? (double) this.accumulatedSteps : Double.POSITIVE_INFINITY;
    }

    @Override
    public double performLocalDescent(Tree startTree, Tree targetTree) {
        Tree currentTree = new SimpleTree(startTree);
        if (currentTree instanceof SimpleTree) {
            ((SimpleTree) currentTree).createNodeList();
        }
        TreeUtils.computeParentPointers(currentTree.getRoot());

        this.improved = false;
        this.accumulatedSteps = 0;
        this.accumulatedNniCost = 0.0;
        this.fullOptimumTrajectory.clear();
        this.lastOptimumMove = null;

        int totalSteps = 0;
        int maxSteps = 1000;

        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;

        // Inicjalizacja stanów metryk na startowym drzewie
        activeMetric.initCalculationState(currentTree, targetTree);
        if (primaryMetric != null) {
            this.incMetric.initCalculationState(currentTree, targetTree);
        }

        double currentDist = activeMetric.getCurrentDistance();

        if (currentDist == 0.0) {
            this.lastOptimumTree = currentTree;
            return 0.0;
        }

        while (currentDist > 0 && totalSteps < maxSteps) {
            this.improved = false;
            searchNeighborhood(currentTree);

            // BEZPIECZNIK 1: Brak poprawy lub brak ruchów -> minimum lokalne
            if (this.tiedMoves.isEmpty() || this.bestDist >= currentDist - 1e-9) {
                break;
            }

            TreeMove bestMove = null;

            // Brak remisów lub brak filtra -> bierzemy pierwszy lepszy ruch
            if (primaryMetric == null || tiedMoves.size() == 1) {
                bestMove = tiedMoves.get(0);
            }
            // TIE-BREAKER: Sprawdzamy remisy ciężką metryką wirtualnie
            else {
                double bestHeavyDist = Double.POSITIVE_INFINITY;
                for (TreeMove tm : tiedMoves) {
                    NniMove nniM = (NniMove) tm;

                    double heavyDist = this.incMetric.applyNni(nniM);
                    if (heavyDist < bestHeavyDist) {
                        bestHeavyDist = heavyDist;
                        bestMove = nniM;
                    }
                    this.incMetric.undoNni(nniM);
                }
            }

            // BEZPIECZNIK 2: Jeśli nie wybrano ruchu, natychmiast przerywamy
            if (bestMove == null) {
                break;
            }

            NniMove finalMove = (NniMove) bestMove;

            // Aktualizacja matematyki dla filtra
            double newDist = activeMetric.applyNni(finalMove);
            activeMetric.commit();

            // BEZPIECZNIK 3: Dystans musi ściśle maleć
            if (newDist >= currentDist - 1e-9) {
                break;
            }

            // Utrzymanie synchronizacji w ciężkiej metryce przy aktywnym filtrze
            if (primaryMetric != null) {
                this.incMetric.applyNni(finalMove);
                this.incMetric.commit();
            }

            // Fizyczna modyfikacja drzewa pod kolejną iterację
            currentTree = applyPhysicalMove(currentTree, finalMove);
            if (currentTree instanceof SimpleTree) {
                ((SimpleTree) currentTree).createNodeList();
            }
            TreeUtils.computeParentPointers(currentTree.getRoot());

            // Rejestracja kroku w trajektorii certyfikatu
            SimpleTree stepCopy = new SimpleTree(currentTree);
            stepCopy.createNodeList();
            TreeUtils.computeParentPointers(stepCopy.getRoot());
            this.fullOptimumTrajectory.add(stepCopy);

            totalSteps++;
            this.accumulatedSteps++;
            this.accumulatedNniCost += finalMove.getNniEquivalentCost();
            this.lastOptimumMove = finalMove;

            currentDist = newDist;
            this.improved = true;
        }

        this.lastOptimumTree = currentTree;
        return currentDist;
    }

    @Override
    public List<Tree> getLastOptimumTrajectory(Tree startTree) {
        if (this.fullOptimumTrajectory != null && !this.fullOptimumTrajectory.isEmpty()) {
            return new ArrayList<>(this.fullOptimumTrajectory);
        }
        if (this.lastOptimumTree != null) {
            return Collections.singletonList(this.lastOptimumTree);
        }
        return Collections.emptyList();
    }

    @Override
    public Tree getLastOptimumTree() {
        return this.lastOptimumTree;
    }

    @Override
    public String getName() {
        return "NNI_IncrementalHeuristic_" + metricShortName;
    }
}