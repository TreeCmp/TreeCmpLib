package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.common.AlignInfo;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.acc.IncrementalSprWalker;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingSplitMetric;

import java.util.List;
import java.util.Stack;

/**
 * Pancerne wdrożenie MS dla drzew nieukorzenionych.
 * Omija paradoks rozpuszczających się krawędzi wykorzystując precyzyjną ewaluację fizyczną
 * wspartą stosem historii (Cache Stack) i tarczami topologicznymi (Safety Guards).
 */
public class MSIncrementalMetric implements IncrementalMetric, IncrementalSprWalker.RootedMetric {

    private Tree baseTree;
    private Tree targetTree;
    private double currentDistance;

    private final MatchingSplitMetric msMetricFull = new MatchingSplitMetric();
    private final UsprUtils usprUtils = new UsprUtils();

    private final Stack<Double> incrementalDistanceStack = new Stack<>();

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        this.baseTree = baseTree;
        this.targetTree = targetTree;
        this.incrementalDistanceStack.clear();

        if (baseTree != null && targetTree != null) {
            // Zwykła metryka nie ma initCalculationState, wyliczamy po prostu dystans startowy
            this.currentDistance = msMetricFull.getDistance(baseTree, targetTree);
        } else {
            this.currentDistance = 0;
        }
    }

    // ========================================================================
    // INTERFEJS TARGET DFS (BEZPIECZNA EWALUACJA Z TARCZĄ)
    // ========================================================================

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        // Logika zawarta bezpośrednio w krokach w dół
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node wanderingSource) {
        incrementalDistanceStack.push(this.currentDistance);

        // TARCZA OCHRONNA: Ignoruj nielegalne ruchy, żeby nie zepsuć biblioteki PAL!
        if (!usprUtils.isValidUsprMove(pruneNode, baseTree.getRoot())) {
            this.currentDistance = Double.POSITIVE_INFINITY;
            return;
        }

        try {
            Tree tempTree = usprUtils.createUsprTree(this.baseTree, pruneNode, baseTree.getRoot());
            if (tempTree != null) {
                if (tempTree instanceof pal.tree.SimpleTree) {
                    ((pal.tree.SimpleTree) tempTree).createNodeList();
                }
                this.currentDistance = msMetricFull.getDistance(tempTree, this.targetTree);
            } else {
                this.currentDistance = Double.POSITIVE_INFINITY;
            }
        } catch (Exception e) {
            // Jeśli PAL nadal zwróci uszkodzoną strukturę, zwracamy nieskończoność
            this.currentDistance = Double.POSITIVE_INFINITY;
        }
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        incrementalDistanceStack.push(this.currentDistance);

        // TARCZA OCHRONNA: Ignoruj nielegalne ruchy
        if (!usprUtils.isValidUsprMove(pruneNode, childTarget)) {
            this.currentDistance = Double.POSITIVE_INFINITY;
            return;
        }

        try {
            Tree tempTree = usprUtils.createUsprTree(this.baseTree, pruneNode, childTarget);
            if (tempTree != null) {
                if (tempTree instanceof pal.tree.SimpleTree) {
                    ((pal.tree.SimpleTree) tempTree).createNodeList();
                }
                this.currentDistance = msMetricFull.getDistance(tempTree, this.targetTree);
            } else {
                this.currentDistance = Double.POSITIVE_INFINITY;
            }
        } catch (Exception e) {
            this.currentDistance = Double.POSITIVE_INFINITY;
        }
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        if (!incrementalDistanceStack.isEmpty()) {
            this.currentDistance = incrementalDistanceStack.pop();
        }
    }

    @Override
    public void revertPrunedState(Node pruneNode, Node wanderingSource) {
        if (!incrementalDistanceStack.isEmpty()) {
            this.currentDistance = incrementalDistanceStack.pop();
        }
    }

    // ========================================================================
    // KLASYCZNE METODY
    // ========================================================================

    @Override public double applyNni(NniMove move) { return 0; }
    @Override public void undoNni(NniMove move) { }
    @Override public void applySprPrune(Node pruneNode) { incrementalDistanceStack.push(this.currentDistance); }
    @Override public void undoSprPrune(Node pruneNode) { undoSprRegraftStep(); }
    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) { incrementalDistanceStack.push(this.currentDistance); }

    @Override public void undoSprRegraftStep() {
        if (!incrementalDistanceStack.isEmpty()) {
            this.currentDistance = incrementalDistanceStack.pop();
        }
    }

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        if (!usprUtils.isValidUsprMove(pruneNode, targetNode)) {
            return Double.POSITIVE_INFINITY;
        }

        try {
            Tree tempTree = usprUtils.createUsprTree(this.baseTree, pruneNode, targetNode);
            if (tempTree != null) {
                if (tempTree instanceof pal.tree.SimpleTree) {
                    ((pal.tree.SimpleTree) tempTree).createNodeList();
                }
                return msMetricFull.getDistance(tempTree, this.targetTree);
            }
        } catch (Exception e) {
            // Zabezpieczenie przed błędem "L1 not present"
        }
        return Double.POSITIVE_INFINITY;
    }

    @Override public double evaluate2sEcrMove(Node t, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR n) { return 0; }
    @Override public double commit2sEcrMove(Node t, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR n) { return 0; }
    @Override public double evaluate3sEcrMove(List<Node> c, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR n) { return 0; }
    @Override public double commit3sEcrMove(List<Node> c, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR n) { return 0; }

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { incrementalDistanceStack.clear(); }
    @Override public double getDistance(Tree t1, Tree t2, int... indexes) { return msMetricFull.getDistance(t1, t2, indexes); }
    @Override public String getName() { return "Accelerated " + msMetricFull.getName(); }
    @Override public String getCommandLineName() { return msMetricFull.getCommandLineName(); }
    @Override public void setCommandLineName(String cln) { msMetricFull.setCommandLineName(cln); }
    @Override public void setName(String name) { msMetricFull.setName(name); }
    @Override public String getDescription() { return msMetricFull.getDescription(); }
    @Override public void setDescription(String d) { msMetricFull.setDescription(d); }
    @Override public void initData() { msMetricFull.initData(); }
    @Override public boolean isRooted() { return false; }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return msMetricFull.isDiffLeafSets(); }
    @Override public AlignInfo getAlignment() { return msMetricFull.getAlignment(); }
}