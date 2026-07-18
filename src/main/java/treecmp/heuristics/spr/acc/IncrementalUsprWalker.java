package treecmp.heuristics.spr.acc;

import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.spr.UsprUtils;
import treecmp.heuristics.spr.SprVisitor;
import treecmp.metrics.IncrementalMetric;
import treecmp.metrics.topological.acc.MSIncrementalMetric;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class IncrementalUsprWalker {

    private final UsprUtils usprUtils = new UsprUtils();

    public void walk(Tree baseTree, IncrementalMetric metric, SprVisitor visitor) {
        if (!(metric instanceof MSIncrementalMetric)) {
            throw new IllegalArgumentException("IncrementalUsprWalker obsługuje tylko MSIncrementalMetric.");
        }
        MSIncrementalMetric msMetric = (MSIncrementalMetric) metric;

        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
        int numLeaves = baseTree.getExternalNodeCount();
        List<Node> allNodes = getAllNodes(baseTree);

        for (Node pruneNode : allNodes) {
            if (pruneNode.isRoot() || pruneNode.getParent() == null) continue;

            BitSet pruneMask = getLeafMask(pruneNode, idGroup, numLeaves);
            Node startNode = pruneNode.getParent();

            if (startNode.getParent() != null) {
                exploreRadially(pruneNode, startNode.getParent(), startNode, msMetric, visitor, pruneMask);
            }
            for (int i = 0; i < startNode.getChildCount(); i++) {
                Node child = startNode.getChild(i);
                if (child != pruneNode) {
                    exploreRadially(pruneNode, child, startNode, msMetric, visitor, pruneMask);
                }
            }
        }
    }

    private void exploreRadially(Node pruneNode, Node currentNode, Node previousNode,
                                 MSIncrementalMetric metric, SprVisitor visitor, BitSet pruneMask) {
        boolean movingUp = (currentNode == previousNode.getParent());
        Node nodeToUpdate = movingUp ? previousNode : currentNode;
        BitSet bitsOut = movingUp ? pruneMask : null;
        BitSet bitsIn = movingUp ? null : pruneMask;

        // Odbieramy sygnał: czy stan trafił na stos?
        boolean statePushed = metric.applyNniStep(nodeToUpdate, bitsOut, bitsIn);

        if (usprUtils.isValidUsprMove(pruneNode, currentNode)) {
            // DODANO pruneNode jako 4 argument
            double dist = metric.getFixedDistanceForRegraft(currentNode, pruneNode.getParent(), pruneMask, pruneNode);
            visitor.visit(dist, pruneNode, currentNode);
        }

        if (currentNode.getParent() != null && currentNode.getParent() != previousNode) {
            exploreRadially(pruneNode, currentNode.getParent(), currentNode, metric, visitor, pruneMask);
        }
        for (int i = 0; i < currentNode.getChildCount(); i++) {
            Node child = currentNode.getChild(i);
            if (child != previousNode && child != pruneNode) {
                exploreRadially(pruneNode, child, currentNode, metric, visitor, pruneMask);
            }
        }

        // ZABEZPIECZENIE: Cofamy w macierzy tylko wtedy, gdy dodaliśmy tam zmianę
        if (statePushed) {
            metric.undoNniStep();
        }
    }

    private BitSet getLeafMask(Node node, IdGroup idGroup, int numLeaves) {
        BitSet bs = new BitSet(numLeaves);
        populateLeafMask(node, idGroup, bs);
        return bs;
    }

    private void populateLeafMask(Node node, IdGroup idGroup, BitSet bs) {
        if (node.isLeaf()) {
            bs.set(idGroup.whichIdNumber(node.getIdentifier().getName()));
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                populateLeafMask(node.getChild(i), idGroup, bs);
            }
        }
    }

    private List<Node> getAllNodes(Tree tree) {
        List<Node> list = new ArrayList<>();
        collectNodes(tree.getRoot(), list);
        return list;
    }

    private void collectNodes(Node node, List<Node> list) {
        if (node != null) {
            list.add(node);
            for (int i = 0; i < node.getChildCount(); i++) {
                collectNodes(node.getChild(i), list);
            }
        }
    }
}