package treecmp.metrics.topological.acc;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.moves.NniMove;
import treecmp.metrics.BaseMetric;
import treecmp.metrics.IncrementalMetric;

import java.util.*;

public abstract class BaseRFIncrementalMetric extends BaseMetric implements IncrementalMetric {

    // Struktury statyczne (zbudowane raz)
    protected final Set<BitSet> targetSplits = new HashSet<>();
    protected final Map<Node, BitSet> nodeBitSets = new IdentityHashMap<>();
    protected BitSet allLeavesMask;

    // Śledzi aktualny wirtualny split węzła w trakcie przeszukiwania otoczenia
    protected final Map<Node, BitSet> activeVirtualSplits = new HashMap<>();

    // Stosy pamiętające dokładny stan sprzed każdego ruchu (zsynchronizowane atomowo)
    protected final Stack<Integer> sharedSplitsHistory = new Stack<>();
    protected final Stack<Node> movingNodeHistory = new Stack<>();
    protected final Stack<BitSet> activeSplitHistory = new Stack<>();
    protected final Stack<Integer> operationNodeCountHistory = new Stack<>();

    protected int sharedSplitsCount;
    protected int totalInternalSplits;
    protected double currentDistance;

    // Śledzi głębokość odcięcia, by poprawnie cofnąć applySprPrune
    protected final Stack<Integer> sprPruneDepths = new Stack<>();

    protected abstract BitSet normalizeSplit(BitSet rawSplit);

    protected boolean isNonTrivial(int card, int total) {
        return card > 1 && card < total;
    }

    protected Tree baseTreeRef;
    protected Tree targetTreeRef;
    private final treecmp.heuristics.tbr.TbrUtils tbrUtilsHelper = new treecmp.heuristics.tbr.TbrUtils();
    private final treecmp.metrics.topological.RFClusterMetric classicRfcHelper = new treecmp.metrics.topological.RFClusterMetric();

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        this.baseTreeRef = baseTree;
        this.targetTreeRef = targetTree;

        targetSplits.clear();
        nodeBitSets.clear();
        activeVirtualSplits.clear();
        sharedSplitsHistory.clear();
        movingNodeHistory.clear();
        activeSplitHistory.clear();
        operationNodeCountHistory.clear();
        sprPruneDepths.clear();

        Map<String, Integer> leafMapping = createLeafMapping(baseTree);
        int leafCount = leafMapping.size();

        this.allLeavesMask = new BitSet(leafCount);
        this.allLeavesMask.set(0, leafCount);

        extractAndStoreSplits(targetTree, leafMapping, targetSplits, false);

        Set<BitSet> baseSplits = new HashSet<>();
        extractAndStoreSplits(baseTree, leafMapping, baseSplits, true);

        sharedSplitsCount = 0;
        for (BitSet bs : baseSplits) {
            if (targetSplits.contains(bs)) {
                sharedSplitsCount++;
            }
        }

        this.totalInternalSplits = baseSplits.size();
        updateCurrentDistance();
    }

    public double applyNniStep(Node nodeToUpdate, BitSet bitsOut, BitSet bitsIn) {
        if (nodeToUpdate == null) return this.currentDistance;

        BitSet oldBSInVirtual = activeVirtualSplits.get(nodeToUpdate);
        BitSet currentBS = (oldBSInVirtual != null) ? oldBSInVirtual : nodeBitSets.get(nodeToUpdate);
        if (currentBS == null) {
            return this.currentDistance;
        }

        sharedSplitsHistory.push(sharedSplitsCount);
        movingNodeHistory.push(nodeToUpdate);
        operationNodeCountHistory.push(1);
        activeSplitHistory.push(oldBSInVirtual);

        if (isShared(currentBS)) sharedSplitsCount--;

        BitSet newBS = (BitSet) currentBS.clone();
        if (bitsOut != null) newBS.andNot(bitsOut);
        if (bitsIn != null) newBS.or(bitsIn);

        activeVirtualSplits.put(nodeToUpdate, newBS);
        if (isShared(newBS)) sharedSplitsCount++;

        updateCurrentDistance();
        return currentDistance;
    }

    public void undoNniStep() {
        if (sharedSplitsHistory.isEmpty()) return;

        this.sharedSplitsCount = sharedSplitsHistory.pop();
        int nodeCount = operationNodeCountHistory.isEmpty() ? 1 : operationNodeCountHistory.pop();

        for (int i = 0; i < nodeCount; i++) {
            Node n = movingNodeHistory.pop();
            BitSet oldBS = activeSplitHistory.pop();
            if (oldBS != null) {
                activeVirtualSplits.put(n, oldBS);
            } else {
                activeVirtualSplits.remove(n);
            }
        }

        updateCurrentDistance();
    }

    @Override
    public double applyNni(NniMove move) {
        if (move == null || move.movingSubtree == null || move.swapPartner == null) {
            return this.currentDistance;
        }
        Node nodeToUpdate = move.movingSubtree.getParent();
        if (nodeToUpdate == null) {
            return this.currentDistance;
        }
        BitSet bitsOut = getCluster(move.movingSubtree);
        BitSet bitsIn = getCluster(move.swapPartner);
        return applyNniStep(nodeToUpdate, bitsOut, bitsIn);
    }

    @Override
    public void undoNni(NniMove move) {
        undoNniStep();
    }

    public double applyUpdate(Node node, BitSet bitsToApply, boolean add) {
        if (node == null) return this.currentDistance;

        BitSet oldBSInVirtual = activeVirtualSplits.get(node);
        BitSet currentBS = (oldBSInVirtual != null) ? oldBSInVirtual : nodeBitSets.get(node);
        if (currentBS == null) {
            return this.currentDistance;
        }

        sharedSplitsHistory.push(sharedSplitsCount);
        movingNodeHistory.push(node);
        operationNodeCountHistory.push(1);
        activeSplitHistory.push(oldBSInVirtual);

        if (isShared(currentBS)) sharedSplitsCount--;

        BitSet newBS = (BitSet) currentBS.clone();
        if (add) newBS.or(bitsToApply); else newBS.andNot(bitsToApply);

        activeVirtualSplits.put(node, newBS);
        if (isShared(newBS)) sharedSplitsCount++;

        updateCurrentDistance();
        return currentDistance;
    }

    public void undoUpdate() {
        undoNniStep();
    }

    @Override
    public void commit() {
        nodeBitSets.putAll(activeVirtualSplits);
        activeVirtualSplits.clear();
        sharedSplitsHistory.clear();
        movingNodeHistory.clear();
        activeSplitHistory.clear();
        operationNodeCountHistory.clear();
        sprPruneDepths.clear();
    }

    @Override
    public double getCurrentDistance() {
        return currentDistance;
    }

    public BitSet getCluster(Node node) {
        if (node == null) return null;
        return activeVirtualSplits.getOrDefault(node, nodeBitSets.get(node));
    }

    public double evaluateExactSprDistance(Node pruneNode, Node targetNode, BitSet movingBits) {
        int virtualShared = this.sharedSplitsCount;

        Node oldParent = pruneNode.getParent();
        if (oldParent != null) {
            BitSet oldParentBits = getCluster(oldParent);
            if (isShared(oldParentBits)) virtualShared--;
        }

        BitSet targetBits = getCluster(targetNode);
        if (targetBits != null) {
            BitSet newNodeBits = (BitSet) targetBits.clone();
            newNodeBits.or(movingBits);
            if (isShared(newNodeBits)) virtualShared++;
        }

        return (totalInternalSplits + targetSplits.size() - 2.0 * virtualShared) / 2.0;
    }

    public boolean isShared(BitSet bs) {
        if (bs == null) return false;
        BitSet norm = normalizeSplit((BitSet) bs.clone());
        int card = norm.cardinality();
        int total = allLeavesMask.cardinality();
        if (isNonTrivial(card, total)) {
            return targetSplits.contains(norm);
        }
        return false;
    }

    @Override
    public double getDistance(Tree t1, Tree t2, int... indexes) {
        initCalculationState(t1, t2);
        return getCurrentDistance();
    }

    protected void updateCurrentDistance() {
        this.currentDistance = (totalInternalSplits + targetSplits.size() - 2.0 * sharedSplitsCount) / 2.0;
    }

    protected Map<String, Integer> createLeafMapping(Tree tree) {
        Map<String, Integer> mapping = new HashMap<>();
        int index = 0;
        Stack<Node> stack = new Stack<>();
        stack.push(tree.getRoot());
        while (!stack.isEmpty()) {
            Node n = stack.pop();
            if (n.isLeaf()) {
                mapping.put(n.getIdentifier().getName(), index++);
            } else {
                for (int i = 0; i < n.getChildCount(); i++) stack.push(n.getChild(i));
            }
        }
        return mapping;
    }

    protected void extractAndStoreSplits(Tree tree, Map<String, Integer> leafMap, Set<BitSet> store, boolean fillCache) {
        buildNodeBitSetsRec(tree.getRoot(), leafMap, store, fillCache);
    }

    private BitSet buildNodeBitSetsRec(Node node, Map<String, Integer> leafMap, Set<BitSet> store, boolean fillCache) {
        BitSet bs = new BitSet();
        if (node.isLeaf()) {
            String name = node.getIdentifier().getName();
            if (leafMap.containsKey(name)) {
                bs.set(leafMap.get(name));
            }
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                bs.or(buildNodeBitSetsRec(node.getChild(i), leafMap, store, fillCache));
            }

            BitSet normalized = normalizeSplit((BitSet) bs.clone());
            int card = normalized.cardinality();
            int total = allLeavesMask.cardinality();

            if (isNonTrivial(card, total)) {
                store.add(normalized);
            }
        }

        if (fillCache) {
            nodeBitSets.put(node, (BitSet) bs.clone());
        }

        return bs;
    }

    // ==========================================
    // IMPLEMENTACJA INTERFEJSU SPR
    // ==========================================

    @Override
    public void applySprPrune(Node pruneNode) {
        if (pruneNode == null) {
            sprPruneDepths.push(0);
            return;
        }
        BitSet movingBits = getCluster(pruneNode);
        Node oldParent = pruneNode.getParent();

        int pruneDepth = 0;
        Node curr = (oldParent != null) ? oldParent.getParent() : null;

        while (curr != null && !curr.isRoot()) {
            applyNniStep(curr, movingBits, null);
            pruneDepth++;
            curr = curr.getParent();
        }
        sprPruneDepths.push(pruneDepth);
    }

    @Override
    public void undoSprPrune(Node pruneNode) {
        if (sprPruneDepths.isEmpty()) return;
        int depth = sprPruneDepths.pop();
        for (int i = 0; i < depth; i++) {
            undoNniStep();
        }
    }

    @Override
    public double evaluateSprRegraft(Node pruneNode, Node targetNode) {
        BitSet movingBits = getCluster(pruneNode);
        return evaluateExactSprDistance(pruneNode, targetNode, movingBits);
    }

    @Override
    public void applySprRegraftStep(Node pruneNode, Node currentNode) {
        BitSet movingBits = getCluster(pruneNode);
        applyNniStep(currentNode, null, movingBits);
    }

    @Override
    public void undoSprRegraftStep() {
        undoNniStep();
    }

    private boolean isDescendantOrSelf(Node descendant, Node ancestor) {
        Node curr = descendant;
        while (curr != null) {
            if (curr == ancestor) return true;
            curr = curr.getParent();
        }
        return false;
    }

    // ==========================================
    // IMPLEMENTACJA 2-sECR
    // ==========================================

    @Override
    public double evaluate2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR newTopology) {
        double evaluatedDistance = commit2sEcrMove(top, m1, m2, boundarySubtrees, newTopology);
        undoNniStep();
        return evaluatedDistance;
    }

    @Override
    public double commit2sEcrMove(Node top, Node m1, Node m2, Node[] boundarySubtrees, treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR newTopology) {
        BitSet topCluster = getCluster(top);
        BitSet[] sBits = new BitSet[4];
        for (int i = 0; i < 4; i++) {
            Node s = boundarySubtrees[i];
            if (s != null && isDescendantOrSelf(s, top)) {
                sBits[i] = getCluster(s);
            } else {
                BitSet outside = (BitSet) allLeavesMask.clone();
                if (topCluster != null) outside.andNot(topCluster);
                sBits[i] = outside;
            }
            if (sBits[i] == null) sBits[i] = new BitSet(allLeavesMask.size());
        }

        BitSet newM1 = new BitSet();
        BitSet newM2 = new BitSet();

        if (newTopology.isFork) {
            newM1.or(sBits[newTopology.indices[0]]);
            newM1.or(sBits[newTopology.indices[1]]);

            newM2.or(sBits[newTopology.indices[2]]);
            newM2.or(sBits[newTopology.indices[3]]);
        } else {
            newM2.or(sBits[newTopology.indices[2]]);
            newM2.or(sBits[newTopology.indices[3]]);

            newM1.or(sBits[newTopology.indices[1]]);
            newM1.or(newM2);
        }

        sharedSplitsHistory.push(sharedSplitsCount);
        operationNodeCountHistory.push(2);

        // Modyfikacja m1
        BitSet oldM1InVirtual = activeVirtualSplits.get(m1);
        movingNodeHistory.push(m1);
        activeSplitHistory.push(oldM1InVirtual);
        BitSet currentM1 = (oldM1InVirtual != null) ? oldM1InVirtual : nodeBitSets.get(m1);
        if (isShared(currentM1)) this.sharedSplitsCount--;
        if (isShared(newM1)) this.sharedSplitsCount++;
        activeVirtualSplits.put(m1, newM1);

        // Modyfikacja m2
        BitSet oldM2InVirtual = activeVirtualSplits.get(m2);
        movingNodeHistory.push(m2);
        activeSplitHistory.push(oldM2InVirtual);
        BitSet currentM2 = (oldM2InVirtual != null) ? oldM2InVirtual : nodeBitSets.get(m2);
        if (isShared(currentM2)) this.sharedSplitsCount--;
        if (isShared(newM2)) this.sharedSplitsCount++;
        activeVirtualSplits.put(m2, newM2);

        updateCurrentDistance();
        return this.currentDistance;
    }

    // ==========================================
    // IMPLEMENTACJA 3-sECR (ŚCISŁY PORZĄDEK DFS ZGODNY Z BIND PHYSICAL)
    // ==========================================

    @Override
    public double evaluate3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR newTopology) {
        double evaluatedDistance = commit3sEcrMove(cluster, boundarySubtrees, newTopology);
        undoNniStep();
        return evaluatedDistance;
    }

    @Override
    public double commit3sEcrMove(List<Node> cluster, Node[] boundarySubtrees, treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR newTopology) {
        Node top = cluster.get(0);
        BitSet topCluster = getCluster(top);
        BitSet[] sBits = new BitSet[5];
        for (int i = 0; i < 5; i++) {
            Node s = boundarySubtrees[i];
            if (s != null && isDescendantOrSelf(s, top)) {
                sBits[i] = getCluster(s);
            } else {
                BitSet outside = (BitSet) allLeavesMask.clone();
                if (topCluster != null) outside.andNot(topCluster);
                sBits[i] = outside;
            }
            if (sBits[i] == null) sBits[i] = new BitSet(allLeavesMask.size());
        }

        Node[] available = cluster.toArray(new Node[0]);
        int[] idxArr = {1};
        Map<Node, BitSet> newClusters = new LinkedHashMap<>();
        compute3sEcrTemplateBits(newTopology, available[0], available, idxArr, sBits, newClusters);

        sharedSplitsHistory.push(sharedSplitsCount);
        operationNodeCountHistory.push(newClusters.size());

        for (Map.Entry<Node, BitSet> entry : newClusters.entrySet()) {
            Node n = entry.getKey();
            BitSet oldBSInVirtual = activeVirtualSplits.get(n);
            movingNodeHistory.push(n);
            activeSplitHistory.push(oldBSInVirtual);

            BitSet currentBS = (oldBSInVirtual != null) ? oldBSInVirtual : nodeBitSets.get(n);
            if (isShared(currentBS)) this.sharedSplitsCount--;

            BitSet newBS = entry.getValue();
            if (isShared(newBS)) this.sharedSplitsCount++;
            activeVirtualSplits.put(n, newBS);
        }

        updateCurrentDistance();
        return this.currentDistance;
    }

    private BitSet compute3sEcrTemplateBits(treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR temp,
                                            Node currentInternal,
                                            Node[] available,
                                            int[] idxArr,
                                            BitSet[] sBits,
                                            Map<Node, BitSet> updates) {
        BitSet leftBits;
        if (temp.left.leafIndex != -1) {
            leftBits = sBits[temp.left.leafIndex];
        } else {
            Node nextInternal = available[idxArr[0]++];
            leftBits = compute3sEcrTemplateBits(temp.left, nextInternal, available, idxArr, sBits, updates);
        }

        BitSet rightBits;
        if (temp.right.leafIndex != -1) {
            rightBits = sBits[temp.right.leafIndex];
        } else {
            Node nextInternal = available[idxArr[0]++];
            rightBits = compute3sEcrTemplateBits(temp.right, nextInternal, available, idxArr, sBits, updates);
        }

        BitSet myBits = new BitSet();
        if (leftBits != null) myBits.or(leftBits);
        if (rightBits != null) myBits.or(rightBits);

        if (currentInternal != available[0]) {
            updates.put(currentInternal, myBits);
        }

        return myBits;
    }

    // ==========================================
    // IMPLEMENTACJA AKCELERATORA TBR
    // ==========================================

    public double evaluateExactTbrDistance(Node pruneNode, Node rerootNode, Node targetNode, BitSet movingBits) {
        if (this.baseTreeRef == null || this.targetTreeRef == null) {
            return getCurrentDistance();
        }

        Tree physicalTree = tbrUtilsHelper.createTbrTree(this.baseTreeRef, pruneNode, rerootNode, targetNode);
        if (physicalTree != null) {
            if (physicalTree instanceof pal.tree.SimpleTree) {
                ((pal.tree.SimpleTree) physicalTree).createNodeList();
            }
            try {
                return classicRfcHelper.getDistance(physicalTree, this.targetTreeRef);
            } catch (Exception e) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return Double.POSITIVE_INFINITY;
    }
}