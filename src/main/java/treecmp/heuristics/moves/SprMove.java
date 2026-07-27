package treecmp.heuristics.moves;

import pal.tree.Node;

import java.util.ArrayList;
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
        return Math.max(1, calculatePathLength(pruneParent, targetNode));
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