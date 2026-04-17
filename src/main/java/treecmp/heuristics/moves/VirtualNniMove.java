package treecmp.heuristics.moves;

import pal.tree.Node;

public class VirtualNniMove extends NniMove {
    public final Node virtualParent;

    public VirtualNniMove(Node movingSubtree, Node swapPartner, Node virtualParent) {
        super(movingSubtree, swapPartner); // Konstruktor klasy bazowej
        this.virtualParent = virtualParent;
    }
}