/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.metrics.Metric;
import treecmp.metrics.topological.NodalL2Metric;

/**
 *
 * @author Damian
 */
public class UsprHeuristicPDMetric extends UsprHeuristicBaseMetric {

    @Override
    protected Metric getMetric() { return new NodalL2Metric(); }
}

