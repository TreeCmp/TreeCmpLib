package treecmp.heuristics.vnd.acc;

import pal.tree.Tree;
import pal.tree.SimpleTree;
import treecmp.common.AlignInfo;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.HeuristicPathLogger;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.metrics.Metric;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

        // --- WPIĘCIE LOGERA: Dynamiczna nazwa pliku z datą, czasem i metryką ---
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(dtf);
        String logFile = "logs/proof_pair_nni_vnd_inc_" + metricName + "_" + timestamp + ".txt";

        int stepCounter = 0;
        double currentBestValue = initialValue;
        Tree currentBestTree = currentTree;

        HeuristicPathLogger.startNewLog(logFile, "VND Incremental (" + metricName + ")", currentBestTree, currentBestValue);

        double totalNniCost = 0.0;
        int k = 0;
        int maxNeighborhoods = incrementalNeighborhoods.size() + (classicFallbackTbr != null ? 1 : 0);
        int failSafeCounter = 0;

        while (k < maxNeighborhoods && currentBestValue > 0 && failSafeCounter < 5000) {
            double distAfterSearch = currentBestValue;

            Tree treeBeforeSearch = currentBestTree;
            Tree treeAfterSearch = currentBestTree;
            String neighborhoodName = "";

            if (k < incrementalNeighborhoods.size()) {
                IncrementalHeuristicBaseMetric currentHeuristic = incrementalNeighborhoods.get(k);
                neighborhoodName = currentHeuristic.getName();

                distAfterSearch = currentHeuristic.performLocalDescent(currentBestTree, tree2);
                treeAfterSearch = currentHeuristic.getLastOptimumTree();

                totalNniCost += currentHeuristic.getAccumulatedNniCost();
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
                }
            }

            if (distAfterSearch < currentBestValue) {
                currentBestValue = distAfterSearch;
                currentBestTree = treeAfterSearch;

                // --- INTEGRACJA LOGOWANIA I DEKOMPOZYCJI ---
                if (ENABLE_LOGGING) {
                    if (neighborhoodName.contains("ECR")) {
                        List<Tree> nniSteps = SubtreeEcr3Utils.buildTrajectoryTrees(treeBeforeSearch, currentBestTree);

                        for (int i = 0; i < nniSteps.size(); i++) {
                            stepCounter++;
                            String subStepName = (nniSteps.size() > 1)
                                    ? neighborhoodName + " -> NNI_Substep_" + (i + 1)
                                    : neighborhoodName; // Jeśli jest tylko 1 krok, nie dodawaj dopisku "NNI_Substep_1"
                            // Dla ostatniego drzewa w trajektorii bierzemy gotowe currentBestValue,
                            // dla drzew pośrednich wyliczamy rzeczywisty dystans:
                            double stepDist = (i == nniSteps.size() - 1)
                                    ? currentBestValue
                                    : this.getDistance(nniSteps.get(i), tree2);

                            HeuristicPathLogger.logStep(logFile, stepCounter, subStepName, nniSteps.get(i), stepDist);
                        }
                    } else {
                        // Dla standardowych ruchów (np. NNI, SPR, Classic_TBR_Fallback) logujemy bezpośrednio jeden krok
                        stepCounter++;
                        HeuristicPathLogger.logStep(logFile, stepCounter, neighborhoodName, currentBestTree, currentBestValue);
                    }
                }

                k = 0; // Reset VND
            } else {
                k++;
            }
            failSafeCounter++;
        }

        // --- WPIĘCIE LOGERA: Zakończenie i podsumowanie ścieżki ---
        HeuristicPathLogger.finishLog(logFile, stepCounter, currentBestValue);

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