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
import treecmp.heuristics.spr.acc.IncrementalSprWalker;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.BaseMetric;
import treecmp.metrics.topological.MatchingPairMetric;

import java.util.*;

public class MPIncrementalMetric extends BaseMetric implements IncrementalMetric, IncrementalSprWalker.RootedMetric {

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
    private Map<Node, BitSet> currentSplits;
    private Node activePruneNode = null;

    private int[][] assigncost;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private int[] t2IntPairCount;
    private int[] currentT1PairCount;
    private Map<Node, Integer> nodeToRow;

    // PREALOKOWANE BUFORY ROBOCZE (Zero-Allocation w gorących ścieżkach)
    private int[] scratchIntersections;
    private int[] scratchChangedRowAll;

    private final MatchingPairMetric mpMetricFull = new MatchingPairMetric();
    private final Stack<StateRecord> history = new Stack<>();
    private final Stack<LapStateDelta> deltaStack = new Stack<>();

    private static class LapStateDelta {
        final int[] rows;
        final int[][] oldRows;
        final int[] oldPairCounts;
        final int[] oldU, oldV, oldRowsol, oldColsol;
        final double oldDistance;
        final Map<Node, BitSet> oldSplits;

        LapStateDelta(int[] rows, int[][] oldRows, int[] oldPairCounts,
                      int[] oldU, int[] oldV, int[] oldRowsol, int[] oldColsol, double oldDistance, Map<Node, BitSet> oldSplits) {
            this.rows = rows; this.oldRows = oldRows; this.oldPairCounts = oldPairCounts;
            this.oldU = oldU; this.oldV = oldV; this.oldRowsol = oldRowsol; this.oldColsol = oldColsol;
            this.oldDistance = oldDistance;
            this.oldSplits = oldSplits;
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

            this.scratchIntersections = new int[dim];
            this.scratchChangedRowAll = new int[dim];

            this.assigncost = new int[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];
            this.u = new int[dim];
            this.v = new int[dim];
            this.currentT1PairCount = new int[dim];

            int expectedMapSize = (N * 2 + 5) * 4 / 3;
            this.nodeToRow = new IdentityHashMap<>(expectedMapSize);
            this.baseSplits = new IdentityHashMap<>(expectedMapSize);
            this.currentSplits = new IdentityHashMap<>(expectedMapSize);

            int maxNodesT2 = getSafeMaxNodeId(this.targetTree);
            this.targetIdToCol = new int[maxNodesT2];
            Arrays.fill(this.targetIdToCol, -1);
            for (int i = 0; i < intT2Num; i++) {
                this.targetIdToCol[this.targetTree.getInternalNode(i).getNumber()] = i;
            }

            int maxNodesT1 = getSafeMaxNodeId(this.baseTree);
            int[] baseIdToRow = new int[maxNodesT1];
            Arrays.fill(baseIdToRow, -1);

            for (int i = 0; i < intT1Num; i++) {
                Node nBase = this.baseTree.getInternalNode(i);
                Node nVirt = this.currentVirtualTree.getInternalNode(i);
                baseIdToRow[nBase.getNumber()] = i;
                nodeToRow.put(nBase, i);
                nodeToRow.put(nVirt, i);
            }

            Node[] allNodesBaseCopy = TreeCmpUtils.getAllNodes(this.baseTree);
            for (Node n : allNodesBaseCopy) {
                BitSet split = getLeaves(n, baseIdGroup);
                baseSplits.put(n, split);
                currentSplits.put(n, (BitSet) split.clone());
            }

            Node[] allNodesVirtCopy = TreeCmpUtils.getAllNodes(this.currentVirtualTree);
            for (Node n : allNodesVirtCopy) {
                BitSet split = getLeaves(n, baseIdGroup);
                baseSplits.put(n, split);
                currentSplits.put(n, (BitSet) split.clone());
            }

            for (int i = 0; i < baseTree.getInternalNodeCount(); i++) {
                Node nOrig = baseTree.getInternalNode(i);
                Node nCopy = getMappedNode(this.baseTree, nOrig);
                if (nCopy != null) {
                    Integer row = nodeToRow.get(nCopy);
                    if (row != null) {
                        nodeToRow.put(nOrig, row);
                    }
                    baseSplits.put(nOrig, baseSplits.get(nCopy));
                    currentSplits.put(nOrig, (BitSet) currentSplits.get(nCopy).clone());
                }
            }

            int[][] lcaMatrix1 = TreeCmpUtils.calcLcaMatrix(this.baseTree, this.baseIdGroup);
            this.targetLcaMatrix = TreeCmpUtils.calcLcaMatrix(this.targetTree, this.baseIdGroup);

            short[] cSize2 = new short[maxNodesT2];
            Node[] postOrderT2 = TreeCmpUtils.getNodesInPostOrder(this.targetTree);
            TreeCmpUtils.calcCladeSizes(this.targetTree, postOrderT2, cSize2);

            this.t2IntPairCount = new int[dim];
            for (int i = 0; i < intT2Num; i++) {
                this.t2IntPairCount[i] = coutChildrenPairsFast(this.targetTree.getInternalNode(i), cSize2);
            }

            short[] cSize1 = new short[maxNodesT1];
            Node[] postOrderT1 = TreeCmpUtils.getNodesInPostOrder(this.baseTree);
            TreeCmpUtils.calcCladeSizes(this.baseTree, postOrderT1, cSize1);

            for (int i = 0; i < intT1Num; i++) {
                this.currentT1PairCount[i] = coutChildrenPairsFast(this.baseTree.getInternalNode(i), cSize1);
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

            int rawMetric = LapSolver.lap(dim, lapCost, rowsol, colsol, u, v);
            this.currentDistance = 0.5 * rawMetric;
        } else {
            this.currentDistance = 0;
        }
    }

    private Integer getRowForNode(Node n) {
        if (n == null || n.isLeaf()) return null;
        Integer row = nodeToRow.get(n);
        if (row != null) return row;
        Node mapped = getMappedNode(this.currentVirtualTree, n);
        if (mapped == null) mapped = getMappedNode(this.baseTree, n);
        if (mapped != null && !mapped.isLeaf()) {
            row = nodeToRow.get(mapped);
            if (row != null) {
                nodeToRow.put(n, row);
                return row;
            }
        }
        return null;
    }

    private BitSet getSplitForNode(Node n) {
        if (n == null) return new BitSet(N);
        BitSet bs = currentSplits.get(n);
        if (bs != null) return bs;
        Node mapped = getMappedNode(this.baseTree, n);
        if (mapped != null) {
            bs = currentSplits.get(mapped);
            if (bs != null) {
                currentSplits.put(n, bs);
                return bs;
            }
        }
        return getLeaves(n, baseIdGroup);
    }

    private void updateRowsSafelyAndSave(Map<Integer, BitSet[]> rowUpdates) {
        int[] rows = new int[rowUpdates.size()];
        int[][] oldRows = new int[rows.length][dim];
        int[] oldPairCounts = new int[rows.length];

        int idx = 0;
        for (Map.Entry<Integer, BitSet[]> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            rows[idx] = r;
            oldRows[idx] = Arrays.copyOf(assigncost[r], dim);
            oldPairCounts[idx] = currentT1PairCount[r];
            idx++;
        }

        deltaStack.push(new LapStateDelta(
                rows, oldRows, oldPairCounts,
                Arrays.copyOf(u, dim), Arrays.copyOf(v, dim),
                Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance, new IdentityHashMap<>()
        ));

        for (Map.Entry<Integer, BitSet[]> entry : rowUpdates.entrySet()) {
            computeRowCost(entry.getKey(), entry.getValue());
        }

        if (rows.length > 0) {
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, rows);
            this.currentDistance = 0.5 * rawMetric;
        }
    }

    private void undoDeltaStack() {
        if (deltaStack.isEmpty()) return;
        LapStateDelta delta = deltaStack.pop();

        for (int i = 0; i < delta.rows.length; i++) {
            System.arraycopy(delta.oldRows[i], 0, assigncost[delta.rows[i]], 0, dim);
            currentT1PairCount[delta.rows[i]] = delta.oldPairCounts[i];
        }

        System.arraycopy(delta.oldU, 0, u, 0, dim);
        System.arraycopy(delta.oldV, 0, v, 0, dim);
        System.arraycopy(delta.oldRowsol, 0, rowsol, 0, dim);
        System.arraycopy(delta.oldColsol, 0, colsol, 0, dim);

        for (Map.Entry<Node, BitSet> e : delta.oldSplits.entrySet()) {
            currentSplits.put(e.getKey(), e.getValue());
        }

        this.currentDistance = delta.oldDistance;
    }

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        BitSet P = getBaseSplit(pruneNode);
        Map<Integer, BitSet[]> updates = new HashMap<>();

        Node curr = pruneNode.getParent().getParent();
        while (curr != null) {
            Integer r = getRowForNode(curr);
            if (r != null) {
                int chCount = curr.getChildCount();
                BitSet[] cSets = new BitSet[chCount];
                for (int i = 0; i < chCount; i++) {
                    cSets[i] = (BitSet) getBaseSplit(curr.getChild(i)).clone();
                    cSets[i].andNot(P);
                }
                updates.put(r, cSets);
            }
            curr = curr.getParent();
        }

        Integer r_floating = getRowForNode(pruneNode.getParent());
        if (r_floating != null) updates.put(r_floating, new BitSet[0]);

        Integer r_w = getRowForNode(wanderingSource);
        if (r_w != null) updates.put(r_w, new BitSet[0]);

        updateRowsSafelyAndSave(updates);
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node wanderingSource) {
        BitSet P = getBaseSplit(pruneNode);
        Map<Integer, BitSet[]> updates = new HashMap<>();

        Integer r_floating = getRowForNode(pruneNode.getParent());
        if (r_floating != null) {
            BitSet restOfTree = new BitSet(N);
            restOfTree.set(0, N);
            restOfTree.andNot(P);
            updates.put(r_floating, new BitSet[]{P, restOfTree});
        }
        updateRowsSafelyAndSave(updates);
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        BitSet P = getBaseSplit(pruneNode);
        Map<Integer, BitSet[]> updates = new HashMap<>();

        Integer r_floating = getRowForNode(pruneNode.getParent());
        if (r_floating != null) {
            BitSet cTarget = (BitSet) getBaseSplit(childTarget).clone();
            cTarget.andNot(P);
            updates.put(r_floating, new BitSet[]{P, cTarget});
        }

        Integer r_p = getRowForNode(parentTarget);
        if (r_p != null && !r_p.equals(r_floating)) {
            int chCount = parentTarget.getChildCount();
            BitSet[] cSets = new BitSet[chCount];
            BitSet targetLeaves = getBaseSplit(childTarget);

            for (int i = 0; i < chCount; i++) {
                cSets[i] = (BitSet) getBaseSplit(parentTarget.getChild(i)).clone();
                cSets[i].andNot(P);

                if (cSets[i].intersects(targetLeaves)) {
                    cSets[i].or(P);
                }
            }
            updates.put(r_p, cSets);
        }
        updateRowsSafelyAndSave(updates);
    }

    @Override public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) { undoDeltaStack(); }
    @Override public void revertPrunedState(Node pruneNode, Node wanderingSource) { undoDeltaStack(); undoDeltaStack(); }
    @Override public void applySprPrune(Node pruneNode) { this.activePruneNode = pruneNode; }
    @Override public void undoSprPrune(Node pruneNode) { this.activePruneNode = null; }

    public double applyNniStep(Node nodeToUpdate, BitSet bitsOut, BitSet bitsIn) {
        Node v = nodeToUpdate;
        Node u = v.getParent();

        Integer rVIndex = getRowForNode(v);
        Integer rUIndex = getRowForNode(u);
        if (rVIndex == null || rUIndex == null) return this.currentDistance;

        int rowV = rVIndex;
        int rowU = rUIndex;

        int[] rows = {rowV, rowU};
        int[][] oldRows = {Arrays.copyOf(assigncost[rowV], dim), Arrays.copyOf(assigncost[rowU], dim)};
        int[] oldPairCounts = {currentT1PairCount[rowV], currentT1PairCount[rowU]};

        deltaStack.push(new LapStateDelta(rows, oldRows, oldPairCounts,
                Arrays.copyOf(this.u, dim), Arrays.copyOf(this.v, dim),
                Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance, new IdentityHashMap<>()));

        Node currentPos = (bitsIn != null) ? v : u;

        updateRowPairs(rowV, v, currentPos);
        updateRowPairs(rowU, u, currentPos);

        int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, this.u, this.v, rows);
        this.currentDistance = 0.5 * rawMetric;

        return this.currentDistance;
    }

    public void undoNniStep() {
        undoDeltaStack();
    }

    private void updateRowPairs(int row, Node n, Node currentPos) {
        BitSet[] cSets;
        if (n == currentPos) {
            cSets = new BitSet[2];
            cSets[0] = getBaseSplit(activePruneNode);
            BitSet cTarget = (BitSet) getBaseSplit(n).clone();
            cTarget.andNot(getBaseSplit(activePruneNode));
            cSets[1] = cTarget;
        } else if (n == activePruneNode.getParent()) {
            cSets = new BitSet[0];
        } else {
            int chCount = n.getChildCount();
            cSets = new BitSet[chCount];
            for (int i = 0; i < chCount; i++) {
                cSets[i] = getCurrentCluster(n.getChild(i), currentPos, activePruneNode);
            }
        }
        computeRowCost(row, cSets);
    }

    private BitSet getCurrentCluster(Node x, Node currentPos, Node pruneNode) {
        if (x == pruneNode) return new BitSet(N);
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

    private BitSet[] getPartitionsForNode(Node n) {
        if (activePruneNode != null && n == activePruneNode.getParent()) {
            return new BitSet[0];
        }
        int chCount = n.getChildCount();
        BitSet[] cSets = new BitSet[chCount];
        for (int i = 0; i < chCount; i++) {
            Node child = n.getChild(i);
            if (activePruneNode != null && child == activePruneNode) {
                cSets[i] = new BitSet(N);
            } else {
                cSets[i] = getSplitForNode(child);
            }
        }
        return cSets;
    }

    // ZERO-ALLOCATION + BRANCHLESS 2-QUADRANT LOOP + DIRECT LCA LOOKUP
    private void computeRowCost(int row, BitSet[] sets) {
        Arrays.fill(scratchIntersections, 0, dim, 0);
        int pairsCount = 0;

        for (int i = 0; i < sets.length; i++) {
            BitSet setA = sets[i];
            if (setA == null || setA.isEmpty()) continue;
            for (int j = i + 1; j < sets.length; j++) {
                BitSet setB = sets[j];
                if (setB == null || setB.isEmpty()) continue;

                for (int l1 = setA.nextSetBit(0); l1 >= 0; l1 = setA.nextSetBit(l1 + 1)) {
                    int[] lcaRowT2 = targetLcaMatrix[l1];
                    for (int l2 = setB.nextSetBit(0); l2 >= 0; l2 = setB.nextSetBit(l2 + 1)) {
                        int lcaT2 = lcaRowT2[l2];
                        if (lcaT2 >= 0) {
                            int c = targetIdToCol[lcaT2];
                            if (c >= 0) scratchIntersections[c]++;
                        }
                        pairsCount++;
                    }
                }
            }
        }

        currentT1PairCount[row] = pairsCount;

        int[] costRow = assigncost[row];
        for (int c = 0; c < intT2Num; c++) {
            costRow[c] = pairsCount + t2IntPairCount[c] - (scratchIntersections[c] << 1);
        }
        for (int c = intT2Num; c < dim; c++) {
            costRow[c] = pairsCount;
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
        SimpleTree tNew = (SimpleTree) createCleanCopy(currentVirtualTree);
        Node P = getMappedNode(tNew, pruneNode);
        Node T = getMappedNode(tNew, targetNode);

        if (P == null || T == null) return Double.POSITIVE_INFINITY;
        Node p = P.getParent();
        if (p == null || p == T || p == T.getParent() || isDescendantOrSelf(T, P)) return Double.POSITIVE_INFINITY;

        Node pp = p.getParent();
        Node sibling = (p.getChild(0) == P) ? p.getChild(1) : p.getChild(0);

        Node newRoot = tNew.getRoot();
        if (pp != null) {
            int pIdx = findChildPos(p, pp);
            pp.setChild(pIdx, sibling);
            sibling.setParent(pp);
        } else {
            newRoot = sibling;
            sibling.setParent(null);
        }

        Node q = T.getParent();
        if (q != null) {
            int vIdx = findChildPos(T, q);
            q.setChild(vIdx, p);
            p.setParent(q);
        } else {
            newRoot = p;
            p.setParent(null);
        }

        p.setChild(0, P);
        P.setParent(p);
        p.setChild(1, T);
        T.setParent(p);

        SimpleTree finalTree = new SimpleTree(newRoot);
        finalTree.createNodeList();
        pal.tree.TreeUtils.computeParentPointers(finalTree.getRoot());
        pal.tree.TreeUtils.mapExternalIdentifiers(this.baseIdGroup, finalTree);

        return mpMetricFull.getDistance(finalTree, this.targetTree);
    }

    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) { throw new UnsupportedOperationException(); }
    @Override public void undoSprRegraftStep() { throw new UnsupportedOperationException(); }

    private void refreshAllRowsInPlace() {
        int[] countWrap = {0};
        postOrderRefresh(this.currentVirtualTree.getRoot(), countWrap);
        if (countWrap[0] > 0) {
            int[] rowsToUpdate = Arrays.copyOf(scratchChangedRowAll, countWrap[0]);
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, rowsToUpdate);
            this.currentDistance = 0.5 * rawMetric;
        }
    }

    private void postOrderRefresh(Node n, int[] countWrap) {
        if (n == null || n.isLeaf()) return;
        for (int i = 0; i < n.getChildCount(); i++) {
            postOrderRefresh(n.getChild(i), countWrap);
        }
        BitSet split = new BitSet(N);
        for (int i = 0; i < n.getChildCount(); i++) {
            split.or(getSplitForNode(n.getChild(i)));
        }
        currentSplits.put(n, split);

        Integer row = getRowForNode(n);
        if (row != null) {
            scratchChangedRowAll[countWrap[0]++] = row;
            computeRowCost(row, getPartitionsForNode(n));
        }
    }

    private double pushUnchangedState() {
        history.push(new StateRecord(assigncost, currentT1PairCount, rowsol, colsol, u, v, currentDistance, currentVirtualTree, currentSplits, nodeToRow, null, null));
        return this.currentDistance;
    }

    @Override
    public double applyNni(NniMove move) {
        Node virtMoving = getMappedNode(this.currentVirtualTree, move.movingSubtree);
        Node virtPartner = getMappedNode(this.currentVirtualTree, move.swapPartner);

        if (virtMoving == null || virtPartner == null) return pushUnchangedState();

        Node p1 = virtMoving.getParent();
        Node p2 = virtPartner.getParent();
        if (p1 == null || p2 == null || p1 == p2) return pushUnchangedState();

        int idx1 = findChildPos(virtMoving, p1);
        int idx2 = findChildPos(virtPartner, p2);
        if (idx1 == -1 || idx2 == -1) return pushUnchangedState();

        history.push(new StateRecord(assigncost, currentT1PairCount, rowsol, colsol, u, v, currentDistance, currentVirtualTree, currentSplits, nodeToRow, virtMoving, virtPartner));

        p1.setChild(idx1, virtPartner); virtPartner.setParent(p1);
        p2.setChild(idx2, virtMoving); virtMoving.setParent(p2);

        refreshAllRowsInPlace();

        return this.currentDistance;
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

            if (r.nniMovingNode != null && r.nniPartnerNode != null) {
                Node p1 = r.nniPartnerNode.getParent();
                Node p2 = r.nniMovingNode.getParent();
                if (p1 != null && p2 != null) {
                    int i1 = findChildPos(r.nniPartnerNode, p1);
                    int i2 = findChildPos(r.nniMovingNode, p2);
                    if (i1 != -1 && i2 != -1) {
                        p1.setChild(i1, r.nniMovingNode); r.nniMovingNode.setParent(p1);
                        p2.setChild(i2, r.nniPartnerNode); r.nniPartnerNode.setParent(p2);
                    }
                }
            } else {
                this.currentVirtualTree = r.oldTree;
            }

            this.currentSplits.clear();
            this.currentSplits.putAll(r.oldSplits);
            this.nodeToRow = new IdentityHashMap<>(r.oldNodeToRow);
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

    private int coutChildrenPairsFast(Node n, short[] clustSizeTab) {
        int chCount = n.getChildCount();
        int sum = 0;
        int sumSq = 0;

        for (int i = 0; i < chCount; i++) {
            Node chNode = n.getChild(i);
            int size = chNode.isLeaf() ? 1 : clustSizeTab[chNode.getNumber()];
            sum += size;
            sumSq += size * size;
        }
        return (sum * sum - sumSq) >> 1;
    }

    // NATYWNY, BEZALOKACYJNY KOMPARATOR BITSETÓW
    private static final Comparator<BitSet> BITSET_COMPARATOR = (a, b) -> {
        if (a == b) return 0;
        int cA = a.cardinality();
        int cB = b.cardinality();
        if (cA != cB) return Integer.compare(cA, cB);

        int i = a.nextSetBit(0);
        int j = b.nextSetBit(0);
        while (i >= 0 && j >= 0) {
            if (i != j) return Integer.compare(i, j);
            i = a.nextSetBit(i + 1);
            j = b.nextSetBit(j + 1);
        }
        return 0;
    };

    // ZERO-ALLOCATION SIGNATURE (Bez BitSet.toString!)
    private static class Signature {
        private final BitSet[] canonicalParts;
        private final int cachedHashCode;

        public Signature(Node n, int N, IdGroup idGroup) {
            int chCount = n.getChildCount();
            this.canonicalParts = new BitSet[chCount];
            for (int i = 0; i < chCount; i++) {
                canonicalParts[i] = getLeaves(n.getChild(i), idGroup);
            }
            Arrays.sort(this.canonicalParts, BITSET_COMPARATOR);
            this.cachedHashCode = Arrays.hashCode(this.canonicalParts);
        }

        @Override
        public int hashCode() {
            return cachedHashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Signature)) return false;
            Signature other = (Signature) obj;
            if (this.cachedHashCode != other.cachedHashCode) return false;
            return Arrays.equals(this.canonicalParts, other.canonicalParts);
        }

        @Override
        public String toString() {
            return Arrays.toString(canonicalParts);
        }
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
        Map<Node, BitSet> oldSplits;
        Map<Node, Integer> oldNodeToRow;
        Node nniMovingNode;
        Node nniPartnerNode;

        StateRecord(int[][] assigncost, int[] pairCount, int[] rs, int[] cs, int[] u, int[] v, double d, Tree oldTree,
                    Map<Node, BitSet> currentSplits, Map<Node, Integer> nodeToRow, Node nniMovingNode, Node nniPartnerNode) {
            this.oldAssigncost = new int[assigncost.length][];
            for (int i = 0; i < assigncost.length; i++) {
                this.oldAssigncost[i] = assigncost[i].clone();
            }
            this.oldPairCount = pairCount.clone();
            this.rowsol = rs.clone();
            this.colsol = cs.clone();
            this.u = u.clone();
            this.v = v.clone();
            this.distance = d;
            this.oldTree = oldTree;
            this.oldSplits = new IdentityHashMap<>(currentSplits);
            this.oldNodeToRow = new IdentityHashMap<>(nodeToRow);
            this.nniMovingNode = nniMovingNode;
            this.nniPartnerNode = nniPartnerNode;
        }
    }

    private double calculateCleanSlateDistance(SimpleTree tNew, boolean isCommit, int maxCostBound) {
        // KLUCZOWA POPRAWKA: Użycie natywnego klucza Signature zamiast String hash
        Map<Signature, Integer> sigToOldRow = new HashMap<>();
        for (int i = 0; i < intT1Num; i++) {
            Node n = this.currentVirtualTree.getInternalNode(i);
            Signature sig = new Signature(n, N, baseIdGroup);
            sigToOldRow.put(sig, i);
        }

        Tree tPerfect = createCleanCopy(tNew);
        int[] newToOld = new int[dim];
        int[] oldToNew = new int[dim];
        Arrays.fill(newToOld, -1);
        Arrays.fill(oldToNew, -1);

        for (int r_new = 0; r_new < intT1Num; r_new++) {
            Node n = tPerfect.getInternalNode(r_new);
            Signature sig = new Signature(n, N, baseIdGroup);
            Integer r_old = sigToOldRow.get(sig);
            if (r_old != null && oldToNew[r_old] == -1) {
                newToOld[r_new] = r_old;
                oldToNew[r_old] = r_new;
            }
        }

        int unmappedOld = 0;
        for (int r_new = 0; r_new < dim; r_new++) {
            if (newToOld[r_new] == -1) {
                while (unmappedOld < dim && oldToNew[unmappedOld] != -1) unmappedOld++;
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

        pal.tree.TreeUtils.mapExternalIdentifiers(this.baseIdGroup, tPerfect);
        int[][] lcaNew = TreeCmpUtils.calcLcaMatrix(tPerfect, this.baseIdGroup);
        int[][] newIntersection = new int[dim][dim];

        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                int ind1 = lcaNew[i][j];
                int ind2 = this.targetLcaMatrix[i][j];
                if (ind1 >= 0 && ind1 < idToRow.length && ind2 >= 0 && ind2 < this.targetIdToCol.length) {
                    int r_new = idToRow[ind1];
                    int c = this.targetIdToCol[ind2];
                    if (r_new >= 0 && c >= 0) newIntersection[r_new][c]++;
                }
            }
        }

        short[] cSizeNew = new short[maxNodesNew];
        Node[] postOrderNew = TreeCmpUtils.getNodesInPostOrder(tPerfect);
        TreeCmpUtils.calcCladeSizes(tPerfect, postOrderNew, cSizeNew);

        int[] newT1PairCount = new int[dim];
        for (int r_new = 0; r_new < intT1Num; r_new++) {
            newT1PairCount[r_new] = coutChildrenPairsFast(tPerfect.getInternalNode(r_new), cSizeNew);
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
        for (int i = 0; i < dim; i++) System.arraycopy(tempAssigncost[i], 0, lapCost[i], 0, dim);

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
            history.push(new StateRecord(this.assigncost, this.currentT1PairCount, this.rowsol, this.colsol, this.u, this.v, this.currentDistance, this.currentVirtualTree, this.currentSplits, this.nodeToRow, null, null));
            this.assigncost = tempAssigncost;
            this.currentT1PairCount = newT1PairCount;
            this.currentVirtualTree = tPerfect;
            this.rowsol = tempRowsol;
            this.colsol = tempColsol;
            this.u = tempU;
            this.v = tempV;
            this.currentDistance = dist;

            this.currentSplits.clear();
            this.nodeToRow.clear();
            Node[] allNodesPerfect = TreeCmpUtils.getAllNodes(tPerfect);
            for (Node n : allNodesPerfect) {
                BitSet split = getLeaves(n, baseIdGroup);
                this.currentSplits.put(n, split);
                if (!n.isLeaf()) {
                    for (int i = 0; i < intT1Num; i++) {
                        if (tPerfect.getInternalNode(i) == n) {
                            this.nodeToRow.put(n, i);
                            break;
                        }
                    }
                }
            }
            Node[] allNodesOriginal = TreeCmpUtils.getAllNodes(this.baseTree);
            for (Node nOrig : allNodesOriginal) {
                Node nCopy = getMappedNode(tPerfect, nOrig);
                if (nCopy != null) {
                    this.currentSplits.put(nOrig, (BitSet) this.currentSplits.get(nCopy).clone());
                    Integer row = this.nodeToRow.get(nCopy);
                    if (row != null) {
                        this.nodeToRow.put(nOrig, row);
                    }
                }
            }
        }

        return dist;
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