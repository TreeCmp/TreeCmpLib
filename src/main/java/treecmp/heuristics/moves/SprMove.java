package treecmp.heuristics.moves;

import pal.tree.Node;

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
}