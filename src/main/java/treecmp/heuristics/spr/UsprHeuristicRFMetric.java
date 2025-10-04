/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFMetric;

/**
 *
 * @author Damian
 */
public class UsprHeuristicRFMetric extends UsprHeuristicBaseMetric {

    @Override
    protected Metric getMetric(){
        return new RFMetric();
    }
}

