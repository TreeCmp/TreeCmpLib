package treecmp.heuristics.base;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import pal.io.InputSource;
import pal.tree.ReadTree;
import pal.tree.TreeParseException;
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
    protected int accumulatedSteps = 0; // NOWOŚĆ: Natywny licznik kroków heurystyki
    protected TreeMove lastOptimumMove = null;
    protected Tree lastMoveBaseTree = null;

    public Tree getLastOptimumTree() {
        return this.lastOptimumTree;
    }

    public double getAccumulatedNniCost() {
        return this.accumulatedNniCost; // Dla VND: ekwiwalent NNI
    }

    public int getAccumulatedSteps() {
        return this.accumulatedSteps; // Dla ECR/SPR: natywna liczba kroków
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
            // NOWOŚĆ: Autonomiczne wywołanie zwraca natywną liczbę kroków
            return (double) this.accumulatedSteps;
        }
        return Double.POSITIVE_INFINITY;
    }

    public double performLocalDescent(Tree startTree, Tree targetTree) {
        Metric primary = getPrimaryMetric();
        Metric secondary = getMetric();
        TreeNeighborhoodUtils tnu = getTreeNeighborhoodUtils();

        // NOWOŚĆ: Resetujemy obie waluty przed startem poszukiwań
        this.accumulatedNniCost = 0.0;
        this.accumulatedSteps = 0;
        this.lastOptimumMove = null;
        this.lastMoveBaseTree = null;
        Tree currentStepTree = startTree;
        Tree targetStepTree = targetTree;

        try {
            if (reduceCommonBinarySubtreesTrees) {
                Tree[] reducedTrees = SubtreeUtils.reduceCommonBinarySubtreesEx(startTree, targetTree, null);
                currentStepTree = reducedTrees[0];
                targetStepTree = reducedTrees[1];
            }

            double currentBestDist = primary.getDistance(currentStepTree, targetStepTree);

            if (currentBestDist == 0) {
                this.lastOptimumTree = currentStepTree;
                return 0.0;
            }

            double previousDist;

            do {
                tnu.clearCosts();

                Tree[] treeList = tnu.generateNeighbours(currentStepTree);
                double bestDist = Double.POSITIVE_INFINITY;
                List<Tree> bestTreeList = new ArrayList<>();

                for (Tree tempTree : treeList) {
                    double tempDist = primary.getDistance(tempTree, targetStepTree);
                    if (tempDist < bestDist) {
                        bestDist = tempDist;
                        bestTreeList.clear();
                        bestTreeList.add(tempTree);
                    } else if (tempDist == bestDist && bestDist != Double.POSITIVE_INFINITY) {
                        bestTreeList.add(tempTree);
                    }
                }

                Tree bestTree = findBestTree(bestTreeList, targetStepTree, secondary);

                if (bestTree == null) {
                    break;
                }

                previousDist = currentBestDist;

                if (bestDist > previousDist) {
                    break;
                } else if (bestDist == previousDist) {
                    double currentSecondaryDist = secondary.getDistance(currentStepTree, targetStepTree);
                    double nextSecondaryDist = secondary.getDistance(bestTree, targetStepTree);

                    if (nextSecondaryDist >= currentSecondaryDist - 1e-9) {
                        break;
                    }
                }

                currentBestDist = bestDist;

                // NOWOŚĆ: Podwójna księgowość (NNI dla VND, kroki dla natywnej heurystyki)
                this.accumulatedNniCost += tnu.getTreeCost(bestTree);
                this.accumulatedSteps++;

                this.lastOptimumMove = tnu.getMoveForTree(bestTree);
                this.lastMoveBaseTree = currentStepTree;

                String bestTreeString = bestTree.toString();
                try (InputSource is = InputSource.openString(bestTreeString)) {
                    currentStepTree = new ReadTree(is);
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
        if (lastOptimumTree == null) {
            return Collections.emptyList();
        }

        if (lastOptimumMove != null && lastMoveBaseTree != null) {
            try {
                List<Tree> traj = lastOptimumMove.getNniTrajectory(lastMoveBaseTree);
                if (traj != null && !traj.isEmpty()) {
                    return traj;
                }
            } catch (Exception e) {
                // Bezpieczny fallback
            }
        }

        return Collections.singletonList(lastOptimumTree);
    }
}