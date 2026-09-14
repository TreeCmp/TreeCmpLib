package treecmp.heuristics.tbr;

import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.Tree;
import treecmp.heuristics.tbr.acc.IncrementalTbrWalker;
import treecmp.heuristics.tbr.acc.TbrNeighborhoodWalker;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IncrementalTbrWalkerTopologyTest {

    @Test
    void testExactMoveParityWithClassicTbrWalker() {
        Tree tree = TestTreeFactory.sixLeavesRootedBalancedTree();

        Set<String> classicMoves = new HashSet<>();
        TbrNeighborhoodWalker classicWalker = new TbrNeighborhoodWalker();

        RFClusterIncrementalMetric metric = new RFClusterIncrementalMetric();
        metric.initCalculationState(tree, tree);

        classicWalker.walk(tree, metric, (dist, prune, reroot, target) -> {
            classicMoves.add(formatMove(prune, reroot, target));
        });

        Set<String> incrementalMoves = new HashSet<>();
        IncrementalTbrWalker incWalker = new IncrementalTbrWalker();

        IncrementalTbrWalker.RootedTbrMetric stubMetric = new IncrementalTbrWalker.RootedTbrMetric() {
            @Override public void setPrunedState(Node pruneNode, Node wanderingSource) {}
            @Override public void revertPrunedState(Node pruneNode, Node wanderingSource) {}
            @Override public void setTargetRoot(Node pruneNode, Node rerootNode, Node wanderingSource) {}
            @Override public void moveTargetDown(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {}
            @Override public void moveTargetUp(Node parentTarget, Node childTarget, Node pruneNode, Node rerootNode, Node wanderingSource) {}
            @Override public void moveRerootDown(Node parentReroot, Node childReroot, Node pruneNode) {}
            @Override public void moveRerootUp(Node parentReroot, Node childReroot, Node pruneNode) {}
            @Override public double getCurrentDistance() { return 1.0; }
        };

        incWalker.walk(tree, stubMetric, (dist, prune, reroot, target) -> {
            incrementalMoves.add(formatMove(prune, reroot, target));
        });

        assertEquals(classicMoves.size(), incrementalMoves.size(),
                "Liczba odwiedzonych ruchów TBR musi być identyczna!");
        assertEquals(classicMoves, incrementalMoves,
                "IncrementalTbrWalker odwiedził inny zbiór ruchów niż TbrNeighborhoodWalker!");
    }

    private String formatMove(Node prune, Node reroot, Node target) {
        return String.format("P:%s_R:%s_T:%s",
                prune.isLeaf() ? prune.getIdentifier().getName() : "i" + prune.getNumber(),
                reroot.isLeaf() ? reroot.getIdentifier().getName() : "i" + reroot.getNumber(),
                target.isLeaf() ? target.getIdentifier().getName() : "i" + target.getNumber());
    }
}