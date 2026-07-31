package treecmp.heuristics.moves;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.spr.SprUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SprMove implements TreeMove {
    public final Node movingNode;   // Poddrzewo, które "odcinamy"
    public final Node targetNode;   // Węzeł, powyżej którego "wszczepiamy" poddrzewo

    public SprMove(Node movingNode, Node targetNode) {
        this.movingNode = movingNode;
        this.targetNode = targetNode;
    }

    @Override
    public String getDescription() {
        return "SPR: Move node " + movingNode.getNumber() + " above node " + targetNode.getNumber();
    }

    @Override
    public int getNniEquivalentCost() {
        Node pruneParent = movingNode.getParent();
        if (pruneParent == null) return 1; // Zabezpieczenie na wypadek dziwnych struktur
        // Odejmujemy 1, ponieważ pierwsze przesunięcie na sąsiednią krawędź daje tę samą topologię (0 NNI)
        return Math.max(1, calculatePathLength(pruneParent, targetNode) - 1);
    }

    @Override
    public List<Tree> getNniTrajectory(Tree startTree) {
        SprUtils sprUtils = new SprUtils();
        Node pruneParent = movingNode.getParent();

        // Zabezpieczenie dla struktur brzegowych
        if (pruneParent == null || pruneParent == targetNode) {
            Tree finalTree = sprUtils.createAndFixSprTree(startTree, movingNode, targetNode);
            return finalTree != null ? Collections.singletonList(finalTree) : Collections.emptyList();
        }

        List<Node> path = getSimplePath(pruneParent, targetNode);

        // Jeśli ścieżka ma mniej niż 3 węzły (ruch o koszcie 1 NNI), od razu zwracamy tylko drzewo docelowe
        if (path.size() < 3) {
            Tree finalTree = sprUtils.createAndFixSprTree(startTree, movingNode, targetNode);
            return finalTree != null ? Collections.singletonList(finalTree) : Collections.emptyList();
        }

        List<Tree> trajectory = new ArrayList<>();

        // Generujemy kolejne drzewa pośrednie NNI along the path:
        // Indeks 0 to pruneParent (start), indeks 1 to rodzeństwo/ojciec (0 NNI),
        // dlatego właściwe kroki NNI zaczynają się od indeksu 2 aż do targetNode.
        for (int i = 2; i < path.size(); i++) {
            Node stepTarget = path.get(i);
            Tree stepTree = sprUtils.createAndFixSprTree(startTree, movingNode, stepTarget);
            if (stepTree != null) {
                trajectory.add(stepTree);
            }
        }

        // Gwarantujemy, że na końcu listy zawsze znajduje się drzewo docelowe
        if (trajectory.isEmpty()) {
            Tree finalTree = sprUtils.createAndFixSprTree(startTree, movingNode, targetNode);
            if (finalTree != null) {
                trajectory.add(finalTree);
            }
        }

        return trajectory;
    }

    /**
     * Zwraca prostą ścieżkę węzłów od 'start' do 'end' w drzewie.
     */
    private List<Node> getSimplePath(Node start, Node end) {
        List<Node> pathStartToRoot = new ArrayList<>();
        Node curr = start;
        while (curr != null) {
            pathStartToRoot.add(curr);
            curr = curr.getParent();
        }

        List<Node> pathEndToRoot = new ArrayList<>();
        curr = end;
        while (curr != null) {
            pathEndToRoot.add(curr);
            curr = curr.getParent();
        }

        // Znajdujemy Najniższego Wspólnego Przodka (LCA)
        Node lca = null;
        for (Node n : pathStartToRoot) {
            if (pathEndToRoot.contains(n)) {
                lca = n;
                break;
            }
        }

        List<Node> path = new ArrayList<>();
        if (lca == null) return path;

        int idxStart = pathStartToRoot.indexOf(lca);
        int idxEnd = pathEndToRoot.indexOf(lca);

        // 1. Od 'start' w górę do LCA (włącznie)
        for (int i = 0; i <= idxStart; i++) {
            path.add(pathStartToRoot.get(i));
        }

        // 2. Od LCA w dół do 'end' (pomijając sam LCA na indeksie idxEnd)
        for (int i = idxEnd - 1; i >= 0; i--) {
            path.add(pathEndToRoot.get(i));
        }

        return path;
    }

    /**
     * Wylicza dystans topologiczny (liczbę krawędzi) między dwoma węzłami.
     */
    private int calculatePathLength(Node a, Node b) {
        if (a == null || b == null || a == b) return 0;

        List<Node> pathA = new ArrayList<>();
        Node curr = a;
        while (curr != null) {
            pathA.add(curr);
            curr = curr.getParent();
        }

        curr = b;
        int distB = 0;
        while (curr != null) {
            int idx = pathA.indexOf(curr);
            if (idx != -1) {
                return idx + distB;
            }
            curr = curr.getParent();
            distB++;
        }
        return 1;
    }
}