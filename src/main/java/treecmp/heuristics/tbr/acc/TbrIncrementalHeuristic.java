package treecmp.heuristics.tbr.acc;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.TbrMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        this.bestMove = null;
        this.improved = false;
        this.bestDist = activeMetric.getCurrentDistance();

        if (activeMetric instanceof RootedTbrMetric) {
            incrementalWalker.walk(currentTree, (RootedTbrMetric) activeMetric,
                    (dist, prune, reroot, target) -> {
                        if (dist <= this.bestDist + 1e-9) {
                            checkImprovementWithTies(dist, new TbrMove(prune, reroot, target));
                        }
                    });
        } else {
            classicWalker.walk(currentTree, activeMetric,
                    (dist, prune, reroot, target) -> {
                        if (dist <= this.bestDist + 1e-9) {
                            checkImprovementWithTies(dist, new TbrMove(prune, reroot, target));
                        }
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
        this.tbrStepsCount = 0;
        this.fullOptimumTrajectory.clear();

        IncrementalMetric activeMetric = this.primaryMetric != null ? this.primaryMetric : this.incMetric;

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

        int maxSteps = 1000;
        int steps = 0;

        while (currentDist > 0 && steps < maxSteps) {
            this.improved = false;
            searchNeighborhood(currentTree);

            if (this.tiedMoves.isEmpty() || this.bestDist > currentDist + 1e-9) {
                break;
            }

            TreeMove bestMove = null;
            Tree bestCandidateTree = null;
            double nextSecDist = currentSecDist;
            boolean isPlateau = Math.abs(this.bestDist - currentDist) <= 1e-9;

            if (this.primaryMetric == null) {
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
                } else {
                    break;
                }
            } else {
                if (!isPlateau && this.tiedMoves.size() == 1) {
                    bestMove = this.tiedMoves.get(0);
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
                            bestCandidateTree = candidateTree;
                        } else if (Math.abs(secDist - bestSecondaryDist) <= 1e-9 && moveCost < bestNniCostForTie) {
                            bestMove = move;
                            bestNniCostForTie = moveCost;
                            bestCandidateTree = candidateTree;
                        }
                    }

                    if (isPlateau) {
                        if (bestSecondaryDist >= currentSecDist - 1e-9) {
                            bestMove = null;
                            bestCandidateTree = null;
                        }
                    }
                    nextSecDist = bestSecondaryDist;
                }
            }

            if (bestMove == null) {
                break;
            }

            Tree nextTree = (bestCandidateTree != null) ? bestCandidateTree : applyPhysicalMove(currentTree, bestMove);
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

            // Dekompozycja ruchu rTBR na sekwencję 1-NNI
            int nniStepsAdded = 0;
            try {
                List<Tree> stepTraj = bestMove.getNniTrajectory(currentTree);
                if (stepTraj != null && !stepTraj.isEmpty()) {
                    this.fullOptimumTrajectory.addAll(stepTraj);
                    nniStepsAdded = stepTraj.size();
                }
            } catch (Exception ignored) {
            }

            if (nniStepsAdded == 0) {
                nniStepsAdded = (int) Math.round(bestMove.getNniEquivalentCost());
            }

            this.accumulatedNniCost += nniStepsAdded;
            this.accumulatedSteps += nniStepsAdded;
            this.tbrStepsCount++;

            this.lastOptimumMove = bestMove;
            this.lastMoveBaseTree = currentTree;
            currentTree = nextTree;

            currentDist = newDist;
            currentSecDist = nextSecDist;
            this.improved = true;
            steps++;
        }

        this.lastOptimumTree = currentTree;
        return currentDist;
    }

    public int getTbrStepsCount() {
        return this.tbrStepsCount;
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
        return "TBR_IncrementalHeuristic_" + metricShortName;
    }
}