package treecmp.metrics.topological;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.metrics.BaseMetric;
import treecmp.metrics.IncrementalMetric;
import treecmp.heuristics.moves.NniMove;

import java.util.*;

public abstract class BaseRFIncrementalMetric extends BaseMetric implements IncrementalMetric {

    // Struktury statyczne (zbudowane raz)
    protected final Set<BitSet> targetSplits = new HashSet<>();
    protected final Map<Node, BitSet> nodeBitSets = new IdentityHashMap<>();
    protected BitSet allLeavesMask;

    // Śledzi aktualny wirtualny split węzła w trakcie długich spacerów SPR
    protected final Map<Node, BitSet> activeVirtualSplits = new HashMap<>();

    // Stosy pamiętające dokładny stan sprzed każdego ruchu
    protected final Stack<Integer> sharedSplitsHistory = new Stack<>();
    protected final Stack<Node> movingNodeHistory = new Stack<>();
    protected final Stack<BitSet> activeSplitHistory = new Stack<>();

    protected int sharedSplitsCount;
    protected int totalInternalSplits;
    protected double currentDistance;

    protected abstract BitSet normalizeSplit(BitSet rawSplit);

    @Override
    public void initCalculationState(Tree baseTree, Tree targetTree) {
        targetSplits.clear();
        nodeBitSets.clear();
        activeVirtualSplits.clear();
        sharedSplitsHistory.clear();
        movingNodeHistory.clear();
        activeSplitHistory.clear();

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
        sharedSplitsHistory.push(sharedSplitsCount);
        movingNodeHistory.push(nodeToUpdate);

        BitSet oldBS = activeVirtualSplits.getOrDefault(nodeToUpdate, nodeBitSets.get(nodeToUpdate));
        activeSplitHistory.push(oldBS);

        if (targetSplits.contains(normalizeSplit(oldBS))) sharedSplitsCount--;

        BitSet newBS = (BitSet) oldBS.clone();
        if (bitsOut != null) newBS.andNot(bitsOut);
        if (bitsIn != null) newBS.or(bitsIn);

        activeVirtualSplits.put(nodeToUpdate, newBS);
        if (targetSplits.contains(normalizeSplit(newBS))) sharedSplitsCount++;

        updateCurrentDistance();
        return currentDistance;
    }

    public void undoNniStep() {
        if (sharedSplitsHistory.isEmpty()) return;
        this.sharedSplitsCount = sharedSplitsHistory.pop();
        activeVirtualSplits.put(movingNodeHistory.pop(), activeSplitHistory.pop());
        updateCurrentDistance();
    }

    @Override
    public double applyNni(NniMove move) {
        // 1. Wyznaczamy węzeł do aktualizacji (rodzic)
        Node nodeToUpdate = move.movingSubtree.getParent();

        // 2. Wyciągamy bity (uwzględniając ewentualne wirtualne zmiany na stosie)
        BitSet bitsOut = activeVirtualSplits.getOrDefault(move.movingSubtree, nodeBitSets.get(move.movingSubtree));
        BitSet bitsIn = activeVirtualSplits.getOrDefault(move.swapPartner, nodeBitSets.get(move.swapPartner));

        // 3. Wywołujemy uniwersalny rdzeń
        return applyNniStep(nodeToUpdate, bitsOut, bitsIn);
    }

    @Override
    public void undoNni(NniMove move) {
        // Cofnięcie NNI z obiektu to dokładnie to samo, co cofnięcie kroku ze stosu
        undoNniStep();
    }

    public double applyUpdate(Node node, BitSet bitsToApply, boolean add) {
        sharedSplitsHistory.push(sharedSplitsCount);
        movingNodeHistory.push(node);

        BitSet oldBS = activeVirtualSplits.getOrDefault(node, nodeBitSets.get(node));
        activeSplitHistory.push(oldBS);

        if (targetSplits.contains(normalizeSplit(oldBS))) sharedSplitsCount--;

        BitSet newBS = (BitSet) oldBS.clone();
        if (add) newBS.or(bitsToApply); else newBS.andNot(bitsToApply);

        activeVirtualSplits.put(node, newBS);
        if (targetSplits.contains(normalizeSplit(newBS))) sharedSplitsCount++;

        updateCurrentDistance();
        return currentDistance;
    }

    public void undoUpdate() {
        if (sharedSplitsHistory.isEmpty()) return;
        this.sharedSplitsCount = sharedSplitsHistory.pop();
        activeVirtualSplits.put(movingNodeHistory.pop(), activeSplitHistory.pop());
        updateCurrentDistance();
    }

    /**
     * Generyczne zatwierdzenie ruchu dla architektury heurystyk.
     */
    public void commit() {
        nodeBitSets.putAll(activeVirtualSplits);
        activeVirtualSplits.clear();
        sharedSplitsHistory.clear();
        movingNodeHistory.clear();
        activeSplitHistory.clear();
    }

    @Override
    public double getCurrentDistance() {
        return currentDistance;
    }

    // Pobiera aktualny klaster ze stosu NNI (lub bazowy)
    public BitSet getCluster(Node node) {
        return activeVirtualSplits.getOrDefault(node, nodeBitSets.get(node));
    }

    // Bezstanowa ewaluacja fizycznego dystansu SPR w oparciu o aktualny stan stosu NNI
    public double evaluateExactSprDistance(Node pruneNode, Node targetNode, BitSet movingBits) {
        int virtualShared = this.sharedSplitsCount;

        // 1. oldParent (stary rodzic) fizycznie znika w ruchu SPR
        Node oldParent = pruneNode.getParent();
        if (oldParent != null) {
            BitSet oldParentBits = getCluster(oldParent);
            if (isShared(oldParentBits)) virtualShared--;
        }

        // 2. Nowy węzeł powstaje bezpośrednio nad miejscem wpięcia (targetNode)
        BitSet targetBits = getCluster(targetNode);
        if (targetBits != null) {
            BitSet newNodeBits = (BitSet) targetBits.clone();
            newNodeBits.or(movingBits);
            if (isShared(newNodeBits)) virtualShared++;
        }

        // 3. Obliczenie dokładnego dystansu RF
        return (totalInternalSplits + targetSplits.size() - 2.0 * virtualShared) / 2.0;
    }

    // Pomocnicza metoda sprawdzająca, czy klaster występuje w drzewie docelowym
    public boolean isShared(BitSet bs) {
        if (bs == null) return false;
        BitSet norm = normalizeSplit((BitSet) bs.clone());
        int card = norm.cardinality();
        int total = allLeavesMask.cardinality();
        // Ignorujemy liście i korzeń
        if (card > 1 && card < total) {
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
        // Symetryczny dystans: |S1| + |S2| - 2*|S1 ∩ S2|
        // TreeCmp używa RF(0.5), więc dzielimy wynik przez 2.
        this.currentDistance = (totalInternalSplits + targetSplits.size() - 2.0 * sharedSplitsCount) / 2.0;
    }

    protected Map<String, Integer> createLeafMapping(Tree tree) {
        Map<String, Integer> mapping = new HashMap<>();
        int index = 0;
        Stack<Node> stack = new Stack<>();
        stack.push(tree.getRoot());
        while(!stack.isEmpty()) {
            Node n = stack.pop();
            if (n.isLeaf()) {
                mapping.put(n.getIdentifier().getName(), index++);
            } else {
                for(int i=0; i<n.getChildCount(); i++) stack.push(n.getChild(i));
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

            if (card > 1 && card < total) {
                store.add(normalized);
            }
        }

        if (fillCache) {
            nodeBitSets.put(node, (BitSet) bs.clone());
        }

        return bs;
    }
}