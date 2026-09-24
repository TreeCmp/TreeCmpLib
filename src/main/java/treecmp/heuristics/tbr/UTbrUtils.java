package treecmp.heuristics.tbr;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.moves.TbrMove;
import treecmp.heuristics.spr.UsprUtils;

import java.util.*;
import java.util.function.Consumer;

public class UTbrUtils extends TreeNeighborhoodUtils {

    private final UsprUtils usprUtils = new UsprUtils();

    @Override
    public boolean isValidUTbrMove(Node pruneNode, Node rerootNode, Node targetNode) {
        if (pruneNode == rerootNode) {
            return usprUtils.isValidUsprMove(pruneNode, targetNode);
        }
        if (targetNode == null || pruneNode == null || rerootNode == null) return false;
        if (targetNode.isRoot() || pruneNode.isRoot()) return false;
        if (targetNode == pruneNode.getParent()) return false;

        // 1. targetNode nie może leżeć wewnątrz odcinanego poddrzewa pruneNode
        Node curr = targetNode;
        while (curr != null) {
            if (curr == pruneNode) return false;
            curr = curr.getParent();
        }

        // 2. rerootNode MUSI leżeć ściśle wewnątrz odcinanego poddrzewa pruneNode
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

    public boolean isValidUtbrMove(Node pruneNode, Node rerootNode, Node targetNode) {
        return isValidUTbrMove(pruneNode, rerootNode, targetNode);
    }

    public Tree createUtbrTree(Tree tree, Node pruneNode, Node rerootNode, Node targetNode) {
        if (tree == null || pruneNode == null || rerootNode == null || targetNode == null) {
            return null;
        }
        if (!isValidUTbrMove(pruneNode, rerootNode, targetNode)) {
            return null;
        }

        try {
            // Bezpieczne i szybkie tworzenie TBR w pamięci operacyjnej
            Tree resultTree = createTbrTree(tree, pruneNode, rerootNode, targetNode);

            if (resultTree != null) {
                if (resultTree.getRoot().getChildCount() == 2) {
                    resultTree = fastUnrootIfNeeded(resultTree);
                }

                if (resultTree == null || resultTree.getRoot().getChildCount() < 3) {
                    return null;
                }

                // Czysto obiektowa walidacja struktury (zero parsowania tekstu Newick)
                if (!isStrictlyValidUnrootedTreeFast(resultTree, tree.getExternalNodeCount())) {
                    return null;
                }
                return resultTree;
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    @Override
    public void forEachNeighbour(Tree tree, Consumer<Tree> action) {
        Tree workingTree = tree;
        if (workingTree.getRoot().getChildCount() == 2) {
            workingTree = fastUnrootIfNeeded(fastTreeClone(workingTree));
            if (workingTree instanceof SimpleTree) {
                TreeUtils.computeParentPointers(workingTree.getRoot());
                ((SimpleTree) workingTree).createNodeList();
            }
        }

        IdGroup idGroup = TreeUtils.getLeafIdGroup(workingTree);
        int numLeaves = workingTree.getExternalNodeCount();

        Set<CanonicalTopologyKey> seenTopologies = new HashSet<>();
        seenTopologies.add(buildCanonicalKey(workingTree, idGroup, numLeaves));

        List<Node> allNodes = getAllNodes(workingTree);

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            List<Node> rerootNodes = getSubtreeNodes(pruneNode);

            for (Node rerootNode : rerootNodes) {
                for (Node targetNode : allNodes) {
                    if (isValidUTbrMove(pruneNode, rerootNode, targetNode)) {
                        Tree resultTree = createUtbrTree(workingTree, pruneNode, rerootNode, targetNode);

                        if (resultTree != null) {
                            CanonicalTopologyKey key = buildCanonicalKey(resultTree, idGroup, numLeaves);
                            if (seenTopologies.add(key)) {
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

    public Tree[] generateNeighboursOBSOLETE(Tree tree) {
        List<Tree> list = new ArrayList<>();
        forEachNeighbour(tree, list::add);
        return list.toArray(new Tree[0]);
    }
}