package treecmp.heuristics.spr;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

import java.util.List;

public class UsprHeuristicMetric extends HeuristicBaseMetric {

    private final Metric metric;
    private final Metric primaryMetric;
    private final String metricShortName;

    public UsprHeuristicMetric(Metric metric, String metricShortName) {
        this(metric, null, metricShortName);
    }

    public UsprHeuristicMetric(Metric metric, Metric primaryMetric, String metricShortName) {
        super(false); // Drzewa nieukorzenione
        this.metric = metric;
        this.primaryMetric = primaryMetric;
        this.metricShortName = metricShortName;
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new UsprUtils();
    }

    @Override
    protected Metric getMetric() {
        return this.metric;
    }

    @Override
    protected Metric getPrimaryMetric() {
        return this.primaryMetric != null ? this.primaryMetric : super.getPrimaryMetric();
    }

    @Override
    public String getName() {
        if (this.primaryMetric != null) {
            return "uSPR_ClassicHeuristic_" + primaryMetric.getCommandLineName() + "_" + metricShortName;
        }
        return "uSPR_ClassicHeuristic_" + metricShortName;
    }

    @Override
    public double performLocalDescent(Tree startTree, Tree targetTree) {
        double dist = super.performLocalDescent(startTree, targetTree);
        sanitizeTree(super.getLastOptimumTree());
        return dist;
    }

    @Override
    public Tree getLastOptimumTree() {
        return sanitizeTree(super.getLastOptimumTree());
    }

    @Override
    public List<Tree> getLastOptimumTrajectory(Tree startTree) {
        List<Tree> trajectory = super.getLastOptimumTrajectory(startTree);
        if (trajectory != null) {
            for (Tree t : trajectory) {
                sanitizeTree(t);
            }
        }
        return trajectory;
    }

    private Tree sanitizeTree(Tree t) {
        if (t == null) return null;
        if (!isRooted()) {
            TreeCmpUtils.unrootTreeIfNeeded(t);
        }
        if (t instanceof SimpleTree) {
            ((SimpleTree) t).createNodeList();
        }
        TreeUtils.computeParentPointers(t.getRoot());
        return t;
    }
}