package treecmp.heuristics.ecr;

import pal.tree.Tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NniTrajectoryBuilder {

    /**
     * Buduje prawdziwą trajektorię elementarnych kroków 1-NNI
     * prowadzącą od drzewa startowego do docelowego drzewa ECR.
     *
     * @param startTree  Drzewo wyjściowe (przed ruchem)
     * @param targetTree Drzewo docelowe (wygrywający ruch ECR)
     * @param maxSteps   Maksymalna liczba kroków (2 dla 2sECR, 3 dla 3sECR)
     * @return Lista drzew pośrednich zakończona drzewem docelowym
     */
    public static List<Tree> buildTrueNniTrajectory(Tree startTree, Tree targetTree, int maxSteps) {
        if (startTree == null || targetTree == null) {
            return Collections.emptyList();
        }

        List<Tree> trajectory = new ArrayList<>();
        Tree currentTree = startTree;

        for (int step = 0; step < maxSteps; step++) {
            // 1. Wygeneruj wszystkie legalne, elementarne sąsiedztwa 1-NNI dla obecnego drzewa
            List<Tree> nniNeighbors = generate1NniNeighbors(currentTree);
            if (nniNeighbors == null || nniNeighbors.isEmpty()) {
                break;
            }

            Tree bestNeighbor = null;
            double minDistance = Double.MAX_VALUE;

            // 2. Wybierz sąsiada 1-NNI, który jest topologicznie najbliżej targetTree
            for (Tree neighbor : nniNeighbors) {
                double dist = calculateTopologicalDistance(neighbor, targetTree);
                if (dist < minDistance) {
                    minDistance = dist;
                    bestNeighbor = neighbor;
                }
            }

            // 3. Jeśli dotarliśmy do celu (dystans 0) lub brak dalszego postępu - kończymy pętlę
            if (bestNeighbor == null || minDistance == 0.0) {
                break;
            }

            trajectory.add(bestNeighbor);
            currentTree = bestNeighbor;
        }

        // 4. Na koniec zawsze upewniamy się, że ostatnim elementem trajektorii jest dokładne targetTree
        if (trajectory.isEmpty() || trajectory.get(trajectory.size() - 1) != targetTree) {
            trajectory.add(targetTree);
        }

        return trajectory;
    }

    /**
     * Generator sąsiedztwa 1-NNI.
     * Podepnij tutaj swoją standardową metodę generującą sąsiadów NNI z biblioteki TreeCmpLib
     * (np. z klasy NniUtils, NniNeighborhood lub generatora heurystyki NNI).
     */
    private static List<Tree> generate1NniNeighbors(Tree tree) {
        // PRZYKŁAD (dostosuj do wywołania w Twojej bibliotece):
        // return treecmp.heuristics.nni.NniNeighborhood.getNeighbors(tree);
        return new ArrayList<>(); // <-- ZASTĄP NATYWNYM GENERATOREM 1-NNI Z TREECMPLIB
    }

    /**
     * Szybka metryka odległości topologicznej do sterowania krokami NNI.
     * Najlepiej użyć dystansu Robinson-Foulds (RF) lub RFCluster między drzewami.
     */
    private static double calculateTopologicalDistance(Tree t1, Tree t2) {
        // PRZYKŁAD (dostosuj do wywołania metryki w Twoim projekcie):
        // return treecmp.metric.RFClusterMetric.getDistance(t1, t2);
        return 0.0; // <-- ZASTĄP NATYWNYM LICZENIEM DYSTANSU (np. RFCluster / RF)
    }
}