package treecmp.heuristics.vnd;

import pal.tree.Tree;
import java.util.List;

public class NoOpVndLogger implements VndStepListener {
    @Override public void onStart(String testName, Tree startTree, double initialDistance) {}
    @Override public void onStep(String neighborhoodName, List<Tree> nniSteps, double newBestValue, Tree targetTree) {}
    @Override public void onFinish(double finalDistance) {}
}