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
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.acc.IncrementalSprWalker;
import treecmp.heuristics.tbr.acc.IncrementalTbrWalker;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingClusterMetric;

import java.util.*;

public class MCIncrementalMetric implements IncrementalMetric,
        IncrementalSprWalker.RootedMetric,
        IncrementalTbrWalker.RootedTbrMetric{

    private Tree baseTree;
    private Tree targetTree;
    private double currentDistance;
    private IdGroup idGroup;
    private int N;

    private final MatchingClusterMetric mcMetricFull = new MatchingClusterMetric();

    private int dim;
    private short[][] assigncost;
    private int[] rowsol;
    private int[] colsol;
    private int[] u;
    private int[] v;

    private Map<Node, BitSet> currentClusters;
    private Map<Node, BitSet> targetClusters;
    private Map<Node, Integer> nodeToRow;

    private Node[] rowToNode;
    private Node[] colToNode;

    private final Stack<short[][]> costHistory = new Stack<>();
    private final Stack<int[]> rowsolHistory = new Stack<>();
    private final Stack<int[]> colsolHistory = new Stack<>();
    private final Stack<int[]> uHistory = new Stack<>();
    private final Stack<int[]> vHistory = new Stack<>();
    private final Stack<Double> distanceHistory = new Stack<>();
    private final Stack<Map<Node, BitSet>> clusterHistory = new Stack<>();

    private final Stack<Integer> nniPushCountHistory = new Stack<>();

    private final treecmp.heuristics.tbr.TbrUtils tbrUtils = new treecmp.heuristics.tbr.TbrUtils();

    // Pole pamiętające pełny zbiór liści odcinanego komponentu
    private BitSet currentPrunedLeaves;

    private static class LapStateDelta {
        final int[] rows;
        final short[][] oldRows;
        final int[] oldU, oldV, oldRowsol, oldColsol;
        final double oldDistance;
        final Map<Node, BitSet> oldClust;

        LapStateDelta(int[] rows, short[][] oldRows, int[] oldU, int[] oldV, int[] oldRowsol, int[] oldColsol, double oldDistance, Map<Node, BitSet> oldClust) {
            this.rows = rows;
            this.oldRows = oldRows;
            this.oldU = oldU;
            this.oldV = oldV;
            this.oldRowsol = oldRowsol;
            this.oldColsol = oldColsol;
            this.oldDistance = oldDistance;
            this.oldClust = oldClust;
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
            this.N = baseTree.getExternalNodeCount();

            int size1 = baseTree.getInternalNodeCount() - 1;
            int size2 = targetTree.getInternalNodeCount() - 1;
            this.dim = Math.max(size1, size2);

            this.assigncost = new short[dim][dim];
            this.rowsol = new int[dim];
            this.colsol = new int[dim];
            this.u = new int[dim];
            this.v = new int[dim];

            this.currentClusters = new IdentityHashMap<>();
            extractClusters(baseTree.getRoot(), idGroup, this.currentClusters);

            this.targetClusters = new IdentityHashMap<>();
            extractClusters(targetTree.getRoot(), idGroup, this.targetClusters);

            this.rowToNode = new Node[dim];
            this.colToNode = new Node[dim];
            this.nodeToRow = new IdentityHashMap<>();

            int r = 0;
            for (int i = 0; i < baseTree.getInternalNodeCount(); i++) {
                Node n = baseTree.getInternalNode(i);
                if (!n.isRoot() && r < dim) {
                    rowToNode[r] = n;
                    nodeToRow.put(n, r);
                    r++;
                }
            }

            int c = 0;
            for (int j = 0; j < targetTree.getInternalNodeCount(); j++) {
                Node n = targetTree.getInternalNode(j);
                if (!n.isRoot() && c < dim) {
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

    private BitSet extractClusters(Node node, IdGroup idGroup, Map<Node, BitSet> map) {
        BitSet bs = new BitSet();
        if (node.isLeaf()) {
            int id = idGroup.whichIdNumber(node.getIdentifier().getName());
            if (id >= 0) bs.set(id);
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                bs.or(extractClusters(node.getChild(i), idGroup, map));
            }
            map.put(node, (BitSet) bs.clone());
        }
        return bs;
    }

    private void buildInitialCostMatrix() {
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                Node n1 = rowToNode[i];
                Node n2 = colToNode[j];

                if (n1 != null && n2 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistXorBit(currentClusters.get(n1), targetClusters.get(n2));
                } else if (n1 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistToOAsMinBit(currentClusters.get(n1));
                } else if (n2 != null) {
                    this.assigncost[i][j] = (short) ClusterDist.getDistToOAsMinBit(targetClusters.get(n2));
                } else {
                    this.assigncost[i][j] = 0;
                }
            }
        }
    }

    public BitSet getCluster(Node n) {
        if (n.isLeaf()) {
            BitSet bs = new BitSet();
            int id = idGroup.whichIdNumber(n.getIdentifier().getName());
            if (id >= 0) bs.set(id);
            return bs;
        } else {
            return currentClusters.get(n);
        }
    }

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        Tree tempTree = tbrUtils.createTbrTree(this.baseTree, pruneNode, rerootNode, targetNode);
        if (tempTree != null) {
            if (tempTree instanceof pal.tree.SimpleTree) {
                ((pal.tree.SimpleTree) tempTree).createNodeList();
            }
            return mcMetricFull.getDistance(tempTree, this.targetTree);
        }
        return Double.POSITIVE_INFINITY;
    }

    private void updateRowSafelyAndSave(Map<Integer, BitSet> rowUpdates) {
        int[] rows = new int[rowUpdates.size()];
        short[][] oldRows = new short[rows.length][dim];
        Map<Node, BitSet> oldClust = new IdentityHashMap<>();

        int idx = 0;
        for (Map.Entry<Integer, BitSet> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            rows[idx] = r;
            oldRows[idx] = Arrays.copyOf(assigncost[r], dim);

            Node n = rowToNode[r];
            if (n != null && currentClusters.containsKey(n)) {
                oldClust.put(n, (BitSet) currentClusters.get(n).clone());
            }
            idx++;
        }

        deltaStack.push(new LapStateDelta(rows, oldRows, Arrays.copyOf(u, dim), Arrays.copyOf(v, dim),
                Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance, oldClust));

        for (Map.Entry<Integer, BitSet> entry : rowUpdates.entrySet()) {
            int r = entry.getKey();
            BitSet newCluster = entry.getValue();

            Node n = rowToNode[r];
            if (n != null) currentClusters.put(n, newCluster);

            for (int j = 0; j < dim; j++) {
                Node n2 = colToNode[j];
                if (n2 != null) {
                    this.assigncost[r][j] = (short) ClusterDist.getDistXorBit(newCluster, targetClusters.get(n2));
                } else {
                    this.assigncost[r][j] = (short) ClusterDist.getDistToOAsMinBit(newCluster);
                }
            }
        }

        if (rows.length > 0) {
            this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, rows);
        }
    }

    private Node resolveWandering(Node wanderingSource, Node pruneNode) {
        if (wanderingSource != null && wanderingSource.isRoot()) {
            Node p = pruneNode.getParent();
            for (int i = 0; i < p.getChildCount(); i++) {
                if (p.getChild(i) != pruneNode) return p.getChild(i);
            }
        }
        return wanderingSource;
    }

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        // Zapisujemy stały zbiór liści odciętego poddrzewa na cały cykl TBR dla danego pruneNode
        this.currentPrunedLeaves = (BitSet) getCluster(pruneNode).clone();
        BitSet P = this.currentPrunedLeaves;
        Map<Integer, BitSet> updates = new HashMap<>();

        Node curr = pruneNode.getParent().getParent();
        while (curr != null) {
            Integer r = nodeToRow.get(curr);
            if (r != null) {
                BitSet bs = (BitSet) currentClusters.get(curr).clone();
                bs.andNot(P);
                if (bs.cardinality() == N) bs.clear();
                updates.put(r, bs);
            }
            curr = curr.getParent();
        }

        Integer r_p = nodeToRow.get(resolveWandering(wanderingSource, pruneNode));
        if (r_p != null) {
            BitSet empty = new BitSet();
            updates.put(r_p, empty);
        }
        updateRowSafelyAndSave(updates);
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node wanderingSource) {
        BitSet P = (this.currentPrunedLeaves != null) ? this.currentPrunedLeaves : getCluster(pruneNode);
        Map<Integer, BitSet> updates = new HashMap<>();

        Integer r_p = nodeToRow.get(resolveWandering(wanderingSource, pruneNode));
        if (r_p != null) {
            BitSet newRp = (BitSet) getCluster(baseTree.getRoot()).clone();
            newRp.andNot(P);
            if (newRp.cardinality() == N) newRp.clear();
            updates.put(r_p, newRp);
        }
        updateRowSafelyAndSave(updates);
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        BitSet P = (this.currentPrunedLeaves != null) ? this.currentPrunedLeaves : getCluster(pruneNode);
        Map<Integer, BitSet> updates = new HashMap<>();

        Node resolvedWandering = resolveWandering(wanderingSource, pruneNode);
        Integer r_p = nodeToRow.get(resolvedWandering);
        if (r_p != null) {
            BitSet childCluster = currentClusters.containsKey(childTarget) ? currentClusters.get(childTarget) : getCluster(childTarget);
            BitSet newRp = (BitSet) childCluster.clone();
            newRp.or(P);
            if (newRp.cardinality() == N) newRp.clear();
            updates.put(r_p, newRp);
        }

        Integer r_parent = nodeToRow.get(parentTarget);
        if (r_parent != null && parentTarget != wanderingSource && parentTarget != resolvedWandering) {
            BitSet parentCluster = currentClusters.get(parentTarget);
            BitSet newParent = (BitSet) parentCluster.clone();
            newParent.or(P);
            if (newParent.cardinality() == N) newParent.clear();
            updates.put(r_parent, newParent);
        }

        updateRowSafelyAndSave(updates);
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        undoDeltaStack();
    }

    @Override
    public void revertPrunedState(Node pruneNode, Node wanderingSource) {
        undoDeltaStack();
        undoDeltaStack();
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

        for (Map.Entry<Node, BitSet> e : delta.oldClust.entrySet()) {
            currentClusters.put(e.getKey(), e.getValue());
        }
        this.currentDistance = delta.oldDistance;
    }

    @Override
    public double applyNni(NniMove move) {
        Node nodeA = move.movingSubtree;
        Node nodeB = move.swapPartner;

        Node pA = nodeA.getParent();
        Node pB = nodeB.getParent();

        BitSet clusterA = getCluster(nodeA);
        BitSet clusterB = getCluster(nodeB);

        int pushes = 0;
        if (pA.getParent() == pB) {
            if (applyNniStep(pA, clusterA, clusterB)) pushes++;
        } else if (pB.getParent() == pA) {
            if (applyNniStep(pB, clusterB, clusterA)) pushes++;
        } else {
            if (applyNniStep(pA, clusterA, clusterB)) pushes++;
            if (applyNniStep(pB, clusterB, clusterA)) pushes++;
        }
        nniPushCountHistory.push(pushes);
        return this.currentDistance;
    }

    @Override
    public void undoNni(NniMove move) {
        int pushes = nniPushCountHistory.isEmpty() ? 0 : nniPushCountHistory.pop();
        for (int i = 0; i < pushes; i++) {
            undoDeltaStack();
        }
    }

    public boolean applyNniStep(Node nodeToUpdate, BitSet bitsOut, BitSet bitsIn) {
        Integer rIndex = nodeToRow.get(nodeToUpdate);
        if (rIndex == null) return false;

        BitSet newCluster = (BitSet) currentClusters.get(nodeToUpdate).clone();
        if (bitsOut != null) newCluster.andNot(bitsOut);
        if (bitsIn != null) newCluster.or(bitsIn);

        if (newCluster.cardinality() == N) newCluster.clear();

        Map<Integer, BitSet> updates = new HashMap<>();
        updates.put(rIndex, newCluster);
        updateRowSafelyAndSave(updates);
        return true;
    }

    public void undoNniStep() {
        undoDeltaStack();
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
            this.currentClusters = clusterHistory.pop();
        }
    }

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        Tree tempTree = new SprUtils().createSprTree(this.baseTree, pruneNode, targetNode);
        if (tempTree != null) {
            if (tempTree instanceof pal.tree.SimpleTree) {
                ((pal.tree.SimpleTree) tempTree).createNodeList();
            }
            return mcMetricFull.getDistance(tempTree, this.targetTree);
        }
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        double dist = commit2sEcrMove(top, m1, m2, b, template);
        undoDeltaStack();
        return dist;
    }

    @Override
    public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] b, SubtreeEcr2Utils.TopologyTemplate2sECR template) {
        Map<Integer, BitSet> updates = new HashMap<>();
        BitSet[] bBits = new BitSet[4];
        for (int i = 0; i < 4; i++) {
            bBits[i] = getCluster(b[i]);
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
            newM1.or(newM2);
        }

        Integer r1 = nodeToRow.get(m1);
        if (r1 != null) {
            if (newM1.cardinality() == N) newM1.clear();
            updates.put(r1, newM1);
        }
        Integer r2 = nodeToRow.get(m2);
        if (r2 != null) {
            if (newM2.cardinality() == N) newM2.clear();
            updates.put(r2, newM2);
        }

        updateRowSafelyAndSave(updates);
        return this.currentDistance;
    }

    @Override
    public double evaluate3sEcrMove(List<Node> cluster, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR template) {
        double dist = commit3sEcrMove(cluster, b, template);
        undoDeltaStack();
        return dist;
    }

    @Override
    public double commit3sEcrMove(List<Node> cluster, Node[] b, SubtreeEcr3Utils.TopologyTemplate3sECR template) {
        Map<Integer, BitSet> updates = new HashMap<>();
        BitSet[] bBits = new BitSet[5];
        for (int i = 0; i < 5; i++) {
            bBits[i] = getCluster(b[i]);
        }

        Node[] available = cluster.toArray(new Node[0]);
        int[] idxArr = {1};

        compute3sEcrTemplateBits(template, available[0], available, idxArr, bBits, updates);

        updateRowSafelyAndSave(updates);
        return this.currentDistance;
    }

    private BitSet compute3sEcrTemplateBits(SubtreeEcr3Utils.TopologyTemplate3sECR temp, Node currentInternal, Node[] available, int[] idxArr, BitSet[] bBits, Map<Integer, BitSet> updates) {
        BitSet myBits = new BitSet();

        if (temp.left.leafIndex != -1) {
            myBits.or(bBits[temp.left.leafIndex]);
        } else {
            Node nextInternal = available[idxArr[0]++];
            BitSet leftBits = compute3sEcrTemplateBits(temp.left, nextInternal, available, idxArr, bBits, updates);
            myBits.or(leftBits);
        }

        if (temp.right.leafIndex != -1) {
            myBits.or(bBits[temp.right.leafIndex]);
        } else {
            Node nextInternal = available[idxArr[0]++];
            BitSet rightBits = compute3sEcrTemplateBits(temp.right, nextInternal, available, idxArr, bBits, updates);
            myBits.or(rightBits);
        }

        if (currentInternal != available[0]) {
            Integer r = nodeToRow.get(currentInternal);
            if (r != null) {
                if (myBits.cardinality() == N) myBits.clear();
                updates.put(r, myBits);
            }
        }

        return myBits;
    }

    private void clearHistory() {
        costHistory.clear();
        rowsolHistory.clear();
        colsolHistory.clear();
        uHistory.clear();
        vHistory.clear();
        distanceHistory.clear();
        clusterHistory.clear();
        deltaStack.clear();
        nniPushCountHistory.clear();
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

        IdentityHashMap<Node, BitSet> clustersCopy = new IdentityHashMap<>();
        for (Map.Entry<Node, BitSet> entry : currentClusters.entrySet()) {
            clustersCopy.put(entry.getKey(), (BitSet) entry.getValue().clone());
        }
        clusterHistory.push(clustersCopy);
    }

    // =========================================================================
    // INKREMENTACJA 1-NNI W T1 (REROOT DFS DLA TBR) - ZŁOŻONOŚĆ O(N^2)
    // =========================================================================

    @Override
    public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {
        // Punkt startowy w T2 dla danego przekorzenienia w T1
        setTargetRoot(pruneNode, wanderingSource);
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        // Wędrówka 1-NNI w T2: aktualizacja dokładnie 2 wierszy
        moveTargetDown(parentTarget, childTarget, pruneNode, wanderingSource);
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        moveTargetUp(parentTarget, childTarget, pruneNode, wanderingSource);
    }

    @Override
    public void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode) {
        // Jeśli parentReroot to pruneNode, to w regraftowanym drzewie korzeń poddrzewa
        // zachowuje wszystkie liście (currentPrunedLeaves), więc jego wiersz nie powinien tracić liści.
        // Jeśli parentReroot jest węzłem wewnętrznym wewnątrz poddrzewa, jego nowy klaster
        // po odwróceniu krawędzi to (currentPrunedLeaves \ childCluster).
        if (parentReroot == pruneNode) {
            // Bezpiecznik na stos delty
            deltaStack.push(new LapStateDelta(new int[0], new short[0][0], Arrays.copyOf(u, dim), Arrays.copyOf(v, dim),
                    Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance, new IdentityHashMap<>()));
            return;
        }

        Integer rParent = nodeToRow.get(parentReroot);
        if (rParent != null && currentPrunedLeaves != null) {
            BitSet childCluster = getCluster(childReroot);
            if (childCluster != null) {
                BitSet newParentCluster = (BitSet) currentPrunedLeaves.clone();
                newParentCluster.andNot(childCluster);
                if (newParentCluster.cardinality() == N) newParentCluster.clear();

                Map<Integer, BitSet> updates = new HashMap<>();
                updates.put(rParent, newParentCluster);

                updateRowSafelyAndSave(updates);
                return;
            }
        }

        deltaStack.push(new LapStateDelta(new int[0], new short[0][0], Arrays.copyOf(u, dim), Arrays.copyOf(v, dim),
                Arrays.copyOf(rowsol, dim), Arrays.copyOf(colsol, dim), currentDistance, new IdentityHashMap<>()));
    }

    @Override
    public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {
        // Cofnięcie odwrócenia krawędzi w T1 ze stosu delty
        undoDeltaStack();
    }

    @Override public double getCurrentDistance() { return this.currentDistance; }
    @Override public void commit() { clearHistory(); }
    @Override public double getDistance(Tree t1, Tree t2, int... indexes) { return mcMetricFull.getDistance(t1, t2, indexes); }
    @Override public String getName() { return "Accelerated " + mcMetricFull.getName(); }
    @Override public String getCommandLineName() { return mcMetricFull.getCommandLineName(); }
    @Override public void setCommandLineName(String cln) { mcMetricFull.setCommandLineName(cln); }
    @Override public void setName(String name) { mcMetricFull.setName(name); }
    @Override public String getDescription() { return mcMetricFull.getDescription(); }
    @Override public void setDescription(String d) { mcMetricFull.setDescription(d); }
    @Override public void initData() { mcMetricFull.initData(); }
    @Override public boolean isRooted() { return true; }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return mcMetricFull.isDiffLeafSets(); }
    @Override public AlignInfo getAlignment() { return mcMetricFull.getAlignment(); }

    /**
     * Drukuje pełną analizę porównawczą stanu inkrementalnego z fizycznym drzewem
     * wygenerowanym przez TbrUtils.
     */
    public void printDiagnosticDump(Tree physicalTree) {
        System.err.println("\n================================================================================");
        System.err.println("            SZCZEGÓŁOWA ANALIZA KLASTRÓW I MACIERZY KOSZTÓW (MC)");
        System.err.println("================================================================================");
        System.err.printf("Wymiar macierzy: %dx%d | Zgłoszony dystans LAP: %.2f%n", dim, dim, currentDistance);

        // 1. Zbieramy klastry z fizycznego drzewa wzorcowego
        Map<Node, BitSet> physClusters = new IdentityHashMap<>();
        extractClusters(physicalTree.getRoot(), idGroup, physClusters);

        Set<String> physClusterStrings = new HashSet<>();
        for (Map.Entry<Node, BitSet> e : physClusters.entrySet()) {
            if (!e.getKey().isRoot()) {
                physClusterStrings.add(bitSetToLeafNames(e.getValue()));
            }
        }

        System.err.println("\n--- [A] Klastry w FIZYCZNYM drzewie (rzeczywista topologia) ---");
        for (String pcs : physClusterStrings) {
            System.err.println("   (fizyczny) " + pcs);
        }

        // 2. Analiza każdego wiersza w stanie inkrementalnym (T1) i jego dopasowania do T2
        System.err.println("\n--- [B] Klastry w STANIE INKREMENTALNYM (rowToNode) vs Dopasowanie w T2 ---");
        Set<String> incrClusterStrings = new HashSet<>();
        double sumCosts = 0;

        for (int i = 0; i < dim; i++) {
            Node n = rowToNode[i];
            BitSet bs = (n != null) ? currentClusters.get(n) : null;
            String bsStr = (bs != null) ? bitSetToLeafNames(bs) : "NULL/EMPTY";
            incrClusterStrings.add(bsStr);

            boolean inPhys = physClusterStrings.contains(bsStr);
            String status = inPhys ? "[OK]" : "[BŁĄD: PHANTOM/STALE]";

            // Informacja o przypisanej kolumnie w T2 przez LapSolwera
            int matchedCol = (rowsol != null && i < rowsol.length) ? rowsol[i] : -1;
            int cost = (matchedCol >= 0 && matchedCol < dim) ? assigncost[i][matchedCol] : -1;
            if (cost >= 0) sumCosts += cost;

            Node colNode = (matchedCol >= 0 && matchedCol < dim) ? colToNode[matchedCol] : null;
            BitSet colBs = (colNode != null) ? targetClusters.get(colNode) : null;
            String colBsStr = (colBs != null) ? bitSetToLeafNames(colBs) : "PADDING";

            System.err.printf("   Wiersz %d | Węzeł: %-6s | Klaster T1: %-26s | %-20s | Cel T2 (kol. %d): %-20s -> Koszt: %d%n",
                    i, (n != null ? (n.isLeaf() ? n.getIdentifier().getName() : "int#" + n.getNumber()) : "null"),
                    bsStr, status, matchedCol, colBsStr, cost);
        }

        System.err.printf("\nSuma cząstkowych wag dopasowania: %.2f%n", sumCosts);

        // 3. Klastry, które powinny być w T1, ale walker ich nie wytworzył
        System.err.println("\n--- [C] Klastry FIZYCZNE, których BRAKUJE w strukturach inkrementalnych ---");
        for (String pcs : physClusterStrings) {
            if (!incrClusterStrings.contains(pcs)) {
                System.err.println("   [BRAKUJĄCY KLASTER] " + pcs);
            }
        }
        System.err.println("================================================================================\n");
    }

    private String bitSetToLeafNames(BitSet bs) {
        if (bs == null || bs.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            if (!first) sb.append(", ");
            if (idGroup != null && i < idGroup.getIdCount()) {
                sb.append(idGroup.getIdentifier(i).getName());
            } else {
                sb.append(i);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}