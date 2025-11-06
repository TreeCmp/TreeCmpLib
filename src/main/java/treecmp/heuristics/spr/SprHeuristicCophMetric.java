/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.topological.CopheneticL2Metric;
import treecmp.metrics.Metric;

/**
 *
 * @author Damian
 */
public class SprHeuristicCophMetric extends HeuristicBaseMetric {

    protected SprHeuristicCophMetric() {
        super(true);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){
        return new SprUtils();
    }


    @Override
    protected Metric getMetric(){
    return new CopheneticL2Metric();
 }
}
