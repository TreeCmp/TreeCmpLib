/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFMetric;

/**
 *
 * @author Damian
 */
public class UsprHeuristicRFMetric extends HeuristicBaseMetric {

    public UsprHeuristicRFMetric() {
        super(true);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){ return new USprUtils(); }

    @Override
    protected Metric getMetric(){
        return new RFMetric();
    }
}

