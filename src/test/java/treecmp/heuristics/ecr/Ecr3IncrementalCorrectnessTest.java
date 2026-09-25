package treecmp.heuristics.ecr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pal.tree.Node;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristic;
import treecmp.metrics.topological.RFMetric;
import treecmp.metrics.topological.acc.RFIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Weryfikacja poprawności inkrementalnego ECR3 vs klasyczny ECR3 dla metryki RF")
public class Ecr3IncrementalCorrectnessTest {

    private static final double EPSILON = 1e-9;
    private final RFMetric classicRf = new RFMetric();

    private void prepareTree(Tree tree) {
        if (tree instanceof SimpleTree) {
            ((SimpleTree) tree).createNodeList();
        }
        TreeUtils.computeParentPointers(tree.getRoot());
    }

    /**
     * TEST 1: Porównanie działania heurystyki ECR3 w zejściu lokalnym (VND stage).
     * Jeśli klasyk wykonuje kroki poprawiające (>0), a inkrementalny ma 0 kroków,
     * ten test natychmiast zgłosi asercję i pokaże różnicę dystansów.
     */
    @ParameterizedTest(name = "Test lokalnego zejścia ECR3 dla N={0}")
    @ValueSource(ints = {10, 20, 30})
    void testEcr3DescentStepParity(int n) {
        Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(n, 12345L);
        Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(n, 67890L);
        prepareTree(t1);
        prepareTree(t2);

        // 1. Klasyczna heurystyka ECR3
        Ecr3ClassicHeuristic classicEcr3 = new Ecr3ClassicHeuristic(classicRf, false, "RF");
        double classicDistAfter = classicEcr3.performLocalDescent(t1, t2);
        int classicSteps = classicEcr3.getAccumulatedSteps();

        // 2. Inkrementalna heurystyka ECR3
        RFIncrementalMetric incRf = new RFIncrementalMetric();
        Ecr3IncrementalHeuristic incEcr3 = new Ecr3IncrementalHeuristic(incRf, "RF");
        double incDistAfter = incEcr3.performLocalDescent(t1, t2);
        int incSteps = incEcr3.getAccumulatedSteps();

        System.out.printf("N=%d | Classic steps: %d (D: %.1f) | Incr steps: %d (D: %.1f)%n",
                n, classicSteps, classicDistAfter, incSteps, incDistAfter);

        if (classicSteps > 0) {
            assertTrue(incSteps > 0, String.format(
                    "Regresja ECR3 w RF dla N=%d! Klasyk znalazł %d kroków poprawy (D=%.1f), a inkrementalny znalazł 0 kroków!",
                    n, classicSteps, classicDistAfter
            ));
        }

        assertEquals(classicDistAfter, incDistAfter, EPSILON,
                String.format("Niezgodność dystansu końcowego po ECR3 dla N=%d!", n));
    }

    /**
     * TEST 2: Precyzyjna weryfikacja każdego pojedynczego ruchu evaluate3sEcrMove.
     * Wywołuje evaluate3sEcrMove i porównuje wynik z klasyczną ewaluacją fizycznego drzewa (createEcr3Tree).
     * Wskaże DOKŁADNIE, na którym węźle i szablonie rozjeżdża się matematyka.
     */
    @Test
    @DisplayName("Pojedyncza ewaluacja każdego ruchu 3s-ECR: evaluate3sEcrMove vs RFMetric(createEcr3Tree)")
    void testSingleEcr3MovesEvaluationParity() throws TreeCmpException {
        int n = 10;
        Tree t1 = TestTreeFactory.randomUnrootedBinaryTree(n, 12345L);
        Tree t2 = TestTreeFactory.randomUnrootedBinaryTree(n, 67890L);
        prepareTree(t1);
        prepareTree(t2);

        RFIncrementalMetric incRf = new RFIncrementalMetric();
        incRf.initCalculationState(t1, t2);

        SubtreeEcr3Utils ecr3Utils = new SubtreeEcr3Utils(true);
        int intNum = t1.getInternalNodeCount();
        int testedMoves = 0;

        for (int i = 0; i < intNum; i++) {
            Node rootOfCluster = t1.getInternalNode(i);
            List<List<Node>> clusters = ecr3Utils.getClusters(rootOfCluster, 4);

            for (List<Node> cluster : clusters) {
                List<Node> subtreesList = ecr3Utils.getBoundarySubtrees(cluster);
                if (subtreesList.size() != 5) continue;

                Node[] s = subtreesList.toArray(new Node[0]);
                TopologyTemplate3sECR originalSignature = ecr3Utils.extractSignature(rootOfCluster, cluster, subtreesList);

                for (TopologyTemplate3sECR template : SubtreeEcr3Utils.getTemplates()) {
                    if (template.isIsomorphic(originalSignature)) continue;

                    // A. Ewaluacja inkrementalna
                    double incDist = incRf.evaluate3sEcrMove(cluster, s, template);

                    // B. Fizyczna budowa drzewa i pomiar klasyczny
                    Tree physicalTree = ecr3Utils.createEcr3Tree(t1, cluster, s, template);
                    if (physicalTree != null) {
                        prepareTree(physicalTree);
                        double classicDist = classicRf.getDistance(physicalTree, t2);
                        testedMoves++;

                        assertEquals(classicDist, incDist, EPSILON, String.format(
                                "Rozbieżność w evaluate3sEcrMove! RootOfCluster=%d, Classic=%.1f, Incr=%.1f",
                                rootOfCluster.getNumber(), classicDist, incDist
                        ));
                    }
                }
            }
        }

        assertTrue(testedMoves > 0, "Powinno zostać przetestowanych co najmniej kilkadziesiąt ruchów 3s-ECR");
        System.out.printf("Zbadano bezbłędnie %d ruchów 3s-ECR!%n", testedMoves);
    }
}