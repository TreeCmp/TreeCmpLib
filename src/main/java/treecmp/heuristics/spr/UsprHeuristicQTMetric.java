/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.QuartetMetricLong;

/**
 *
 * @author Damian
 */
public class UsprHeuristicQTMetric extends HeuristicBaseMetric {

    public UsprHeuristicQTMetric() {
        super(true);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){ return new USprUtils(); }

    @Override
    protected Metric getMetric(){
        return new QuartetMetricLong();
    }
}

