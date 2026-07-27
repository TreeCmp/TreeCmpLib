package treecmp.heuristics.vnd.acc;

import pal.tree.Tree;
import pal.tree.SimpleTree;
import treecmp.common.AlignInfo;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.metrics.Metric;

import java.util.List;

public class VndIncrementalHeuristic implements Metric {

    private final List<IncrementalHeuristicBaseMetric> incrementalNeighborhoods;
    private final Metric classicFallbackTbr;
    private final String metricName;

    public VndIncrementalHeuristic(List<IncrementalHeuristicBaseMetric> incrementalNeighborhoods,
                                   Metric classicFallbackTbr,
                                   String metricName) {
        this.incrementalNeighborhoods = incrementalNeighborhoods;
        this.classicFallbackTbr = classicFallbackTbr;
        this.metricName = metricName;
    }

    public double getDistance(Tree tree1, Tree tree2) {
        Tree currentTree = new SimpleTree(tree1);
        if (currentTree instanceof SimpleTree) {
            ((SimpleTree) currentTree).createNodeList();
        }

        // Bierzemy dystans startowy z pierwszego (najszybszego) otoczenia
        double currentBestDist = incrementalNeighborhoods.get(0).evaluateInitialDistance(currentTree, tree2);

        // NOWOŚĆ: Rejestr całkowitego wypracowanego dystansu NNI
        double totalNniCost = 0.0;

        int k = 0;
        int maxNeighborhoods = incrementalNeighborhoods.size() + (classicFallbackTbr != null ? 1 : 0);
        int failSafeCounter = 0;

        // Pętla VND: Dopóki nie dotarliśmy do celu i nie wyczerpaliśmy otoczeń
        while (k < maxNeighborhoods && currentBestDist > 0 && failSafeCounter < 5000) {
            double distAfterSearch = currentBestDist;
            Tree treeAfterSearch = currentTree;

            if (k < incrementalNeighborhoods.size()) {
                // 1. Wywołanie wyczerpującego zejścia w lokalnym otoczeniu
                IncrementalHeuristicBaseMetric currentHeuristic = incrementalNeighborhoods.get(k);
                distAfterSearch = currentHeuristic.performLocalDescent(currentTree, tree2);
                treeAfterSearch = currentHeuristic.getLastOptimumTree();

                // NOWOŚĆ: Pobieramy i sumujemy rzeczywisty koszt NNI (nawet jeśli utknęliśmy na > 0)
                totalNniCost += currentHeuristic.getAccumulatedNniCost();

            } else {
                // 2. Ostatnia deska ratunku (TBR Klasyczne)
                double tbrDist = 0;
                try {
                    tbrDist = classicFallbackTbr.getDistance(currentTree, tree2);
                } catch (TreeCmpException e) {
                    throw new RuntimeException(e);
                }

                if (tbrDist != Double.POSITIVE_INFINITY && tbrDist < currentBestDist) {
                    // Ponieważ klasyczne TBR nie udostępnia łatwo wyliczonego kosztu NNI
                    // i nie mutuje drzewa, traktujemy je jako "wybawienie" i szacujemy koszt
                    distAfterSearch = 0; // Zakładamy dotarcie do celu
                    totalNniCost += (tbrDist * 4.0); // Ręcznie ustawiony mnożnik zastępczy dla TBR
                }
            }

            // --- LOGIKA VND ---
            if (distAfterSearch < currentBestDist) {
                // SUKCES! Zeszliśmy w dół po wyczerpaniu dostępnego otoczenia.
                currentBestDist = distAfterSearch;
                currentTree = treeAfterSearch;
                k = 0; // Zgodnie z koncepcją VND - wracamy do NNI
            } else {
                // MINIMUM LOKALNE!
                k++;
            }
            failSafeCounter++;
        }

        // Zwracamy wypracowany rzeczywisty koszt kroków, ale tylko jeśli algorytm zakończył się sukcesem
        return (currentBestDist == 0) ? totalNniCost : Double.POSITIVE_INFINITY;
    }

    @Override
    public double getDistance(Tree t1, Tree t2, int... indexes) { return getDistance(t1, t2); }

    @Override
    public String getName() { return "VND_" + metricName; }

    @Override public String getCommandLineName() { return "vnd"; }
    @Override public void setCommandLineName(String cln) {}
    @Override public void setName(String name) {}
    @Override public String getDescription() { return "Variable Neighborhood Descent Orchestrator"; }
    @Override public void setDescription(String d) {}
    @Override public void initData() {}
    @Override public boolean isRooted() { return incrementalNeighborhoods.get(0).isRooted(); }
    @Override public boolean isWeighted() { return false; }
    @Override public boolean isDiffLeafSets() { return false; }
    @Override public AlignInfo getAlignment() { return null; }
}