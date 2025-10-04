/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.metrics.topological.MatchingPairUnrootedMetric;
import treecmp.metrics.Metric;

/**
 *
 * @author Damian
 */
public class UsprHeuristicMPUMetric extends UsprHeuristicBaseMetric {

    @Override
    protected Metric getMetric() { return new MatchingPairUnrootedMetric(); }
}


