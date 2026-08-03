package treecmp.heuristics.spr.acc;

import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.moves.SprMove;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.MCIncrementalMetric;
import treecmp.metrics.topological.acc.MPIncrementalMetric;
import treecmp.metrics.topological.acc.MSIncrementalMetric;

import java.util.ArrayList;
import java.util.List;

public class SprIncrementalHeuristicMetric extends IncrementalHeuristicBaseMetric {

    protected final ClassicSprWalker standardWalker;
    private final IncrementalSprWalker rootedWalker;
    protected final SprUtils sprUtils;
    private final String metricShortName;

    protected IncrementalMetric primaryMetric;

    public SprIncrementalHeuristicMetric(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(metric.isRooted(), metric);
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
        this.standardWalker = new ClassicSprWalker();
        this.rootedWalker = new IncrementalSprWalker();
        this.sprUtils = new SprUtils();
    }

    public SprIncrementalHeuristicMetric(IncrementalMetric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;
        this.tiedMoves.clear();
        this.bestDist = Double.POSITIVE_INFINITY;

        if (activeMetric instanceof MSIncrementalMetric ||
                activeMetric instanceof MPIncrementalMetric ||
                activeMetric instanceof MCIncrementalMetric) {
            rootedWalker.walk(currentTree, (IncrementalSprWalker.RootedMetric) activeMetric, (currentDist, movingNode, targetNode) -> {
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
            Tree newTree = sprUtils.applyPhysicalSprMove(tree, sprMove);
            if (newTree != null) {
                if (this.incMetric.isRooted()) {
                    newTree.getRoot().setBranchLength(0.0);
                } else {
                    TreeCmpUtils.unrootTreeIfNeeded(newTree);
                }
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
        this.accumulatedSteps = 0;
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
                    this.accumulatedNniCost += bestMove.getNniEquivalentCost();
                    this.accumulatedSteps++;

                    // 2. DOPIERO POTEM APLIKUJEMY RUCH I ZMIENIAMY DRZEWO
                    this.lastOptimumMove = bestMove;
                    this.lastMoveBaseTree = currentTree;
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
        return dist == 0.0 ? (double) this.accumulatedSteps : Double.POSITIVE_INFINITY;
    }

    @Override public boolean isRooted() { return this.incMetric.isRooted(); }
    @Override public String getName() { return "Heur. SPR " + this.metricShortName; }
    @Override public String getCommandLineName() { return "hspr_" + this.incMetric.getCommandLineName(); }
}