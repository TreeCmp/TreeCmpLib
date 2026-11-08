package treecmp.heuristics.tbr;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.moves.TbrMove;
import treecmp.heuristics.spr.SprUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class TbrUtils extends TreeNeighborhoodUtils {

    private final SprUtils sprUtils = new SprUtils();

    // =========================================================================
    // POLIMORFICZNA WALIDACJA RUCHU rTBR
    // =========================================================================

    @Override
    public boolean isValidTbrMove(Node pruneNode, Node rerootNode, Node targetNode) {
        if (pruneNode == rerootNode) {
            return sprUtils.isValidSprMove(pruneNode, targetNode);
        }
        if (targetNode == null || pruneNode == null || rerootNode == null) return false;
        if (pruneNode.isRoot() || pruneNode.getParent() == null) return false;
        if (targetNode == pruneNode.getParent()) return false;

        Node pParent = pruneNode.getParent();
        Node sibling = (pParent.getChild(0) == pruneNode) ? pParent.getChild(1) : pParent.getChild(0);

        // Ruch tożsamościowy (powrót na rodzeństwo bez zmiany korzenia)
        if (rerootNode == pruneNode && targetNode == sibling) {
            return false;
        }

        // 1. targetNode nie może leżeć wewnątrz odcinanego poddrzewa pruneNode
        Node curr = targetNode;
        while (curr != null) {
            if (curr == pruneNode) return false;
            curr = curr.getParent();
        }

        // 2. rerootNode MUSI leżeć wewnątrz odcinanego poddrzewa pruneNode
        boolean rerootInPruneSubtree = false;
        Node currR = rerootNode;
        while (currR != null) {
            if (currR == pruneNode) {
                rerootInPruneSubtree = true;
                break;
            }
            currR = currR.getParent();
        }
        if (!rerootInPruneSubtree) return false;

        return true;
    }

    // =========================================================================
    // FIZYCZNE TWORZENIE DRZEWA rTBR
    // =========================================================================

    @Override
    public Tree createTbrTree(Tree tree, Node pruneNode, Node rerootNode, Node targetNode) {
        if (tree == null || pruneNode == null || rerootNode == null || targetNode == null) {
            return null;
        }
        if (!isValidTbrMove(pruneNode, rerootNode, targetNode)) {
            return null;
        }

        try {
            Tree resultTree = super.createTbrTree(tree, pruneNode, rerootNode, targetNode);
            if (resultTree != null) {
                if (resultTree instanceof SimpleTree) {
                    ((SimpleTree) resultTree).createNodeList();
                }
                TreeUtils.computeParentPointers(resultTree.getRoot());
                return resultTree;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    // =========================================================================
    // PRZEGLĄDANIE OTOCZENIA (FOR-EACH)
    // =========================================================================

    @Override
    public void forEachNeighbour(Tree tree, Consumer<Tree> action) {
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        int intNum = tree.getInternalNodeCount();

        int neighSize = calcSprNeighbours(tree) * intNum;
        Set<treecmp.heuristics.TreeHolder> seenTrees = new HashSet<>((4 * neighSize) / 3);

        treecmp.heuristics.TreeHolder baseTreeHolder = new treecmp.heuristics.TreeRootedHolder(tree, idGroup);
        List<Node> allNodes = getAllNodes(tree);

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            List<Node> rerootNodes = getSubtreeNodes(pruneNode);

            for (Node rerootNode : rerootNodes) {
                for (Node targetNode : allNodes) {
                    if (isValidTbrMove(pruneNode, rerootNode, targetNode)) {

                        Tree resultTree = createTbrTree(tree, pruneNode, rerootNode, targetNode);

                        if (resultTree != null) {
                            treecmp.heuristics.TreeHolder newHolder = new treecmp.heuristics.TreeRootedHolder(resultTree, idGroup);
                            if (!newHolder.equals(baseTreeHolder)) {
                                if (seenTrees.add(newHolder)) {
                                    TbrMove move = new TbrMove(pruneNode, rerootNode, targetNode);
                                    registerTreeCost(resultTree, move.getNniEquivalentCost());
                                    registerTreeMove(resultTree, move);
                                    action.accept(resultTree);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public Tree[] generateNeighboursOBSOLETE(Tree tree) {
        List<Tree> list = new ArrayList<>();
        forEachNeighbour(tree, list::add);
        return list.toArray(new Tree[0]);
    }
}