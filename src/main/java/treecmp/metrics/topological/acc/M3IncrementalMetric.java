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
import treecmp.heuristics.tbr.UTbrUtils;
import treecmp.heuristics.tbr.acc.RootedTbrMetric;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingTripletMetric;

import java.util.*;

public class M3IncrementalMetric implements IncrementalMetric, RootedTbrMetric {

    private Tree baseTree;
    private Tree targetTree;
    private double currentDistance;
    private int dim;
    private int intT1Num;
    private int intT2Num;
    private int N;
    private long allLeavesMask;

    private IdGroup baseIdGroup;
    private Map<Node, Integer> nodeToRow;
    private Node[] rowToNode;

    private int[][] assigncost;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private int[] currentT1TripletCount;
    private int[] t2IntTripletCount;

    // 3 rozłączne gałęzie (maski bitowe) dla każdego węzła wewnętrznego T1
    private long[] branch0;
    private long[] branch1;
    private long[] branch2;

    // Maski podziałów trójdzielnych drzewa docelowego T2
    private long[][] targetMask;

    private long currentPrunedMask;

    private final MatchingTripletMetric mtMetricFull = new MatchingTripletMetric();
    private final Stack<LapStateDelta> deltaStack = new Stack<>();
    private final UTbrUtils utbrUtils = new UTbrUtils();
    private final UsprUtils usprUtils = new UsprUtils();

    public BitSet getSplit(Node n) {
        BitSet bs = new BitSet(N);
        long m = getLeafMask(n);
        while (m != 0L) {
            int bit = Long.numberOfTrailingZeros(m);
            bs.set(bit);
            m &= (m - 1L);
        }
        return bs;
    }

    private static class LapStateDelta {
        final int[] rows;
        final int[][] oldRows;
        final int[] oldTripletCounts;
        final long[][] oldBranches;
        final int[] oldU, oldV, oldRowsol, oldColsol;
        final double oldDistance;

        LapStateDelta(int[] rows, int[][] oldRows, int[] oldTripletCounts, long[][] oldBranches,
                      int[] oldU, int[] oldV, int[] oldRowsol, int[] oldColsol, double oldDistance) {
            this.rows = rows;
            this.oldRows = oldRows;
            this.oldTripletCounts = oldTripletCounts;
            this.oldBranches = oldBranches;
            this.oldU = oldU;
            this.oldV = oldV;
            this.oldRowsol = oldRowsol;
            this.oldColsol = oldColsol;
            this.oldDistance = oldDistance;
        }
    }

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        deltaStack.clear();

        if (baseTree != null && targetTree != null) {
            // BEZWZGLĘDNY BRAK KLONOWANIA: Używamy przekazanych referencji węzłów bezpośrednio
            this.baseTree = baseTree;
            this.targetTree = targetTree;
            this.baseIdGroup = TreeUtils.getLeafIdGroup(this.baseTree);
            this.N = this.baseTree.getExternalNodeCount();
            this.allLeavesMask = (N >= 64) ? -1L : ((1L << N) - 1L);

            this.intT1Num = this.baseTree.getInternalNodeCount();
            this.intT2Num = this.targetTree.getInternalNodeCount();
            this.dim = Math.max(intT1Num, intT2Num);

            this.assigncost = new int[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];
            this.u = new int[dim];
            this.v = new int[dim];

            this.currentT1TripletCount = new int[dim];
            this.t2IntTripletCount = new int[dim];

            this.branch0 = new long[dim];
            this.branch1 = new long[dim];
            this.branch2 = new long[dim];
            this.targetMask = new long[dim][3];

            this.nodeToRow = new IdentityHashMap<>(dim * 2);
            this.rowToNode = new Node[dim];

            for (int i = 0; i < intT1Num; i++) {
                Node n = this.baseTree.getInternalNode(i);
                nodeToRow.put(n, i);
                rowToNode[i] = n;
            }

            // Prekompilacja masek drzewa docelowego T2
            for (int c = 0; c < intT2Num; c++) {
                Node n2 = this.targetTree.getInternalNode(c);
                long[] b = getTripartiteLeaves(n2, this.targetTree.getRoot());
                targetMask[c][0] = b[0];
                targetMask[c][1] = b[1];
                targetMask[c][2] = b[2];
                t2IntTripletCount[c] = Long.bitCount(b[0]) * Long.bitCount(b[1]) * Long.bitCount(b[2]);
            }

            // Inicjalizacja podziałów drzewa T1
            for (int r = 0; r < intT1Num; r++) {
                Node n1 = this.baseTree.getInternalNode(r);
                long[] b = getTripartiteLeaves(n1, this.baseTree.getRoot());
                branch0[r] = b[0];
                branch1[r] = b[1];
                branch2[r] = b[2];
                computeRowCost(r, b[0], b[1], b[2]);
            }

            for (int r = intT1Num; r < dim; r++) {
                for (int c = 0; c < dim; c++) {
                    assigncost[r][c] = (c < intT2Num) ? t2IntTripletCount[c] : 0;
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

    private long[] getTripartiteLeaves(Node n, Node root) {
        long[] b = new long[3];
        int chCount = n.getChildCount();
        long union = 0L;

        for (int i = 0; i < chCount && i < 2; i++) {
            b[i] = getLeafMask(n.getChild(i));
            union |= b[i];
        }

        if (n != root) {
            b[2] = allLeavesMask ^ union;
        } else if (chCount >= 3) {
            b[2] = getLeafMask(n.getChild(2));
        }
        return b;
    }

    private long getLeafMask(Node n) {
        if (n.isLeaf()) {
            int id = baseIdGroup.whichIdNumber(n.getIdentifier().getName());
            return (id >= 0 && id < 64) ? (1L << id) : 0L;
        }
        long mask = 0L;
        for (int i = 0; i < n.getChildCount(); i++) {
            mask |= getLeafMask(n.getChild(i));
        }
        return mask;
    }

    private void computeRowCost(int row, long b0, long b1, long b2) {
        int card0 = Long.bitCount(b0);
        int card1 = Long.bitCount(b1);
        int card2 = Long.bitCount(b2);
        int tripletCount = card0 * card1 * card2;
        currentT1TripletCount[row] = tripletCount;

        for (int c = 0; c < intT2Num; c++) {
            long x = targetMask[c][0];
            long y = targetMask[c][1];
            long z = targetMask[c][2];

            int aX = Long.bitCount(b0 & x);
            int aY = Long.bitCount(b0 & y);
            int aZ = Long.bitCount(b0 & z);

            int bX = Long.bitCount(b1 & x);
            int bY = Long.bitCount(b1 & y);
            int bZ = Long.bitCount(b1 & z);

            int cX = Long.bitCount(b2 & x);
            int cY = Long.bitCount(b2 & y);
            int cZ = Long.bitCount(b2 & z);

            int inter = aX * (bY * cZ + bZ * cY)
                    + aY * (bX * cZ + bZ * cX)
                    + aZ * (bX * cY + bY * cX);

            assigncost[row][c] = tripletCount + t2IntTripletCount[c] - (inter << 1);
        }

        for (int c = intT2Num; c < dim; c++) {
            assigncost[row][c] = tripletCount;
        }
    }

    private void updateRowsSafelyAndSave(Map<Integer, long[]> rowUpdates) {
        int count = rowUpdates.size();
        int[] rows = new int[count];
        int[][] oldRows = new int[count][dim];
        int[] oldTripletCounts = new int[count];
        long[][] oldBranches = new long[count][3];

        int idx = 0;
        for (Map.Entry<Integer, long[]> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            rows[idx] = r;
            oldRows[idx] = Arrays.copyOf(assigncost[r], dim);
            oldTripletCounts[idx] = currentT1TripletCount[r];
            oldBranches[idx][0] = branch0[r];
            oldBranches[idx][1] = branch1[r];
            oldBranches[idx][2] = branch2[r];
            idx++;
        }

        deltaStack.push(new LapStateDelta(
                rows, oldRows, oldTripletCounts, oldBranches,
                Arrays.copyOf(u, dim), Arrays.copyOf(v, dim),
                Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim),
                currentDistance
        ));

        for (Map.Entry<Integer, long[]> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            long[] b = entry.getValue();
            branch0[r] = b[0];
            branch1[r] = b[1];
            branch2[r] = b[2];
            computeRowCost(r, b[0], b[1], b[2]);
        }

        if (count > 0) {
            int rawMetric = LapSolver.lapUpdate(dim, assigncost, rowsol, colsol, u, v, rows);
            this.currentDistance = 0.5 * rawMetric;
        }
    }

    private void undoDeltaStack() {
        if (deltaStack.isEmpty()) return;
        LapStateDelta delta = deltaStack.pop();

        for (int i = 0; i < delta.rows.length; i++) {
            int r = delta.rows[i];
            System.arraycopy(delta.oldRows[i], 0, assigncost[r], 0, dim);
            currentT1TripletCount[r] = delta.oldTripletCounts[i];
            branch0[r] = delta.oldBranches[i][0];
            branch1[r] = delta.oldBranches[i][1];
            branch2[r] = delta.oldBranches[i][2];
        }

        System.arraycopy(delta.oldU, 0, u, 0, dim);
        System.arraycopy(delta.oldV, 0, v, 0, dim);
        System.arraycopy(delta.oldRowsol, 0, rowsol, 0, dim);
        System.arraycopy(delta.oldColsol, 0, colsol, 0, dim);

        this.currentDistance = delta.oldDistance;
    }

    // =========================================================================
    // IMPLEMENTACJA KROKÓW 2D-DFS uTBR
    // =========================================================================

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        this.currentPrunedMask = getLeafMask(pruneNode);
        long P = this.currentPrunedMask;
        Map<Integer, long[]> updates = new HashMap<>();

        // Propagacja odcięcia poddrzewa P w górę ścieżki do korzenia
        Node curr = wanderingSource.getParent();
        while (curr != null) {
            Integer r = nodeToRow.get(curr);
            if (r != null) {
                long b0 = branch0[r], b1 = branch1[r], b2 = branch2[r];
                if ((b0 & P) != 0L) {
                    b0 &= ~P;
                    if (curr.getParent() != null) b2 |= P;
                } else if ((b1 & P) != 0L) {
                    b1 &= ~P;
                    if (curr.getParent() != null) b2 |= P;
                } else if ((b2 & P) != 0L) {
                    b2 &= ~P;
                }
                updates.put(r, new long[]{b0, b1, b2});
            }
            curr = curr.getParent();
        }

        Integer r_floating = nodeToRow.get(wanderingSource);
        if (r_floating != null) {
            updates.put(r_floating, new long[]{0L, 0L, 0L});
        }

        updateRowsSafelyAndSave(updates);
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {
        long P = (this.currentPrunedMask != 0L) ? this.currentPrunedMask : getLeafMask(pruneNode);
        Map<Integer, long[]> updates = new HashMap<>();

        Integer r_floating = nodeToRow.get(wanderingSource);
        if (r_floating != null) {
            Node root = baseTree.getRoot();
            long b0 = getLeafMask(root.getChild(0)) & ~P;
            long b1 = getLeafMask(root.getChild(1)) & ~P;
            long b2 = (allLeavesMask ^ P) ^ (b0 | b1);
            updates.put(r_floating, new long[]{P, b0, (b1 == 0L) ? b2 : b1});
        }
        updateRowsSafelyAndSave(updates);
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        long P = (this.currentPrunedMask != 0L) ? this.currentPrunedMask : getLeafMask(pruneNode);
        Map<Integer, long[]> updates = new HashMap<>();

        Integer r_floating = nodeToRow.get(wanderingSource);
        long childLeaves = getLeafMask(childTarget) & ~P;
        long rest = (allLeavesMask ^ P) ^ childLeaves;

        if (r_floating != null) {
            updates.put(r_floating, new long[]{P, childLeaves, rest});
        }

        Integer r_parent = nodeToRow.get(parentTarget);
        if (r_parent != null && !r_parent.equals(r_floating)) {
            long b0 = branch0[r_parent], b1 = branch1[r_parent], b2 = branch2[r_parent];
            if ((b0 & childLeaves) != 0L) {
                b0 |= P;
                b2 &= ~P;
            } else if ((b1 & childLeaves) != 0L) {
                b1 |= P;
                b2 &= ~P;
            } else {
                b2 |= P;
            }
            updates.put(r_parent, new long[]{b0, b1, b2});
        }

        updateRowsSafelyAndSave(updates);
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        undoDeltaStack();
    }

    @Override
    public void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode) {
        long P = (this.currentPrunedMask != 0L) ? this.currentPrunedMask : getLeafMask(pruneNode);
        long Q = allLeavesMask ^ P;
        long cLeaves = getLeafMask(childReroot);
        long pMinusC = P ^ cLeaves;

        Map<Integer, long[]> updates = new HashMap<>();
        Integer rPrune = nodeToRow.get(pruneNode);
        if (rPrune != null) {
            updates.put(rPrune, new long[]{cLeaves, pMinusC, Q});
        }

        if (parentReroot != pruneNode) {
            Integer rParent = nodeToRow.get(parentReroot);
            if (rParent != null) {
                long b0 = branch0[rParent], b1 = branch1[rParent], b2 = branch2[rParent];
                if ((b0 & cLeaves) != 0L) {
                    b0 |= Q; b2 &= ~Q;
                } else if ((b1 & cLeaves) != 0L) {
                    b1 |= Q; b2 &= ~Q;
                } else {
                    b2 |= Q;
                }
                updates.put(rParent, new long[]{b0, b1, b2});
            }
        }

        if (!updates.isEmpty()) {
            updateRowsSafelyAndSave(updates);
        } else {
            deltaStack.push(new LapStateDelta(new int[0], new int[0][0], new int[0], new long[0][0],
                    Arrays.copyOf(u, dim), Arrays.copyOf(v, dim),
                    Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance));
        }
    }

    @Override
    public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {
        undoDeltaStack();
    }

    @Override
    public void revertPrunedState(Node pruneNode, Node wanderingSource) {
        undoDeltaStack();
        undoDeltaStack();
    }

    // =========================================================================
    // METODY WYMAGANE PRZEZ SPR, ECR ORAZ INTERFEJS
    // =========================================================================

    public boolean applyNniStep(Node nodeToUpdate, BitSet bitsOut, BitSet bitsIn) { return false; }
    public void undoNniStep() { undoDeltaStack(); }

    public double getFixedDistanceForRegraft(Node targetNode, Node wanderingSource, BitSet pruneMask, Node pruneNode) {
        return evaluateSprRegraft(pruneNode, targetNode);
    }

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        Tree tempTree = usprUtils.createUsprTree(baseTree, pruneNode, targetNode);
        if (tempTree != null) {
            return mtMetricFull.getDistance(tempTree, targetTree);
        }
        return Double.POSITIVE_INFINITY;
    }

    public double evaluateExactUTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        Tree tempTree = utbrUtils.createUtbrTree(baseTree, pruneNode, rerootNode, targetNode);
        if (tempTree != null) {
            if (tempTree instanceof SimpleTree) {
                ((SimpleTree) tempTree).createNodeList();
            }
            return mtMetricFull.getDistance(tempTree, targetTree);
        }
        return Double.POSITIVE_INFINITY;
    }

    public double evaluateExactUtbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        return evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, movingBits);
    }

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        return evaluateExactUTbrDistance(pruneNode, rerootNode, targetNode, movingBits);
    }

    @Override public void applySprPrune(Node pruneNode) { }
    @Override public void undoSprPrune(Node pruneNode) { }
    @Override public void applySprRegraftStep(Node pruneNode, Node currentNode) { throw new UnsupportedOperationException(); }
    @Override public void undoSprRegraftStep() { throw new UnsupportedOperationException(); }

    @Override public double applyNni(NniMove move) { return currentDistance; }
    @Override public void undoNni(NniMove move) { }
    @Override public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR t) { return currentDistance; }
    @Override public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR t) { return currentDistance; }
    @Override public double evaluate3sEcrMove(List<Node> c, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR t) { return currentDistance; }
    @Override public double commit3sEcrMove(List<Node> c, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR t) { return currentDistance; }

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { deltaStack.clear(); }
    @Override public double getDistance(Tree t1, Tree t2, int... indexes) { return mtMetricFull.getDistance(t1, t2, indexes); }
    @Override public String getName() { return "Accelerated " + mtMetricFull.getName(); }
    @Override public String getCommandLineName() { return mtMetricFull.getCommandLineName(); }
    @Override public void setCommandLineName(String cln) { mtMetricFull.setCommandLineName(cln); }
    @Override public void setName(String name) { mtMetricFull.setName(name); }
    @Override public String getDescription() { return mtMetricFull.getDescription(); }
    @Override public void setDescription(String d) { mtMetricFull.setDescription(d); }
    @Override public void initData() { mtMetricFull.initData(); }
    @Override public boolean isRooted() { return false; }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return mtMetricFull.isDiffLeafSets(); }
    @Override public AlignInfo getAlignment() { return mtMetricFull.getAlignment(); }
}