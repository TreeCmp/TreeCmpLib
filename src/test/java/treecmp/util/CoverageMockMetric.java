package treecmp.util;

import pal.misc.IdGroup;
import pal.tree.TreeUtils;
import treecmp.common.AlignInfo;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.TreeHolder;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.TreeUnrootedHolder;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.heuristics.moves.NniMove;
import pal.tree.Node;
import pal.tree.Tree;

import java.util.HashSet;
import java.util.Set;

public class CoverageMockMetric implements IncrementalMetric {

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

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        evaluationCount++;

        // Zależnie od trybu pakujemy w odpowiedniego Holdera za pomocą odpowiedniego Utils!
        if (isRooted) {
            Tree physicalNeighborTree = mockSprUtils.createSprTree(baseTree, pruneNode, targetNode);
            visitedTopologies.add(new TreeRootedHolder(physicalNeighborTree, idGroup));
        } else {
            Tree physicalNeighborTree = mockUsprUtils.createUsprTree(baseTree, pruneNode, targetNode);
            visitedTopologies.add(new TreeUnrootedHolder(physicalNeighborTree, idGroup));
        }

        return 1.0;
    }

    @Override public void applySprPrune(Node pruneNode) { }
    @Override public void undoSprPrune(Node pruneNode) { }
    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) { }
    @Override public void undoSprRegraftStep() { }
    @Override public double applyNni(NniMove move) { return 1.0; }
    @Override public void undoNni(NniMove move) { }
    @Override public double getCurrentDistance() { return 1.0; }
    @Override public void commit() { }

    // ==========================================
    // METODY BAZOWE INTERFEJSU METRIC (ZAŚLEPKI DLA KOMPILATORA)
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