package treecmp.heuristics.vnd;

import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.HeuristicPathLogger;
import treecmp.metrics.topological.RFMetric;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleBiFunction;

public class DetailedTrajectoryVndLogger implements VndStepListener {
    private final String logFile;
    private final ToDoubleBiFunction<Tree, Tree> distanceEvaluator;
    private int stepCounter = 0;
    private Tree lastLoggedTree;
    private static final RFMetric RF = new RFMetric();

    public DetailedTrajectoryVndLogger(String prefixName, String metricName,
                                       ToDoubleBiFunction<Tree, Tree> distanceEvaluator) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(dtf);
        this.logFile = "logs/" + prefixName + "_" + metricName + "_" + timestamp + ".txt";
        this.distanceEvaluator = distanceEvaluator;
    }

    @Override
    public void onStart(String testName, Tree startTree, double initialDistance) {
        this.stepCounter = 0;
        this.lastLoggedTree = cloneAndIndex(startTree);
        HeuristicPathLogger.startNewLog(logFile, testName, this.lastLoggedTree, initialDistance);
    }

    @Override
    public void onStep(String heuristicName, List<Tree> trajectory, double currentBestValue, Tree targetTree) {
        if (trajectory == null || trajectory.isEmpty()) {
            return;
        }

        List<Tree> validSubsteps = new ArrayList<>();
        for (Tree candidate : trajectory) {
            if (candidate == null) continue;
            Tree indexedCandidate = cloneAndIndex(candidate);

            // Weryfikacja różnicy w przestrzeni bezkorzennej (zgodnie z metodologią DendroPy)
            Tree uLast = TreeCmpUtils.unrootTreeIfNeeded(this.lastLoggedTree);
            Tree uCand = TreeCmpUtils.unrootTreeIfNeeded(indexedCandidate);
            double diff = RF.getDistance(uLast, uCand);

            // Filtrujemy identyczne topologie bezkorzenne (RF == 0)
            if (diff > 0.0) {
                validSubsteps.add(indexedCandidate);
                this.lastLoggedTree = indexedCandidate;
            }
        }

        if (validSubsteps.isEmpty()) {
            return;
        }

        int totalSubsteps = validSubsteps.size();
        for (int i = 0; i < totalSubsteps; i++) {
            Tree stepTree = validSubsteps.get(i);
            stepCounter++;

            String stepLabel = (totalSubsteps == 1)
                    ? heuristicName
                    : heuristicName + " -> NNI_Substep_" + (i + 1);

            double evaluatedDist = (i == totalSubsteps - 1)
                    ? currentBestValue
                    : safeEvaluate(heuristicName, stepTree, targetTree, currentBestValue);

            HeuristicPathLogger.logStep(logFile, stepCounter, stepLabel, stepTree, evaluatedDist);
        }
    }

    @Override
    public void onFinish(double finalDistance) {
        if (finalDistance > 0.0) {
            // Usuwamy niepełny log, aby weryfikator analizował tylko trajektorie ze zbieżnością do 0.0
            try {
                File f = new File(logFile);
                if (f.exists()) {
                    f.delete();
                }
            } catch (Exception ignored) {
            }
            return;
        }

        HeuristicPathLogger.finishLog(logFile, stepCounter, finalDistance);
    }

    private Tree cloneAndIndex(Tree t) {
        if (t == null) return null;
        Tree copy = new SimpleTree(t);
        if (copy instanceof SimpleTree) {
            ((SimpleTree) copy).createNodeList();
        }
        TreeUtils.computeParentPointers(copy.getRoot());
        return copy;
    }

    private double safeEvaluate(String heuristicName, Tree stepTree, Tree targetTree, double fallbackValue) {
        try {
            return distanceEvaluator.applyAsDouble(stepTree, targetTree);
        } catch (Exception e) {
            return fallbackValue;
        }
    }
}