package treecmp.heuristics.vnd;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.HeuristicPathLogger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleBiFunction;

public class DetailedTrajectoryVndLogger implements VndStepListener {
    private final String logFile;
    private final ToDoubleBiFunction<Tree, Tree> distanceEvaluator;
    private int stepCounter = 0;

    public DetailedTrajectoryVndLogger(String prefixName, String metricName,
                                       ToDoubleBiFunction<Tree, Tree> distanceEvaluator) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(dtf);
        this.logFile = "logs/" + prefixName + "_" + metricName + "_" + timestamp + ".txt";
        this.distanceEvaluator = distanceEvaluator;
    }

    @Override
    public void onStart(String testName, Tree startTree, double initialDistance) {
        HeuristicPathLogger.startNewLog(logFile, testName, startTree, initialDistance);
    }

    @Override
    public void onStep(String heuristicName, List<Tree> trajectory, double currentBestValue, Tree targetTree) {
        if (trajectory == null || trajectory.isEmpty()) {
            return;
        }

        // 1. Zabezpieczenie: jeśli to ruch z uSPR lub SPR, logujemy bezpośrednio
        // sprawne drzewo wynikowe na końcu trajektorii, pomijając rozrywane stany przejściowe!
        boolean isSprOrUspr = heuristicName.toLowerCase().contains("spr");
        if (isSprOrUspr || trajectory.size() == 1) {
            Tree finalTree = trajectory.get(trajectory.size() - 1);
            if (finalTree instanceof pal.tree.SimpleTree) {
                ((pal.tree.SimpleTree) finalTree).createNodeList();
            }
            pal.tree.TreeUtils.computeParentPointers(finalTree.getRoot());

            stepCounter++;
            HeuristicPathLogger.logStep(logFile, stepCounter, heuristicName, finalTree, currentBestValue);
            return;
        }

        // 2. Trajektorie dla bezpiecznych heurystyk (NNI, ECR - gdzie graf ma zawsze 100% liści)
        for (Tree t : trajectory) {
            if (t != null) {
                if (t instanceof pal.tree.SimpleTree) {
                    ((pal.tree.SimpleTree) t).createNodeList();
                }
                pal.tree.TreeUtils.computeParentPointers(t.getRoot());
            }
        }

        for (int i = 0; i < trajectory.size(); i++) {
            Tree currentStepTree = trajectory.get(i);

            if (i == 0 && trajectory.size() > 1) {
                double dist = safeEvaluate(heuristicName, currentStepTree, targetTree, currentBestValue);
                if (dist >= currentBestValue && trajectory.size() > 2) {
                    continue;
                }
            }

            stepCounter++;
            String subStepName = heuristicName + " -> NNI_Substep_" + (i + 1);

            double stepDist = (i == trajectory.size() - 1)
                    ? currentBestValue
                    : safeEvaluate(heuristicName, currentStepTree, targetTree, currentBestValue);

            HeuristicPathLogger.logStep(logFile, stepCounter, subStepName, currentStepTree, stepDist);
        }
    }

    @Override
    public void onFinish(double finalDistance) {
        HeuristicPathLogger.finishLog(logFile, stepCounter, finalDistance);
    }

    // =========================================================================
    // DIAGNOSTYCZNY EWALUATOR (WYCHWYTUJE WINNĄ HEURYSTYKĘ I BRAKUJĄCE LIŚCIE)
    // =========================================================================
    private double safeEvaluate(String heuristicName, Tree stepTree, Tree targetTree, double fallbackValue) {
        try {
            return distanceEvaluator.applyAsDouble(stepTree, targetTree);
        } catch (Exception e) {
            System.err.println("\n=================== [DIAGNOZA VND - WYKRYTO USZKODZONE DRZEWO] ===================");
            System.err.println("1. WINNA HEURYSTYKA : " + heuristicName);
            System.err.println("2. TYP WYJĄTKU      : " + e.getClass().getSimpleName() + " - " + e.getMessage());
            System.err.println("3. LICZBA LIŚCI     : Badane = " + stepTree.getExternalNodeCount() + " | Cel = " + targetTree.getExternalNodeCount());

            List<String> stepLeaves = getLeafNames(stepTree);
            List<String> targetLeaves = getLeafNames(targetTree);

            List<String> missingInStep = new ArrayList<>(targetLeaves);
            missingInStep.removeAll(stepLeaves);

            System.err.println("4. BRAKUJĄCE LIŚCIE : " + (missingInStep.isEmpty() ? "Brak (nazwy się zgadzają)" : missingInStep));
            System.err.println("5. STRUKTURA NEWICK : " + stepTree.toString());
            System.err.println("==================================================================================\n");

            return fallbackValue;
        }
    }

    private List<String> getLeafNames(Tree t) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < t.getExternalNodeCount(); i++) {
            Node n = t.getExternalNode(i);
            if (n != null && n.getIdentifier() != null) {
                names.add(n.getIdentifier().getName());
            } else {
                names.add("NULL_LEAF_" + i);
            }
        }
        return names;
    }
}