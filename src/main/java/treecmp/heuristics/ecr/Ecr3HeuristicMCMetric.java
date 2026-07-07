package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.MatchingClusterMetric;

public class Ecr3HeuristicMCMetric extends HeuristicBaseMetric {

    public Ecr3HeuristicMCMetric() {
        super(true); // Drzewo ukorzenione
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SubtreeEcr3Utils(false); // false = topologia ukorzeniona
    }

    @Override
    protected Metric getMetric() {
        return new MatchingClusterMetric();
    }

    @Override
    public String getName() {
        return "3sECR_ClassicHeuristic_MC";
    }
}