package treecmp.heuristics.vnd;

import pal.tree.Tree;
import java.util.List;

public interface VndStepListener {
    void onStart(String testName, Tree startTree, double initialDistance);

    // Przekazujemy gotową listę drzew pośrednich oraz docelowe drzewo tree2
    void onStep(String neighborhoodName, List<Tree> nniSteps, double newBestValue, Tree targetTree);

    void onFinish(double finalDistance);
}