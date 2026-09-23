package treecmp.heuristics.tbr.acc;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TbrMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Uniwersalna, akcelerowana heurystyka (Steepest Descent) dla otoczenia uTBR.
 * Dedykowana dla drzew nieukorzenionych ze ścisłym bezpiecznikiem plateau i minimów lokalnych.
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
        this.bestMove = null;
        this.improved = false;
        this.bestDist = activeMetric.getCurrentDistance();

        walker.walk(currentTree, activeMetric, (neighborDist, pruneNode, rerootNode, targetNode) -> {
            if (neighborDist <= this.bestDist + 1e-9) {
                checkImprovementWithTies(neighborDist, new TbrMove(pruneNode, rerootNode, targetNode));
            }
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
        IncrementalMetric activeMetric = this.primaryMetric != null ? this.primaryMetric : this.incMetric;
        return activeMetric.getCurrentDistance();
    }

    @Override
    public double performLocalDescent(Tree startTree, Tree targetTree) {
        Tree currentTree = new SimpleTree(startTree);
        if (currentTree instanceof SimpleTree) {
            ((SimpleTree) currentTree).createNodeList();
        }
        TreeUtils.computeParentPointers(currentTree.getRoot());

        this.improved = true;
        this.accumulatedNniCost = 0.0;
        this.accumulatedSteps = 0;
        this.utbrStepsCount = 0;
        this.fullOptimumTrajectory.clear();

        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;

        activeMetric.initCalculationState(currentTree, targetTree);
        double currentDist = activeMetric.getCurrentDistance();

        double currentSecDist = Double.POSITIVE_INFINITY;
        if (this.primaryMetric != null) {
            this.incMetric.initCalculationState(currentTree, targetTree);
            currentSecDist = this.incMetric.getCurrentDistance();
        }

        if (currentDist == 0.0) {
            this.lastOptimumTree = currentTree;
            return 0.0;
        }

        int maxIterations = 1000;
        int iteration = 0;

        while (currentDist > 0 && iteration < maxIterations) {
            this.improved = false;
            searchNeighborhood(currentTree);

            if (this.tiedMoves.isEmpty() || this.bestDist > currentDist + 1e-9) {
                break;
            }

            TreeMove chosenMove = null;
            Tree bestCandidateTree = null;
            double nextSecDist = currentSecDist;
            boolean isPlateau = Math.abs(this.bestDist - currentDist) <= 1e-9;

            if (this.primaryMetric == null) {
                if (this.bestDist < currentDist - 1e-9) {
                    if (this.tiedMoves.size() > 1) {
                        double lowestNniCost = Double.POSITIVE_INFINITY;
                        for (TreeMove move : this.tiedMoves) {
                            double currentMoveCost = move.getNniEquivalentCost();
                            if (currentMoveCost < lowestNniCost) {
                                lowestNniCost = currentMoveCost;
                                chosenMove = move;
                            }
                        }
                    } else {
                        chosenMove = this.tiedMoves.get(0);
                    }
                } else {
                    break;
                }
            } else {
                if (!isPlateau && this.tiedMoves.size() == 1) {
                    chosenMove = this.tiedMoves.get(0);
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
                        double moveNniCost = move.getNniEquivalentCost();

                        if (secDist < bestSecondaryDist - 1e-9) {
                            bestSecondaryDist = secDist;
                            chosenMove = move;
                            bestNniCostForTie = moveNniCost;
                            bestCandidateTree = candidateTree;
                        } else if (Math.abs(secDist - bestSecondaryDist) <= 1e-9 && moveNniCost < bestNniCostForTie) {
                            chosenMove = move;
                            bestNniCostForTie = moveNniCost;
                            bestCandidateTree = candidateTree;
                        }
                    }

                    if (isPlateau) {
                        if (bestSecondaryDist >= currentSecDist - 1e-9) {
                            chosenMove = null;
                            bestCandidateTree = null;
                        }
                    }
                    nextSecDist = bestSecondaryDist;
                }
            }

            if (chosenMove == null) {
                break;
            }

            Tree nextTree = (bestCandidateTree != null) ? bestCandidateTree : applyPhysicalMove(currentTree, chosenMove);
            if (nextTree == null || nextTree == currentTree) {
                break;
            }

            if (nextTree instanceof SimpleTree) {
                ((SimpleTree) nextTree).createNodeList();
            }
            TreeUtils.computeParentPointers(nextTree.getRoot());

            activeMetric.initCalculationState(nextTree, targetTree);
            double newDist = activeMetric.getCurrentDistance();

            if (newDist > currentDist + 1e-9) {
                break;
            }
            if (Math.abs(newDist - currentDist) <= 1e-9) {
                if (this.primaryMetric == null || nextSecDist >= currentSecDist - 1e-9) {
                    break;
                }
            }

            if (this.primaryMetric != null) {
                this.incMetric.initCalculationState(nextTree, targetTree);
                nextSecDist = this.incMetric.getCurrentDistance();
            }

            // Dekompozycja ruchu uTBR na sekwencję elementarnych kroków 1-NNI
            int nniStepsAdded = 0;
            try {
                List<Tree> stepTraj = chosenMove.getNniTrajectory(currentTree);
                if (stepTraj != null && !stepTraj.isEmpty()) {
                    this.fullOptimumTrajectory.addAll(stepTraj);
                    nniStepsAdded = stepTraj.size();
                }
            } catch (Exception ignored) {
            }

            if (nniStepsAdded == 0) {
                nniStepsAdded = (int) Math.round(chosenMove.getNniEquivalentCost());
            }

            this.accumulatedNniCost += nniStepsAdded;
            this.accumulatedSteps += nniStepsAdded;
            this.utbrStepsCount++;

            this.lastOptimumMove = chosenMove;
            this.lastMoveBaseTree = currentTree;
            currentTree = nextTree;

            currentDist = newDist;
            currentSecDist = nextSecDist;
            this.improved = true;
            iteration++;
        }

        this.lastOptimumTree = currentTree;
        return currentDist;
    }

    public int getUtbrStepsCount() {
        return this.utbrStepsCount;
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
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        double dist = performLocalDescent(tree1, tree2);
        return dist == 0.0 ? this.accumulatedNniCost : Double.POSITIVE_INFINITY;
    }

    @Override
    public String getName() {
        return "uTBR_IncrementalHeuristic_" + metricShortName;
    }
}