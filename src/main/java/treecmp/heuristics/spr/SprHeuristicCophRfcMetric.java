/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.topological.CopheneticL2Metric;
import treecmp.metrics.Metric;


/**
 *
 * @author Damian
 */
public class SprHeuristicCophRfcMetric extends SprHeuristicRfcBaseMetric {

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){ return new UsprUtils(); }

    @Override
    protected Metric getMetric(){
    return new CopheneticL2Metric();
 }
}
