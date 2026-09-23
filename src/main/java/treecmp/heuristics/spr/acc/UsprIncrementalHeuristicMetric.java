package treecmp.heuristics.spr.acc;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.SprMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.M3IncrementalMetric;
import treecmp.metrics.topological.acc.MSIncrementalMetric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UsprIncrementalHeuristicMetric extends IncrementalHeuristicBaseMetric {

    protected final ClassicUsprWalker standardWalker;
    private final IncrementalUsprWalker unrootedWalker;
    protected final UsprUtils usprUtils;
    private final String metricShortName;

    protected IncrementalMetric primaryMetric;
    private int sprStepsCount = 0;

    public UsprIncrementalHeuristicMetric(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(false, metric);
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
        this.bestMove = null;
        this.improved = false;
        this.bestDist = activeMetric.getCurrentDistance();

        if (activeMetric instanceof MSIncrementalMetric || activeMetric instanceof M3IncrementalMetric) {
            unrootedWalker.walk(currentTree, activeMetric, (currentDist, movingNode, targetNode) -> {
                if (currentDist <= this.bestDist + 1e-9) {
                    checkImprovementWithTies(currentDist, new SprMove(movingNode, targetNode));
                }
            });
        } else {
            standardWalker.walk(currentTree, activeMetric, (currentDist, movingNode, targetNode) -> {
                if (currentDist <= this.bestDist + 1e-9) {
                    checkImprovementWithTies(currentDist, new SprMove(movingNode, targetNode));
                }
            });
        }
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof SprMove) {
            SprMove sprMove = (SprMove) move;
            Tree newTree = usprUtils.createUsprTree(tree, sprMove.sourceNode, sprMove.targetNode);
            if (newTree != null) {
                TreeCmpUtils.unrootTreeIfNeeded(newTree);
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
        IncrementalMetric activeMetric = primaryMetric != null ? primaryMetric : this.incMetric;
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
        this.sprStepsCount = 0;
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

            if (primaryMetric == null) {
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
                if (primaryMetric == null || nextSecDist >= currentSecDist - 1e-9) {
                    break;
                }
            }

            if (this.primaryMetric != null) {
                this.incMetric.initCalculationState(nextTree, targetTree);
                nextSecDist = this.incMetric.getCurrentDistance();
            }

            // Dekompozycja ruchu uSPR na sekwencję 1-NNI bez podwójnego zliczania
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
            this.sprStepsCount++;

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

    public int getSprStepsCount() {
        return this.sprStepsCount;
    }

    @Override public boolean isRooted() { return false; }
    @Override public String getName() { return "Heur. uSPR " + this.metricShortName; }
    @Override public String getCommandLineName() { return "huspr_" + this.incMetric.getCommandLineName(); }
}