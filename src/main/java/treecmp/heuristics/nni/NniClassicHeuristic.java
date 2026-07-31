package treecmp.heuristics.nni;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.metrics.Metric;

import java.util.List;

public class NniClassicHeuristic extends HeuristicBaseMetric {

    private final Metric metric;
    private final Metric primaryMetric;
    private final boolean isRooted;
    private final String metricShortName;

    public NniClassicHeuristic(Metric metric, boolean isRooted, String metricShortName) {
        this(metric, null, isRooted, metricShortName);
    }

    public NniClassicHeuristic(Metric metric, Metric primaryMetric, boolean isRooted, String metricShortName) {
        super(isRooted);
        this.metric = metric;
        this.primaryMetric = primaryMetric;
        this.isRooted = isRooted;
        this.metricShortName = metricShortName;
    }

    @Override
    protected TreeNeighborhoodUtils getTreeNeighborhoodUtils() {
        return new NniUtils(!isRooted);
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
        return "NNI_ClassicHeuristic_" + metricShortName;
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