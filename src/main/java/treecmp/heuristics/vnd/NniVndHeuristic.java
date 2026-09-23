package treecmp.heuristics.vnd;

import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.common.AlignInfo;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.metrics.Metric;

import java.util.Collections;
import java.util.List;

public class NniVndHeuristic implements Metric {

    public static boolean ENABLE_LOGGING = true;

    private final List<HeuristicBaseMetric> classicNeighborhoods;
    private final String metricName;

    public NniVndHeuristic(List<HeuristicBaseMetric> classicNeighborhoods, String metricName) {
        this.classicNeighborhoods = classicNeighborhoods;
        this.metricName = metricName;
    }

    public double getDistance(Tree tree1, Tree tree2) {
        Tree currentTree = new SimpleTree(tree1);
        if (currentTree instanceof SimpleTree) {
            ((SimpleTree) currentTree).createNodeList();
        }

        double initialValue = classicNeighborhoods.get(0).evaluateInitialDistance(currentTree, tree2);

        VndStepListener logger = ENABLE_LOGGING
                ? new DetailedTrajectoryVndLogger(
                "proof_pair_vnd_classic",
                metricName,
                classicNeighborhoods.get(0)::evaluateInitialDistance
        )
                : new NoOpVndLogger();

        logger.onStart("VND Classic (" + metricName + ")", currentTree, initialValue);

        double currentBestValue = initialValue;
        Tree currentBestTree = currentTree;
        double totalNniCost = 0.0;
        int k = 0;
        int failSafeCounter = 0;

        while (k < classicNeighborhoods.size() && currentBestValue > 0 && failSafeCounter < 5000) {
            HeuristicBaseMetric currentHeuristic = classicNeighborhoods.get(k);
            Tree treeBeforeSearch = currentBestTree;

            String neighborhoodName = currentHeuristic.getName();
            String baseName = "Unknown";
            if (neighborhoodName.toLowerCase().contains("nni")) baseName = "NNI";
            else if (neighborhoodName.toLowerCase().contains("ecr2")) baseName = "ECR2";
            else if (neighborhoodName.toLowerCase().contains("ecr3")) baseName = "ECR3";
            else if (neighborhoodName.toLowerCase().contains("spr")) baseName = "SPR";

            long stepStartTimeNs = System.nanoTime();
            double distAfterSearch = currentHeuristic.performLocalDescent(currentBestTree, tree2);
            long timeSpentNs = System.nanoTime() - stepStartTimeNs;

            boolean success = (distAfterSearch < currentBestValue);
            VndTimeProfiler.INSTANCE.get().recordTime(baseName, success, timeSpentNs);

            Tree treeAfterSearch = currentHeuristic.getLastOptimumTree();

            // KOSZT NALICZAMY WYŁĄCZNIE WTEDY, GDY RUCH POPRAWIŁ WYNIK
            if (distAfterSearch < currentBestValue) {
                currentBestValue = distAfterSearch;
                currentBestTree = treeAfterSearch;

                List<Tree> trajectory = currentHeuristic.getLastOptimumTrajectory(treeBeforeSearch);
                String name = currentHeuristic.getName();

                if (trajectory == null || trajectory.isEmpty()) {
                    trajectory = Collections.singletonList(currentBestTree);
                }

                int stepsBefore = (logger instanceof DetailedTrajectoryVndLogger)
                        ? ((DetailedTrajectoryVndLogger) logger).getStepCount() : 0;

                logger.onStep(name, trajectory, currentBestValue, tree2);

                int stepsAfter = (logger instanceof DetailedTrajectoryVndLogger)
                        ? ((DetailedTrajectoryVndLogger) logger).getStepCount() : 0;

                // Akumulujemy dokładnie tyle kroków 1-NNI, ile faktycznie zrzucono do certyfikatu
                if (logger instanceof DetailedTrajectoryVndLogger) {
                    totalNniCost += (stepsAfter - stepsBefore);
                } else {
                    totalNniCost += trajectory.size();
                }

                k = 0; // Reset VND
            } else {
                k++;
            }
            failSafeCounter++;
        }

        logger.onFinish(currentBestValue);

        return (currentBestValue == 0) ? totalNniCost : Double.POSITIVE_INFINITY;
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