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
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.BaseMetric;
import treecmp.metrics.topological.MatchingPairMetric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class MPIncrementalMetric extends BaseMetric implements IncrementalMetric {

    private Tree baseTree;
    private Tree targetTree;
    private Tree currentVirtualTree;
    private double currentDistance;
    private int dim;
    private int intT1Num;
    private int intT2Num;
    private int N;

    private IdGroup baseIdGroup;
    private int[][] targetLcaMatrix;
    private int[] targetIdToCol;

    private Map<Node, BitSet> baseSplits;
    private Node activePruneNode = null;

    private int[][] assigncost;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private int[] t2IntPairCount;
    private int[] currentT1PairCount;
    private Map<Node, Integer> nodeToRow;

    private final MatchingPairMetric mpMetricFull = new MatchingPairMetric();
    private final Stack<StateRecord> history = new Stack<>();
    private final Stack<LapStateDelta> deltaStack = new Stack<>();

    // ========================================================================
    // DELTA STOS DLA MP (PAMIĘĆ O(N))
    // ========================================================================
    private static class LapStateDelta {
        final int rowV, rowU;
        final int[] oldRowV, oldRowU;
        final int oldPairCountV, oldPairCountU;
        final int[] oldU, oldV, oldRowsol, oldColsol;
        final double oldDistance;

        LapStateDelta(int rowV, int rowU, int[] oldRowV, int[] oldRowU,
                      int oldPairCountV, int oldPairCountU,
                      int[] oldU, int[] oldV, int[] oldRowsol, int[] oldColsol,
                      double oldDistance) {
            this.rowV = rowV; this.rowU = rowU;
            this.oldRowV = oldRowV; this.oldRowU = oldRowU;
            this.oldPairCountV = oldPairCountV; this.oldPairCountU = oldPairCountU;
            this.oldU = oldU; this.oldV = oldV;
            this.oldRowsol = oldRowsol; this.oldColsol = oldColsol;
            this.oldDistance = oldDistance;
        }
    }

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        history.clear();
        deltaStack.clear();

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
            this.u = new int[dim];
            this.v = new int[dim];
            this.currentT1PairCount = new int[dim];
            this.nodeToRow = new IdentityHashMap<>();

            int maxNodesT2 = getSafeMaxNodeId(this.targetTree);
            this.targetIdToCol = new int[maxNodesT2];
            Arrays.fill(this.targetIdToCol, -1);
            for (int i = 0; i < intT2Num; i++) {
                this.targetIdToCol[this.targetTree.getInternalNode(i).getNumber()] = i;
            }

            int maxNodesT1 = getSafeMaxNodeId(this.baseTree);
            int[] baseIdToRow = new int[maxNodesT1];
            Arrays.fill(baseIdToRow, -1);
            this.baseSplits = new IdentityHashMap<>();
            for (int i = 0; i < intT1Num; i++) {
                Node n = this.baseTree.getInternalNode(i);
                baseIdToRow[n.getNumber()] = i;
                nodeToRow.put(n, i);
                baseSplits.put(n, getLeaves(n, baseIdGroup));
            }

            int[][] lcaMatrix1 = TreeCmpUtils.calcLcaMatrix(this.baseTree, this.baseIdGroup);
            this.targetLcaMatrix = TreeCmpUtils.calcLcaMatrix(this.targetTree, this.baseIdGroup);

            short[] cSize2 = new short[maxNodesT2];
            Node[] postOrderT2 = TreeCmpUtils.getNodesInPostOrder(this.targetTree);
            TreeCmpUtils.calcCladeSizes(this.targetTree, postOrderT2, cSize2);

            this.t2IntPairCount = new int[dim];
            for (int i = 0; i < intT2Num; i++) {
                this.t2IntPairCount[i] = coutChildrenPairs(this.targetTree.getInternalNode(i), cSize2);
            }

            short[] cSize1 = new short[maxNodesT1];
            Node[] postOrderT1 = TreeCmpUtils.getNodesInPostOrder(this.baseTree);
            TreeCmpUtils.calcCladeSizes(this.baseTree, postOrderT1, cSize1);

            for (int i = 0; i < intT1Num; i++) {
                this.currentT1PairCount[i] = coutChildrenPairs(this.baseTree.getInternalNode(i), cSize1);
            }

            int[][] initialIntersection = new int[dim][dim];
            for (int i = 0; i < N; i++) {
                for (int j = i + 1; j < N; j++) {
                    int ind1 = lcaMatrix1[i][j];
                    int ind2 = this.targetLcaMatrix[i][j];
                    if (ind1 >= 0 && ind1 < baseIdToRow.length && ind2 >= 0 && ind2 < this.targetIdToCol.length) {
                        int r = baseIdToRow[ind1];
                        int c = this.targetIdToCol[ind2];
                        if (r >= 0 && c >= 0) {
                            initialIntersection[r][c]++;
                        }
                    }
                }
            }

            for (int r = 0; r < dim; r++) {
                for (int c = 0; c < dim; c++) {
                    if (r < intT1Num && c < intT2Num) {
                        assigncost[r][c] = currentT1PairCount[r] + t2IntPairCount[c] - (initialIntersection[r][c] << 1);
                    } else if (r >= intT1Num && c < intT2Num) {
                        assigncost[r][c] = t2IntPairCount[c];
                    } else if (r < intT1Num && c >= intT2Num) {
                        assigncost[r][c] = currentT1PairCount[r];
                    } else {
                        assigncost[r][c] = 0;
                    }
                }
            }

            int[][] lapCost = new int[dim][dim];
            for (int i = 0; i < dim; i++) {
                System.arraycopy(assigncost[i], 0, lapCost[i], 0, dim);
            }

            int rawMetric = LapSolver.lap(dim, lapCost, rowsol, colsol, this.u, this.v);
            this.currentDistance = 0.5 * rawMetric;
        } else {
            this.currentDistance = 0;
        }
    }

    // ========================================================================
    // LOGIKA REWOLUCJI INKREMENTALNEJ MP DLA CONTINUOUS WALKERA
    // ========================================================================

    @Override
    public void applySprPrune(Node pruneNode) {
        this.activePruneNode = pruneNode;
    }

    @Override
    public void undoSprPrune(Node pruneNode) {
        this.activePruneNode = null;
    }

    public double applyNniStep(Node nodeToUpdate, BitSet bitsOut, BitSet bitsIn) {
        Node v = nodeToUpdate;
        Node u = v.getParent();

        Integer rVIndex = nodeToRow.get(v);
        Integer rUIndex = nodeToRow.get(u);
        if (rVIndex == null || rUIndex == null) return this.currentDistance;

        int rowV = rVIndex;
        int rowU = rUIndex;

        // Snapshot stanu
        int[] oldRowV = Arrays.copyOf(assigncost[rowV], dim);
        int[] oldRowU = Arrays.copyOf(assigncost[rowU], dim);
        int oldPairCountV = currentT1PairCount[rowV];
        int oldPairCountU = currentT1PairCount[rowU];
        int[] oldU = Arrays.copyOf(this.u, dim);
        int[] oldV = Arrays.copyOf(this.v, dim);
        int[] oldRowsol = Arrays.copyOf(rowsol, dim);
        int[] oldColsol = Arrays.copyOf(colsol, dim);

        deltaStack.push(new LapStateDelta(rowV, rowU, oldRowV, oldRowU, oldPairCountV, oldPairCountU,
                oldU, oldV, oldRowsol, oldColsol, currentDistance));

        // Pozycja wędrującego poddrzewa M w strukturze Walkera
        Node currentPos = (bitsIn != null) ? v : u;

        // Przeliczenie par LCA wyłącznie dla 2 zmodyfikowanych węzłów
        updateRowPairs(rowV, v, currentPos);
        updateRowPairs(rowU, u, currentPos);

        int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, this.u, this.v, new int[]{rowV, rowU});
        this.currentDistance = 0.5 * rawMetric;

        return this.currentDistance;
    }

    public void undoNniStep() {
        if (deltaStack.isEmpty()) return;
        LapStateDelta delta = deltaStack.pop();

        System.arraycopy(delta.oldRowV, 0, assigncost[delta.rowV], 0, dim);
        System.arraycopy(delta.oldRowU, 0, assigncost[delta.rowU], 0, dim);
        currentT1PairCount[delta.rowV] = delta.oldPairCountV;
        currentT1PairCount[delta.rowU] = delta.oldPairCountU;

        System.arraycopy(delta.oldU, 0, u, 0, dim);
        System.arraycopy(delta.oldV, 0, v, 0, dim);
        System.arraycopy(delta.oldRowsol, 0, rowsol, 0, dim);
        System.arraycopy(delta.oldColsol, 0, colsol, 0, dim);

        this.currentDistance = delta.oldDistance;
    }

    private void updateRowPairs(int row, Node n, Node currentPos) {
        BitSet C1 = new BitSet(N);
        BitSet C2 = new BitSet(N);

        if (n == currentPos) {
            // Węzeł jest obecnie punktem podczepienia odciętego poddrzewa
            C1 = getBaseSplit(activePruneNode);
            C2 = getCurrentCluster(n, currentPos, activePruneNode);
        } else if (n == activePruneNode.getParent()) {
            // Węzeł jest pierwotnym rodzicem, z którego wyrwano gałąź - został ominięty (0 par)
        } else {
            // Węzeł zachowuje swoje fizyczne dzieci, ale ich zawartość mogła ulec zmianie
            if (n.getChildCount() == 2) {
                C1 = getCurrentCluster(n.getChild(0), currentPos, activePruneNode);
                C2 = getCurrentCluster(n.getChild(1), currentPos, activePruneNode);
            }
        }

        computeRowCost(row, C1, C2);
    }

    private BitSet getCurrentCluster(Node x, Node currentPos, Node pruneNode) {
        if (x == pruneNode) return new BitSet(N); // Poddrzewo wędrujące
        BitSet res = (BitSet) getBaseSplit(x).clone();

        boolean pInside = isDescendantOrSelf(pruneNode, x);
        boolean cInside = isDescendantOrSelf(currentPos, x);

        if (pInside && !cInside) {
            res.andNot(getBaseSplit(pruneNode));
        } else if (!pInside && cInside) {
            res.or(getBaseSplit(pruneNode));
        }
        return res;
    }

    private void computeRowCost(int row, BitSet setA, BitSet setB) {
        int[] newIntersections = new int[dim];
        int pairsCount = 0;

        if (setA != null && setB != null && !setA.isEmpty() && !setB.isEmpty()) {
            for (int l1 = setA.nextSetBit(0); l1 >= 0; l1 = setA.nextSetBit(l1 + 1)) {
                for (int l2 = setB.nextSetBit(0); l2 >= 0; l2 = setB.nextSetBit(l2 + 1)) {
                    int minL = Math.min(l1, l2);
                    int maxL = Math.max(l1, l2);

                    int lcaT2 = targetLcaMatrix[minL][maxL];
                    if (lcaT2 >= 0) {
                        int c = targetIdToCol[lcaT2];
                        if (c >= 0) {
                            newIntersections[c]++;
                        }
                    }
                    pairsCount++;
                }
            }
        }

        currentT1PairCount[row] = pairsCount;

        for (int c = 0; c < dim; c++) {
            if (c < intT2Num) {
                assigncost[row][c] = pairsCount + t2IntPairCount[c] - (newIntersections[c] << 1);
            } else if (c >= intT2Num) {
                assigncost[row][c] = pairsCount;
            }
        }
    }

    private BitSet getBaseSplit(Node n) {
        if (n.isLeaf()) {
            BitSet bs = new BitSet(N);
            bs.set(baseIdGroup.whichIdNumber(n.getIdentifier().getName()));
            return bs;
        }
        return baseSplits.get(n);
    }

    private boolean isDescendantOrSelf(Node descendant, Node ancestor) {
        Node curr = descendant;
        while (curr != null) {
            if (curr == ancestor) return true;
            curr = curr.getParent();
        }
        return false;
    }

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        Tree tempTree = new SprUtils().createSprTree(this.baseTree, pruneNode, targetNode);
        if (tempTree != null) {
            return mpMetricFull.getDistance(tempTree, this.targetTree);
        }
        return Double.POSITIVE_INFINITY;
    }

    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) { throw new UnsupportedOperationException("Użyj applyNniStep"); }
    @Override public void undoSprRegraftStep() { throw new UnsupportedOperationException("Użyj undoNniStep"); }


    // ========================================================================
    // KLASYCZNA LOGIKA ECR / FIZYCZNE NNI
    // ========================================================================

    private double calculateCleanSlateDistance(SimpleTree tNew, boolean isCommit, int maxCostBound) {

        Map<String, Integer> sigToOldRow = new HashMap<>();
        for (int i = 0; i < intT1Num; i++) {
            Node n = this.currentVirtualTree.getInternalNode(i);
            Signature sig = new Signature(n, N, baseIdGroup);
            sigToOldRow.put(sig.hash, i);
        }

        Tree tPerfect = createCleanCopy(tNew);

        int[] newToOld = new int[dim];
        int[] oldToNew = new int[dim];
        Arrays.fill(newToOld, -1);
        Arrays.fill(oldToNew, -1);

        for (int r_new = 0; r_new < intT1Num; r_new++) {
            Node n = tPerfect.getInternalNode(r_new);
            Signature sig = new Signature(n, N, baseIdGroup);
            Integer r_old = sigToOldRow.get(sig.hash);
            if (r_old != null && oldToNew[r_old] == -1) {
                newToOld[r_new] = r_old;
                oldToNew[r_old] = r_new;
            }
        }

        int unmappedOld = 0;
        for (int r_new = 0; r_new < dim; r_new++) {
            if (newToOld[r_new] == -1) {
                while (unmappedOld < dim && oldToNew[unmappedOld] != -1) {
                    unmappedOld++;
                }
                if (unmappedOld < dim) {
                    newToOld[r_new] = unmappedOld;
                    oldToNew[unmappedOld] = r_new;
                }
            }
        }

        int maxNodesNew = getSafeMaxNodeId(tPerfect);
        int[] idToRow = new int[maxNodesNew];
        Arrays.fill(idToRow, -1);
        for (int r_new = 0; r_new < intT1Num; r_new++) {
            idToRow[tPerfect.getInternalNode(r_new).getNumber()] = r_new;
        }

        int[][] lcaNew = TreeCmpUtils.calcLcaMatrix(tPerfect, this.baseIdGroup);
        int[][] newIntersection = new int[dim][dim];

        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                int ind1 = lcaNew[i][j];
                int ind2 = this.targetLcaMatrix[i][j];
                if (ind1 >= 0 && ind1 < idToRow.length && ind2 >= 0 && ind2 < this.targetIdToCol.length) {
                    int r_new = idToRow[ind1];
                    int c = this.targetIdToCol[ind2];
                    if (r_new >= 0 && c >= 0) {
                        newIntersection[r_new][c]++;
                    }
                }
            }
        }

        short[] cSizeNew = new short[maxNodesNew];
        Node[] postOrderNew = TreeCmpUtils.getNodesInPostOrder(tPerfect);
        TreeCmpUtils.calcCladeSizes(tPerfect, postOrderNew, cSizeNew);

        int[] newT1PairCount = new int[dim];
        for (int r_new = 0; r_new < intT1Num; r_new++) {
            newT1PairCount[r_new] = coutChildrenPairs(tPerfect.getInternalNode(r_new), cSizeNew);
        }

        int[][] tempAssigncost = new int[dim][dim];
        for (int r = 0; r < dim; r++) {
            for (int c = 0; c < dim; c++) {
                if (r < intT1Num && c < intT2Num) {
                    tempAssigncost[r][c] = newT1PairCount[r] + t2IntPairCount[c] - (newIntersection[r][c] << 1);
                } else if (r >= intT1Num && c < intT2Num) {
                    tempAssigncost[r][c] = t2IntPairCount[c];
                } else if (r < intT1Num && c >= intT2Num) {
                    tempAssigncost[r][c] = newT1PairCount[r];
                } else {
                    tempAssigncost[r][c] = 0;
                }
            }
        }

        int[][] lapCost = new int[dim][dim];
        for (int i = 0; i < dim; i++) {
            System.arraycopy(tempAssigncost[i], 0, lapCost[i], 0, dim);
        }

        int[] tempRowsol = new int[dim];
        int[] tempColsol = new int[dim];
        int[] tempU = new int[dim];
        int[] tempV = this.v.clone();

        for (int r_new = 0; r_new < dim; r_new++) {
            int r_old = newToOld[r_new];
            tempU[r_new] = this.u[r_old];
            tempRowsol[r_new] = this.rowsol[r_old];
        }

        for (int c = 0; c < dim; c++) {
            int r_old = this.colsol[c];
            tempColsol[c] = oldToNew[r_old];
        }

        int[][] mappedOldAssigncost = new int[dim][dim];
        for (int r_new = 0; r_new < dim; r_new++) {
            mappedOldAssigncost[r_new] = this.assigncost[newToOld[r_new]];
        }

        List<Integer> changedRowsList = new ArrayList<>();
        for (int r_new = 0; r_new < dim; r_new++) {
            if (!Arrays.equals(mappedOldAssigncost[r_new], tempAssigncost[r_new])) {
                changedRowsList.add(r_new);
            }
        }
        int[] changedRows = changedRowsList.stream().mapToInt(i -> i).toArray();

        int rawMetric;
        if (changedRows.length > 0 && changedRows.length < dim && maxCostBound >= 0) {
            rawMetric = LapSolver.lapUpdateBoundedInt(dim, lapCost, tempRowsol, tempColsol, tempU, tempV, changedRows, maxCostBound);
        } else {
            rawMetric = LapSolver.lap(dim, lapCost, tempRowsol, tempColsol, tempU, tempV);
        }

        double dist = 0.5 * rawMetric;

        if (isCommit) {
            history.push(new StateRecord(this.assigncost, this.currentT1PairCount, this.rowsol, this.colsol, this.u, this.v, this.currentDistance, this.currentVirtualTree));
            this.assigncost = tempAssigncost;
            this.currentT1PairCount = newT1PairCount;
            this.currentVirtualTree = tPerfect;
            this.rowsol = tempRowsol;
            this.colsol = tempColsol;
            this.u = tempU;
            this.v = tempV;
            this.currentDistance = dist;
        }

        return dist;
    }

    private double pushUnchangedState() {
        history.push(new StateRecord(assigncost, currentT1PairCount, rowsol, colsol, u, v, currentDistance, currentVirtualTree));
        return this.currentDistance;
    }

    @Override
    public double applyNni(NniMove move) {
        SimpleTree tNew = (SimpleTree) createCleanCopy(currentVirtualTree);

        Node L = getMappedNode(tNew, move.movingSubtree);
        Node S = getMappedNode(tNew, move.swapPartner);
        if (L == null || S == null) return pushUnchangedState();

        Node p1 = L.getParent();
        Node p2 = S.getParent();
        if (p1 == null || p2 == null || p1 == p2) return pushUnchangedState();

        int idx1 = findChildPos(L, p1);
        int idx2 = findChildPos(S, p2);
        if (idx1 == -1 || idx2 == -1) return pushUnchangedState();

        p1.setChild(idx1, S); S.setParent(p1);
        p2.setChild(idx2, L); L.setParent(p2);

        return calculateCleanSlateDistance(tNew, true, N * N * 10);
    }

    @Override
    public void undoNni(NniMove move) {
        if (!history.isEmpty()) {
            StateRecord r = history.pop();
            this.assigncost = r.oldAssigncost;
            this.currentT1PairCount = r.oldPairCount;
            this.rowsol = r.rowsol;
            this.colsol = r.colsol;
            this.u = r.u;
            this.v = r.v;
            this.currentDistance = r.distance;
            this.currentVirtualTree = r.oldTree;
        }
    }

    @Override
    public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR newTopology) {
        return internalApply2sEcrMove(top, m1, m2, boundarySubtrees, newTopology, false);
    }

    @Override
    public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR newTopology) {
        return internalApply2sEcrMove(top, m1, m2, boundarySubtrees, newTopology, true);
    }

    private double internalApply2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, SubtreeEcr2Utils.TopologyTemplate2sECR template, boolean isCommit) {
        SimpleTree tNew = (SimpleTree) createCleanCopy(currentVirtualTree);

        Node vTop = getMappedNode(tNew, top);
        Node vM1 = getMappedNode(tNew, m1);
        Node vM2 = getMappedNode(tNew, m2);

        if (vTop == null || vM1 == null || vM2 == null) return isCommit ? pushUnchangedState() : this.currentDistance;

        Node[] vBounds = new Node[4];
        for(int i=0; i<4; i++) {
            vBounds[i] = getMappedNode(tNew, boundarySubtrees[i]);
            if (vBounds[i] == null) return isCommit ? pushUnchangedState() : this.currentDistance;
        }

        while(vTop.getChildCount() > 0) vTop.removeChild(0);
        while(vM1.getChildCount() > 0) vM1.removeChild(0);
        while(vM2.getChildCount() > 0) vM2.removeChild(0);

        if (template.isFork) {
            vTop.insertChild(vM1, 0); vM1.setParent(vTop);
            vTop.insertChild(vM2, 1); vM2.setParent(vTop);
            vM1.insertChild(vBounds[template.indices[0]], 0); vBounds[template.indices[0]].setParent(vM1);
            vM1.insertChild(vBounds[template.indices[1]], 1); vBounds[template.indices[1]].setParent(vM1);
            vM2.insertChild(vBounds[template.indices[2]], 0); vBounds[template.indices[2]].setParent(vM2);
            vM2.insertChild(vBounds[template.indices[3]], 1); vBounds[template.indices[3]].setParent(vM2);
        } else {
            vTop.insertChild(vBounds[template.indices[0]], 0); vBounds[template.indices[0]].setParent(vTop);
            vTop.insertChild(vM1, 1); vM1.setParent(vTop);
            vM1.insertChild(vBounds[template.indices[1]], 0); vBounds[template.indices[1]].setParent(vM1);
            vM1.insertChild(vM2, 1); vM2.setParent(vM1);
            vM2.insertChild(vBounds[template.indices[2]], 0); vBounds[template.indices[2]].setParent(vM2);
            vM2.insertChild(vBounds[template.indices[3]], 1); vBounds[template.indices[3]].setParent(vM2);
        }

        return calculateCleanSlateDistance(tNew, isCommit, N * N * 10);
    }

    @Override
    public double evaluate3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR newTopology) {
        return internalApply3sEcrMove(cluster, boundarySubtrees, newTopology, false);
    }

    @Override
    public double commit3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR newTopology) {
        return internalApply3sEcrMove(cluster, boundarySubtrees, newTopology, true);
    }

    private double internalApply3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, SubtreeEcr3Utils.TopologyTemplate3sECR template, boolean isCommit) {
        SimpleTree tNew = (SimpleTree) createCleanCopy(currentVirtualTree);

        Node[] vAvailable = new Node[4];
        for(int i=0; i<4; i++) {
            vAvailable[i] = getMappedNode(tNew, cluster.get(i));
            if (vAvailable[i] == null) return isCommit ? pushUnchangedState() : this.currentDistance;
        }

        Node[] vBounds = new Node[5];
        for(int i=0; i<5; i++) {
            vBounds[i] = getMappedNode(tNew, boundarySubtrees[i]);
            if (vBounds[i] == null) return isCommit ? pushUnchangedState() : this.currentDistance;
        }

        for (int i=0; i<4; i++) {
            while(vAvailable[i].getChildCount() > 0) vAvailable[i].removeChild(0);
        }

        bindMapped3sEcrTemplate(template, vAvailable[0], vAvailable, 1, vBounds);

        return calculateCleanSlateDistance(tNew, isCommit, N * N * 10);
    }

    private int bindMapped3sEcrTemplate(SubtreeEcr3Utils.TopologyTemplate3sECR temp, Node currentInternal, Node[] available, int nextAvailIdx, Node[] newS) {
        int idx = nextAvailIdx;
        if (temp.left.leafIndex != -1) {
            currentInternal.insertChild(newS[temp.left.leafIndex], 0);
            newS[temp.left.leafIndex].setParent(currentInternal);
        } else {
            Node nextInt = available[idx++];
            currentInternal.insertChild(nextInt, 0); nextInt.setParent(currentInternal);
            idx = bindMapped3sEcrTemplate(temp.left, nextInt, available, idx, newS);
        }

        if (temp.right.leafIndex != -1) {
            currentInternal.insertChild(newS[temp.right.leafIndex], 1);
            newS[temp.right.leafIndex].setParent(currentInternal);
        } else {
            Node nextInt = available[idx++];
            currentInternal.insertChild(nextInt, 1); nextInt.setParent(currentInternal);
            idx = bindMapped3sEcrTemplate(temp.right, nextInt, available, idx, newS);
        }
        return idx;
    }

    private Node getMappedNode(Tree destTree, Node srcNode) {
        if (srcNode == null) return null;
        if (srcNode.isLeaf()) return TreeUtils.getNodeByName(destTree, srcNode.getIdentifier().getName());
        Signature targetSig = new Signature(srcNode, N, baseIdGroup);
        for (int i = 0; i < destTree.getInternalNodeCount(); i++) {
            Signature sig = new Signature(destTree.getInternalNode(i), N, baseIdGroup);
            if (sig.equals(targetSig)) return destTree.getInternalNode(i);
        }
        return null;
    }

    private int findChildPos(Node child, Node parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) == child) return i;
        }
        return -1;
    }

    private Tree createCleanCopy(Tree original) {
        SimpleTree copy = new SimpleTree(original);
        copy.createNodeList();
        TreeUtils.computeParentPointers(copy.getRoot());
        return copy;
    }

    private int getSafeMaxNodeId(Tree tree) {
        int maxId = 0;
        Node[] allNodes = TreeCmpUtils.getAllNodes(tree);
        for (Node n : allNodes) {
            if (n != null && n.getNumber() > maxId) maxId = n.getNumber();
        }
        return maxId + 1;
    }

    private int coutChildrenPairs(Node n, short[] clustSizeTab) {
        int chCount = n.getChildCount();
        int[] cSize = new int[chCount];
        for (int i = 0; i < chCount; i++) {
            Node chNode = n.getChild(i);
            if (chNode.isLeaf()) cSize[i] = 1;
            else cSize[i] = clustSizeTab[chNode.getNumber()];
        }
        int pairCount = 0;
        for (int i = 0; i < cSize.length; i++) {
            for (int j = i + 1; j < cSize.length; j++) {
                pairCount += (cSize[i] * cSize[j]);
            }
        }
        return pairCount;
    }

    private static class Signature {
        String hash;
        public Signature(Node n, int N, IdGroup idGroup) {
            List<BitSet> parts = new ArrayList<>();
            for (int i = 0; i < n.getChildCount(); i++) {
                parts.add(getLeaves(n.getChild(i), idGroup));
            }
            String[] strParts = new String[parts.size()];
            for (int i = 0; i < parts.size(); i++) {
                strParts[i] = parts.get(i).toString();
            }
            Arrays.sort(strParts);
            this.hash = Arrays.toString(strParts);
        }
        @Override
        public int hashCode() { return hash.hashCode(); }
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Signature)) return false;
            return this.hash.equals(((Signature)obj).hash);
        }
        @Override
        public String toString() { return hash; }
    }

    private static BitSet getLeaves(Node n, IdGroup idGroup) {
        BitSet bs = new BitSet(); populate(n, idGroup, bs); return bs;
    }

    private static void populate(Node n, IdGroup idGroup, BitSet bs) {
        if (n.isLeaf()) bs.set(idGroup.whichIdNumber(n.getIdentifier().getName()));
        else for (int i = 0; i < n.getChildCount(); i++) populate(n.getChild(i), idGroup, bs);
    }

    private static class StateRecord {
        int[][] oldAssigncost;
        int[] oldPairCount;
        int[] rowsol, colsol, u, v;
        double distance;
        Tree oldTree;

        StateRecord(int[][] assigncost, int[] pairCount, int[] rs, int[] cs, int[] u, int[] v, double d, Tree oldTree) {
            this.oldAssigncost = assigncost;
            this.oldPairCount = pairCount.clone();
            this.rowsol = rs.clone();
            this.colsol = cs.clone();
            this.u = u.clone();
            this.v = v.clone();
            this.distance = d;
            this.oldTree = oldTree;
        }
    }

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { history.clear(); deltaStack.clear(); }
    @Override public double getDistance(Tree t1, Tree t2, int... indexes) { return mpMetricFull.getDistance(t1, t2, indexes); }
    @Override public String getName() { return "Accelerated " + mpMetricFull.getName(); }
    @Override public String getCommandLineName() { return mpMetricFull.getCommandLineName(); }
    @Override public void setCommandLineName(String cln) { mpMetricFull.setCommandLineName(cln); }
    @Override public void setName(String name) { mpMetricFull.setName(name); }
    @Override public String getDescription() { return mpMetricFull.getDescription(); }
    @Override public void setDescription(String d) { mpMetricFull.setDescription(d); }
    @Override public void initData() { mpMetricFull.initData(); }
    @Override public boolean isRooted() { return true; }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return mpMetricFull.isDiffLeafSets(); }
    @Override public AlignInfo getAlignment() { return mpMetricFull.getAlignment(); }
}