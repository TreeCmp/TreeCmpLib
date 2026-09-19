package treecmp.heuristics.tbr;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.moves.TbrMove;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class TbrUtils extends TreeNeighborhoodUtils {

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
                            if (resultTree instanceof SimpleTree) {
                                TreeUtils.computeParentPointers(resultTree.getRoot());
                                ((SimpleTree) resultTree).createNodeList();
                            }

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
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        int intNum = tree.getInternalNodeCount();

        int neighSize = calcSprNeighbours(tree) * intNum;
        Set<treecmp.heuristics.TreeHolder> tbrTreeSet = new HashSet<>((4 * neighSize) / 3);

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
                            if (resultTree instanceof SimpleTree) {
                                TreeUtils.computeParentPointers(resultTree.getRoot());
                                ((SimpleTree) resultTree).createNodeList();
                            }

                            treecmp.heuristics.TreeHolder newHolder = new treecmp.heuristics.TreeRootedHolder(resultTree, idGroup);
                            if (!newHolder.equals(baseTreeHolder)) {
                                if (tbrTreeSet.add(newHolder)) {
                                    TbrMove move = new TbrMove(pruneNode, rerootNode, targetNode);
                                    registerTreeCost(resultTree, move.getNniEquivalentCost());
                                    registerTreeMove(resultTree, move);
                                }
                            }
                        }
                    }
                }
            }
        }

        Tree[] tbrTreeArray = new Tree[tbrTreeSet.size()];
        int i = 0;
        for (treecmp.heuristics.TreeHolder th : tbrTreeSet) {
            tbrTreeArray[i] = th.tree;
            i++;
        }
        return tbrTreeArray;
    }
}