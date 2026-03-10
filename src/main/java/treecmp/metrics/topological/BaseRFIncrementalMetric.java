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
        // 1. Zapisujemy stan przed zmianą na stosie historii (do undo)
        sharedSplitsHistory.push(sharedSplitsCount);

        // Pobieramy oba węzły tworzące krawędź wewnętrzną (rodziców zamienianych poddrzew)
        Node pMoving = move.movingSubtree.getParent();
        Node pPartner = move.swapPartner.getParent();

        // Rejestrujemy oba węzły w historii zmian
        movingNodeHistory.push(pMoving);
        activeSplitHistory.push(activeVirtualSplits.get(pMoving));

        movingNodeHistory.push(pPartner);
        activeSplitHistory.push(activeVirtualSplits.get(pPartner));

        // 2. Pobieramy maski bitowe poddrzew, które "wędrują"
        BitSet bitsMoving = getVirtualOrPhysicalBitSet(move.movingSubtree);
        BitSet bitsPartner = getVirtualOrPhysicalBitSet(move.swapPartner);

        // 3. Aktualizujemy oba węzły wewnętrzne krawędzi
        updateNodeBitSet(pMoving, bitsMoving, bitsPartner);
        updateNodeBitSet(pPartner, bitsPartner, bitsMoving);

        // 4. Obliczamy nowy dystans
        updateCurrentDistance();
        return currentDistance;
    }

    /**
     * Zwraca BitSet (maskę klastra/splitu) dla węzła.
     * Najpierw sprawdza, czy istnieje tymczasowa (wirtualna) wersja dla obecnego ruchu,
     * a jeśli nie, zwraca wersję z oryginalnego drzewa.
     */
    protected BitSet getVirtualOrPhysicalBitSet(Node node) {
        // 1. Sprawdź, czy węzeł jest w mapie aktywnych zmian (Virtual)
        if (activeVirtualSplits.containsKey(node)) {
            return activeVirtualSplits.get(node);
        }

        // 2. Jeśli nie, weź bitset wyliczony podczas inicjalizacji (Physical)
        BitSet physical = nodeBitSets.get(node);

        if (physical == null) {
            // To nie powinno się zdarzyć, jeśli initCalculationState działa poprawnie
            throw new IllegalStateException("Brak bitsetu dla węzła: " + node.getNumber());
        }

        return physical;
    }

    private void updateNodeBitSet(Node node, BitSet leaving, BitSet entering) {
        BitSet oldRaw = activeVirtualSplits.get(node);
        if (oldRaw == null) oldRaw = nodeBitSets.get(node);

        // 1. Normalizacja starego stanu (ważne dla unrooted!)
        BitSet oldNormalized = normalizeSplit((BitSet) oldRaw.clone());

        // 2. Obliczenie nowego stanu wirtualnego
        BitSet newRaw = (BitSet) oldRaw.clone();
        newRaw.andNot(leaving);
        newRaw.or(entering);

        // 3. Normalizacja nowego stanu - bez tego contains() zawiedzie
        BitSet newNormalized = normalizeSplit((BitSet) newRaw.clone());

        // 4. Aktualizacja licznika
        if (targetSplits.contains(oldNormalized)) {
            sharedSplitsCount--;
        }
        if (targetSplits.contains(newNormalized)) {
            sharedSplitsCount++;
        }

        activeVirtualSplits.put(node, newRaw);
    }

    @Override
    public void undoNni(NniMove move) {
        // Przywracamy licznik
        this.sharedSplitsCount = sharedSplitsHistory.pop();

        // Przywracamy stan drugiego węzła (pPartner)
        Node p2 = movingNodeHistory.pop();
        BitSet b2 = activeSplitHistory.pop();
        if (b2 == null) activeVirtualSplits.remove(p2);
        else activeVirtualSplits.put(p2, b2);

        // Przywracamy stan pierwszego węzła (pMoving)
        Node p1 = movingNodeHistory.pop();
        BitSet b1 = activeSplitHistory.pop();
        if (b1 == null) activeVirtualSplits.remove(p1);
        else activeVirtualSplits.put(p1, b1);

        updateCurrentDistance();
    }
    private void updateVirtualMap(Node n, BitSet b) {
        if (b == null) activeVirtualSplits.remove(n);
        else activeVirtualSplits.put(n, b);
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

    protected BitSet buildNodeBitSetsRec(Node node, Map<String, Integer> leafMap, Set<BitSet> store, boolean fillCache) {
        BitSet bs = new BitSet();
        if (node.isLeaf()) {
            String name = node.getIdentifier().getName();
            if (leafMap.containsKey(name)) bs.set(leafMap.get(name));
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                bs.or(buildNodeBitSetsRec(node.getChild(i), leafMap, store, fillCache));
            }

            // POPRAWKA: Sprawdzamy split również dla korzenia (szczególnie ważne dla unrooted 4-leaves)
            BitSet normalized = normalizeSplit((BitSet) bs.clone());
            int card = normalized.cardinality();
            int total = allLeavesMask.cardinality();

            // Split jest wewnętrzny, jeśli dzieli liście na co najmniej dwie grupy (2 <= card <= N-2)
            if (card >= 2 && card <= total - 2) {
                store.add(normalized);
            }
        }
        if (fillCache) nodeBitSets.put(node, (BitSet) bs.clone());
        return bs;
    }

    /**
     * Trwale zatwierdza wirtualne zmiany (activeVirtualSplits) jako nową bazę fizyczną.
     * Czyści historię ruchów, przygotowując metrykę na nową iterację heurystyki.
     */
    public void commitNni() {
        // Przenosimy wirtualne klastry/splity do głównego cache'u
        nodeBitSets.putAll(activeVirtualSplits);

        // Czyścimy stan wirtualny i historię - nowa iteracja zaczyna z "czystą kartą"
        activeVirtualSplits.clear();
        sharedSplitsHistory.clear();
        movingNodeHistory.clear();
        activeSplitHistory.clear();
    }

}