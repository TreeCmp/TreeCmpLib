/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.metrics.topological.MatchingClusterMetricO3;
import treecmp.metrics.Metric;

/**
 *
 * @author Damian
 */
public class SprHeuristicMcRfcMetric extends SprHeuristicRfcBaseMetric{

 @Override
protected Metric getMetric(){
    return new MatchingClusterMetricO3();
 }
}
