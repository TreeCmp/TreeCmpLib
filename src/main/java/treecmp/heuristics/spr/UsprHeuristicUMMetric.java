/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.metrics.Metric;
import treecmp.metrics.topological.UMASTMetric;

/**
 *
 * @author Damian
 */
public class UsprHeuristicUMMetric extends UsprHeuristicBaseMetric {

    @Override
    protected Metric getMetric(){
        return new UMASTMetric();
    }
}

