package treecmp.heuristics.tbr.acc;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TbrMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.metrics.IncrementalMetric;

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

            // JEŚLI METRYKA WSPIERA 1-NNI 2D-DFS (MC, MP) -> UŻYWAMY INCREMENTAL WALKERA!
            if (activeMetric instanceof IncrementalTbrWalker.RootedTbrMetric) {
                incrementalWalker.walk(currentTree, (IncrementalTbrWalker.RootedTbrMetric) activeMetric,
                        (currentDist, pruneNode, rerootNode, targetNode) -> {
                            checkImprovementWithTies(currentDist, new TbrMove(pruneNode, rerootNode, targetNode));
                        });
            } else {
                // Fallback dla metryk bitowych klastrów (RFC)
                classicWalker.walk(currentTree, activeMetric,
                        (currentDist, pruneNode, rerootNode, targetNode) -> {
                            checkImprovementWithTies(currentDist, new TbrMove(pruneNode, rerootNode, targetNode));
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

        if (currentDist == 0) {
            this.lastOptimumTree = currentTree;
            return 0.0;
        }

        while (this.improved && currentDist > 0) {
            this.improved = false;
            searchNeighborhood(currentTree);

            if (!this.tiedMoves.isEmpty() && this.bestDist <= currentDist) {
                TreeMove bestMove = null;

                if (this.primaryMetric == null || this.tiedMoves.size() == 1) {
                    // SCENARIUSZ 1: Brak drugorzędnej metryki. Wybór tańszego ruchu pod kątem NNI
                    if (this.tiedMoves.size() > 1 && this.bestDist < currentDist) {
                        double lowestNniCost = Double.POSITIVE_INFINITY;
                        for (TreeMove move : this.tiedMoves) {
                            double cost = move.getNniEquivalentCost();
                            if (cost < lowestNniCost) {
                                lowestNniCost = cost;
                                bestMove = move;
                            }
                        }
                    } else if (this.bestDist < currentDist) {
                        bestMove = this.tiedMoves.get(0);
                    }
                } else {
                    // SCENARIUSZ 2: Ewaluacja remisów metryką właściwą (this.incMetric)
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

                    if (this.primaryMetric == null && newDist >= currentDist) {
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