package treecmp.heuristics.vnd;

import pal.tree.Tree;
import pal.tree.SimpleTree;
import treecmp.common.AlignInfo;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.HeuristicPathLogger;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.metrics.Metric;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class NniVndHeuristic implements Metric {

    // GLOBALNY PRZEŁĄCZNIK LOGOWANIA (Zmień na false przed testami wydajnościowymi!)
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

        String logFile = null;
        if (ENABLE_LOGGING) {
            // --- WPIĘCIE LOGERA: Dynamiczna nazwa pliku z datą, czasem i metryką ---
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
            String timestamp = LocalDateTime.now().format(dtf);
            logFile = "logs/proof_pair_vnd_classic_" + metricName + "_" + timestamp + ".txt";
            HeuristicPathLogger.startNewLog(logFile, "VND Classic (" + metricName + ")", currentTree, initialValue);
        }

        int stepCounter = 0;
        double currentBestValue = initialValue;
        Tree currentBestTree = currentTree;

        double totalNniCost = 0.0;
        int k = 0;
        int failSafeCounter = 0;

        while (k < classicNeighborhoods.size() && currentBestValue > 0 && failSafeCounter < 5000) {
            HeuristicBaseMetric currentHeuristic = classicNeighborhoods.get(k);

            // ZAPAMIĘTUJEMY DRZEWO PRZED RUCHEM
            Tree treeBeforeSearch = currentBestTree;

            // Wykonujemy zejście w lokalnym otoczeniu (np. NNI, ECR lub SPR)
            double distAfterSearch = currentHeuristic.performLocalDescent(currentBestTree, tree2);
            Tree treeAfterSearch = currentHeuristic.getLastOptimumTree();

            totalNniCost += currentHeuristic.getAccumulatedNniCost();

            if (distAfterSearch < currentBestValue) {
                currentBestValue = distAfterSearch;
                currentBestTree = treeAfterSearch;

                // --- INTEGRACJA LOGOWANIA I DEKOMPOZYCJI ---
                if (ENABLE_LOGGING) {
                    String neighborhoodName = currentHeuristic.getName();

                    if (neighborhoodName.contains("ECR")) {
                        List<Tree> nniSteps = SubtreeEcr3Utils.buildTrajectoryTrees(treeBeforeSearch, currentBestTree);

                        for (int i = 0; i < nniSteps.size(); i++) {
                            stepCounter++;
                            String subStepName = neighborhoodName + " -> NNI_Substep_" + (i + 1);
                            HeuristicPathLogger.logStep(logFile, stepCounter, subStepName, nniSteps.get(i), currentBestValue);
                        }
                    } else {
                        // Dla standardowych ruchów (np. NNI, SPR) logujemy bezpośrednio jeden krok
                        stepCounter++;
                        HeuristicPathLogger.logStep(logFile, stepCounter, neighborhoodName, currentBestTree, currentBestValue);
                    }
                }

                k = 0; // Zgodnie z VND - po poprawie wracamy do pierwszego otoczenia
            } else {
                k++;
            }
            failSafeCounter++;
        }

        // --- WPIĘCIE LOGERA: Zakończenie i podsumowanie ścieżki ---
        if (ENABLE_LOGGING) {
            HeuristicPathLogger.finishLog(logFile, stepCounter, currentBestValue);
        }

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