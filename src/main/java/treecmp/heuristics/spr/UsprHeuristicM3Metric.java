/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.metrics.topological.MatchingTripletMetric;
import treecmp.metrics.Metric;

/**
 *
 * @author Damian
 */
public class UsprHeuristicM3Metric extends HeuristicBaseMetric {

    public UsprHeuristicM3Metric() {
        super(false);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){ return new UsprUtils(); }

    @Override
    protected Metric getMetric(){
        return new MatchingTripletMetric();
    }
}

