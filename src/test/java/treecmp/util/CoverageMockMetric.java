package treecmp.util;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.AlignInfo;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeHolder;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.TreeUnrootedHolder;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalMetric;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalMetric;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.nni.acc.NniIncrementalMetric;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.IncrementalMetric;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CoverageMockMetric implements IncrementalMetric,
        NniIncrementalMetric,
        Ecr2IncrementalMetric,
        Ecr3IncrementalMetric {

    private final Set<TreeHolder> visitedTopologies = new HashSet<>();
    private int evaluationCount = 0;
    private IdGroup idGroup;
    private Tree baseTree;

    // Dodajemy instancję SprUtils do generowania fizycznych drzew w Mocku
    private final SprUtils mockSprUtils = new SprUtils();
    private final UsprUtils mockUsprUtils = new UsprUtils();

    // Flaga decydująca o wyborze Holdera (Rooted vs Unrooted)
    private final boolean isRooted;

    public CoverageMockMetric(boolean isRooted) {
        this.isRooted = isRooted;
    }

    public Set<TreeHolder> getVisitedTopologies() { return visitedTopologies; }
    public int getEvaluationCount() { return evaluationCount; }

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        this.baseTree = baseTree;
        this.idGroup = TreeUtils.getLeafIdGroup(baseTree);
    }

    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        evaluationCount++;

        // Zależnie od trybu pakujemy w odpowiedniego Holdera za pomocą odpowiedniego Utils
        if (isRooted) {
            Tree physicalNeighborTree = mockSprUtils.createSprTree(baseTree, pruneNode, targetNode);
            visitedTopologies.add(new TreeRootedHolder(physicalNeighborTree, idGroup));
        } else {
            Tree physicalNeighborTree = mockUsprUtils.createUsprTree(baseTree, pruneNode, targetNode);
            visitedTopologies.add(new TreeUnrootedHolder(physicalNeighborTree, idGroup));
        }

        return 1.0;
    }

    public void applySprPrune(Node pruneNode) { }
    public void undoSprPrune(Node pruneNode) { }
    public void applySprRegraftStep(Node pruneNode, Node currentNode) { }
    public void undoSprRegraftStep() { }

    @Override
    public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR newTopology) {
        return 0;
    }

    @Override
    public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR newTopology) {
        return 0;
    }

    @Override
    public double evaluate3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR newTopology) {
        return 0;
    }

    @Override
    public double commit3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR newTopology) {
        return 0;
    }

    @Override
    public double applyNni(NniMove move) {
        return 1.0;
    }

    @Override
    public void undoNni(NniMove move) { }

    @Override
    public double getCurrentDistance() {
        return 1.0;
    }

    @Override
    public void commit() { }

    // ==========================================
    // METODY BAZOWE INTERFEJSU METRIC
    // ==========================================

    @Override
    public double getDistance(Tree t1, Tree t2, int... indexes) throws TreeCmpException {
        return 0;
    }

    @Override public String getName() { return ""; }
    @Override public String getCommandLineName() { return ""; }
    @Override public void setCommandLineName(String commandLineName) { }
    @Override public void setName(String name) { }
    @Override public String getDescription() { return ""; }
    @Override public void setDescription(String description) { }
    @Override public void initData() { }

    @Override public boolean isRooted() { return false; }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return false; }

    @Override public AlignInfo getAlignment() { return null; }
}