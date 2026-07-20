package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.Tree;
import pal.misc.IdGroup;
import pal.tree.TreeUtils;
import treecmp.common.AlignInfo;
import treecmp.common.ClusterDist;
import treecmp.common.LapSolver;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingSplitMetric;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class MSIncrementalMetric implements IncrementalMetric {

    private Tree baseTree;
    private Tree targetTree;
    private double currentDistance;
    private IdGroup idGroup;

    private final MatchingSplitMetric msMetricFull = new MatchingSplitMetric();

    private int dim;
    private short[][] assigncost;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private Map<Node, BitSet> baseSplits;
    private Map<Node, BitSet> currentSplits;
    private Map<Node, BitSet> targetSplits;
    private Map<Node, Integer> nodeToRow;

    private Node[] rowToNode;
    private Node[] colToNode;

    private final Stack<short[][]> costHistory = new Stack<>();
    private final Stack<int[]> rowsolHistory = new Stack<>();
    private final Stack<int[]> colsolHistory = new Stack<>();
    private final Stack<int[]> uHistory = new Stack<>();
    private final Stack<int[]> vHistory = new Stack<>();
    private final Stack<Double> distanceHistory = new Stack<>();
    private final Stack<Map<Node, BitSet>> splitHistory = new Stack<>();
    private final Stack<Integer> nniPushCountHistory = new Stack<>();

    private static class LapStateDelta {
        final int[] rows;
        final short[][] oldRows;
        final int[] oldU, oldV, oldRowsol, oldColsol;
        final double oldDistance;
        final Map<Node, BitSet> oldSplits;

        LapStateDelta(int[] rows, short[][] oldRows, int[] oldU, int[] oldV, int[] oldRowsol, int[] oldColsol, double oldDistance, Map<Node, BitSet> oldSplits) {
            this.rows = rows;
            this.oldRows = oldRows;
            this.oldU = oldU;
            this.oldV = oldV;
            this.oldRowsol = oldRowsol;
            this.oldColsol = oldColsol;
            this.oldDistance = oldDistance;
            this.oldSplits = oldSplits;
        }
    }

    private final Stack<LapStateDelta> deltaStack = new Stack<>();

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        this.baseTree = baseTree;
        this.targetTree = targetTree;

        if (baseTree != null && targetTree != null) {
            clearHistory();
            this.idGroup = TreeUtils.getLeafIdGroup(baseTree);
            int numLeaves = baseTree.getExternalNodeCount();

            // Klasyczny wymiar N-3 (bez liści i korzenia wirtualnego)
            int size1 = baseTree.getInternalNodeCount() - 1;
            int size2 = targetTree.getInternalNodeCount() - 1;
            this.dim = Math.max(size1, size2);

            this.assigncost = new short[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];
            this.u = new int[dim];
            this.v = new int[dim];

            this.baseSplits = new IdentityHashMap<>();
            this.currentSplits = new IdentityHashMap<>();
            extractSplits(baseTree.getRoot(), idGroup, this.baseSplits, numLeaves, false);
            for (Map.Entry<Node, BitSet> e : baseSplits.entrySet()) {
                currentSplits.put(e.getKey(), (BitSet) e.getValue().clone());
            }

            this.targetSplits = new IdentityHashMap<>();
            extractSplits(targetTree.getRoot(), idGroup, this.targetSplits, numLeaves, true);

            this.rowToNode = new Node[dim];
            this.colToNode = new Node[dim];
            this.nodeToRow = new IdentityHashMap<>();

            int r = 0;
            for (int i = 0; i < baseTree.getInternalNodeCount(); i++) {
                Node n = baseTree.getInternalNode(i);
                if (n.isRoot()) continue;
                if (r < dim) {
                    rowToNode[r] = n;
                    nodeToRow.put(n, r);
                    r++;
                }
            }

            int c = 0;
            for (int j = 0; j < targetTree.getInternalNodeCount(); j++) {
                Node n = targetTree.getInternalNode(j);
                if (n.isRoot()) continue;
                if (c < dim) {
                    colToNode[c] = n;
                    c++;
                }
            }

            buildInitialCostMatrix(numLeaves);
            this.currentDistance = LapSolver.lapShort(dim, assigncost, rowsol, colsol, u, v);
        } else {
            this.currentDistance = 0;
        }
    }

    private BitSet extractSplits(Node node, IdGroup idGroup, Map<Node, BitSet> map, int numLeaves, boolean polarize) {
        BitSet bs = new BitSet(numLeaves);
        if (node.isLeaf()) {
            int id = idGroup.whichIdNumber(node.getIdentifier().getName());
            if (id >= 0) bs.set(id);
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                bs.or(extractSplits(node.getChild(i), idGroup, map, numLeaves, polarize));
            }
        }
        BitSet splitToSave = (BitSet) bs.clone();
        if (polarize && splitToSave.get(0)) {
            splitToSave.flip(0, numLeaves);
        }
        map.put(node, splitToSave);
        return bs;
    }

    private void buildInitialCostMatrix(int numLeaves) {
        for (int i = 0; i < dim; i++) {
            Node n1 = rowToNode[i];
            BitSet canonicalSplit = new BitSet(numLeaves);
            if (n1 != null && currentSplits.containsKey(n1)) {
                canonicalSplit = (BitSet) currentSplits.get(n1).clone();
                if (canonicalSplit.get(0)) canonicalSplit.flip(0, numLeaves);
            }

            for (int j = 0; j < dim; j++) {
                Node n2 = colToNode[j];
                if (n1 != null && n2 != null) {
                    short cost = (short) ClusterDist.getDistXorBit(canonicalSplit, targetSplits.get(n2));
                    // Prawdziwy dystans MS to min(koszt, N - koszt)
                    this.assigncost[i][j] = (short) Math.min(cost, numLeaves - cost);
                } else if (n1 != null) {
                    short cost = (short) canonicalSplit.cardinality();
                    this.assigncost[i][j] = (short) Math.min(cost, numLeaves - cost);
                } else if (n2 != null) {
                    short cost = (short) targetSplits.get(n2).cardinality();
                    this.assigncost[i][j] = (short) Math.min(cost, numLeaves - cost);
                } else {
                    this.assigncost[i][j] = 0;
                }
            }
        }
    }

    private void updateRowSafelyAndSave(Map<Integer, BitSet> rowUpdates) {
        int[] rows = new int[rowUpdates.size()];
        short[][] oldRows = new short[rows.length][dim];
        Map<Node, BitSet> oldSplits = new IdentityHashMap<>();

        int idx = 0;
        for (Map.Entry<Integer, BitSet> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            rows[idx] = r;
            oldRows[idx] = Arrays.copyOf(assigncost[r], dim);

            Node n = rowToNode[r];
            if (n != null && currentSplits.containsKey(n)) {
                oldSplits.put(n, (BitSet) currentSplits.get(n).clone());
            }
            idx++;
        }

        deltaStack.push(new LapStateDelta(rows, oldRows, Arrays.copyOf(u, dim), Arrays.copyOf(v, dim),
                Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance, oldSplits));

        int numLeaves = baseTree.getExternalNodeCount();

        for (Map.Entry<Integer, BitSet> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            BitSet newSplit = entry.getValue();

            Node n = rowToNode[r];
            if (n != null) currentSplits.put(n, newSplit);

            BitSet canonicalSplit = (BitSet) newSplit.clone();
            if (canonicalSplit.get(0)) canonicalSplit.flip(0, numLeaves);

            for (int j = 0; j < dim; j++) {
                Node n2 = colToNode[j];
                if (n2 != null) {
                    short cost = (short) ClusterDist.getDistXorBit(canonicalSplit, targetSplits.get(n2));
                    this.assigncost[r][j] = (short) Math.min(cost, numLeaves - cost);
                } else {
                    short cost = (short) canonicalSplit.cardinality();
                    this.assigncost[r][j] = (short) Math.min(cost, numLeaves - cost);
                }
            }
        }

        if (rows.length > 0) {
            this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, rows);
        }
    }

    private void undoDeltaStack() {
        if (deltaStack.isEmpty()) return;
        LapStateDelta delta = deltaStack.pop();
        for (int i = 0; i < delta.rows.length; i++) {
            System.arraycopy(delta.oldRows[i], 0, assigncost[delta.rows[i]], 0, dim);
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

    public double getFixedDistanceForRegraft(Node targetNode, Node wanderingSource, BitSet pruneMask, Node pruneNode) {
        Integer r_w = nodeToRow.get(wanderingSource);

        if (r_w == null) {
            return evaluateSprRegraft(pruneNode, targetNode);
        }

        BitSet origT = baseSplits.get(targetNode);
        BitSet pureT = (BitSet) origT.clone();
        pureT.andNot(pruneMask);

        BitSet combinedX = (BitSet) origT.clone();
        combinedX.or(pruneMask);

        BitSet currentT = currentSplits.get(targetNode);
        BitSet shadowEdge;
        if (currentT == null) {
            shadowEdge = combinedX;
        } else if (currentT.equals(pureT)) {
            shadowEdge = combinedX;
        } else {
            shadowEdge = pureT;
        }

        int numLeaves = baseTree.getExternalNodeCount();
        if (shadowEdge.get(0)) shadowEdge.flip(0, numLeaves);

        short[] oldRow = Arrays.copyOf(assigncost[r_w], dim);
        int[] oldU = Arrays.copyOf(u, dim);
        int[] oldV = Arrays.copyOf(v, dim);
        int[] oldRowsol = Arrays.copyOf(rowsol, dim);
        int[] oldColsol = Arrays.copyOf(colsol, dim);

        for (int j = 0; j < dim; j++) {
            Node n2 = colToNode[j];
            if (n2 != null) {
                short cost = (short) ClusterDist.getDistXorBit(shadowEdge, targetSplits.get(n2));
                assigncost[r_w][j] = (short) Math.min(cost, numLeaves - cost);
            } else {
                short cost = (short) shadowEdge.cardinality();
                assigncost[r_w][j] = (short) Math.min(cost, numLeaves - cost);
            }
        }

        double fixedDist = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, new int[]{r_w});

        System.arraycopy(oldRow, 0, assigncost[r_w], 0, dim);
        System.arraycopy(oldU, 0, u, 0, dim);
        System.arraycopy(oldV, 0, v, 0, dim);
        System.arraycopy(oldRowsol, 0, rowsol, 0, dim);
        System.arraycopy(oldColsol, 0, colsol, 0, dim);

        return fixedDist;
    }

    private BitSet getSplitBits(Node node) {
        if (node.isLeaf()) {
            BitSet bs = new BitSet(baseTree.getExternalNodeCount());
            int id = idGroup.whichIdNumber(node.getIdentifier().getName());
            if (id >= 0) bs.set(id);
            return bs;
        } else {
            return currentSplits.get(node);
        }
    }

    public boolean applyNniStep(Node nodeToUpdate, BitSet bitsOut, BitSet bitsIn) {
        Integer rIndex = nodeToRow.get(nodeToUpdate);
        if (rIndex == null) return false;

        BitSet newSplit = (BitSet) currentSplits.get(nodeToUpdate).clone();
        if (bitsOut != null) newSplit.andNot(bitsOut);
        if (bitsIn != null) newSplit.or(bitsIn);

        Map<Integer, BitSet> updates = new HashMap<>();
        updates.put(rIndex, newSplit);
        updateRowSafelyAndSave(updates);
        return true;
    }

    public void undoNniStep() {
        undoDeltaStack();
    }

    @Override
    public double applyNni(NniMove move) {
        Node nodeA = move.movingSubtree;
        Node nodeB = move.swapPartner;

        Node edgeNode = (nodeA.getParent() != baseTree.getRoot() && nodeA.getParent() != nodeB.getParent())
                ? nodeA.getParent() : nodeB.getParent();

        Integer rIndex = nodeToRow.get(edgeNode);
        if (rIndex != null) {
            BitSet oldSplit = currentSplits.get(edgeNode);
            BitSet newSplit = (BitSet) oldSplit.clone();
            newSplit.xor(getSplitBits(nodeA));
            newSplit.xor(getSplitBits(nodeB));

            Map<Integer, BitSet> updates = new HashMap<>();
            updates.put(rIndex, newSplit);
            updateRowSafelyAndSave(updates);
            nniPushCountHistory.push(1);
        } else {
            nniPushCountHistory.push(0);
        }
        return this.currentDistance;
    }

    @Override
    public void undoNni(NniMove move) {
        int pushes = nniPushCountHistory.isEmpty() ? 0 : nniPushCountHistory.pop();
        for (int i = 0; i < pushes; i++) {
            undoDeltaStack();
        }
    }

    @Override public void applySprPrune(Node pruneNode) { saveCurrentStateToHistory(); }
    @Override public void undoSprPrune(Node pruneNode) { undoSprRegraftStep(); }
    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) { saveCurrentStateToHistory(); }
    @Override public void undoSprRegraftStep() {
        if (!distanceHistory.isEmpty()) {
            this.assigncost = costHistory.pop();
            this.rowsol = rowsolHistory.pop();
            this.colsol = colsolHistory.pop();
            this.u = uHistory.pop();
            this.v = vHistory.pop();
            this.currentDistance = distanceHistory.pop();
            this.currentSplits = splitHistory.pop();
        }
    }

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        UsprUtils utils = new UsprUtils();
        Tree neighbor = utils.createUsprTree(baseTree, pruneNode, targetNode);
        try {
            if (neighbor instanceof pal.tree.SimpleTree) {
                ((pal.tree.SimpleTree) neighbor).createNodeList();
            }
            return msMetricFull.getDistance(neighbor, targetTree);
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }
    }

    // ========================================================================
    // O(1) INCREMENTAL EXTENDED CLUSTER REDUCTION (ECR)
    // ========================================================================

    @Override
    public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        double dist = commit2sEcrMove(top, m1, m2, b, template);
        undoDeltaStack(); // Bezpieczne cofnięcie transakcji
        return dist;
    }

    @Override
    public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        Map<Integer, BitSet> updates = new HashMap<>();
        BitSet[] bBits = new BitSet[4];
        for (int i = 0; i < 4; i++) {
            bBits[i] = getSplitBits(b[i]);
        }

        BitSet newM1 = new BitSet();
        BitSet newM2 = new BitSet();

        if (template.isFork) {
            newM1.or(bBits[template.indices[0]]);
            newM1.or(bBits[template.indices[1]]);
            newM2.or(bBits[template.indices[2]]);
            newM2.or(bBits[template.indices[3]]);
        } else {
            newM2.or(bBits[template.indices[2]]);
            newM2.or(bBits[template.indices[3]]);
            newM1.or(bBits[template.indices[1]]);
            newM1.or(newM2); // CHAIN: m1 zawiera m2
        }

        Integer r1 = nodeToRow.get(m1);
        if (r1 != null) updates.put(r1, newM1);
        Integer r2 = nodeToRow.get(m2);
        if (r2 != null) updates.put(r2, newM2);

        updateRowSafelyAndSave(updates);
        return this.currentDistance;
    }

    @Override
    public double evaluate3sEcrMove(List<Node> cluster, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR template) {
        double dist = commit3sEcrMove(cluster, b, template);
        undoDeltaStack(); // Bezpieczne cofnięcie transakcji
        return dist;
    }

    @Override
    public double commit3sEcrMove(List<Node> cluster, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR template) {
        Map<Integer, BitSet> updates = new HashMap<>();
        BitSet[] bBits = new BitSet[5];
        for (int i = 0; i < 5; i++) {
            bBits[i] = getSplitBits(b[i]);
        }

        Node[] available = cluster.toArray(new Node[0]);
        int[] idxArr = {1}; // indeks 0 to 'top' (niezmienny), alokujemy od 1

        compute3sEcrTemplateBits(template, available[0], available, idxArr, bBits, updates);

        updateRowSafelyAndSave(updates);
        return this.currentDistance;
    }

    private BitSet compute3sEcrTemplateBits(SubtreeEcr3Utils.TopologyTemplate3sECR temp, Node currentInternal, Node[] available, int[] idxArr, BitSet[] bBits, Map<Integer, BitSet> updates) {
        BitSet myBits = new BitSet();

        // Lewe poddrzewo z szablonu
        if (temp.left.leafIndex != -1) {
            myBits.or(bBits[temp.left.leafIndex]);
        } else {
            Node nextInternal = available[idxArr[0]++];
            BitSet leftBits = compute3sEcrTemplateBits(temp.left, nextInternal, available, idxArr, bBits, updates);
            myBits.or(leftBits);
        }

        // Prawe poddrzewo z szablonu
        if (temp.right.leafIndex != -1) {
            myBits.or(bBits[temp.right.leafIndex]);
        } else {
            Node nextInternal = available[idxArr[0]++];
            BitSet rightBits = compute3sEcrTemplateBits(temp.right, nextInternal, available, idxArr, bBits, updates);
            myBits.or(rightBits);
        }

        // Top Node pozostaje niezmienny (reprezentuje cały klaster), aktualizujemy tylko wewnętrzne
        if (currentInternal != available[0]) {
            Integer r = nodeToRow.get(currentInternal);
            if (r != null) {
                updates.put(r, myBits);
            }
        }

        return myBits;
    }
    private void saveCurrentStateToHistory() {
        short[][] costCopy = new short[dim][dim];
        for (int i = 0; i < dim; i++) {
            System.arraycopy(this.assigncost[i], 0, costCopy[i], 0, dim);
        }
        costHistory.push(costCopy);
        rowsolHistory.push(this.rowsol.clone());
        colsolHistory.push(this.colsol.clone());
        uHistory.push(this.u.clone());
        vHistory.push(this.v.clone());
        distanceHistory.push(this.currentDistance);

        IdentityHashMap<Node, BitSet> splitsCopy = new IdentityHashMap<>();
        for (Map.Entry<Node, BitSet> entry : currentSplits.entrySet()) {
            splitsCopy.put(entry.getKey(), (BitSet) entry.getValue().clone());
        }
        splitHistory.push(splitsCopy);
    }

    private void clearHistory() {
        costHistory.clear();
        rowsolHistory.clear();
        colsolHistory.clear();
        uHistory.clear();
        vHistory.clear();
        distanceHistory.clear();
        splitHistory.clear();
        deltaStack.clear();
        nniPushCountHistory.clear();
    }

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { clearHistory(); }
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