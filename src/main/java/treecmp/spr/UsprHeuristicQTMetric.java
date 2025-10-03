/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.spr;

import treecmp.metrics.Metric;
import treecmp.metrics.topological.QuartetMetricLong;
import treecmp.metrics.topological.RFMetric;

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

