package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.Tree;
import pal.misc.IdGroup;
import pal.tree.TreeUtils;
import treecmp.common.AlignInfo;
import treecmp.common.LapSolver;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalMetric;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalMetric;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.nni.acc.NniIncrementalMetric;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.spr.acc.RootedSprIncrementalMetric;
import treecmp.heuristics.tbr.acc.RootedTbrMetric;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.MatchingClusterMetric;

import java.util.*;

public class MCIncrementalMetric implements IncrementalMetric,
        NniIncrementalMetric,
        RootedSprIncrementalMetric,
        RootedTbrMetric,
        Ecr2IncrementalMetric,
        Ecr3IncrementalMetric {

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

    // Prealokowane reprezentacje słów 64-bitowych dla błyskawicznego XOR bez alokacji
    private int numWords;
    private long[][] targetClusterWords;
    private short[] targetNullRowCosts;
    private long[] scratchWords;

    private final Stack<short[][]> costHistory = new Stack<>();
    private final Stack<int[]> rowsolHistory = new Stack<>();
    private final Stack<int[]> colsolHistory = new Stack<>();
    private final Stack<int[]> uHistory = new Stack<>();
    private final Stack<int[]> vHistory = new Stack<>();
    private final Stack<Double> distanceHistory = new Stack<>();
    private final Stack<Map<Node, BitSet>> clusterHistory = new Stack<>();
    private final Stack<Integer> nniPushCountHistory = new Stack<>();

    private final treecmp.heuristics.tbr.TbrUtils tbrUtils = new treecmp.heuristics.tbr.TbrUtils();

    private BitSet currentPrunedLeaves;
    private BitSet fullTreeLeaves;

    // =========================================================================
    // PREALOKOWANY STOS DELTA (ZERO-ALLOCATION W 2D-DFS TBR, NNI, ECR)
    // =========================================================================
    private int deltaMaxDepth = 128;
    private int deltaPointer = 0;
    private int[] deltaRowsCount;
    private int[][] deltaRows;
    private short[][][] deltaOldRows;
    private int[][] deltaOldU;
    private int[][] deltaOldV;
    private int[][] deltaOldRowsol;
    private int[][] deltaOldColsol;
    private double[] deltaOldDistance;
    private Node[][] deltaOldClustNode;
    private BitSet[][] deltaOldClustBits;

    private int[][] cachedKArrays;
    private BitSet tbrScratch1;
    private BitSet tbrScratch2;
    private int[] scratchPruneRows;
    private BitSet[] scratchPruneBitSets;

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

            this.numWords = (N + 63) >>> 6;
            this.scratchWords = new long[numWords];
            this.targetClusterWords = new long[dim][numWords];
            this.targetNullRowCosts = new short[dim];

            int maxK = Math.max(64, dim + 1);
            this.cachedKArrays = new int[maxK][];
            for (int i = 0; i < maxK; i++) {
                this.cachedKArrays[i] = new int[i];
            }

            int depth = Math.max(128, N * 4);
            this.deltaMaxDepth = depth;
            this.deltaPointer = 0;
            this.deltaRowsCount = new int[depth];
            this.deltaRows = new int[depth][dim];
            this.deltaOldRows = new short[depth][dim][dim];
            this.deltaOldU = new int[depth][dim];
            this.deltaOldV = new int[depth][dim];
            this.deltaOldRowsol = new int[depth][dim];
            this.deltaOldColsol = new int[depth][dim];
            this.deltaOldDistance = new double[depth];
            this.deltaOldClustNode = new Node[depth][dim];
            this.deltaOldClustBits = new BitSet[depth][dim];
            for (int d = 0; d < depth; d++) {
                for (int r = 0; r < dim; r++) {
                    this.deltaOldClustBits[d][r] = new BitSet(N);
                }
            }

            this.tbrScratch1 = new BitSet(N);
            this.tbrScratch2 = new BitSet(N);
            this.currentPrunedLeaves = new BitSet(N);
            this.fullTreeLeaves = new BitSet(N);
            this.fullTreeLeaves.set(0, N);

            this.scratchPruneRows = new int[dim];
            this.scratchPruneBitSets = new BitSet[dim];
            for (int i = 0; i < dim; i++) {
                this.scratchPruneBitSets[i] = new BitSet(N);
            }

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

            for (int j = 0; j < dim; j++) {
                Node n2 = colToNode[j];
                if (n2 != null) {
                    BitSet bs = targetClusters.get(n2);
                    if (bs != null) {
                        long[] words = bs.toLongArray();
                        System.arraycopy(words, 0, targetClusterWords[j], 0, words.length);
                        targetNullRowCosts[j] = (short) bs.cardinality();
                    }
                }
            }

            buildInitialCostMatrix();
            this.currentDistance = LapSolver.lapShort(dim, assigncost, rowsol, colsol, u, v);
        } else {
            this.currentDistance = 0;
        }
    }

    private BitSet extractClusters(Node node, IdGroup idGroup, Map<Node, BitSet> map) {
        BitSet bs = new BitSet(N);
        if (node.isLeaf()) {
            int id = idGroup.whichIdNumber(node.getIdentifier().getName());
            if (id >= 0) bs.set(id);
            map.put(node, (BitSet) bs.clone());
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                bs.or(extractClusters(node.getChild(i), idGroup, map));
            }
            map.put(node, (BitSet) bs.clone());
        }
        return bs;
    }

    private void fillWordsFromBitSet(BitSet bs, long[] dest) {
        Arrays.fill(dest, 0L);
        if (bs != null && !bs.isEmpty()) {
            long[] words = bs.toLongArray();
            System.arraycopy(words, 0, dest, 0, Math.min(words.length, dest.length));
        }
    }

    private void computeRowCostsFast(int r, BitSet cluster) {
        fillWordsFromBitSet(cluster, scratchWords);
        int card = 0;
        for (int w = 0; w < numWords; w++) {
            card += Long.bitCount(scratchWords[w]);
        }
        short[] costRow = assigncost[r];
        for (int j = 0; j < dim; j++) {
            if (colToNode[j] != null) {
                long[] tWords = targetClusterWords[j];
                int diff = 0;
                for (int w = 0; w < numWords; w++) {
                    diff += Long.bitCount(scratchWords[w] ^ tWords[w]);
                }
                costRow[j] = (short) diff;
            } else {
                costRow[j] = (short) card;
            }
        }
    }

    private void buildInitialCostMatrix() {
        for (int i = 0; i < dim; i++) {
            Node n1 = rowToNode[i];
            if (n1 != null) {
                computeRowCostsFast(i, currentClusters.get(n1));
            } else {
                System.arraycopy(targetNullRowCosts, 0, assigncost[i], 0, dim);
            }
        }
    }

    public BitSet getCluster(Node n) {
        BitSet bs = currentClusters.get(n);
        if (bs != null) return bs;
        if (n.isLeaf()) {
            BitSet leafBs = new BitSet(N);
            int id = idGroup.whichIdNumber(n.getIdentifier().getName());
            if (id >= 0) leafBs.set(id);
            currentClusters.put(n, leafBs);
            return leafBs;
        }
        return null;
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

    private void ensureDeltaCapacity() {
        if (deltaPointer >= deltaMaxDepth) {
            int newDepth = deltaMaxDepth * 2;
            deltaRowsCount = Arrays.copyOf(deltaRowsCount, newDepth);
            deltaRows = Arrays.copyOf(deltaRows, newDepth);
            deltaOldRows = Arrays.copyOf(deltaOldRows, newDepth);
            deltaOldU = Arrays.copyOf(deltaOldU, newDepth);
            deltaOldV = Arrays.copyOf(deltaOldV, newDepth);
            deltaOldRowsol = Arrays.copyOf(deltaOldRowsol, newDepth);
            deltaOldColsol = Arrays.copyOf(deltaOldColsol, newDepth);
            deltaOldDistance = Arrays.copyOf(deltaOldDistance, newDepth);
            deltaOldClustNode = Arrays.copyOf(deltaOldClustNode, newDepth);
            deltaOldClustBits = Arrays.copyOf(deltaOldClustBits, newDepth);

            for (int d = deltaMaxDepth; d < newDepth; d++) {
                deltaRows[d] = new int[dim];
                deltaOldRows[d] = new short[dim][dim];
                deltaOldU[d] = new int[dim];
                deltaOldV[d] = new int[dim];
                deltaOldRowsol[d] = new int[dim];
                deltaOldColsol[d] = new int[dim];
                deltaOldClustNode[d] = new Node[dim];
                deltaOldClustBits[d] = new BitSet[dim];
                for (int r = 0; r < dim; r++) {
                    deltaOldClustBits[d][r] = new BitSet(N);
                }
            }
            deltaMaxDepth = newDepth;
        }
    }

    private void pushEmptyDelta() {
        ensureDeltaCapacity();
        int d = deltaPointer;
        deltaRowsCount[d] = 0;
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;
    }

    private void updateSingleRowAndSave(int r, BitSet newCluster) {
        ensureDeltaCapacity();
        int d = deltaPointer;
        deltaRows[d][0] = r;
        System.arraycopy(assigncost[r], 0, deltaOldRows[d][0], 0, dim);

        Node n = rowToNode[r];
        if (n != null) {
            deltaOldClustNode[d][0] = n;
            BitSet oldBs = currentClusters.get(n);
            deltaOldClustBits[d][0].clear();
            if (oldBs != null) deltaOldClustBits[d][0].or(oldBs);

            if (oldBs == null) {
                oldBs = new BitSet(N);
                currentClusters.put(n, oldBs);
            }
            oldBs.clear();
            oldBs.or(newCluster);
        } else {
            deltaOldClustNode[d][0] = null;
        }

        deltaRowsCount[d] = 1;
        System.arraycopy(u, 0, deltaOldU[d], 0, dim);
        System.arraycopy(v, 0, deltaOldV[d], 0, dim);
        System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
        System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;

        computeRowCostsFast(r, newCluster);

        int[] changedRowsParam = cachedKArrays[1];
        changedRowsParam[0] = r;
        this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
    }

    private void updateTwoRowsAndSave(int r1, BitSet c1, int r2, BitSet c2) {
        ensureDeltaCapacity();
        int d = deltaPointer;
        deltaRows[d][0] = r1;
        deltaRows[d][1] = r2;
        System.arraycopy(assigncost[r1], 0, deltaOldRows[d][0], 0, dim);
        System.arraycopy(assigncost[r2], 0, deltaOldRows[d][1], 0, dim);

        Node n1 = rowToNode[r1];
        if (n1 != null) {
            deltaOldClustNode[d][0] = n1;
            BitSet b1 = currentClusters.get(n1);
            deltaOldClustBits[d][0].clear();
            if (b1 != null) deltaOldClustBits[d][0].or(b1);
            if (b1 == null) { b1 = new BitSet(N); currentClusters.put(n1, b1); }
            b1.clear();
            b1.or(c1);
        } else deltaOldClustNode[d][0] = null;

        Node n2 = rowToNode[r2];
        if (n2 != null) {
            deltaOldClustNode[d][1] = n2;
            BitSet b2 = currentClusters.get(n2);
            deltaOldClustBits[d][1].clear();
            if (b2 != null) deltaOldClustBits[d][1].or(b2);
            if (b2 == null) { b2 = new BitSet(N); currentClusters.put(n2, b2); }
            b2.clear();
            b2.or(c2);
        } else deltaOldClustNode[d][1] = null;

        deltaRowsCount[d] = 2;
        System.arraycopy(u, 0, deltaOldU[d], 0, dim);
        System.arraycopy(v, 0, deltaOldV[d], 0, dim);
        System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
        System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;

        computeRowCostsFast(r1, c1);
        computeRowCostsFast(r2, c2);

        int[] changedRowsParam = cachedKArrays[2];
        changedRowsParam[0] = r1;
        changedRowsParam[1] = r2;
        this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
    }

    private void updateMultipleRowsAndSave(int count, int[] rows, BitSet[] clusters) {
        if (count <= 0) {
            pushEmptyDelta();
            return;
        }
        ensureDeltaCapacity();
        int d = deltaPointer;

        for (int i = 0; i < count; i++) {
            int r = rows[i];
            deltaRows[d][i] = r;
            System.arraycopy(assigncost[r], 0, deltaOldRows[d][i], 0, dim);

            Node n = rowToNode[r];
            if (n != null) {
                deltaOldClustNode[d][i] = n;
                BitSet oldBs = currentClusters.get(n);
                deltaOldClustBits[d][i].clear();
                if (oldBs != null) deltaOldClustBits[d][i].or(oldBs);
            } else {
                deltaOldClustNode[d][i] = null;
            }
        }

        deltaRowsCount[d] = count;
        System.arraycopy(u, 0, deltaOldU[d], 0, dim);
        System.arraycopy(v, 0, deltaOldV[d], 0, dim);
        System.arraycopy(rowsol, 0, deltaOldRowsol[d], 0, dim);
        System.arraycopy(colsol, 0, deltaOldColsol[d], 0, dim);
        deltaOldDistance[d] = currentDistance;
        deltaPointer++;

        for (int i = 0; i < count; i++) {
            int r = rows[i];
            BitSet newClust = clusters[i];
            Node n = rowToNode[r];
            if (n != null) {
                BitSet currBs = currentClusters.get(n);
                if (currBs == null) {
                    currBs = new BitSet(N);
                    currentClusters.put(n, currBs);
                }
                currBs.clear();
                currBs.or(newClust);
            }
            computeRowCostsFast(r, newClust);
        }

        int[] changedRowsParam = (count < cachedKArrays.length) ? cachedKArrays[count] : new int[count];
        System.arraycopy(rows, 0, changedRowsParam, 0, count);
        this.currentDistance = LapSolver.lapShortUpdate(dim, assigncost, rowsol, colsol, u, v, changedRowsParam);
    }

    private void updateRowSafelyAndSave(Map<Integer, BitSet> rowUpdates) {
        if (rowUpdates == null || rowUpdates.isEmpty()) {
            pushEmptyDelta();
            return;
        }
        int count = 0;
        for (Map.Entry<Integer, BitSet> entry : rowUpdates.entrySet()) {
            scratchPruneRows[count] = entry.getKey();
            scratchPruneBitSets[count].clear();
            scratchPruneBitSets[count].or(entry.getValue());
            count++;
        }
        updateMultipleRowsAndSave(count, scratchPruneRows, scratchPruneBitSets);
    }

    private void undoDeltaStack() {
        if (deltaPointer <= 0) return;
        deltaPointer--;
        int d = deltaPointer;
        int count = deltaRowsCount[d];

        if (count > 0) {
            for (int i = 0; i < count; i++) {
                int r = deltaRows[d][i];
                System.arraycopy(deltaOldRows[d][i], 0, assigncost[r], 0, dim);
                Node n = deltaOldClustNode[d][i];
                if (n != null) {
                    BitSet bs = currentClusters.get(n);
                    if (bs == null) {
                        bs = new BitSet(N);
                        currentClusters.put(n, bs);
                    }
                    bs.clear();
                    bs.or(deltaOldClustBits[d][i]);
                }
            }
            System.arraycopy(deltaOldU[d], 0, u, 0, dim);
            System.arraycopy(deltaOldV[d], 0, v, 0, dim);
            System.arraycopy(deltaOldRowsol[d], 0, rowsol, 0, dim);
            System.arraycopy(deltaOldColsol[d], 0, colsol, 0, dim);
        }
        this.currentDistance = deltaOldDistance[d];
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

    // =========================================================================
    // ZERO-ALLOCATION 2D-DFS TBR (RootedTbrMetric)
    // =========================================================================

    @Override
    public void setPrunedState(Node pruneNode, Node wanderingSource) {
        this.currentPrunedLeaves.clear();
        this.currentPrunedLeaves.or(getCluster(pruneNode));
        BitSet P = this.currentPrunedLeaves;

        int count = 0;
        Node curr = pruneNode.getParent().getParent();
        while (curr != null) {
            Integer r = nodeToRow.get(curr);
            if (r != null) {
                scratchPruneRows[count] = r;
                BitSet bs = scratchPruneBitSets[count];
                bs.clear();
                bs.or(currentClusters.get(curr));
                bs.andNot(P);
                if (bs.cardinality() == N) bs.clear();
                count++;
            }
            curr = curr.getParent();
        }

        Integer r_p = nodeToRow.get(resolveWandering(wanderingSource, pruneNode));
        if (r_p != null) {
            scratchPruneRows[count] = r_p;
            scratchPruneBitSets[count].clear();
            count++;
        }
        updateMultipleRowsAndSave(count, scratchPruneRows, scratchPruneBitSets);
    }

    @Override
    public void setTargetRoot(Node pruneNode, Node wanderingSource) {
        BitSet P = this.currentPrunedLeaves;
        Integer r_p = nodeToRow.get(resolveWandering(wanderingSource, pruneNode));
        if (r_p != null) {
            tbrScratch1.clear();
            tbrScratch1.or(fullTreeLeaves);
            tbrScratch1.andNot(P);
            if (tbrScratch1.cardinality() == N) tbrScratch1.clear();
            updateSingleRowAndSave(r_p, tbrScratch1);
        } else {
            pushEmptyDelta();
        }
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node wanderingSource) {
        BitSet P = this.currentPrunedLeaves;
        Node resolvedWandering = resolveWandering(wanderingSource, pruneNode);
        Integer r_p = nodeToRow.get(resolvedWandering);

        if (r_p != null) {
            BitSet childCluster = getCluster(childTarget);
            tbrScratch1.clear();
            if (childCluster != null) tbrScratch1.or(childCluster);
            tbrScratch1.or(P);
            if (tbrScratch1.cardinality() == N) tbrScratch1.clear();
        }

        Integer r_parent = nodeToRow.get(parentTarget);
        boolean updateParent = (r_parent != null && parentTarget != wanderingSource && parentTarget != resolvedWandering && !r_parent.equals(r_p));
        if (updateParent) {
            BitSet parentCluster = currentClusters.get(parentTarget);
            tbrScratch2.clear();
            if (parentCluster != null) tbrScratch2.or(parentCluster);
            tbrScratch2.or(P);
            if (tbrScratch2.cardinality() == N) tbrScratch2.clear();
        }

        if (r_p != null && updateParent) {
            updateTwoRowsAndSave(r_p, tbrScratch1, r_parent, tbrScratch2);
        } else if (r_p != null) {
            updateSingleRowAndSave(r_p, tbrScratch1);
        } else if (updateParent) {
            updateSingleRowAndSave(r_parent, tbrScratch2);
        } else {
            pushEmptyDelta();
        }
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

    @Override
    public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {
        setTargetRoot(pruneNode, wanderingSource);
    }

    @Override
    public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        moveTargetDown(parentTarget, childTarget, pruneNode, wanderingSource);
    }

    @Override
    public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {
        undoDeltaStack();
    }

    @Override
    public void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode) {
        if (parentReroot == pruneNode) {
            pushEmptyDelta();
            return;
        }

        Integer rParent = nodeToRow.get(parentReroot);
        if (rParent != null && currentPrunedLeaves != null) {
            BitSet childCluster = getCluster(childReroot);
            if (childCluster != null) {
                tbrScratch1.clear();
                tbrScratch1.or(currentPrunedLeaves);
                tbrScratch1.andNot(childCluster);
                if (tbrScratch1.cardinality() == N) tbrScratch1.clear();

                updateSingleRowAndSave(rParent, tbrScratch1);
                return;
            }
        }

        pushEmptyDelta();
    }

    @Override
    public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {
        undoDeltaStack();
    }

    // =========================================================================
    // NNI, SPR & ECR
    // =========================================================================

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

        tbrScratch1.clear();
        tbrScratch1.or(currentClusters.get(nodeToUpdate));
        if (bitsOut != null) tbrScratch1.andNot(bitsOut);
        if (bitsIn != null) tbrScratch1.or(bitsIn);

        if (tbrScratch1.cardinality() == N) tbrScratch1.clear();

        updateSingleRowAndSave(rIndex, tbrScratch1);
        return true;
    }

    public void undoNniStep() {
        undoDeltaStack();
    }

    public void applySprPrune(Node pruneNode) { saveCurrentStateToHistory(); }
    public void undoSprPrune(Node pruneNode) { undoSprRegraftStep(); }
    public void applySprRegraftStep(Node pruneNode, Node currentNode) { saveCurrentStateToHistory(); }
    public void undoSprRegraftStep() {
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
        tbrScratch1.clear();
        tbrScratch2.clear();

        if (template.isFork) {
            tbrScratch1.or(getCluster(b[template.indices[0]]));
            tbrScratch1.or(getCluster(b[template.indices[1]]));
            tbrScratch2.or(getCluster(b[template.indices[2]]));
            tbrScratch2.or(getCluster(b[template.indices[3]]));
        } else {
            tbrScratch2.or(getCluster(b[template.indices[2]]));
            tbrScratch2.or(getCluster(b[template.indices[3]]));
            tbrScratch1.or(getCluster(b[template.indices[1]]));
            tbrScratch1.or(tbrScratch2);
        }

        if (tbrScratch1.cardinality() == N) tbrScratch1.clear();
        if (tbrScratch2.cardinality() == N) tbrScratch2.clear();

        Integer r1 = nodeToRow.get(m1);
        Integer r2 = nodeToRow.get(m2);

        if (r1 != null && r2 != null) {
            updateTwoRowsAndSave(r1, tbrScratch1, r2, tbrScratch2);
        } else if (r1 != null) {
            updateSingleRowAndSave(r1, tbrScratch1);
        } else if (r2 != null) {
            updateSingleRowAndSave(r2, tbrScratch2);
        } else {
            pushEmptyDelta();
        }
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
        BitSet myBits = new BitSet(N);

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
        deltaPointer = 0;
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

    public void printDiagnosticDump(Tree physicalTree) {
        System.err.println("\n================================================================================");
        System.err.println("            SZCZEGÓŁOWA ANALIZA KLASTRÓW I MACIERZY KOSZTÓW (MC)");
        System.err.println("================================================================================");
        System.err.printf("Wymiar macierzy: %dx%d | Zgłoszony dystans LAP: %.2f%n", dim, dim, currentDistance);

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

        System.err.println("\n--- [C] Klastry FIZYCZNE, których BRAKUJE w strukturach inkrementalnych ---");
        for (int i = 0; i < dim; i++) {
            // opcjonalny zrzut
        }
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