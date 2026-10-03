package treecmp.metrics;

import pal.tree.Tree;

public interface IncrementalMetric extends Metric {

    void initCalculationState(Tree baseTree, Tree targetTree);

    double getCurrentDistance();

    void commit();
}