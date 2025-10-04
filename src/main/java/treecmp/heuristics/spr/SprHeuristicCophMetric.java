/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.metrics.topological.CopheneticL2Metric;
import treecmp.metrics.Metric;

/**
 *
 * @author Damian
 */
public class SprHeuristicCophMetric extends SprHeuristicBaseMetric{

 @Override
protected Metric getMetric(){
    return new CopheneticL2Metric();
 }
}
