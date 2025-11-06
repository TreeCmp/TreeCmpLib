/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package treecmp.heuristics.spr;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import pal.io.InputSource;
import pal.tree.ReadTree;
import pal.tree.TreeParseException;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.*;
import pal.tree.Tree;

/**
 *
 * @author Damian
 */
public abstract class HeuristicBaseMetric extends BaseMetric implements Metric {

    protected HeuristicBaseMetric(boolean rooted) {
        super();
        this.rooted = rooted;
        this.m = getMetric();
        this.tnu = getTreeNeighborhoodUtils();
    }

    private Metric m = getMetric();
    private TreeNeighborhoodUtils tnu = getTreeNeighborhoodUtils();
    protected boolean reduceCommonBinarySubtreesTrees = false;

    public double getDistance(Tree tree1, Tree tree2, int... indexes) {
        double dist = 0;
        double startDist = 0;

        try {
            startDist = m.getDistance(tree1, tree2);
            if (startDist == 0) {
                return 0;
            }

            Tree t1 = tree1;
            Tree t2 = tree2;
            if (reduceCommonBinarySubtreesTrees) {
                int startLeafNum = tree1.getExternalNodeCount();
                //  System.out.println("Number of leaves: " + startLeafNum);

                Tree[] reducedTrees = SubtreeUtils.reduceCommonBinarySubtreesEx(tree1, tree2, null);

                t1 = reducedTrees[0];
                t2 = reducedTrees[1];
                int reducedLeafNum = t1.getExternalNodeCount();
                //   System.out.println("Number of leaves after reduction: " + reducedLeafNum);
            }

            int sprDist = 0; // Lub nniDist, to tylko nazwa zmiennej
            Tree[] treeList;
            Tree bestTree = null;
            Tree tempTree = null;
            double bestDist, tempDist;
            Tree currentStepTree = t1;
            double bestDist1 = Double.POSITIVE_INFINITY, bestDist2 = Double.POSITIVE_INFINITY;

            do {
                treeList = tnu.generateNeighbours(currentStepTree);

                bestDist = Double.POSITIVE_INFINITY;
                sprDist++;
                for (int i = 0; i < treeList.length; i++) {
                    tempTree = treeList[i];
                    tempDist = m.getDistance(tempTree, t2);
                    if (tempDist < bestDist) {
                        bestDist = tempDist;
                        bestTree = tempTree;
                    }
                }

                // TODO: warto to zbadać
                if (bestTree == null) {
                    // Nie znaleziono lepszego sąsiada, utknęliśmy
                    return Double.POSITIVE_INFINITY;
                }

                {
                    String bestTreeString = bestTree.toString();
                    InputSource is = InputSource.openString(bestTreeString);
                    currentStepTree = new ReadTree(is);
                    is.close();
                }

                bestDist1 = bestDist2;
                bestDist2 = bestDist;
                if (bestDist1 <= bestDist2) {
                    return Double.POSITIVE_INFINITY;
                }

            } while (bestDist != 0);

            dist = (double) sprDist;
        } catch (TreeCmpException | TreeParseException | IOException ex) {
            Logger.getLogger(HeuristicBaseMetric.class.getName()).log(Level.SEVERE, null, ex);
        }

        return dist;
    }

    protected abstract TreeNeighborhoodUtils getTreeNeighborhoodUtils();
    protected abstract Metric getMetric();

}
