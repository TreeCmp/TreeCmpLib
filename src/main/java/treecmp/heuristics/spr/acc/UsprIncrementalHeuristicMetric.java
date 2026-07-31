package treecmp.heuristics.spr.acc;

import pal.tree.Tree;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.moves.SprMove;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.M3IncrementalMetric;
import treecmp.metrics.topological.acc.MSIncrementalMetric;

import java.util.ArrayList;
import java.util.List;

public class UsprIncrementalHeuristicMetric extends IncrementalHeuristicBaseMetric {

    protected final ClassicUsprWalker standardWalker;
    private final IncrementalUsprWalker unrootedWalker;
    protected final UsprUtils usprUtils;
    private final String metricShortName;

    protected IncrementalMetric primaryMetric;

    public UsprIncrementalHeuristicMetric(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(false, metric); // false dla drzew nieukorzenionych
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
        this.standardWalker = new ClassicUsprWalker();
        this.unrootedWalker = new IncrementalUsprWalker();
        this.usprUtils = new UsprUtils();
    }

    public UsprIncrementalHeuristicMetric(IncrementalMetric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;
        this.tiedMoves.clear();

        // Wybór zoptymalizowanego walkera dla metryk wspieranych przez IncrementalUsprWalker
        if (activeMetric instanceof MSIncrementalMetric ||
                activeMetric instanceof M3IncrementalMetric) {
            unrootedWalker.walk(currentTree, activeMetric, (currentDist, movingNode, targetNode) -> {
                checkImprovementWithTies(currentDist, new SprMove(movingNode, targetNode));
            });
        } else {
            standardWalker.walk(currentTree, activeMetric, (currentDist, movingNode, targetNode) -> {
                checkImprovementWithTies(currentDist, new SprMove(movingNode, targetNode));
            });
        }
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof SprMove) {
            SprMove sprMove = (SprMove) move;
            Tree newTree = usprUtils.createUsprTree(tree, sprMove.movingNode, sprMove.targetNode); //
            if (newTree != null) {
                TreeCmpUtils.unrootTreeIfNeeded(newTree);
                newTree.createNodeList();
                return newTree;
            }
        }
        return tree;
    }

    @Override
    protected double commitMoveToMetric(TreeMove move) {
        return this.incMetric.getCurrentDistance();
    }

    // =========================================================
    // KLUCZOWA ZMIANA: Implementacja kontraktu Orkiestratora
    // =========================================================
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

        while (this.improved && currentDist > 0) {
            this.improved = false;
            searchNeighborhood(currentTree);

            if (!this.tiedMoves.isEmpty() && this.bestDist < currentDist) {
                TreeMove bestMove = null;

                if (primaryMetric == null || tiedMoves.size() == 1) {
                    bestMove = tiedMoves.get(0);
                } else {
                    double bestSecondaryDist = Double.POSITIVE_INFINITY;
                    for (TreeMove move : tiedMoves) {
                        Tree candidateTree = applyPhysicalMove(currentTree, move);
                        pal.tree.TreeUtils.computeParentPointers(candidateTree.getRoot());
                        this.incMetric.initCalculationState(candidateTree, targetTree);

                        double secDist = this.incMetric.getCurrentDistance();
                        if (secDist < bestSecondaryDist) {
                            bestSecondaryDist = secDist;
                            bestMove = move;
                        }
                    }
                }

                if (bestMove != null) {
                    // 1. NAJPIERW ZLICZAMY KOSZT NNI (na oryginalnym drzewie)
                    this.accumulatedNniCost += bestMove.getNniEquivalentCost(); //[cite: 22]

                    // 2. DOPIERO POTEM APLIKUJEMY RUCH I ZMIENIAMY DRZEWO[cite: 20]
                    currentTree = applyPhysicalMove(currentTree, bestMove);

                    pal.tree.TreeUtils.computeParentPointers(currentTree.getRoot());
                    activeMetric.initCalculationState(currentTree, targetTree);
                    double newDist = activeMetric.getCurrentDistance();

                    // Circuit Breaker: Przerywamy na płaskowyżu
                    if (newDist >= currentDist) {
                        break;
                    }

                    currentDist = newDist;
                    this.improved = true;
                }
            }
        }

        this.lastOptimumTree = currentTree;
        return currentDist;
    }

    // Zapewnienie kompatybilności wstecznej dla starszych testów
    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        double dist = performLocalDescent(tree1, tree2);
        return dist == 0.0 ? this.accumulatedNniCost : Double.POSITIVE_INFINITY;
    }

    @Override public boolean isRooted() { return false; }
    @Override public String getName() { return "Heur. uSPR " + this.metricShortName; }
    @Override public String getCommandLineName() { return "huspr_" + this.incMetric.getCommandLineName(); }
}