/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package treecmp.heuristics.spr;

import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.MatchingPairMetric;


/**
 *
 * @author Damian
 */
public class SprHeuristicMPMetric extends HeuristicBaseMetric {

    protected SprHeuristicMPMetric() {
        super(true);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils(){
        return new SprUtils();
    }

    @Override
    protected Metric getMetric(){
    return new MatchingPairMetric();
 }
}
