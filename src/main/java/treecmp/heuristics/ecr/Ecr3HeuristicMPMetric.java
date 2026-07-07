package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.MatchingPairMetric;

public class Ecr3HeuristicMPMetric extends HeuristicBaseMetric {

    public Ecr3HeuristicMPMetric() {
        super(true); // Drzewo ukorzenione
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SubtreeEcr3Utils(false); // topologia ukorzeniona
    }

    @Override
    protected Metric getMetric() {
        return new MatchingPairMetric();
    }

    @Override
    public String getName() {
        return "3sECR_ClassicHeuristic_MP";
    }
}