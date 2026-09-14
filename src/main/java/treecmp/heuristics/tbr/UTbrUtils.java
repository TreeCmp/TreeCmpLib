package treecmp.heuristics.tbr;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.spr.SprTopologyGuard;
import treecmp.heuristics.spr.UsprUtils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

        Node curr = targetNode;
        while (curr != null) {
            if (curr == pruneNode) return false;
            curr = curr.getParent();
        }
        return true;
    }

    public boolean isValidUtbrMove(Node pruneNode, Node rerootNode, Node targetNode) {
        return isValidUTbrMove(pruneNode, rerootNode, targetNode);
    }

    public Tree createUtbrTree(Tree tree, Node pruneNode, Node rerootNode, Node targetNode) {
        Tree resultTree;
        if (pruneNode == rerootNode) {
            resultTree = usprUtils.createUsprTree(tree, pruneNode, targetNode);
        } else {
            resultTree = createTbrTree(tree, pruneNode, rerootNode, targetNode);
        }

        if (resultTree != null) {
            // Przypisanie zwracanego obiektu Tree
            if (resultTree.getRoot().getChildCount() == 2) {
                resultTree = TreeCmpUtils.unrootTreeIfNeeded(resultTree);
                if (resultTree instanceof SimpleTree) {
                    TreeUtils.computeParentPointers(resultTree.getRoot());
                    ((SimpleTree) resultTree).createNodeList();
                }
            }

            if (resultTree.getRoot().getChildCount() < 3) {
                return null;
            }

            if (resultTree.getExternalNodeCount() != tree.getExternalNodeCount()) {
                return null;
            }

            if (!SprTopologyGuard.isStrictlyValidUnrootedTree(resultTree, tree.getExternalNodeCount())) {
                return null;
            }
        }
        return resultTree;
    }

    @Override
    public void forEachNeighbour(Tree tree, Consumer<Tree> action) {
        // Przypisanie znormalizowanego drzewa do workingTree
        Tree workingTree = tree;
        if (workingTree.getRoot().getChildCount() == 2) {
            workingTree = TreeCmpUtils.unrootTreeIfNeeded(workingTree.getCopy());
            if (workingTree instanceof SimpleTree) {
                TreeUtils.computeParentPointers(workingTree.getRoot());
                ((SimpleTree) workingTree).createNodeList();
            }
        }

        IdGroup idGroup = TreeUtils.getLeafIdGroup(workingTree);
        int numLeaves = workingTree.getExternalNodeCount();

        Set<String> seenTopologies = new HashSet<>();

        String baseTreeHash = getUnrootedCanonicalTopology(workingTree, idGroup, numLeaves);
        seenTopologies.add(baseTreeHash);

        List<Node> allNodes = getAllNodes(workingTree);

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            List<Node> rerootNodes = getSubtreeNodes(pruneNode);

            for (Node rerootNode : rerootNodes) {
                for (Node targetNode : allNodes) {
                    if (isValidUTbrMove(pruneNode, rerootNode, targetNode)) {
                        Tree resultTree = createUtbrTree(workingTree, pruneNode, rerootNode, targetNode);

                        if (resultTree != null) {
                            String topologyHash = getUnrootedCanonicalTopology(resultTree, idGroup, numLeaves);
                            if (seenTopologies.add(topologyHash)) {
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

    // =========================================================================
    // KANONICZNA DEDUPLIKACJA PODZIAŁÓW
    // =========================================================================

    private String getUnrootedCanonicalTopology(Tree tree, IdGroup idGroup, int numLeaves) {
        List<String> splits = new ArrayList<>();
        getSplits(tree.getRoot(), idGroup, numLeaves, splits);
        Collections.sort(splits);
        StringBuilder sb = new StringBuilder();
        for (String split : splits) {
            sb.append(split).append("|");
        }
        return sb.toString();
    }

    private BitSet getSplits(Node node, IdGroup idGroup, int numLeaves, List<String> splits) {
        BitSet bs = new BitSet(numLeaves);
        if (node.isLeaf()) {
            if (node.getIdentifier() != null && node.getIdentifier().getName() != null) {
                int id = idGroup.whichIdNumber(node.getIdentifier().getName());
                if (id >= 0 && id < numLeaves) {
                    bs.set(id);
                }
            }
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                bs.or(getSplits(node.getChild(i), idGroup, numLeaves, splits));
            }
        }

        if (!node.isRoot()) {
            BitSet normalized = (BitSet) bs.clone();
            if (normalized.get(0)) {
                normalized.flip(0, numLeaves);
            }
            splits.add(normalized.toString());
        }
        return bs;
    }
}