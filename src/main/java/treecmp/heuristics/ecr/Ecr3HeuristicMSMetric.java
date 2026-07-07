package treecmp.heuristics.ecr;

import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.MatchingSplitMetric;

public class Ecr3HeuristicMSMetric extends HeuristicBaseMetric {

    public Ecr3HeuristicMSMetric() {
        super(false);
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new SubtreeEcr3Utils(true);
    }

    @Override
    protected Metric getMetric() {
        return new MatchingSplitMetric();
    }

    @Override
    public String getName() {
        return "3sECR_ClassicHeuristic_MS";
    }
}