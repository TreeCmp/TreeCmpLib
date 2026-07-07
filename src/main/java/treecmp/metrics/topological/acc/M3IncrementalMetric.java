package treecmp.metrics.topological.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import pal.tree.SimpleTree;
import treecmp.common.AlignInfo;
import treecmp.common.LapSolver;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingTripletMetric;

import java.util.*;

public class MatchingTripletIncrementalMetric implements IncrementalMetric {

    private Tree baseTree;
    private Tree targetTree;
    private Tree currentVirtualTree;
    private double currentDistance;
    private int dim;
    private int intT1Num;
    private int intT2Num;
    private int N;

    private IdGroup baseIdGroup;
    private int[] aliasTarget;
    private int[][] targetLcaMatrix;

    private int[][] assigncost;
    private int[] rowsol;
    private int[] colsol;

    private int[] currentT1TripletCount;
    private int[] t2IntTripletCount;

    private int[][] evalCost;
    private int[] evalRowsol;
    private int[] evalColsol;
    private int[] evalU;
    private int[] evalV;
    private int[][] evalIntersection;

    private final UsprUtils usprUtils = new UsprUtils();
    private final MatchingTripletMetric mtMetricFull = new MatchingTripletMetric();
    private final Stack<StateRecord> history = new Stack<>();

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        history.clear();

        if (baseTree != null && targetTree != null) {
            this.baseIdGroup = TreeUtils.getLeafIdGroup(baseTree);
            this.baseTree = createCleanCopy(baseTree);
            this.targetTree = createCleanCopy(targetTree);
            this.currentVirtualTree = createCleanCopy(this.baseTree);

            this.intT1Num = this.baseTree.getInternalNodeCount();
            this.intT2Num = this.targetTree.getInternalNodeCount();
            this.dim = Math.max(intT1Num, intT2Num);
            this.N = this.baseTree.getExternalNodeCount();

            this.assigncost = new int[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];

            this.evalCost = new int[dim][dim];
            this.evalRowsol = new int[dim];
            this.evalColsol = new int[dim];
            this.evalU = new int[dim];
            this.evalV = new int[dim];
            this.evalIntersection = new int[dim][dim];

            int[][] lcaMatrix1 = TreeCmpUtils.calcLcaMatrix(this.baseTree, null);
            int[] aliasBase = TreeUtils.mapExternalIdentifiers(this.baseIdGroup, this.baseTree);

            this.targetLcaMatrix = TreeCmpUtils.calcLcaMatrix(this.targetTree, null);
            this.aliasTarget = TreeUtils.mapExternalIdentifiers(this.baseIdGroup, this.targetTree);

            int maxNodesT2 = getSafeMaxNodeId(this.targetTree);
            short[] cSize2 = new short[maxNodesT2];
            Node[] postOrderT2 = TreeCmpUtils.getNodesInPostOrder(this.targetTree);
            TreeCmpUtils.calcCladeSizes(this.targetTree, postOrderT2, cSize2);
            Set<Node>[] verticesOutsideClade2 = TreeCmpUtils.getVerticesOutsideClade(this.targetTree);

            this.t2IntTripletCount = new int[dim];
            for (int i = 0; i < intT2Num; i++) {
                t2IntTripletCount[i] = coutTriplets(this.targetTree.getInternalNode(i), cSize2, verticesOutsideClade2);
            }

            int maxNodesT1 = getSafeMaxNodeId(this.baseTree);
            short[] cSize1 = new short[maxNodesT1];
            Node[] postOrderT1 = TreeCmpUtils.getNodesInPostOrder(this.baseTree);
            TreeCmpUtils.calcCladeSizes(this.baseTree, postOrderT1, cSize1);
            Set<Node>[] verticesOutsideClade1 = TreeCmpUtils.getVerticesOutsideClade(this.baseTree);

            this.currentT1TripletCount = new int[dim];
            for (int i = 0; i < intT1Num; i++) {
                this.currentT1TripletCount[i] = coutTriplets(this.baseTree.getInternalNode(i), cSize1, verticesOutsideClade1);
            }

            int[][] initialIntersection = new int[intT1Num][intT2Num];
            for (int i = 0; i < N; i++) {
                for (int j = i + 1; j < N; j++) {
                    for (int k = j + 1; k < N; k++) {
                        int ind1 = TreeCmpUtils.getNcv(this.baseTree, i, j, k, lcaMatrix1, aliasBase);
                        int ind2 = TreeCmpUtils.getNcv(this.targetTree, i, j, k, this.targetLcaMatrix, this.aliasTarget);
                        initialIntersection[ind1][ind2]++;
                    }
                }
            }

            for (int r = 0; r < dim; r++) {
                for (int c = 0; c < dim; c++) {
                    if (r < intT1Num && c < intT2Num) {
                        assigncost[r][c] = this.currentT1TripletCount[r] + t2IntTripletCount[c] - (initialIntersection[r][c] << 1);
                    } else if (r >= intT1Num && c < intT2Num) {
                        assigncost[r][c] = t2IntTripletCount[c];
                    } else if (r < intT1Num && c >= intT2Num) {
                        assigncost[r][c] = this.currentT1TripletCount[r];
                    } else {
                        assigncost[r][c] = 0;
                    }
                }
            }

            int[][] lapCost = new int[dim][dim];
            for (int i = 0; i < dim; i++) {
                System.arraycopy(assigncost[i], 0, lapCost[i], 0, dim);
            }
            int[] lapU = new int[dim];
            int[] lapV = new int[dim];

            int rawMetric = LapSolver.lap(dim, lapCost, rowsol, colsol, lapU, lapV);
            this.currentDistance = 0.5 * rawMetric;
        } else {
            this.currentDistance = 0;
        }
    }

    @Override
    public double applyNni(NniMove move) {
        Tree safeCopy = createCleanCopy(this.currentVirtualTree);
        Node virtMoving = getMappedNode(safeCopy, move.movingSubtree);
        Node virtSwapPartner = getMappedNode(safeCopy, move.swapPartner);

        if (virtMoving == null || virtSwapPartner == null) return pushUnchangedState();

        Tree rawNew = null;
        try {
            rawNew = usprUtils.createUsprTree(safeCopy, virtMoving, virtSwapPartner);
        } catch (Exception e) {
            return pushUnchangedState();
        }

        if (rawNew == null || rawNew.getExternalNodeCount() != N) return pushUnchangedState();

        SimpleTree tNew = new SimpleTree(rawNew);
        tNew.createNodeList();
        TreeUtils.computeParentPointers(tNew.getRoot());

        // TOPOLOGICZNE MAPOWANIE INDEKSÓW (Eliminuje Index -1)
        Signature[] oldSigs = new Signature[intT1Num];
        for (int i = 0; i < intT1Num; i++) {
            oldSigs[i] = new Signature(this.currentVirtualTree.getInternalNode(i), N, baseIdGroup);
        }

        int[] newToOldMap = new int[intT1Num];
        Arrays.fill(newToOldMap, -1);
        boolean[] oldUsed = new boolean[intT1Num];

        for (int i = 0; i < intT1Num; i++) {
            Signature newSig = new Signature(tNew.getInternalNode(i), N, baseIdGroup);
            for (int j = 0; j < intT1Num; j++) {
                if (!oldUsed[j] && oldSigs[j].equals(newSig)) {
                    newToOldMap[i] = j;
                    oldUsed[j] = true;
                    break;
                }
            }
        }

        int[] changedRows = new int[intT1Num];
        int cIdx = 0;
        for (int j = 0; j < intT1Num; j++) {
            if (!oldUsed[j]) changedRows[cIdx++] = j;
        }

        cIdx = 0;
        for (int i = 0; i < intT1Num; i++) {
            if (newToOldMap[i] == -1 && cIdx < changedRows.length) {
                newToOldMap[i] = changedRows[cIdx++];
            }
            tNew.getInternalNode(i).setNumber(newToOldMap[i]);
        }

        for (int i = 0; i < N; i++) {
            Node leaf = tNew.getExternalNode(i);
            Node oldLeaf = TreeUtils.getNodeByName(this.currentVirtualTree, leaf.getIdentifier().getName());
            if (oldLeaf != null) leaf.setNumber(oldLeaf.getNumber());
        }

        int[][] lcaNew = TreeCmpUtils.calcLcaMatrix(tNew, null);
        int[] aliasNew = TreeUtils.mapExternalIdentifiers(this.baseIdGroup, tNew);
        int[][] newIntersection = new int[intT1Num][intT2Num];

        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                for (int k = j + 1; k < N; k++) {
                    int ind1 = TreeCmpUtils.getNcv(tNew, i, j, k, lcaNew, aliasNew);
                    int ind2 = TreeCmpUtils.getNcv(targetTree, i, j, k, this.targetLcaMatrix, this.aliasTarget);
                    newIntersection[ind1][ind2]++;
                }
            }
        }

        int maxNodesNew = getSafeMaxNodeId(tNew);
        short[] cSizeNew = new short[maxNodesNew];
        Node[] postOrderNew = TreeCmpUtils.getNodesInPostOrder(tNew);
        TreeCmpUtils.calcCladeSizes(tNew, postOrderNew, cSizeNew);
        Set<Node>[] verticesOutsideCladeNew = TreeCmpUtils.getVerticesOutsideClade(tNew);

        int[] newT1TripletCount = new int[intT1Num];
        for (int i = 0; i < intT1Num; i++) {
            newT1TripletCount[i] = coutTriplets(tNew.getInternalNode(i), cSizeNew, verticesOutsideCladeNew);
        }

        int[][] tempAssigncost = new int[dim][dim];
        for (int r = 0; r < dim; r++) {
            for (int c = 0; c < dim; c++) {
                if (r < intT1Num && c < intT2Num) {
                    tempAssigncost[r][c] = newT1TripletCount[r] + t2IntTripletCount[c] - (newIntersection[r][c] << 1);
                } else if (r >= intT1Num && c < intT2Num) {
                    tempAssigncost[r][c] = t2IntTripletCount[c];
                } else if (r < intT1Num && c >= intT2Num) {
                    tempAssigncost[r][c] = newT1TripletCount[r];
                } else {
                    tempAssigncost[r][c] = 0;
                }
            }
        }

        history.push(new StateRecord(assigncost, rowsol, colsol, currentDistance, currentVirtualTree, currentT1TripletCount));

        this.assigncost = tempAssigncost;
        this.currentVirtualTree = tNew;
        this.currentT1TripletCount = newT1TripletCount;

        int[][] lapCost = new int[dim][dim];
        for (int i = 0; i < dim; i++) {
            System.arraycopy(assigncost[i], 0, lapCost[i], 0, dim);
        }

        int[] tempRowsol = new int[dim];
        int[] tempColsol = new int[dim];
        int rawMetric = LapSolver.lap(dim, lapCost, tempRowsol, tempColsol, new int[dim], new int[dim]);

        this.rowsol = tempRowsol;
        this.colsol = tempColsol;
        this.currentDistance = 0.5 * rawMetric;

        return this.currentDistance;
    }

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        Tree safeCopy = createCleanCopy(this.currentVirtualTree);
        Node safePrune = getMappedNode(safeCopy, pruneNode);
        Node safeTarget = getMappedNode(safeCopy, targetNode);

        if (safePrune == null || safeTarget == null) return Double.POSITIVE_INFINITY;

        Tree rawNew = null;
        try {
            rawNew = usprUtils.createUsprTree(safeCopy, safePrune, safeTarget);
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }

        if (rawNew == null || rawNew.getExternalNodeCount() != N) return Double.POSITIVE_INFINITY;

        SimpleTree tNew = new SimpleTree(rawNew);
        tNew.createNodeList();
        TreeUtils.computeParentPointers(tNew.getRoot());

        Signature[] oldSigs = new Signature[intT1Num];
        for (int i = 0; i < intT1Num; i++) {
            oldSigs[i] = new Signature(this.currentVirtualTree.getInternalNode(i), N, baseIdGroup);
        }

        int[] newToOldMap = new int[intT1Num];
        Arrays.fill(newToOldMap, -1);
        boolean[] oldUsed = new boolean[intT1Num];

        for (int i = 0; i < intT1Num; i++) {
            Signature newSig = new Signature(tNew.getInternalNode(i), N, baseIdGroup);
            for (int j = 0; j < intT1Num; j++) {
                if (!oldUsed[j] && oldSigs[j].equals(newSig)) {
                    newToOldMap[i] = j;
                    oldUsed[j] = true;
                    break;
                }
            }
        }

        int[] changedRows = new int[intT1Num];
        int cIdx = 0;
        for (int j = 0; j < intT1Num; j++) {
            if (!oldUsed[j]) changedRows[cIdx++] = j;
        }

        cIdx = 0;
        for (int i = 0; i < intT1Num; i++) {
            if (newToOldMap[i] == -1 && cIdx < changedRows.length) {
                newToOldMap[i] = changedRows[cIdx++];
            }
            tNew.getInternalNode(i).setNumber(newToOldMap[i]);
        }

        for (int i = 0; i < N; i++) {
            Node leaf = tNew.getExternalNode(i);
            Node oldLeaf = TreeUtils.getNodeByName(this.currentVirtualTree, leaf.getIdentifier().getName());
            if (oldLeaf != null) leaf.setNumber(oldLeaf.getNumber());
        }

        int[][] lcaNew = TreeCmpUtils.calcLcaMatrix(tNew, null);
        int[] aliasNew = TreeUtils.mapExternalIdentifiers(this.baseIdGroup, tNew);

        for (int[] row : evalIntersection) Arrays.fill(row, 0);

        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                for (int k = j + 1; k < N; k++) {
                    int ind1 = TreeCmpUtils.getNcv(tNew, i, j, k, lcaNew, aliasNew);
                    int ind2 = TreeCmpUtils.getNcv(targetTree, i, j, k, this.targetLcaMatrix, this.aliasTarget);
                    evalIntersection[ind1][ind2]++;
                }
            }
        }

        int maxNodesNew = getSafeMaxNodeId(tNew);
        short[] cSizeNew = new short[maxNodesNew];
        Node[] postOrderNew = TreeCmpUtils.getNodesInPostOrder(tNew);
        TreeCmpUtils.calcCladeSizes(tNew, postOrderNew, cSizeNew);
        Set<Node>[] verticesOutsideCladeNew = TreeCmpUtils.getVerticesOutsideClade(tNew);

        int[] t1IntTripletCountNew = new int[intT1Num];
        for (int i = 0; i < intT1Num; i++) {
            t1IntTripletCountNew[i] = coutTriplets(tNew.getInternalNode(i), cSizeNew, verticesOutsideCladeNew);
        }

        for (int r = 0; r < dim; r++) {
            for (int c = 0; c < dim; c++) {
                if (r < intT1Num && c < intT2Num) {
                    evalCost[r][c] = t1IntTripletCountNew[r] + t2IntTripletCount[c] - (evalIntersection[r][c] << 1);
                } else if (r >= intT1Num && c < intT2Num) {
                    evalCost[r][c] = t2IntTripletCount[c];
                } else if (r < intT1Num && c >= intT2Num) {
                    evalCost[r][c] = t1IntTripletCountNew[r];
                } else {
                    evalCost[r][c] = 0;
                }
            }
        }

        int metric = LapSolver.lap(dim, evalCost, new int[dim], new int[dim], new int[dim], new int[dim]);
        return 0.5 * metric;
    }

    @Override public void applySprPrune(Node pruneNode) {}
    @Override public void undoSprPrune(Node pruneNode) {}
    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) {}
    @Override public void undoSprRegraftStep() { }

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

    private double pushUnchangedState() {
        history.push(new StateRecord(assigncost, rowsol, colsol, currentDistance, currentVirtualTree, currentT1TripletCount));
        return this.currentDistance;
    }

    @Override
    public void undoNni(NniMove move) {
        if (!history.isEmpty()) {
            StateRecord r = history.pop();
            this.assigncost = r.oldAssigncost;
            this.rowsol = r.rowsol;
            this.colsol = r.colsol;
            this.currentDistance = r.distance;
            this.currentVirtualTree = r.oldTree;
            this.currentT1TripletCount = r.oldTripletCount;
        }
    }

    private Tree createCleanCopy(Tree original) {
        SimpleTree copy = new SimpleTree(original);
        copy.createNodeList();
        TreeUtils.computeParentPointers(copy.getRoot());
        for (int i = 0; i < copy.getInternalNodeCount(); i++) {
            copy.getInternalNode(i).setNumber(i);
        }
        return copy;
    }

    private Node getMappedNode(Tree destTree, Node srcNode) {
        if (srcNode.isLeaf()) {
            return TreeUtils.getNodeByName(destTree, srcNode.getIdentifier().getName());
        }
        Signature targetSig = new Signature(srcNode, N, baseIdGroup);
        for (int i = 0; i < destTree.getInternalNodeCount(); i++) {
            Signature sig = new Signature(destTree.getInternalNode(i), N, baseIdGroup);
            if (sig.equals(targetSig)) return destTree.getInternalNode(i);
        }
        return null;
    }

    private static class Signature {
        BitSet[] clusters = new BitSet[3];
        public Signature(Node n, int N, IdGroup idGroup) {
            for (int i = 0; i < 3; i++) clusters[i] = new BitSet();
            int idx = 0;
            if (n.getParent() != null) {
                clusters[idx++] = getLeavesExcluding(n, idGroup, N);
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                if (idx < 3) clusters[idx++] = getLeaves(n.getChild(i), idGroup);
            }
            Arrays.sort(clusters, (a, b) -> a.toString().compareTo(b.toString()));
        }

        public boolean equals(Signature other) {
            for (int i = 0; i < 3; i++) {
                if (!this.clusters[i].equals(other.clusters[i])) return false;
            }
            return true;
        }
    }

    private static BitSet getLeaves(Node n, IdGroup idGroup) {
        BitSet bs = new BitSet();
        populate(n, bs, idGroup);
        return bs;
    }

    private static void populate(Node n, BitSet bs, IdGroup idGroup) {
        if (n.isLeaf()) {
            bs.set(idGroup.whichIdNumber(n.getIdentifier().getName()));
        } else {
            for (int i = 0; i < n.getChildCount(); i++) populate(n.getChild(i), bs, idGroup);
        }
    }

    private static BitSet getLeavesExcluding(Node exclude, IdGroup idGroup, int N) {
        BitSet bs = getLeaves(exclude, idGroup);
        BitSet comp = new BitSet(N);
        comp.set(0, N);
        comp.andNot(bs);
        return comp;
    }

    private int getSafeMaxNodeId(Tree tree) {
        int maxId = 0;
        Node[] allNodes = TreeCmpUtils.getAllNodes(tree);
        for (Node n : allNodes) {
            if (n != null && n.getNumber() > maxId) maxId = n.getNumber();
        }
        return maxId + 1;
    }

    private int coutTriplets(Node n, short[] clustSizeTab, Set<Node>[] verticesOutsideClade) {
        int chCount = n.getChildCount();
        int[] chSize = new int[chCount + 1];

        for (int i = 0; i < chCount; i++) {
            Node chNode = n.getChild(i);
            if (chNode.isLeaf()) {
                chSize[i] = 1;
            } else {
                chSize[i] = clustSizeTab[chNode.getNumber()];
            }
        }

        if (verticesOutsideClade != null && n.getNumber() < verticesOutsideClade.length && verticesOutsideClade[n.getNumber()] != null) {
            chSize[chCount] = verticesOutsideClade[n.getNumber()].size();
        } else {
            chSize[chCount] = 0;
        }

        int pairCount = 0;
        for (int i = 0; i < chSize.length; i++) {
            for (int j = i + 1; j < chSize.length; j++) {
                for (int k = j + 1; k < chSize.length; k++) {
                    pairCount += (chSize[i] * chSize[j] * chSize[k]);
                }
            }
        }
        return pairCount;
    }

    private static class StateRecord {
        int[][] oldAssigncost;
        int[] rowsol, colsol;
        double distance;
        Tree oldTree;
        int[] oldTripletCount;

        StateRecord(int[][] assigncost, int[] rs, int[] cs, double d, Tree oldTree, int[] tc) {
            this.oldAssigncost = assigncost;
            this.rowsol = rs.clone();
            this.colsol = cs.clone();
            this.distance = d;
            this.oldTree = oldTree;
            this.oldTripletCount = tc.clone();
        }
    }

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { history.clear(); }
    @Override public double getDistance(Tree t1, Tree t2, int... indexes) { return mtMetricFull.getDistance(t1, t2, indexes); }
    @Override public String getName() { return "Zero-Alloc SPR " + mtMetricFull.getName(); }
    @Override public String getCommandLineName() { return mtMetricFull.getCommandLineName(); }
    @Override public void setCommandLineName(String cln) { mtMetricFull.setCommandLineName(cln); }
    @Override public void setName(String name) { mtMetricFull.setName(name); }
    @Override public String getDescription() { return mtMetricFull.getDescription(); }
    @Override public void setDescription(String d) { mtMetricFull.setDescription(d); }
    @Override public void initData() { mtMetricFull.initData(); }
    @Override public boolean isRooted() { return mtMetricFull.isRooted(); }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return mtMetricFull.isDiffLeafSets(); }
    @Override public AlignInfo getAlignment() { return mtMetricFull.getAlignment(); }
}