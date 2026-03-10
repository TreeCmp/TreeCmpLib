/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.TripletMetric;

/**
 *
 * @author Damian
 */
public class SprHeuristicTTMetric extends HeuristicBaseMetric {

    protected SprHeuristicTTMetric() {
        super(true);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){
        return new SprUtils();
    }

    @Override
    protected Metric getMetric(){
    return new TripletMetric();
 }
}
