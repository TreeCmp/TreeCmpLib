package treecmp.heuristics.tbr.acc;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TbrMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;

/**
 * Uniwersalna, akcelerowana heurystyka (Steepest Descent) dla otoczenia rTBR.
 * Obsługuje warianty Pure oraz dwuetapowy filtr (Tie-breaker z primaryMetric).
 */
public class TbrIncrementalHeuristic extends IncrementalHeuristicBaseMetric {

        private final TbrNeighborhoodWalker classicWalker;
        private final IncrementalTbrWalker incrementalWalker;
        private final String metricShortName;
        private final IncrementalMetric primaryMetric;
        private final TbrUtils tbrUtils;
        private int tbrStepsCount = 0;

        public TbrIncrementalHeuristic(IncrementalMetric metric, String metricShortName) {
            this(metric, null, metricShortName);
        }

        public TbrIncrementalHeuristic(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
            super(true, metric);
            this.primaryMetric = primaryMetric;
            this.metricShortName = metricShortName;
            this.classicWalker = new TbrNeighborhoodWalker();
            this.incrementalWalker = new IncrementalTbrWalker();
            this.tbrUtils = new TbrUtils();
        }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        IncrementalMetric activeMetric = this.primaryMetric != null ? this.primaryMetric : this.incMetric;
        this.tiedMoves.clear();
        this.bestDist = Double.POSITIVE_INFINITY;

        // Wszystkie metryki 2D-DFS (RFCluster, MC, MP) implementują RootedTbrMetric
        // i są obsługiwane przez gotową instancję incrementalWalker:
        if (activeMetric instanceof RootedTbrMetric) {
            incrementalWalker.walk(currentTree, (RootedTbrMetric) activeMetric,
                    (dist, prune, reroot, target) -> {
                        checkImprovementWithTies(dist, new TbrMove(prune, reroot, target));
                    });
        } else {
            // Klasyczny fallback dla metryk nie-inkrementalnych
            classicWalker.walk(currentTree, activeMetric,
                    (dist, prune, reroot, target) -> {
                        checkImprovementWithTies(dist, new TbrMove(prune, reroot, target));
                    });
        }
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof TbrMove) {
            TbrMove tm = (TbrMove) move;
            if (!tbrUtils.isValidTbrMove(tm.movingNode, tm.rerootNode, tm.targetNode)) {
                return null;
            }

            Tree newTree = tbrUtils.createTbrTree(tree, tm.movingNode, tm.rerootNode, tm.targetNode);
            if (newTree != null) {
                if (newTree instanceof SimpleTree) {
                    ((SimpleTree) newTree).createNodeList();
                }
                TreeUtils.computeParentPointers(newTree.getRoot());
                return newTree;
            }
        }
        return null;
    }

    @Override
    protected double commitMoveToMetric(TreeMove move) {
        return this.incMetric.getCurrentDistance();
    }

    @Override
    public double performLocalDescent(Tree startTree, Tree targetTree) {
        Tree currentTree = new SimpleTree(startTree);
        if (currentTree instanceof SimpleTree) {
            ((SimpleTree) currentTree).createNodeList();
        }

        this.improved = true;
        this.accumulatedNniCost = 0.0;
        this.tbrStepsCount = 0;
        IncrementalMetric activeMetric = this.primaryMetric != null ? this.primaryMetric : this.incMetric;

        activeMetric.initCalculationState(currentTree, targetTree);
        double currentDist = activeMetric.getCurrentDistance();

        // Śledzimy dystans metryki drugorzędnej, aby zapobiec cyklom na płaskowyżach
        double currentSecDist = Double.POSITIVE_INFINITY;
        if (this.primaryMetric != null) {
            this.incMetric.initCalculationState(currentTree, targetTree);
            currentSecDist = this.incMetric.getCurrentDistance();
        }

        if (currentDist == 0) {
            this.lastOptimumTree = currentTree;
            return 0.0;
        }

        while (this.improved && currentDist > 0) {
            this.improved = false;
            searchNeighborhood(currentTree);

            if (!this.tiedMoves.isEmpty() && this.bestDist <= currentDist) {
                TreeMove bestMove = null;
                double nextSecDist = currentSecDist;

                if (this.primaryMetric == null || this.tiedMoves.size() == 1) {
                    // SCENARIUSZ 1: Brak drugorzędnej metryki LUB dokładnie 1 ruch remisowy
                    if (this.bestDist < currentDist - 1e-9) {
                        if (this.tiedMoves.size() > 1) {
                            double lowestNniCost = Double.POSITIVE_INFINITY;
                            for (TreeMove move : this.tiedMoves) {
                                double cost = move.getNniEquivalentCost();
                                if (cost < lowestNniCost) {
                                    lowestNniCost = cost;
                                    bestMove = move;
                                }
                            }
                        } else {
                            bestMove = this.tiedMoves.get(0);
                        }
                    }
                } else {
                    // SCENARIUSZ 2: Ewaluacja remisów metryką drugorzędną (this.incMetric)
                    double bestSecondaryDist = Double.POSITIVE_INFINITY;
                    double bestNniCostForTie = Double.POSITIVE_INFINITY;

                    for (TreeMove move : this.tiedMoves) {
                        Tree candidateTree = applyPhysicalMove(currentTree, move);
                        if (candidateTree == null || candidateTree == currentTree) {
                            continue;
                        }

                        TreeUtils.computeParentPointers(candidateTree.getRoot());
                        this.incMetric.initCalculationState(candidateTree, targetTree);

                        double secDist = this.incMetric.getCurrentDistance();
                        double moveCost = move.getNniEquivalentCost();

                        if (secDist < bestSecondaryDist - 1e-9) {
                            bestSecondaryDist = secDist;
                            bestMove = move;
                            bestNniCostForTie = moveCost;
                        } else if (Math.abs(secDist - bestSecondaryDist) <= 1e-9 && moveCost < bestNniCostForTie) {
                            bestMove = move;
                            bestNniCostForTie = moveCost;
                        }
                    }

                    // BLOKADA PĘTLI: Ruch neutralny w metryce głównej (płaskowyż)
                    // wolno zaakceptować TYLKO wtedy, gdy metryka pomocnicza ściśle maleje!
                    if (Math.abs(this.bestDist - currentDist) <= 1e-9) {
                        if (bestSecondaryDist >= currentSecDist - 1e-9) {
                            bestMove = null; // Ślepy zaułek na płaskowyżu -> przerywamy wspinaczkę
                        }
                    }
                    nextSecDist = bestSecondaryDist;
                }

                if (bestMove != null) {
                    Tree nextTree = applyPhysicalMove(currentTree, bestMove);
                    if (nextTree == null || nextTree == currentTree) {
                        break;
                    }

                    this.accumulatedNniCost += bestMove.getNniEquivalentCost();
                    this.tbrStepsCount++;

                    this.lastOptimumMove = bestMove;
                    this.lastMoveBaseTree = currentTree;
                    currentTree = nextTree;

                    TreeUtils.computeParentPointers(currentTree.getRoot());
                    activeMetric.initCalculationState(currentTree, targetTree);
                    double newDist = activeMetric.getCurrentDistance();

                    // Bezpiecznik leksykograficzny
                    if (newDist > currentDist - 1e-9) {
                        if (this.primaryMetric == null || nextSecDist >= currentSecDist - 1e-9) {
                            break;
                        }
                    }

                    currentDist = newDist;
                    currentSecDist = nextSecDist;
                    this.improved = true;
                }
            }
        }

        this.lastOptimumTree = currentTree;
        return currentDist;
    }

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        double dist = performLocalDescent(tree1, tree2);
        return dist == 0.0 ? (double) this.tbrStepsCount : Double.POSITIVE_INFINITY;
    }

    @Override
    public String getName() {
        return "TBR_IncrementalHeuristic_" + metricShortName;
    }
}