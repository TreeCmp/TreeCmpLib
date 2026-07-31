package treecmp.heuristics.vnd;

import pal.tree.Tree;
import treecmp.heuristics.HeuristicPathLogger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    public void onStep(String neighborhoodName, List<Tree> nniSteps, double newBestValue, Tree targetTree) {
        if (nniSteps == null || nniSteps.isEmpty()) {
            return;
        }

        // 1. Ruch bezpośredni / pojedynczy krok NNI (brak podkroków)
        if (nniSteps.size() == 1) {
            stepCounter++;
            HeuristicPathLogger.logStep(logFile, stepCounter, neighborhoodName, nniSteps.get(0), newBestValue);
            return;
        }

        // 2. Trajektoria makroruchu (wiele kroków NNI)
        for (int i = 0; i < nniSteps.size(); i++) {
            Tree currentStepTree = nniSteps.get(i);

            // Filtracja: pomijamy ewentualny duplikat drzewa startowego na indeksie 0,
            // jeśli nie wnosi on poprawy dystansu w stosunku do stanu przed ruchem
            if (i == 0 && nniSteps.size() > 1) {
                double dist = distanceEvaluator.applyAsDouble(currentStepTree, targetTree);
                if (dist >= newBestValue && nniSteps.size() > 2) {
                    continue;
                }
            }

            stepCounter++;
            String subStepName = neighborhoodName + " -> NNI_Substep_" + (i + 1);

            // Ostatni krok w trajektorii otrzymuje dokładną wartość newBestValue,
            // kroki pośrednie mają wyliczony rzeczywisty dystans do celu
            double stepDist = (i == nniSteps.size() - 1)
                    ? newBestValue
                    : distanceEvaluator.applyAsDouble(currentStepTree, targetTree);

            HeuristicPathLogger.logStep(logFile, stepCounter, subStepName, currentStepTree, stepDist);
        }
    }

    @Override
    public void onFinish(double finalDistance) {
        HeuristicPathLogger.finishLog(logFile, stepCounter, finalDistance);
    }
}