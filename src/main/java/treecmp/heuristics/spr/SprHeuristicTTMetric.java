/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.metrics.Metric;
import treecmp.metrics.topological.TripletMetric;

/**
 *
 * @author Damian
 */
public class SprHeuristicTTMetric extends SprHeuristicBaseMetric{

 @Override
protected Metric getMetric(){
    return new TripletMetric();
 }
}
