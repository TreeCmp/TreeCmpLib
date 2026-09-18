package treecmp.heuristics.moves;

import pal.tree.Node;
import pal.tree.Tree;

import java.util.Collections;
import java.util.List;

/**
 * Klasa reprezentująca pojedynczy ruch TBR (Tree Bisection and Reconnection).
 * Przechowuje pełną informację o strukturze ruchu oraz oblicza równoważny koszt NNI.
 */
public class TbrMove implements TreeMove {

    public final Node movingNode;  // Odcięty korzeń (pruneNode)
    public final Node rerootNode;  // Nowy korzeń po przekorzenieniu odciętego poddrzewa
    public final Node targetNode;  // Docelowe miejsce wpięcia w głównym drzewie

    public TbrMove(Node movingNode, Node rerootNode, Node targetNode) {
        this.movingNode = movingNode;
        this.rerootNode = rerootNode;
        this.targetNode = targetNode;
    }

    @Override
    public String getDescription() {
        return "TbrMove[prune=" + getNodeName(movingNode) +
                ", reroot=" + getNodeName(rerootNode) +
                ", target=" + getNodeName(targetNode) + "]";
    }

    @Override
    public int getNniEquivalentCost() {
        int cost = 0;

        // 1. Koszt przekorzenienia wewnątrz odciętego poddrzewa: dist(movingNode, rerootNode)
        if (movingNode != null && rerootNode != null && movingNode != rerootNode) {
            cost += getPathDistanceInSubtree(movingNode, rerootNode);
        }

        // 2. Koszt przemieszczenia punktu wpięcia w drzewie głównym: dist(movingNode.getParent(), targetNode)
        if (movingNode != null && movingNode.getParent() != null && targetNode != null) {
            Node originalAttachment = movingNode.getParent();
            cost += getTreeDistance(originalAttachment, targetNode, movingNode);
        }

        return Math.max(1, cost);
    }

    private int getPathDistanceInSubtree(Node root, Node target) {
        int dist = 0;
        Node curr = target;
        while (curr != null && curr != root) {
            dist++;
            curr = curr.getParent();
        }
        return dist;
    }

    private int getTreeDistance(Node u, Node v, Node excludeSubtree) {
        if (u == v) return 0;

        int dU = 0;
        Node curr = u;
        while (curr != null) {
            if (curr == excludeSubtree) break;
            dU++;
            curr = curr.getParent();
        }

        int dV = 0;
        curr = v;
        while (curr != null) {
            if (curr == excludeSubtree) break;
            dV++;
            curr = curr.getParent();
        }

        Node pU = u;
        Node pV = v;
        int steps = 0;

        while (dU > dV && pU != null) {
            pU = pU.getParent();
            dU--;
            steps++;
        }
        while (dV > dU && pV != null) {
            pV = pV.getParent();
            dV--;
            steps++;
        }
        while (pU != pV && pU != null && pV != null) {
            pU = pU.getParent();
            pV = pV.getParent();
            steps += 2;
        }

        return steps;
    }

    @Override
    public List<Tree> getNniTrajectory(Tree startTree) {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return getDescription();
    }

    private String getNodeName(Node n) {
        if (n == null) return "null";
        return n.isLeaf() ? n.getIdentifier().getName() : "Internal_" + n.getNumber();
    }
}