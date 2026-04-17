package treecmp.heuristics.tbr;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeNeighborhoodUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TbrUtils extends TreeNeighborhoodUtils {

    @Override
    public Tree[] generateNeighbours(Tree tree) {
        IdGroup idGroup = TreeUtils.getLeafIdGroup(tree);
        int intNum = tree.getInternalNodeCount();

        int neighSize = calcSprNeighbours(tree) * intNum;
        Set<treecmp.heuristics.TreeHolder> tbrTreeSet = new HashSet<>((4 * neighSize) / 3);

        List<Node> allNodes = getAllNodes(tree);

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            List<Node> rerootNodes = getSubtreeNodes(pruneNode);

            for (Node rerootNode : rerootNodes) {
                for (Node targetNode : allNodes) {
                    if (isValidTbrMove(pruneNode, rerootNode, targetNode)) {

                        Tree resultTree;
                        // ===============================================
                        // DELEGACJA DO SPR
                        // ===============================================
                        if (pruneNode == rerootNode) {
                            resultTree = createSprTree(tree, pruneNode, targetNode);
                        } else {
                            resultTree = createTbrTree(tree, pruneNode, rerootNode, targetNode);
                        }

                        if (resultTree != null) {
                            tbrTreeSet.add(new treecmp.heuristics.TreeRootedHolder(resultTree, idGroup));
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