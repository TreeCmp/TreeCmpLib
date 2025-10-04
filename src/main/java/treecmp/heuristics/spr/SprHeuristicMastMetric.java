/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.metrics.Metric;
import treecmp.metrics.topological.RMASTMetric;

/**
 *
 * @author Damian
 */
public class SprHeuristicMastMetric extends SprHeuristicBaseMetric{

 @Override
protected Metric getMetric(){
    return new RMASTMetric();
 }
}
