package treecmp.heuristics.tbr;

import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.acc.IncrementalTbrWalker;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IncrementalTbrWalkerTopologyTest {

    @Test
    void testExactMoveParityWithClassicTbrWalker() {
        Tree tree = TestTreeFactory.sixLeavesRootedBalancedTree();

        // 1. Zbiór kanonicznych ruchów TBR wygenerowany niezależnie
        Set<String> classicMoves = generateCanonicalTbrMoves(tree);

        // 2. Weryfikacja IncrementalTbrWalker z produkcyjną metryką RFClusterIncrementalMetric
        Set<String> incrementalMoves = new HashSet<>();
        IncrementalTbrWalker incWalker = new IncrementalTbrWalker();

        RFClusterIncrementalMetric metric = new RFClusterIncrementalMetric();
        metric.initCalculationState(tree, tree);

        incWalker.walk(tree, metric, (dist, prune, reroot, target) -> {
            incrementalMoves.add(formatMove(prune, reroot, target));
        });

        assertEquals(classicMoves.size(), incrementalMoves.size(),
                "Liczba odwiedzonych ruchów TBR musi być identyczna!");
        assertEquals(classicMoves, incrementalMoves,
                "IncrementalTbrWalker odwiedził inny zbiór ruchów niż kanoniczne otoczenie TBR!");
    }

    /**
     * Kanoniczny generator wyznaczający poprawną przestrzeń ruchów ukorzenionego TBR:
     * - Prune (P): dowolny węzeł poza korzeniem,
     * - Reroot (R): dowolny węzeł w odciętym poddrzewie T1,
     * - Target (T): dowolny węzeł w T2, z wykluczeniem zapadniętego rodzica P
     *               oraz ruchu tożsamościowego na rodzeństwo (gdy R == P).
     */
    private Set<String> generateCanonicalTbrMoves(Tree tree) {
        Set<String> moves = new HashSet<>();
        List<Node> allNodes = new ArrayList<>();
        collectAllNodes(tree.getRoot(), allNodes);

        for (Node prune : allNodes) {
            if (prune.isRoot() || prune.getParent() == null) continue;

            Node parent = prune.getParent();
            Node sibling = getSibling(prune);

            List<Node> t1Nodes = new ArrayList<>();
            collectAllNodes(prune, t1Nodes);
            Set<Node> t1Set = new HashSet<>(t1Nodes);

            for (Node reroot : t1Nodes) {
                for (Node target : allNodes) {
                    if (t1Set.contains(target)) continue;
                    if (target == parent) continue;
                    if (reroot == prune && target == sibling) continue;

                    moves.add(formatMove(prune, reroot, target));
                }
            }
        }
        return moves;
    }

    private Node getSibling(Node node) {
        Node parent = node.getParent();
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            Node child = parent.getChild(i);
            if (child != node) return child;
        }
        return null;
    }

    private void collectAllNodes(Node node, List<Node> list) {
        if (node != null) {
            list.add(node);
            for (int i = 0; i < node.getChildCount(); i++) {
                collectAllNodes(node.getChild(i), list);
            }
        }
    }

    private String formatMove(Node prune, Node reroot, Node target) {
        return String.format("P:%s_R:%s_T:%s",
                prune.isLeaf() ? prune.getIdentifier().getName() : "i" + prune.getNumber(),
                reroot.isLeaf() ? reroot.getIdentifier().getName() : "i" + reroot.getNumber(),
                target.isLeaf() ? target.getIdentifier().getName() : "i" + target.getNumber());
    }
}