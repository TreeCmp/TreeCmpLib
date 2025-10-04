/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.metrics.Metric;
import treecmp.metrics.topological.MatchingPairMetric;


/**
 *
 * @author Damian
 */
public class SprHeuristicMpRfcMetric extends SprHeuristicRfcBaseMetric{

 @Override
 protected Metric getMetric(){
    return new MatchingPairMetric();
 }
}
