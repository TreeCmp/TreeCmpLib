/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.metrics.Metric;
import treecmp.metrics.topological.QuartetMetricLong;

/**
 *
 * @author Damian
 */
public class UsprHeuristicQTMetric extends UsprHeuristicBaseMetric {

    @Override
    protected Metric getMetric(){
        return new QuartetMetricLong();
    }
}

