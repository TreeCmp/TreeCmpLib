package treecmp.heuristics.tbr;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.TreeUnrootedHolder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class UTbrUtils extends TreeNeighborhoodUtils {

    public Tree createUtbrTree(Tree tree, Node pruneNode, Node rerootNode, Node targetNode) {
        if (pruneNode == rerootNode) {
            return createUsprTree(tree, pruneNode, targetNode);
        } else {
            return createTbrTree(tree, pruneNode, rerootNode, targetNode);
        }
    }

    public boolean isValidUtbrMove(Node pruneNode, Node rerootNode, Node targetNode) {
        return isValidUTbrMove(pruneNode, rerootNode, targetNode);
    }

    @Override
    public void forEachNeighbour(Tree tree, Consumer<Tree> action) {
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        int intNum = tree.getInternalNodeCount();

        int neighSize = calcUsprNeighbours(tree) * intNum;
        Set<TreeUnrootedHolder> seenTrees = new HashSet<>((4 * neighSize) / 3);

        TreeUnrootedHolder baseTreeHolder = null;
        try {
            baseTreeHolder = new TreeUnrootedHolder(tree, idGroup);
        } catch (Exception ignored) {}

        List<Node> allNodes = getAllNodes(tree);

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            List<Node> rerootNodes = getSubtreeNodes(pruneNode);

            for (Node rerootNode : rerootNodes) {
                for (Node targetNode : allNodes) {
                    if (isValidUtbrMove(pruneNode, rerootNode, targetNode)) {
                        Tree resultTree = createUtbrTree(tree, pruneNode, rerootNode, targetNode);

                        if (resultTree != null) {
                            try {
                                TreeUnrootedHolder newHolder = new TreeUnrootedHolder(resultTree, idGroup);
                                if (baseTreeHolder == null || !newHolder.equals(baseTreeHolder)) {
                                    // add() zwraca true tylko wtedy, gdy drzewo nie było wcześniej w secie
                                    if (seenTrees.add(newHolder)) {
                                        action.accept(resultTree);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }
    }

    //@Override
    public Tree[] generateNeighboursOBSOLETE(Tree tree) {
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        int intNum = tree.getInternalNodeCount();

        int neighSize = calcUsprNeighbours(tree) * intNum;
        Set<TreeUnrootedHolder> utbrTreeSet = new HashSet<>((4 * neighSize) / 3);

        // ZABEZPIECZENIE: Pobieramy hash drzewa bazowego
        TreeUnrootedHolder baseTreeHolder = null;
        try {
            baseTreeHolder = new TreeUnrootedHolder(tree, idGroup);
        } catch (Exception e) {
            // Ignorujemy w skrajnym przypadku
        }

        List<Node> allNodes = getAllNodes(tree);

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            List<Node> rerootNodes = getSubtreeNodes(pruneNode);

            for (Node rerootNode : rerootNodes) {
                for (Node targetNode : allNodes) {

                    // Używamy ujednoliconej nazwy
                    if (isValidUtbrMove(pruneNode, rerootNode, targetNode)) {

                        // Wywołujemy naszą nową publiczną metodę!
                        Tree resultTree = createUtbrTree(tree, pruneNode, rerootNode, targetNode);

                        if (resultTree != null) {
                            try {
                                TreeUnrootedHolder newHolder = new TreeUnrootedHolder(resultTree, idGroup);
                                // FILTROWANIE: Ignorujemy drzewo bazowe
                                if (baseTreeHolder == null || !newHolder.equals(baseTreeHolder)) {
                                    utbrTreeSet.add(newHolder);
                                }
                            } catch (Exception e) {
                                // Ignorujemy skrajne degeneracje biblioteki PAL
                            }
                        }
                    }
                }
            }
        }

        Tree[] utbrTreeArray = new Tree[utbrTreeSet.size()];
        int i = 0;
        for (TreeUnrootedHolder th : utbrTreeSet) {
            utbrTreeArray[i] = th.tree;
            i++;
        }
        return utbrTreeArray;
    }

}
