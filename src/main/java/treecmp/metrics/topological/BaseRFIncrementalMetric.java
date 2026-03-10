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

    // --- ZMIANA: ŚLEDZENIE WIRTUALNEJ TOPOLOGII ---
    // Śledzi aktualny wirtualny split węzła w trakcie długich spacerów SPR
    protected final Map<Node, BitSet> activeVirtualSplits = new HashMap<>();

    // --- MECHANIZM O(1) UNDO ---
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
        // Czyszczenie całego stanu!
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

        extractTargetSplits(targetTree, leafMapping, targetSplits);

        Set<BitSet> baseSplits = new HashSet<>();
        extractBaseSplitsAndFillCache(baseTree, leafMapping, baseSplits);

        sharedSplitsCount = 0;
        for (BitSet bs : baseSplits) {
            if (targetSplits.contains(bs)) {
                sharedSplitsCount++;
            }
        }

        this.totalInternalSplits = baseSplits.size();
        updateCurrentDistance();
    }

    @Override
    public double applyNni(NniMove move) {
        // 0. Identyfikujemy węzeł, którego klaster FAKTYCZNIE się zmienia (Rodzic węzła odchodzącego)
        Node changingParent = move.movingSubtree.getParent();
        if (changingParent == null) {
            throw new IllegalStateException("Ruch NNI jest nieprawidłowy: przemieszczany węzeł nie ma rodzica.");
        }

        // Pobieramy bity wędrujących poddrzew (najpierw z wirtualnego cache, potem z fizycznego)
        BitSet leavingBits = activeVirtualSplits.get(move.movingSubtree);
        if (leavingBits == null) leavingBits = nodeBitSets.get(move.movingSubtree);

        BitSet enteringBits = activeVirtualSplits.get(move.swapPartner);
        if (enteringBits == null) enteringBits = nodeBitSets.get(move.swapPartner);

        if (leavingBits == null || enteringBits == null) {
            throw new IllegalStateException("Node cache miss inside NNI update.");
        }

        // 1. Zapisz obecny stan na stosy (dla O(1) Undo)
        // UWAGA: Zapisujemy changingParent, bo to on ulega zmianie, a nie sam movingSubtree!
        sharedSplitsHistory.push(sharedSplitsCount);
        movingNodeHistory.push(changingParent);
        BitSet previousActive = activeVirtualSplits.get(changingParent);
        activeSplitHistory.push(previousActive);

        // 2. Odkryj stary split rodzica (z poprzednich wirtualnych ruchów lub z fizycznego drzewa)
        BitSet oldSplitRaw = previousActive;
        if (oldSplitRaw == null) {
            oldSplitRaw = nodeBitSets.get(changingParent);
        }
        BitSet oldSplit = normalizeSplit(oldSplitRaw);

        // 3. Skonstruuj nowy split rodzica matematycznie: (StarySplit \ Odchodzący) U Przychodzący
        BitSet newSplitRaw = (BitSet) oldSplitRaw.clone();
        newSplitRaw.andNot(leavingBits); // Usuwamy to, co odpinamy
        newSplitRaw.or(enteringBits);    // Dodajemy to, co przypinamy

        BitSet newSplit = normalizeSplit(newSplitRaw);

        // 4. Zaktualizuj licznik wspólnych klastrów
        if (targetSplits.contains(oldSplit)) sharedSplitsCount--;
        if (targetSplits.contains(newSplit)) sharedSplitsCount++;

        // 5. Zaktualizuj wirtualną mapę dla TEGO węzła (rodzica) na potrzeby kolejnych ruchów
        activeVirtualSplits.put(changingParent, newSplitRaw);

        updateCurrentDistance();
        return currentDistance;
    }

    @Override
    public void undoNni(NniMove move) {
        // --- Błyskawiczne przywracanie stanu w O(1) ---
        // Zdejmujemy ze stosu dokładnie to, co zapisaliśmy
        sharedSplitsCount = sharedSplitsHistory.pop();
        Node movingNode = movingNodeHistory.pop();
        BitSet previousActive = activeSplitHistory.pop();

        if (previousActive == null) {
            activeVirtualSplits.remove(movingNode);
        } else {
            activeVirtualSplits.put(movingNode, previousActive);
        }

        updateCurrentDistance();
    }

    @Override
    public double getCurrentDistance() {
        return currentDistance;
    }

    @Override
    public double getDistance(Tree t1, Tree t2, int... indexes) {
        initCalculationState(t1, t2);
        return getCurrentDistance();
    }

    protected void updateCurrentDistance() {
        this.currentDistance = (double) (totalInternalSplits - sharedSplitsCount);
    }

    private BitSet calculateOldSplitFromTopology(Node movingNode) {
        Node parent = movingNode.getParent();
        if (parent == null) return new BitSet();

        Node sibling = null;
        for(int i=0; i<parent.getChildCount(); i++) {
            Node child = parent.getChild(i);
            if (child != movingNode) {
                sibling = child;
                break;
            }
        }
        BitSet bs = (BitSet) nodeBitSets.get(movingNode).clone();
        if (sibling != null && nodeBitSets.containsKey(sibling)) {
            bs.or(nodeBitSets.get(sibling));
        }
        return bs;
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

    private void extractTargetSplits(Tree tree, Map<String, Integer> mapping, Set<BitSet> store) {
        buildNodeBitSetsRec(tree.getRoot(), mapping, store, false);
    }

    private void extractBaseSplitsAndFillCache(Tree tree, Map<String, Integer> mapping, Set<BitSet> store) {
        buildNodeBitSetsRec(tree.getRoot(), mapping, store, true);
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

            if (!node.isRoot()) {
                BitSet normalized = normalizeSplit((BitSet) bs.clone());
                int card = normalized.cardinality();
                int total = allLeavesMask.cardinality();
                if (card > 1 && card < total - 1) {
                    store.add(normalized);
                }
            }
        }

        // To była nasza wczesniejsza poprawka - zapisuj wszystko!
        if (fillCache) {
            nodeBitSets.put(node, (BitSet) bs.clone());
        }

        return bs;
    }
}