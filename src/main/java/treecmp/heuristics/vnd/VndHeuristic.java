package treecmp.heuristics.vnd;

import pal.tree.Tree;
import pal.tree.SimpleTree;
import treecmp.common.AlignInfo;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.metrics.Metric;

import java.util.List;

public class VndHeuristic implements Metric {

    private final List<HeuristicBaseMetric> classicNeighborhoods;
    private final String metricName;

    public VndHeuristic(List<HeuristicBaseMetric> classicNeighborhoods, String metricName) {
        this.classicNeighborhoods = classicNeighborhoods;
        this.metricName = metricName;
    }

    public double getDistance(Tree tree1, Tree tree2) {
        Tree currentTree = new SimpleTree(tree1);
        if (currentTree instanceof SimpleTree) {
            ((SimpleTree) currentTree).createNodeList();
        }

        // Poprawka: Wykorzystujemy nową hermetyczną metodę z klasy bazowej
        double currentBestDist = classicNeighborhoods.get(0).evaluateInitialDistance(currentTree, tree2);

        double totalNniCost = 0.0;
        int k = 0;
        int failSafeCounter = 0;

        while (k < classicNeighborhoods.size() && currentBestDist > 0 && failSafeCounter < 5000) {
            double distAfterSearch = currentBestDist;
            Tree treeAfterSearch = currentTree;

            HeuristicBaseMetric currentHeuristic = classicNeighborhoods.get(k);

            distAfterSearch = currentHeuristic.performLocalDescent(currentTree, tree2);
            treeAfterSearch = currentHeuristic.getLastOptimumTree();

            totalNniCost += currentHeuristic.getAccumulatedNniCost();

            if (distAfterSearch < currentBestDist) {
                currentBestDist = distAfterSearch;
                currentTree = treeAfterSearch;
                k = 0;
            } else {
                k++;
            }
            failSafeCounter++;
        }

        return (currentBestDist == 0) ? totalNniCost : Double.POSITIVE_INFINITY;
    }

    @Override
    public double getDistance(Tree t1, Tree t2, int... indexes) { return getDistance(t1, t2); }

    @Override
    public String getName() { return "ClassicVND_" + metricName; }

    @Override public String getCommandLineName() { return "cvnd_" + metricName.toLowerCase(); }
    @Override public void setCommandLineName(String cln) {}
    @Override public void setName(String name) {}
    @Override public String getDescription() { return "Classic Variable Neighborhood Descent"; }
    @Override public void setDescription(String d) {}
    @Override public void initData() {}
    @Override public boolean isRooted() { return classicNeighborhoods.get(0).isRooted(); }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return false; }
    @Override public AlignInfo getAlignment() { return null; }
}