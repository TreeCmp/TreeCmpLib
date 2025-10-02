/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.metrics.weighted;

/**
 *
 * @author Damian
 */

import distanceAlg1.Geodesic;
import distanceAlg1.PhyloTree;
import pal.tree.Tree;
import polyAlg.PolyMain;
import treecmp.common.NodeUtilsExt;

/**
 *
 * @author Damian
 */
public class GeoMetricWrapper {

    /**
     * Calculates the **geodesic distance** between two phylogenetic trees, {@code t1} and {@code t2},
     * using the polynomial-time algorithm (likely based on the BHV space geometry).
     *
     * <p>The method first converts the input trees from their generic {@code Tree} format into
     * {@code PhyloTree} objects (which are required by {@code PolyMain.getGeodesic}) and then computes the distance.
     *
     * @param t1 The first phylogenetic tree (in generic {@code Tree} format).
     * @param t2 The second phylogenetic tree (in generic {@code Tree} format).
     * @param rooted A boolean flag indicating whether the trees should be treated as rooted for the distance calculation.
     * @param logFileName The name of the file where verbose output or geodesic path information should be logged;
     * can be {@code null} if no logging is desired.
     * @return The computed geodesic distance between {@code t1} and {@code t2}.
     */
    public double getDistance(Tree t1, Tree t2, boolean rooted, String logFileName) {
        String tree1Newick = NodeUtilsExt.treeToSimpleString(t1, true);
        String tree2Newick = NodeUtilsExt.treeToSimpleString(t2, true);
        
        PhyloTree pt1 = new PhyloTree(tree1Newick, rooted);
        PhyloTree pt2 = new PhyloTree(tree2Newick, rooted);

        Geodesic geo = null;
        geo = PolyMain.getGeodesic(pt1, pt2, logFileName);
        double dist = geo.getDist();     
        return dist;
    }
}
