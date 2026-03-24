package treecmp.heuristics.spr;

import pal.tree.Node;

/**
 * Interfejs pozwalający na przekazanie wyników z Walkera do heurystyki.
 */
@FunctionalInterface
public interface SprVisitor {
    void visit(double dist, Node movingNode, Node targetNode);
}