package treecmp.heuristics.tbr.acc;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TbrMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.IncrementalMetric;

import java.util.List;

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
        Tree currentTree = new pal.tree.SimpleTree(startTree);
        if (currentTree instanceof pal.tree.SimpleTree) {
            ((pal.tree.SimpleTree) currentTree).createNodeList();
        }

        this.improved = true;
        this.accumulatedNniCost = 0.0;
        this.utbrStepsCount = 0;
        this.fullOptimumTrajectory.clear(); // Wyczyszczenie bufora trajektorii NNI na starcie
        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;

        activeMetric.initCalculationState(currentTree, targetTree);
        double currentDist = activeMetric.getCurrentDistance();

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

                if (primaryMetric == null || tiedMoves.size() == 1) {
                    if (this.bestDist < currentDist - 1e-9) {
                        if (tiedMoves.size() > 1) {
                            double lowestNniCost = Double.POSITIVE_INFINITY;
                            for (TreeMove move : tiedMoves) {
                                double currentMoveCost = move.getNniEquivalentCost();
                                if (currentMoveCost < lowestNniCost) {
                                    lowestNniCost = currentMoveCost;
                                    bestMove = move;
                                }
                            }
                        } else {
                            bestMove = tiedMoves.get(0);
                        }
                    }
                } else {
                    double bestSecondaryDist = Double.POSITIVE_INFINITY;
                    double bestNniCostForTie = Double.POSITIVE_INFINITY;

                    for (TreeMove move : tiedMoves) {
                        Tree candidateTree = applyPhysicalMove(currentTree, move);
                        if (candidateTree == null || candidateTree == currentTree) {
                            continue;
                        }
                        pal.tree.TreeUtils.computeParentPointers(candidateTree.getRoot());
                        this.incMetric.initCalculationState(candidateTree, targetTree);

                        double secDist = this.incMetric.getCurrentDistance();
                        double moveNniCost = move.getNniEquivalentCost();

                        if (secDist < bestSecondaryDist - 1e-9) {
                            bestSecondaryDist = secDist;
                            bestMove = move;
                            bestNniCostForTie = moveNniCost;
                        } else if (Math.abs(secDist - bestSecondaryDist) <= 1e-9 && moveNniCost < bestNniCostForTie) {
                            bestMove = move;
                            bestNniCostForTie = moveNniCost;
                        }
                    }

                    if (Math.abs(this.bestDist - currentDist) <= 1e-9) {
                        if (bestSecondaryDist >= currentSecDist - 1e-9) {
                            bestMove = null;
                        }
                    }
                    nextSecDist = bestSecondaryDist;
                }

                if (bestMove != null) {
                    Tree nextTree = applyPhysicalMove(currentTree, bestMove);
                    if (nextTree == null || nextTree == currentTree) {
                        break;
                    }

                    TreeUtils.computeParentPointers(nextTree.getRoot());
                    activeMetric.initCalculationState(nextTree, targetTree);
                    double newDist = activeMetric.getCurrentDistance();

                    // Bezpiecznik leksykograficzny: odrzucenie ruchu przed modyfikacją stanu
                    if (newDist > currentDist - 1e-9) {
                        if (this.primaryMetric == null || nextSecDist >= currentSecDist - 1e-9) {
                            break;
                        }
                    }

                    try {
                        List<Tree> stepTraj = bestMove.getNniTrajectory(currentTree);
                        if (stepTraj != null && !stepTraj.isEmpty()) {
                            this.fullOptimumTrajectory.addAll(stepTraj);
                        }
                    } catch (Exception e) {
                        // Bezpieczny fallback
                    }

                    this.accumulatedNniCost += bestMove.getNniEquivalentCost();
                    this.utbrStepsCount++;

                    this.lastOptimumMove = bestMove;
                    this.lastMoveBaseTree = currentTree;
                    currentTree = nextTree;

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
        return dist == 0.0 ? (double) this.utbrStepsCount : Double.POSITIVE_INFINITY;
    }

    @Override
    public String getName() {
        return "uTBR_IncrementalHeuristic_" + metricShortName;
    }
}