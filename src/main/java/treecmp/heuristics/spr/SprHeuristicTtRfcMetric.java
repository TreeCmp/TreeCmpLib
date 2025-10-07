/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.TripletMetric;

/**
 *
 * @author Damian
 */
public class SprHeuristicTtRfcMetric extends SprHeuristicRfcBaseMetric{

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){ return new USprUtils(); }

    @Override
    protected Metric getMetric(){
    return new TripletMetric();
 }
}
