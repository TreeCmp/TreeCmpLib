/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package treecmp.heuristics.base;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import pal.io.InputSource;
import pal.tree.ReadTree;
import pal.tree.TreeParseException;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.spr.SubtreeUtils;
import treecmp.metrics.*;
import pal.tree.Tree;

/**
 *
 * @author Damian
 */
public abstract class HeuristicBaseMetric extends BaseMetric implements Metric {

    protected boolean reduceCommonBinarySubtreesTrees = false;

    protected HeuristicBaseMetric(boolean rooted) {
        super();
        this.rooted = rooted;
    }

    // To jest Twoja metryka docelowa (np. MAST)
    protected abstract Metric getMetric();
    protected abstract TreeNeighborhoodUtils getTreeNeighborhoodUtils();

    // NOWOŚĆ: Domyślnie metryką prowadzącą jest ta sama, co docelowa (czyli normalna heurystyka)
    protected Metric getPrimaryMetric() {
        return getMetric();
    }

    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        Metric primary = getPrimaryMetric();
        Metric secondary = getMetric();
        TreeNeighborhoodUtils tnu = getTreeNeighborhoodUtils();

        try {
            // Używamy primary do sprawdzenia stanu początkowego
            if (primary.getDistance(tree1, tree2) == 0) return 0;

            Tree currentStepTree = tree1;
            if (reduceCommonBinarySubtreesTrees) {
                Tree[] reducedTrees = SubtreeUtils.reduceCommonBinarySubtreesEx(tree1, tree2, null);
                currentStepTree = reducedTrees[0];
                tree2 = reducedTrees[1];
            }

            int stepCount = 0;
            double bestDist1 = Double.POSITIVE_INFINITY, bestDist2 = Double.POSITIVE_INFINITY;

            do {
                Tree[] treeList = tnu.generateNeighbours(currentStepTree);
                double bestDist = Double.POSITIVE_INFINITY;
                List<Tree> bestTreeList = new ArrayList<>(); // Obsługa remisów
                stepCount++;

                for (Tree tempTree : treeList) {
                    double tempDist = primary.getDistance(tempTree, tree2);
                    if (tempDist < bestDist) {
                        bestDist = tempDist;
                        bestTreeList.clear();
                        bestTreeList.add(tempTree);
                    } else if (tempDist == bestDist && bestDist != Double.POSITIVE_INFINITY) {
                        bestTreeList.add(tempTree);
                    }
                }

                // Rozstrzyganie remisów (jeśli primary == secondary, weźmie po prostu pierwsze drzewo)
                Tree bestTree = findBestTree(bestTreeList, tree2, secondary);

                if (bestTree == null) return Double.POSITIVE_INFINITY;

                // Aktualizacja drzewa
                String bestTreeString = bestTree.toString();
                try (InputSource is = InputSource.openString(bestTreeString)) {
                    currentStepTree = new ReadTree(is);
                }

                bestDist1 = bestDist2;
                bestDist2 = bestDist;
                if (bestDist1 <= bestDist2) return Double.POSITIVE_INFINITY;

            } while (bestDist2 != 0);

            return (double) stepCount;

        } catch (TreeCmpException | TreeParseException | IOException ex) {
            Logger.getLogger(HeuristicBaseMetric.class.getName()).log(Level.SEVERE, null, ex);
        }
        return Double.POSITIVE_INFINITY;
    }

    protected Tree findBestTree(List<Tree> treeList, Tree t2, Metric secondary) throws TreeCmpException {
        if (treeList.isEmpty()) return null;
        // Jeśli nie ma filtrowania (metryki są te same), nie trać czasu na pętlę
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
}
