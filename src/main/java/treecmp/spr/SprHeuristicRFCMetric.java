/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.spr;

import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFClusterMetric;

/**
 *
 * @author Damian
 */
public class SprHeuristicRFCMetric extends SprHeuristicBaseMetric{

 @Override
protected Metric getMetric(){
    return new RFClusterMetric();
 }
}
