/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.topological.MatchingClusterMetricO3;
import treecmp.metrics.Metric;

/**
 *
 * @author Damian
 */
public class SprHeuristicMCMetric extends SprHeuristicBaseMetric {

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){
        return new SprUtils();
    }

    @Override
    protected Metric getMetric(){
    return new MatchingClusterMetricO3();
 }
}
