/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.topological.MatchingSplitMetric;
import treecmp.metrics.Metric;

/**
 *
 * @author Damian
 */
public class UsprHeuristicMSMetric extends HeuristicBaseMetric {

    protected UsprHeuristicMSMetric() {
        super(true);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){ return new USprUtils(); }

    @Override
    protected Metric getMetric(){
        return new MatchingSplitMetric();
    }
}

