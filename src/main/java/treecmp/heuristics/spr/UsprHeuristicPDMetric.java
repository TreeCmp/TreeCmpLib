/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.NodalL2Metric;

/**
 *
 * @author Damian
 */
public class UsprHeuristicPDMetric extends HeuristicBaseMetric {

    protected UsprHeuristicPDMetric() {
        super(true);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){ return new USprUtils(); }

    @Override
    protected Metric getMetric() { return new NodalL2Metric(); }
}

