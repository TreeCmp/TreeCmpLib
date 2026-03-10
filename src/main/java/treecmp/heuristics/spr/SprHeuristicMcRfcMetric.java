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
public class SprHeuristicMcRfcMetric extends SprHeuristicRfcBaseMetric {

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){ return new USprUtils(); }

    @Override
    protected Metric getMetric(){
    return new MatchingClusterMetricO3();
 }
}
