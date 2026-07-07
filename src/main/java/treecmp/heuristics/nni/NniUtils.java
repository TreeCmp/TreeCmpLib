package treecmp.heuristics.nni;

import java.util.*;

import pal.misc.IdGroup;
import pal.tree.*;
import treecmp.heuristics.TreeNeighborhoodUtils;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.TreeUnrootedHolder;
import treecmp.heuristics.moves.NniMove;

public class NniUtils extends TreeNeighborhoodUtils {

    private final boolean unrooted;

    public NniUtils(boolean unrooted) {
        this.unrooted = unrooted;
    }

    public NniMove[] generateNniMoves(Tree tree) {
        List<NniMove> moves = new ArrayList<>();
        int internalNodeCount = tree.getInternalNodeCount();

        for (int i = 0; i < internalNodeCount; i++) {
            Node parent = tree.getInternalNode(i);

            for (int j = 0; j < parent.getChildCount(); j++) {
                Node child = parent.getChild(j);

                if (!child.isLeaf()) {
                    addMovesForInternalEdge(parent, child, moves);
                }
            }
        }
        return moves.toArray(new NniMove[0]);
    }

    private void addMovesForInternalEdge(Node parent, Node child, List<NniMove> moves) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            Node sibling = parent.getChild(i);
            if (sibling == child) continue;

            Node grandchild1 = child.getChild(0);
            Node grandchild2 = child.getChild(1);

            moves.add(new NniMove(sibling, grandchild1));
            moves.add(new NniMove(sibling, grandchild2));

            if (unrooted && parent.isRoot()) {
                break;
            }
        }
    }

    private Tree createNniTree(Tree baseTree, NniMove move) {
        // Użycie odziedziczonego, błyskawicznego klonowania!
        Tree resultTree = fastTreeClone(baseTree);

        Node movingInCopy = findNodeInTree(resultTree, move.movingSubtree);
        Node partnerInCopy = findNodeInTree(resultTree, move.swapPartner);

        performNodeSwap(movingInCopy, partnerInCopy);
        return refreshTreeInPlace(resultTree); // Szybki update indeksów po NNI
    }

    public Tree applyPhysicalMove(Tree tree, NniMove move) {
        performNodeSwap(move.movingSubtree, move.swapPartner);
        return refreshTreeInPlace(tree);
    }

    private void performNodeSwap(Node node1, Node node2) {
        Node p1 = node1.getParent();
        Node p2 = node2.getParent();

        if (p1 == null || p2 == null) return;

        int idx1 = findChildPos(node1, p1);
        int idx2 = findChildPos(node2, p2);

        p1.setChild(idx1, node2);
        p2.setChild(idx2, node1);
    }

    private Node findNodeInTree(Tree tree, Node originalNode) {
        int num = originalNode.getNumber();
        if (originalNode.isLeaf()) {
            return tree.getExternalNode(num);
        } else {
            return tree.getInternalNode(num);
        }
    }

    @Override
    public Tree[] generateNeighbours(Tree tree) {
        NniMove[] moves = generateNniMoves(tree);
        Tree[] neighbours = new Tree[moves.length];
        for (int i = 0; i < moves.length; i++) {
            neighbours[i] = createNniTree(tree, moves[i]);
        }
        return neighbours;
    }
}