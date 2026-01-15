package treecmp.heuristics.spr;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.moves.NniMove;
import treecmp.metrics.IncrementalMetric;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SprNeighborhoodWalker {

    public void walk(Tree baseTree, IncrementalMetric metric, Consumer<Double> resultConsumer) {
        // 1. Pobieramy wszystkie węzły z PAL-a
        // PAL nie ma wygodnego iteratora po wszystkim, więc robimy travers lub używamy helperów
        // Zakładam, że masz metodę pomocniczą lub po prostu rekurencję.
        List<Node> allNodes = getAllNodes(baseTree);

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot()) continue; // Korzenia nie odcinamy

            Node originalParent = pruneNode.getParent();

            // Rozpoczynamy spacer.
            // pruneNode - to co przenosimy
            // originalParent - miejsce, gdzie aktualnie jesteśmy podpięci
            // null - skąd przyszliśmy (by nie wracać)
            traverseRegraftLocations(pruneNode, originalParent, null, metric, resultConsumer);
        }
    }

    private void traverseRegraftLocations(Node movingSubtree,
                                          Node currentAttachPoint,
                                          Node cameFrom,
                                          IncrementalMetric metric,
                                          Consumer<Double> resultConsumer) {

        // Musimy znaleźć sąsiadów 'currentAttachPoint', na których możemy przeskoczyć.
        // W PAL sąsiedzi to: Ojciec oraz Dzieci.
        List<Node> neighbors = getPalNeighbors(currentAttachPoint);

        for (Node neighbor : neighbors) {
            // 1. Nie wracamy tam skąd przyszliśmy (DFS)
            if (neighbor == cameFrom) continue;

            // 2. Nie wchodzimy w 'movingSubtree' (bo ono jest wirtualnie odcięte i je niesiemy)
            if (neighbor == movingSubtree) continue;

            // 3. UWAGA SPECJALNA: Jeśli currentAttachPoint jest korzeniem globalnym,
            // a my przyszliśmy z jednego dziecka i idziemy do drugiego,
            // to relacja NNI może wyglądać inaczej.
            // Jednak w SPR "spacer" polega na zamianie miejscami poddrzewa przenoszonego
            // z poddrzewem znajdującym się na krawędzi sąsiedniej.

            // Konstrukcja ruchu: Zamieniamy 'movingSubtree' z 'neighbor'
            // To symuluje przejście przez krawędź (currentAttachPoint, neighbor)
            NniMove move = new NniMove(movingSubtree, neighbor);

            // Aplikuj (O(n))
            double dist = metric.applyNni(move);

            // Zgłoś wynik
            resultConsumer.accept(dist);

            // Rekurencja (Idź dalej w głąb)
            traverseRegraftLocations(movingSubtree, neighbor, currentAttachPoint, metric, resultConsumer);

            // Wycofaj (O(n))
            metric.undoNni(move);
        }
    }

    /**
     * Helper do wyciągania sąsiadów w grafie nieskierowanym z węzła PAL.
     */
    private List<Node> getPalNeighbors(Node node) {
        List<Node> neighbors = new ArrayList<>(3);

        // Dodaj ojca (jeśli istnieje)
        if (node.getParent() != null) {
            neighbors.add(node.getParent());
        }

        // Dodaj dzieci
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            neighbors.add(node.getChild(i));
        }
        return neighbors;
    }

    /**
     * Helper do pobrania wszystkich węzłów z drzewa PAL (DFS/BFS).
     */
    private List<Node> getAllNodes(Tree tree) {
        List<Node> list = new ArrayList<>();
        collectNodes(tree.getRoot(), list);
        return list;
    }

    private void collectNodes(Node node, List<Node> list) {
        list.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNodes(node.getChild(i), list);
        }
    }
}