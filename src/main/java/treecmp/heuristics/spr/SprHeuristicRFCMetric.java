/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFClusterMetric;

/**
 *
 * @author Damian
 */
public class SprHeuristicRFCMetric extends HeuristicBaseMetric {

    public SprHeuristicRFCMetric() {
        super(true);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){
        return new SprUtils();
    }

    @Override
    protected Metric getMetric(){
        return new RFClusterMetric();
     }
}
