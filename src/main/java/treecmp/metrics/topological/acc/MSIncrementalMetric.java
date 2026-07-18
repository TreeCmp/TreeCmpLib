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

    private static class LapStateDelta {
        final int rowIndex;
        final short[] oldRow;
        final int[] oldU, oldV, oldRowsol, oldColsol;
        final double oldDistance;
        final BitSet oldSplit;
        final Node movingNode;

        LapStateDelta(int rowIndex, short[] oldRow, int[] oldU, int[] oldV, int[] oldRowsol, int[] oldColsol, double oldDistance, BitSet oldSplit, Node movingNode) {
            this.rowIndex = rowIndex;
            this.oldRow = oldRow;
            this.oldU = oldU;
            this.oldV = oldV;
            this.oldRowsol = oldRowsol;
            this.oldColsol = oldColsol;
            this.oldDistance = oldDistance;
            this.oldSplit = oldSplit;
            this.movingNode = movingNode;
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
            // Mapujemy całe drzewo dla definicji Cienia, ale do macierzy pójdą tylko węzły wewnętrzne
            extractSplits(baseTree.getRoot(), idGroup, this.baseSplits, baseTree.getExternalNodeCount(), false);
            for (Map.Entry<Node, BitSet> e : baseSplits.entrySet()) {
                currentSplits.put(e.getKey(), (BitSet) e.getValue().clone());
            }

            this.targetSplits = new IdentityHashMap<>();
            extractSplits(targetTree.getRoot(), idGroup, this.targetSplits, targetTree.getExternalNodeCount(), true);

            this.rowToNode = new Node[dim];
            this.colToNode = new Node[dim];
            this.nodeToRow = new IdentityHashMap<>();

            // Ładujemy TYLKO węzły wewnętrzne (LIŚCIE ZIGNOROWANE W LAPSOLVERZE)
            int r = 0;
            for (int i = 0; i < baseTree.getInternalNodeCount(); i++) {
                Node n = baseTree.getInternalNode(i);
                // TARCZA OCHRONNA ODBLOKOWANA:
                if (n.isRoot()) continue;

                if (r < dim) { // Zabezpieczenie przed przepełnieniem
                    rowToNode[r] = n;
                    nodeToRow.put(n, r);
                    r++;
                }
            }

            int c = 0;
            for (int j = 0; j < targetTree.getInternalNodeCount(); j++) {
                Node n = targetTree.getInternalNode(j);
                // TARCZA OCHRONNA ODBLOKOWANA:
                if (n.isRoot()) continue;

                if (c < dim) { // Zabezpieczenie przed przepełnieniem
                    colToNode[c] = n;
                    c++;
                }
            }

            buildInitialCostMatrix();
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

    private void buildInitialCostMatrix() {
        int numLeaves = baseTree.getExternalNodeCount();
        for (int i = 0; i < dim; i++) {
            Node n1 = rowToNode[i];

            BitSet canonicalSplit = new BitSet(numLeaves);
            if (n1 != null && currentSplits.containsKey(n1)) {
                canonicalSplit = (BitSet) currentSplits.get(n1).clone();
                if (canonicalSplit.get(0)) {
                    canonicalSplit.flip(0, numLeaves);
                }
            }

            for (int j = 0; j < dim; j++) {
                Node n2 = colToNode[j];
                if (n1 != null && n2 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistXorBit(canonicalSplit, targetSplits.get(n2));
                } else if (n1 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistToOAsMinBit(canonicalSplit);
                } else if (n2 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistToOAsMinBit(targetSplits.get(n2));
                } else {
                    this.assigncost[i][j] = 0;
                }
            }
        }
    }

    public boolean applyNniStep(Node nodeToUpdate, BitSet bitsOut, BitSet bitsIn) {
        Integer rIndex = nodeToRow.get(nodeToUpdate);
        // ZABEZPIECZENIE: Jeśli węzła nie ma w macierzy (liść lub sztuczny korzeń), przerywamy!
        if (rIndex == null) return false;

        int rowIndex = rIndex;
        short[] oldRow = Arrays.copyOf(assigncost[rowIndex], dim);
        int[] oldU = Arrays.copyOf(u, dim);
        int[] oldV = Arrays.copyOf(v, dim);
        int[] oldRowsol = Arrays.copyOf(rowsol, dim);
        int[] oldColsol = Arrays.copyOf(colsol, dim);
        BitSet oldSplit = (BitSet) currentSplits.get(nodeToUpdate).clone();

        deltaStack.push(new LapStateDelta(rowIndex, oldRow, oldU, oldV, oldRowsol, oldColsol, currentDistance, oldSplit, nodeToUpdate));

        BitSet newSplit = (BitSet) oldSplit.clone();
        if (bitsOut != null) newSplit.andNot(bitsOut);
        if (bitsIn != null) newSplit.or(bitsIn);
        currentSplits.put(nodeToUpdate, newSplit);

        BitSet canonicalSplit = (BitSet) newSplit.clone();
        int numLeaves = baseTree.getExternalNodeCount();
        if (canonicalSplit.get(0)) {
            canonicalSplit.flip(0, numLeaves);
        }

        for (int j = 0; j < dim; j++) {
            Node n2 = colToNode[j];
            if (n2 != null) {
                this.assigncost[rowIndex][j] = (short) ClusterDist.getDistXorBit(canonicalSplit, targetSplits.get(n2));
            } else {
                this.assigncost[rowIndex][j] = (short) ClusterDist.getDistToOAsMinBit(canonicalSplit);
            }
        }

        this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, new int[]{rowIndex});

        // SUKCES: Stan został odłożony na stos, zwracamy TRUE
        return true;
    }

    public void undoNniStep() {
        if (deltaStack.isEmpty()) return;
        LapStateDelta delta = deltaStack.pop();

        System.arraycopy(delta.oldRow, 0, assigncost[delta.rowIndex], 0, dim);
        System.arraycopy(delta.oldU, 0, u, 0, dim);
        System.arraycopy(delta.oldV, 0, v, 0, dim);
        System.arraycopy(delta.oldRowsol, 0, rowsol, 0, dim);
        System.arraycopy(delta.oldColsol, 0, colsol, 0, dim);

        currentSplits.put(delta.movingNode, delta.oldSplit);
        this.currentDistance = delta.oldDistance;
    }

    public double getFixedDistanceForRegraft(Node targetNode, Node wanderingSource, BitSet pruneMask, Node pruneNode) {
        Integer r_w = nodeToRow.get(wanderingSource);

        if (r_w == null) {
            for (int i = 0; i < wanderingSource.getChildCount(); i++) {
                Node child = wanderingSource.getChild(i);
                if (child != pruneNode && nodeToRow.containsKey(child)) {
                    r_w = nodeToRow.get(child);
                    break;
                }
            }
            if (r_w == null) return this.currentDistance;
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
                assigncost[r_w][j] = (short) ClusterDist.getDistXorBit(shadowEdge, targetSplits.get(n2));
            } else {
                assigncost[r_w][j] = (short) ClusterDist.getDistToOAsMinBit(shadowEdge);
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
        Tree tempTree = new UsprUtils().createUsprTree(this.baseTree, pruneNode, targetNode);
        if (tempTree != null) {
            if (tempTree instanceof pal.tree.SimpleTree) {
                ((pal.tree.SimpleTree) tempTree).createNodeList();
            }
            return msMetricFull.getDistance(tempTree, this.targetTree);
        }
        return Double.POSITIVE_INFINITY;
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
    }

    @Override public double applyNni(NniMove move) { return 0; }
    @Override public void undoNni(NniMove move) { }
    @Override public double evaluate2sEcrMove(Node t, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR n) { return 0; }
    @Override public double commit2sEcrMove(Node t, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR n) { return 0; }
    @Override public double evaluate3sEcrMove(List<Node> c, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR n) { return 0; }
    @Override public double commit3sEcrMove(List<Node> c, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR n) { return 0; }
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

    public void printDebugMatrix(String prefix) {
        System.out.println(prefix + " | Dim: " + dim + " | Current Dist: " + currentDistance);
        for (int i = 0; i < dim; i++) {
            Node n1 = rowToNode[i];
            BitSet split = (n1 != null) ? currentSplits.get(n1) : null;
            System.out.print("  Wiersz " + i + " (Węzeł " + (n1 != null ? n1.getNumber() : "NULL") + ", Split: " + split + ") Koszty: [");
            for (int j = 0; j < dim; j++) {
                System.out.print(assigncost[i][j] + (j < dim - 1 ? ", " : ""));
            }
            System.out.println("]");
        }
    }

}