package treecmp.heuristics.tbr.acc;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TbrMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.IncrementalMetric;

/**
 * Uniwersalna, akcelerowana heurystyka (Steepest Descent) dla otoczenia uTBR.
 * Dedykowana dla drzew nieukorzenionych. Obsługuje tryb Pure oraz Tie-breaker.
 */
public class UtbrIncrementalHeuristic extends IncrementalHeuristicBaseMetric {

    private final UtbrNeighborhoodWalker walker;
    private final String metricShortName;
    private final IncrementalMetric primaryMetric;
    private final UTbrUtils utbrUtils;
    private int utbrStepsCount = 0;

    public UtbrIncrementalHeuristic(IncrementalMetric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    public UtbrIncrementalHeuristic(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(false, metric);
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
        this.walker = new UtbrNeighborhoodWalker();
        this.utbrUtils = new UTbrUtils();
    }

    @Override
    protected void searchNeighborhood(Tree currentTree) {
        IncrementalMetric activeMetric = this.primaryMetric != null ? this.primaryMetric : this.incMetric;
        this.tiedMoves.clear();
        this.bestDist = Double.POSITIVE_INFINITY;

        walker.walk(currentTree, activeMetric, (currentDist, pruneNode, rerootNode, targetNode) -> {
            checkImprovementWithTies(currentDist, new TbrMove(pruneNode, rerootNode, targetNode));
        });
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof TbrMove) {
            TbrMove tm = (TbrMove) move;
            if (!utbrUtils.isValidUtbrMove(tm.movingNode, tm.rerootNode, tm.targetNode)) {
                return null;
            }

            Tree newTree = utbrUtils.createUtbrTree(tree, tm.movingNode, tm.rerootNode, tm.targetNode);
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
        this.utbrStepsCount = 0;
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
                    this.utbrStepsCount++;

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
        return dist == 0.0 ? (double) this.utbrStepsCount : Double.POSITIVE_INFINITY;
    }

    @Override
    public String getName() {
        return "uTBR_IncrementalHeuristic_" + metricShortName;
    }
}