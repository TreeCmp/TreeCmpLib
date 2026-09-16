package treecmp.heuristics.tbr.acc;

import pal.tree.Node;

@FunctionalInterface
public interface TbrVisitor {
    void visit(double distance, Node pruneNode, Node rerootNode, Node targetNode);
}