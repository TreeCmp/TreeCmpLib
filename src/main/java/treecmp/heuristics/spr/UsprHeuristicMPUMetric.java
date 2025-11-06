/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.topological.MatchingPairUnrootedMetric;
import treecmp.metrics.Metric;

/**
 *
 * @author Damian
 */
public class UsprHeuristicMPUMetric extends HeuristicBaseMetric {

    public UsprHeuristicMPUMetric() {
        super(true);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){ return new USprUtils(); }

    @Override
    protected Metric getMetric() { return new MatchingPairUnrootedMetric(); }
}


