package treecmp.heuristics.moves;

import pal.tree.Node;

/**
 * Klasa reprezentująca pojedynczy ruch TBR (Tree Bisection and Reconnection).
 * Przechowuje pełną informację o strukturze ruchu do jego późniejszego fizycznego zaaplikowania.
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
    public String toString() {
        return getDescription();
    }

    private String getNodeName(Node n) {
        if (n == null) return "null";
        return n.isLeaf() ? n.getIdentifier().getName() : "Internal_" + n.getNumber();
    }
}