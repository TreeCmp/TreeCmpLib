package treecmp.heuristics.spr;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.moves.SprMove;
import treecmp.heuristics.moves.TreeMove;
import treecmp.metrics.topological.BaseRFIncrementalMetric;
import treecmp.metrics.topological.RFClusterIncrementalMetric;
import treecmp.metrics.topological.RFIncrementalMetric;

import java.util.ArrayList;
import java.util.List;

public abstract class SprIncrementalHeuristicMetric extends IncrementalHeuristicBaseMetric {

    // Używamy nowej klasy SprUtils do fizycznych operacji
    private final SprUtils sprUtils = new SprUtils();

    public SprIncrementalHeuristicMetric(boolean rooted) {
        super(rooted, rooted ? new RFClusterIncrementalMetric() : new RFIncrementalMetric());
    }

    @Override
    protected void searchNeighborhood(Tree tree) {
        int totalNodes = tree.getInternalNodeCount() + tree.getExternalNodeCount();
        for (int i = 0; i < totalNodes; i++) {
            Node movingNode = getNodeByNumber(tree, i);
            // Korzeń nie może być odciętym poddrzewem w SPR
            if (movingNode.isRoot()) continue;

            // Startujemy "inteligentne ślizganie" poddrzewa
            slideRecursive(movingNode, movingNode.getParent(), movingNode, incMetric);
        }
    }

    private void slideRecursive(Node moving, Node currentPos, Node comingFrom, BaseRFIncrementalMetric incMetric) {
        for (Node target : getNeighbors(currentPos)) {
            if (target == comingFrom || target == moving) continue;

            // KROK NNI: Wirtualne przesunięcie o jedną krawędź
            NniMove step = new NniMove(moving, target);
            double dist = incMetric.applyNni(step);

            // Rejestrujemy ten punkt wpięcia jako potencjalny ruch SPR
            checkImprovement(dist, new SprMove(moving, target));

            // Jeśli nie dotarliśmy do liścia, ślizgamy się dalej wzdłuż tej gałęzi
            if (!target.isLeaf()) {
                slideRecursive(moving, target, currentPos, incMetric);
            }

            // Cofamy krok w metryce (O(1) undo), aby sprawdzić inne ścieżki
            incMetric.undoNni(step);
        }
    }

    @Override
    protected Tree applyPhysicalMove(Tree tree, TreeMove move) {
        if (move instanceof SprMove) {
            // POPRAWKA: Dodano kropkę i wywołanie metody z odpowiedniej klasy SprUtils
            return sprUtils.applyPhysicalSprMove(tree, (SprMove) move);
        }
        return tree;
    }

    @Override
    protected double commitMoveToMetric(TreeMove move) {
        if (move instanceof SprMove) {
            SprMove sm = (SprMove) move;
            // Ponieważ ruch SPR może być "daleki", po fizycznej zmianie drzewa
            // najbezpieczniej jest zainicjować stan metryki na nowo dla aktualnej bazy.
            // Pozwala to uniknąć błędów w wirtualnych rodzicach.
            // Uwaga: Jeśli wydajność jest krytyczna, można tu odtworzyć ścieżkę NNI.
            return incMetric.applyNni(new NniMove(sm.movingNode, sm.targetNode));
        }
        return incMetric.getCurrentDistance();
    }

    private List<Node> getNeighbors(Node n) {
        List<Node> neighbors = new ArrayList<>();
        // Węzeł może "ślizgać się" w górę (rodzic) lub w dół (dzieci)
        if (n.getParent() != null) neighbors.add(n.getParent());
        for (int i = 0; i < n.getChildCount(); i++) neighbors.add(n.getChild(i));
        return neighbors;
    }

    private Node getNodeByNumber(Tree t, int num) {
        // Pomocnicza metoda mapująca numery węzłów na obiekty Node PAL
        int extNum = t.getExternalNodeCount();
        return (num < extNum) ? t.getExternalNode(num) : t.getInternalNode(num - extNum);
    }
}