package treecmp.heuristics.tbr;

import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.tbr.acc.UtbrNeighborhoodWalker;
import treecmp.metrics.topological.RFMetric;
import treecmp.metrics.topological.acc.RFIncrementalMetric;
import treecmp.util.TestTreeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

    public class EveryMoveMatchesClassicOracleTest {

        private static String formatNode(Node n) {
            if (n == null) return "null";
            String name = n.getIdentifier() != null ? n.getIdentifier().getName() : "";
            return name.isEmpty() ? "#" + n.getNumber() : name;
        }

        @Test
        public void testEveryMoveMatchesClassicOracle() {
            // Używamy drzew nieukorzenionych
            Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(10, 42L);
            Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(10, 84L);
            ((SimpleTree) t1).createNodeList();
            ((SimpleTree) t2).createNodeList();

            RFIncrementalMetric incMetric = new RFIncrementalMetric();
            RFMetric classicMetric = new RFMetric();
            UTbrUtils utils = new UTbrUtils();

            incMetric.initCalculationState(t1, t2);

            UtbrNeighborhoodWalker walker = new UtbrNeighborhoodWalker();
            walker.walk(t1, incMetric, (dist, prune, reroot, target) -> {

                // Fizyczna mutacja uTBR
                Tree physical = utils.createUtbrTree(t1, prune, reroot, target);

                if (physical != null) {
                    ((SimpleTree) physical).createNodeList();
                    double expected = classicMetric.getDistance(physical, t2);

                    assertEquals(expected, dist, 1e-6,
                            String.format("Mismatch at move: P=%s, R=%s, T=%s",
                                    formatNode(prune),
                                    formatNode(reroot),
                                    formatNode(target)));
                }
            });
        }
    }