package treecmp.heuristics.base;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import pal.io.InputSource;
import pal.tree.ReadTree;
import pal.tree.SimpleTree;
import pal.tree.TreeParseException;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.moves.TreeMove;
import treecmp.heuristics.spr.SubtreeUtils;
import treecmp.metrics.*;
import pal.tree.Tree;

public abstract class HeuristicBaseMetric extends BaseMetric implements Metric {

    protected boolean reduceCommonBinarySubtreesTrees = false;
    protected Tree lastOptimumTree;
    protected double accumulatedNniCost = 0.0;
    protected int accumulatedSteps = 0;
    protected List<Tree> fullOptimumTrajectory = new ArrayList<>();

    public Tree getLastOptimumTree() {
        return this.lastOptimumTree;
    }

    public double getAccumulatedNniCost() {
        return this.accumulatedNniCost;
    }

    public int getAccumulatedSteps() {
        return this.accumulatedSteps;
    }

    protected HeuristicBaseMetric(boolean rooted) {
        super();
        this.rooted = rooted;
    }

    protected abstract Metric getMetric();
    protected abstract TreeNeighborhoodUtils getTreeNeighborhoodUtils();

    protected Metric getPrimaryMetric() {
        return getMetric();
    }

    protected double getBaseStepCost() {
        return 1.0;
    }

    public double evaluateInitialDistance(Tree startTree, Tree targetTree) {
        try {
            return getPrimaryMetric().getDistance(startTree, targetTree);
        } catch (TreeCmpException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        double finalMetricDist = performLocalDescent(tree1, tree2);
        if (finalMetricDist == 0.0) {
            return (double) this.accumulatedSteps;
        }
        return Double.POSITIVE_INFINITY;
    }

    public double performLocalDescent(Tree startTree, Tree targetTree) {
        Metric primary = getPrimaryMetric();
        Metric secondary = getMetric();
        TreeNeighborhoodUtils tnu = getTreeNeighborhoodUtils();

        this.accumulatedNniCost = 0.0;
        this.accumulatedSteps = 0;
        this.fullOptimumTrajectory.clear();

        Tree currentStepTree = ensureIndexedSimpleTree(startTree);
        Tree targetStepTree = targetTree;

        try {
            if (reduceCommonBinarySubtreesTrees) {
                Tree[] reducedTrees = SubtreeUtils.reduceCommonBinarySubtreesEx(currentStepTree, targetStepTree, null);
                currentStepTree = ensureIndexedSimpleTree(reducedTrees[0]);
                targetStepTree = reducedTrees[1];
            }

            final Tree effectiveTargetTree = targetStepTree;
            double currentBestDist = primary.getDistance(currentStepTree, effectiveTargetTree);

            if (currentBestDist == 0) {
                this.lastOptimumTree = currentStepTree;
                return 0.0;
            }

            double previousDist;

            do {
                final double[] bestDistHolder = { Double.POSITIVE_INFINITY };
                List<Tree> bestTreeList = new ArrayList<>();

                tnu.forEachNeighbour(currentStepTree, tempTree -> {
                    double tempDist;
                    try {
                        tempDist = primary.getDistance(tempTree, effectiveTargetTree);
                    } catch (TreeCmpException e) {
                        throw new RuntimeException(e);
                    }

                    if (tempDist < bestDistHolder[0]) {
                        bestDistHolder[0] = tempDist;
                        bestTreeList.clear();
                        bestTreeList.add(tempTree);
                    } else if (tempDist == bestDistHolder[0] && bestDistHolder[0] != Double.POSITIVE_INFINITY) {
                        bestTreeList.add(tempTree);
                    }
                });

                double bestDist = bestDistHolder[0];
                Tree bestTree = null;

                if (!bestTreeList.isEmpty()) {
                    double bestSecDist = Double.POSITIVE_INFINITY;
                    double bestNniCost = Double.POSITIVE_INFINITY;

                    for (Tree candidate : bestTreeList) {
                        double secDist;
                        try {
                            secDist = secondary.getDistance(candidate, effectiveTargetTree);
                        } catch (TreeCmpException e) {
                            throw new RuntimeException(e);
                        }

                        double nniCost = tnu.getTreeCost(candidate);

                        if (secDist < bestSecDist - 1e-9) {
                            bestSecDist = secDist;
                            bestNniCost = nniCost;
                            bestTree = candidate;
                        } else if (Math.abs(secDist - bestSecDist) <= 1e-9 && nniCost < bestNniCost) {
                            bestNniCost = nniCost;
                            bestTree = candidate;
                        }
                    }
                }

                if (bestTree == null) {
                    break;
                }

                previousDist = currentBestDist;

                if (bestDist > previousDist) {
                    break;
                } else if (bestDist == previousDist) {
                    double currentSecondaryDist = secondary.getDistance(currentStepTree, effectiveTargetTree);
                    double nextSecondaryDist = secondary.getDistance(bestTree, effectiveTargetTree);

                    if (nextSecondaryDist >= currentSecondaryDist - 1e-9) {
                        break;
                    }
                }

                currentBestDist = bestDist;

                this.accumulatedNniCost += tnu.getTreeCost(bestTree);
                this.accumulatedSteps++;

                TreeMove move = tnu.getMoveForTree(bestTree);
                if (move != null) {
                    try {
                        List<Tree> stepTraj = move.getNniTrajectory(currentStepTree);
                        if (stepTraj != null && !stepTraj.isEmpty()) {
                            this.fullOptimumTrajectory.addAll(stepTraj);
                        } else {
                            this.fullOptimumTrajectory.add(bestTree);
                        }
                    } catch (Exception e) {
                        this.fullOptimumTrajectory.add(bestTree);
                    }
                } else {
                    this.fullOptimumTrajectory.add(bestTree);
                }

                String bestTreeString = bestTree.toString();
                try (InputSource is = InputSource.openString(bestTreeString)) {
                    currentStepTree = ensureIndexedSimpleTree(new ReadTree(is));
                }

            } while (currentBestDist > 0);

            this.lastOptimumTree = currentStepTree;
            return currentBestDist;

        } catch (TreeCmpException | TreeParseException | IOException ex) {
            Logger.getLogger(HeuristicBaseMetric.class.getName()).log(Level.SEVERE, null, ex);
        }

        this.lastOptimumTree = currentStepTree;
        return Double.POSITIVE_INFINITY;
    }

    private Tree ensureIndexedSimpleTree(Tree tree) {
        if (tree == null) return null;
        SimpleTree st = (tree instanceof SimpleTree) ? (SimpleTree) tree : new SimpleTree(tree);
        st.createNodeList();
        TreeUtils.computeParentPointers(st.getRoot());
        return st;
    }

    protected Tree findBestTree(List<Tree> treeList, Tree t2, Metric secondary) throws TreeCmpException {
        if (treeList.isEmpty()) return null;
        if (getPrimaryMetric() == secondary || treeList.size() == 1) return treeList.get(0);

        Tree bestTree = null;
        double minSecondaryDist = Double.POSITIVE_INFINITY;
        for (Tree t : treeList) {
            double d = secondary.getDistance(t, t2);
            if (d < minSecondaryDist) {
                minSecondaryDist = d;
                bestTree = t;
            }
        }
        return bestTree;
    }

    protected java.util.IdentityHashMap<pal.tree.Tree, Double> treeCosts = new java.util.IdentityHashMap<>();

    public double getTreeCost(pal.tree.Tree t) {
        return treeCosts.getOrDefault(t, 1.0);
    }

    protected void registerTreeCost(pal.tree.Tree t, double cost) {
        if (t != null) {
            treeCosts.put(t, cost);
        }
    }

    public void clearCosts() {
        treeCosts.clear();
    }

    public List<Tree> getLastOptimumTrajectory(Tree startTree) {
        if (this.fullOptimumTrajectory != null && !this.fullOptimumTrajectory.isEmpty()) {
            return new ArrayList<>(this.fullOptimumTrajectory);
        }

        if (lastOptimumTree == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(lastOptimumTree);
    }
}