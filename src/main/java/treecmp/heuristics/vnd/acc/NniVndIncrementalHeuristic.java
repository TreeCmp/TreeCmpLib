package treecmp.heuristics.vnd.acc;

import pal.tree.Tree;
import pal.tree.SimpleTree;
import treecmp.common.AlignInfo;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.HeuristicPathLogger;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.vnd.DetailedTrajectoryVndLogger;
import treecmp.heuristics.vnd.NoOpVndLogger;
import treecmp.heuristics.vnd.VndStepListener;
import treecmp.heuristics.vnd.VndTimeProfiler;
import treecmp.metrics.Metric;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public class NniVndIncrementalHeuristic implements Metric {

    public static boolean ENABLE_LOGGING = true;
    private final List<IncrementalHeuristicBaseMetric> incrementalNeighborhoods;
    private final Metric classicFallbackTbr;
    private final String metricName;

    public NniVndIncrementalHeuristic(List<IncrementalHeuristicBaseMetric> incrementalNeighborhoods,
                                      Metric classicFallbackTbr,
                                      String metricName) {
        this.incrementalNeighborhoods = incrementalNeighborhoods;
        this.classicFallbackTbr = classicFallbackTbr;
        this.metricName = metricName;
    }

    public double getDistance(Tree tree1, Tree tree2) {
        Tree currentTree = new SimpleTree(tree1);
        if (currentTree instanceof SimpleTree) {
            ((SimpleTree) currentTree).createNodeList();
        }

        double initialValue = incrementalNeighborhoods.get(0).evaluateInitialDistance(currentTree, tree2);

        // --- 1. WYBÓR STRATEGII LOGOWANIA ---
        VndStepListener logger = ENABLE_LOGGING
                ? new DetailedTrajectoryVndLogger(
                "proof_pair_nni_vnd_inc",
                metricName,
                incrementalNeighborhoods.get(0)::evaluateInitialDistance
        )
                : new NoOpVndLogger();

        int stepCounter = 0;
        double currentBestValue = initialValue;
        Tree currentBestTree = currentTree;

        logger.onStart("VND Incremental (" + metricName + ")", currentBestTree, currentBestValue);

        double totalNniCost = 0.0;
        int k = 0;
        int maxNeighborhoods = incrementalNeighborhoods.size() + (classicFallbackTbr != null ? 1 : 0);
        int failSafeCounter = 0;

        while (k < maxNeighborhoods && currentBestValue > 0 && failSafeCounter < 5000) {
            double distAfterSearch = currentBestValue;

            Tree treeBeforeSearch = currentBestTree;
            Tree treeAfterSearch = currentBestTree;
            String neighborhoodName = "";

            List<Tree> trajectory = null;
            long stepStartTimeNs = System.nanoTime(); // START STOPER

            if (k < incrementalNeighborhoods.size()) {
                IncrementalHeuristicBaseMetric currentHeuristic = incrementalNeighborhoods.get(k);
                neighborhoodName = currentHeuristic.getName(); // np. "NNI_Incr", "SPR_Incr"

                // Oczyszczamy nazwę z szumu na potrzeby czytelnego logowania statystyk
                String baseName = "Unknown";
                if (neighborhoodName.toLowerCase().contains("nni")) baseName = "NNI";
                else if (neighborhoodName.toLowerCase().contains("ecr2")) baseName = "ECR2";
                else if (neighborhoodName.toLowerCase().contains("ecr3")) baseName = "ECR3";
                else if (neighborhoodName.toLowerCase().contains("spr")) baseName = "SPR";

                distAfterSearch = currentHeuristic.performLocalDescent(currentBestTree, tree2);
                treeAfterSearch = currentHeuristic.getLastOptimumTree();

                long timeSpentNs = System.nanoTime() - stepStartTimeNs; // STOP STOPER

                boolean success = (distAfterSearch < currentBestValue);
                VndTimeProfiler.INSTANCE.get().recordTime(baseName, success, timeSpentNs); // RAPORTOWANIE Z PĘTLI INC

                totalNniCost += currentHeuristic.getAccumulatedNniCost();
                trajectory = currentHeuristic.getLastOptimumTrajectory(treeBeforeSearch);
            } else {
                neighborhoodName = "Classic_TBR_Fallback";
                double tbrDist = 0;
                try {
                    tbrDist = classicFallbackTbr.getDistance(currentBestTree, tree2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }

                if (tbrDist != Double.POSITIVE_INFINITY && tbrDist < currentBestValue) {
                    distAfterSearch = 0;
                    totalNniCost += (tbrDist * 4.0);

                    trajectory = Collections.singletonList(currentBestTree);
                }
            }

            if (distAfterSearch < currentBestValue) {
                currentBestValue = distAfterSearch;
                currentBestTree = treeAfterSearch;

                // Bezpiecznik na wypadek pustej listy
                if (trajectory == null || trajectory.isEmpty()) {
                    trajectory = Collections.singletonList(currentBestTree);
                }

                // Przekazujemy pełną trajektorię do loggera
                logger.onStep(neighborhoodName, trajectory, currentBestValue, tree2);

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
    public String getName() { return "NNI_VND_" + metricName; }

    @Override public String getCommandLineName() { return "vnd"; }
    @Override public void setCommandLineName(String cln) {}
    @Override public void setName(String name) {}
    @Override public String getDescription() { return "NNI Distance Heuristic via Variable Neighborhood Descent"; }
    @Override public void setDescription(String d) {}
    @Override public void initData() {}
    @Override public boolean isRooted() { return incrementalNeighborhoods.get(0).isRooted(); }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return false; }
    @Override public AlignInfo getAlignment() { return null; }
}