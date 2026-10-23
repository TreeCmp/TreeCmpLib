package treecmp.heuristics.spr.acc;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.SprMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UsprIncrementalHeuristicMetric extends IncrementalHeuristicBaseMetric {

    private final IncrementalUsprWalker unrootedWalker;
    protected final UsprUtils usprUtils;
    private final String metricShortName;

    protected IncrementalMetric primaryMetric;
    private int sprStepsCount = 0;

    private final List<Tree> stepBaseTrees = new ArrayList<>();
    private final List<TreeMove> stepMoves = new ArrayList<>();
    private final List<TreeMove> fallbackImprovingMoves = new ArrayList<>();

    public UsprIncrementalHeuristicMetric(IncrementalMetric metric, IncrementalMetric primaryMetric, String metricShortName) {
        super(false, metric);
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
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
        double initialDist = activeMetric.getCurrentDistance();
        this.bestDist = initialDist;
        final boolean allowPlateau = (this.primaryMetric != null);

        unrootedWalker.walk(currentTree, activeMetric, (currentDist, movingNode, targetNode) -> {
            if (!allowPlateau) {
                if (currentDist < this.bestDist - 1e-9) {
                    this.bestDist = currentDist;
                    this.tiedMoves.clear();
                    SprMove move = new SprMove(movingNode, targetNode);
                    this.tiedMoves.add(move);
                    this.bestMove = move;
                    this.improved = true;
                }
            } else {
                if (currentDist <= this.bestDist + 1e-9) {
                    checkImprovementWithTies(currentDist, new SprMove(movingNode, targetNode));
                }
            }
        });
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
                for (int i = 0; i < newTree.getExternalNodeCount(); i++) {
                    newTree.getExternalNode(i).setNumber(i);
                }
                for (int i = 0; i < newTree.getInternalNodeCount(); i++) {
                    newTree.getInternalNode(i).setNumber(i);
                }
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
        for (int i = 0; i < currentTree.getExternalNodeCount(); i++) {
            currentTree.getExternalNode(i).setNumber(i);
        }
        for (int i = 0; i < currentTree.getInternalNodeCount(); i++) {
            currentTree.getInternalNode(i).setNumber(i);
        }

        this.improved = true;
        this.accumulatedNniCost = 0.0;
        this.accumulatedSteps = 0;
        this.sprStepsCount = 0;
        this.fullOptimumTrajectory.clear();
        this.nativeOptimumTrajectory.clear();
        this.stepBaseTrees.clear();
        this.stepMoves.clear();

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
            double verifiedNewDist = Double.POSITIVE_INFINITY;
            double nextSecDist = currentSecDist;
            boolean isPlateau = Math.abs(this.bestDist - currentDist) <= 1e-9;

            if (primaryMetric == null) {
                if (this.bestDist < currentDist - 1e-9) {
                    for (int i = 0; i < this.tiedMoves.size(); i++) {
                        TreeMove candMove = this.tiedMoves.get(i);
                        Tree candTree = applyPhysicalMove(currentTree, candMove);
                        if (candTree == null || candTree == currentTree) {
                            continue;
                        }
                        activeMetric.initCalculationState(candTree, targetTree);
                        double d = activeMetric.getCurrentDistance();
                        if (Math.abs(d - this.bestDist) <= 1e-9) {
                            bestMove = candMove;
                            bestCandidateTree = candTree;
                            verifiedNewDist = d;
                            break;
                        } else if (d < currentDist - 1e-9 && d < verifiedNewDist - 1e-9) {
                            bestMove = candMove;
                            bestCandidateTree = candTree;
                            verifiedNewDist = d;
                        }
                    }

                    if (bestMove == null) {
                        for (int i = 0; i < this.fallbackImprovingMoves.size(); i++) {
                            TreeMove candMove = this.fallbackImprovingMoves.get(i);
                            Tree candTree = applyPhysicalMove(currentTree, candMove);
                            if (candTree == null || candTree == currentTree) {
                                continue;
                            }
                            activeMetric.initCalculationState(candTree, targetTree);
                            double d = activeMetric.getCurrentDistance();
                            if (d < currentDist - 1e-9 && d < verifiedNewDist - 1e-9) {
                                bestMove = candMove;
                                bestCandidateTree = candTree;
                                verifiedNewDist = d;
                                break;
                            }
                        }
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

                    for (int i = 0; i < this.tiedMoves.size(); i++) {
                        TreeMove move = this.tiedMoves.get(i);
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

            int nniStepsAdded = (int) Math.round(bestMove.getNniEquivalentCost());
            if (nniStepsAdded <= 0) {
                nniStepsAdded = 1;
            }

            Tree nextTree = (bestCandidateTree != null) ? bestCandidateTree : applyPhysicalMove(currentTree, bestMove);
            if (nextTree == null || nextTree == currentTree) {
                break;
            }

            double newDist;
            if (primaryMetric == null && verifiedNewDist < Double.POSITIVE_INFINITY) {
                newDist = verifiedNewDist;
            } else {
                activeMetric.initCalculationState(nextTree, targetTree);
                newDist = activeMetric.getCurrentDistance();
            }

            if (newDist > currentDist + 1e-9) {
                break;
            }
            if (Math.abs(newDist - currentDist) <= 1e-9) {
                if (primaryMetric == null) {
                    if (this.bestDist < currentDist - 1e-9) {
                        bestMove = this.tiedMoves.get(0);
                    } else {
                        break;
                    }
                }
            }

            if (this.primaryMetric != null) {
                this.incMetric.initCalculationState(nextTree, targetTree);
                nextSecDist = this.incMetric.getCurrentDistance();
            }

            this.stepBaseTrees.add(currentTree);
            this.stepMoves.add(bestMove);
            this.nativeOptimumTrajectory.add(ensureIndexedSimpleTree(TreeNeighborhoodUtils.fastTreeClone(nextTree)));

            this.accumulatedNniCost += nniStepsAdded;
            this.accumulatedSteps++;
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
        if (this.fullOptimumTrajectory.isEmpty() && !this.stepMoves.isEmpty()) {
            for (int i = 0; i < this.stepMoves.size(); i++) {
                try {
                    List<Tree> stepTraj = this.stepMoves.get(i).getNniTrajectory(this.stepBaseTrees.get(i));
                    if (stepTraj != null && !stepTraj.isEmpty()) {
                        this.fullOptimumTrajectory.addAll(stepTraj);
                    }
                } catch (Exception ignored) {
                }
            }
        }
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
        return dist == 0.0 ? (double) this.sprStepsCount : Double.POSITIVE_INFINITY;
    }

    public int getSprStepsCount() {
        return this.sprStepsCount;
    }

    @Override public boolean isRooted() { return false; }
    @Override public String getName() { return "Heur. uSPR " + this.metricShortName; }
    @Override public String getCommandLineName() { return "huspr_" + this.incMetric.getCommandLineName(); }
}