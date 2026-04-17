package treecmp.heuristics.tbr;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeNeighborhoodUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UTbrUtils extends TreeNeighborhoodUtils {

    @Override
    public Tree[] generateNeighbours(Tree tree) {
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        int intNum = tree.getInternalNodeCount();

        int neighSize = calcUsprNeighbours(tree) * intNum;
        Set<treecmp.heuristics.TreeUnootedHolder> utbrTreeSet = new HashSet<>((4 * neighSize) / 3);

        List<Node> allNodes = getAllNodes(tree);

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            List<Node> rerootNodes = getSubtreeNodes(pruneNode);

            for (Node rerootNode : rerootNodes) {
                for (Node targetNode : allNodes) {
                    if (isValidUTbrMove(pruneNode, rerootNode, targetNode)) {

                        Tree resultTree;
                        // ===============================================
                        // DELEGACJA DO SPR: Gwarantuje nam w 100% spójne topologie!
                        // ===============================================
                        if (pruneNode == rerootNode) {
                            resultTree = createUsprTree(tree, pruneNode, targetNode);
                        } else {
                            resultTree = createTbrTree(tree, pruneNode, rerootNode, targetNode);
                        }

                        if (resultTree != null) {
                            try {
                                utbrTreeSet.add(new treecmp.heuristics.TreeUnootedHolder(resultTree, idGroup));
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
        for (treecmp.heuristics.TreeUnootedHolder th : utbrTreeSet) {
            utbrTreeArray[i] = th.tree;
            i++;
        }
        return utbrTreeArray;
    }
}