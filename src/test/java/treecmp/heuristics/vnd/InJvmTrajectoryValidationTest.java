package treecmp.heuristics.vnd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.ecr.Ecr2ClassicHeuristic;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristic;
import treecmp.heuristics.nni.NniClassicHeuristic;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.heuristics.spr.SprHeuristicMetric;
import treecmp.heuristics.spr.UsprHeuristicMetric;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.heuristics.tbr.TbrHeuristicMetric;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.MatchingClusterMetric;
import treecmp.metrics.topological.MatchingSplitMetric;
import treecmp.metrics.topological.RFClusterMetric;
import treecmp.metrics.topological.RFMetric;
import treecmp.metrics.topological.acc.MCIncrementalMetric;
import treecmp.metrics.topological.acc.MSIncrementalMetric;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;
import treecmp.metrics.topological.acc.RFIncrementalMetric;
import treecmp.util.TestTreeFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InJvmTrajectoryValidationTest {

    private static final RFMetric RF = new RFMetric();

    /**
     * Wewnętrzny listener zbierający wszystkie kroki trajektorii w pamięci,
     * zachowując identyczną logikę filtrowania kroków tożsamych co DetailedTrajectoryVndLogger.
     */
    private static class CollectingVndStepListener implements VndStepListener {
        final List<Tree> collectedTrees = new ArrayList<>();
        double finalDistance = Double.POSITIVE_INFINITY;

        @Override
        public void onStart(String testName, Tree startTree, double initialDistance) {
            collectedTrees.clear();
            SimpleTree copy = new SimpleTree(startTree);
            copy.createNodeList();
            TreeUtils.computeParentPointers(copy.getRoot());
            collectedTrees.add(copy);
        }

        @Override
        public void onStep(String heuristicName, List<Tree> trajectory, double currentBestValue, Tree targetTree) {
            if (trajectory == null || trajectory.isEmpty()) return;

            Tree lastTree = collectedTrees.get(collectedTrees.size() - 1);
            for (Tree candidate : trajectory) {
                if (candidate == null) continue;

                Tree uLast = TreeCmpUtils.unrootTreeIfNeeded(lastTree);
                Tree uCand = TreeCmpUtils.unrootTreeIfNeeded(candidate);
                double diff = RF.getDistance(uLast, uCand);

                // Dokładnie tak jak w DetailedTrajectoryVndLogger:
                // filtrujemy stany pośrednie tożsame bezkorzennie (RF == 0),
                // dzięki czemu do certyfikatu trafiają wyłącznie rzeczywiste kroki 1-NNI (RF == 2).
                if (diff > 0.0) {
                    SimpleTree copy = new SimpleTree(candidate);
                    copy.createNodeList();
                    TreeUtils.computeParentPointers(copy.getRoot());
                    collectedTrees.add(copy);
                    lastTree = copy;
                }
            }
        }

        @Override
        public void onFinish(double finalDistance) {
            this.finalDistance = finalDistance;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"RF", "MS", "MC", "RFC"})
    @DisplayName("In-JVM Certificate Audit: VND trajectory is strictly continuous 1-NNI and reaches target")
    void testVndTrajectoryStrictContinuousCertification(String metricCode) {
        boolean isRooted = metricCode.equals("MC") || metricCode.equals("RFC");

        // Drzewa testowe N=8 zapewniające szybkie zbieganie w testach jednostkowych
        Tree startTree = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(8, 12345L)
                : TestTreeFactory.randomUnrootedBinaryTree(8, 12345L);
        Tree targetTree = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(8, 67890L)
                : TestTreeFactory.randomUnrootedBinaryTree(8, 67890L);

        Metric evalMetric;
        switch (metricCode) {
            case "RF":  evalMetric = new RFMetric(); break;
            case "MS":  evalMetric = new MatchingSplitMetric(); break;
            case "MC":  evalMetric = new MatchingClusterMetric(); break;
            case "RFC": evalMetric = new RFClusterMetric(); break;
            default: throw new IllegalArgumentException("Nieznana metryka: " + metricCode);
        }

        CollectingVndStepListener inMemoryListener = new CollectingVndStepListener();

        // Budujemy łańcuch operatorów VND
        NniVndHeuristic vnd = new NniVndHeuristic(Arrays.asList(
                new NniClassicHeuristic(evalMetric, isRooted, "NNI"),
                new Ecr2ClassicHeuristic(evalMetric, isRooted, "ECR2"),
                isRooted ? new SprHeuristicMetric(evalMetric, null, true, "SPR")
                        : new UsprHeuristicMetric(evalMetric, null, "SPR"),
                new TbrHeuristicMetric(evalMetric, null, isRooted, "TBR")
        ), "VND_" + metricCode, inMemoryListener);

        // Wykonanie pełnego przeszukiwania VND
        double nniCost = vnd.getDistance(startTree, targetTree);

        // Asercja 1: Heurystyka musi osiągnąć cel
        assertNotEquals(Double.POSITIVE_INFINITY, nniCost, "Heurystyka powinna znaleźć cel (D=0.0)");
        assertEquals(0.0, inMemoryListener.finalDistance, 1e-6, "Końcowy dystans raportowany przez listener musi wynosić 0.0");

        List<Tree> trajectory = inMemoryListener.collectedTrees;
        assertTrue(trajectory.size() >= 2, "Trajektoria musi zawierać co najmniej drzewo startowe i docelowe");

        // Asercja 2: Rygorystyczna weryfikacja każdego podkroku 1-NNI (DendroPy RF == 2.0)
        for (int i = 0; i < trajectory.size() - 1; i++) {
            Tree curr = trajectory.get(i);
            Tree nxt = trajectory.get(i + 1);

            Tree uCurr = TreeCmpUtils.unrootTreeIfNeeded(curr);
            Tree uNxt = TreeCmpUtils.unrootTreeIfNeeded(nxt);

            double dendropyRf = RF.getDistance(uCurr, uNxt) * 2.0;

            assertEquals(2.0, dendropyRf, String.format(
                    "[%s] Naruszenie ciągłości 1-NNI w kroku %d -> %d! Wykryto RF=%.0f zamiast 2.0",
                    metricCode, i, i + 1, dendropyRf
            ));
        }

        // Asercja 3: Drzewo końcowe musi być tożsame z celem w przestrzeni bezkorzennej
        Tree uLast = TreeCmpUtils.unrootTreeIfNeeded(trajectory.get(trajectory.size() - 1));
        Tree uTarget = TreeCmpUtils.unrootTreeIfNeeded(targetTree);
        assertEquals(0.0, RF.getDistance(uLast, uTarget), "Końcowe drzewo w trajektorii musi mieć RF=0 względem celu");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RF", "MS", "MC", "RFC"})
    @DisplayName("In-JVM Certificate Audit: INCREMENTAL VND trajectory is strictly continuous 1-NNI")
    void testIncrementalVndTrajectoryStrictContinuousCertification(String metricCode) {
        boolean isRooted = metricCode.equals("MC") || metricCode.equals("RFC");

        Tree startTree = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(8, 12345L)
                : TestTreeFactory.randomUnrootedBinaryTree(8, 12345L);
        Tree targetTree = isRooted
                ? TestTreeFactory.randomRootedBinaryTree(8, 67890L)
                : TestTreeFactory.randomUnrootedBinaryTree(8, 67890L);

        List<IncrementalHeuristicBaseMetric> incNeighborhoods;
        switch (metricCode) {
            case "RF":
                RFIncrementalMetric rfInc = new RFIncrementalMetric();
                incNeighborhoods = Arrays.asList(
                        new NniIncrementalHeuristic(rfInc, "NNI"),
                        new Ecr2IncrementalHeuristic(rfInc, "ECR2"),
                        new UsprIncrementalHeuristicMetric(rfInc, "SPR"),
                        new UtbrIncrementalHeuristic(rfInc, "TBR")
                );
                break;
            case "RFC":
                RFClusterIncrementalMetric rfcInc = new RFClusterIncrementalMetric();
                incNeighborhoods = Arrays.asList(
                        new NniIncrementalHeuristic(rfcInc, "NNI"),
                        new Ecr2IncrementalHeuristic(rfcInc, "ECR2"),
                        new SprIncrementalHeuristicMetric(rfcInc, "SPR"),
                        new TbrIncrementalHeuristic(rfcInc, "TBR")
                );
                break;
            case "MC":
                MCIncrementalMetric mcInc = new MCIncrementalMetric();
                incNeighborhoods = Arrays.asList(
                        new NniIncrementalHeuristic(mcInc, "NNI"),
                        new Ecr2IncrementalHeuristic(mcInc, "ECR2"),
                        new SprIncrementalHeuristicMetric(mcInc, "SPR"),
                        new TbrIncrementalHeuristic(mcInc, "TBR")
                );
                break;
            case "MS":
                MSIncrementalMetric msInc = new MSIncrementalMetric();
                incNeighborhoods = Arrays.asList(
                        new NniIncrementalHeuristic(msInc, "NNI"),
                        new Ecr2IncrementalHeuristic(msInc, "ECR2"),
                        new UsprIncrementalHeuristicMetric(msInc, "SPR"),
                        new UtbrIncrementalHeuristic(msInc, "TBR")
                );
                break;
            default:
                throw new IllegalArgumentException("Nieznana metryka: " + metricCode);
        }

        CollectingVndStepListener inMemoryListener = new CollectingVndStepListener();

        NniVndIncrementalHeuristic incVnd = new NniVndIncrementalHeuristic(
                incNeighborhoods,
                null,
                "INC_VND_" + metricCode,
                inMemoryListener
        );

        double nniCost = incVnd.getDistance(startTree, targetTree);

        // Asercja 1: Heurystyka musi osiągnąć cel
        assertNotEquals(Double.POSITIVE_INFINITY, nniCost, "Heurystyka inkrementalna powinna znaleźć cel (D=0.0)");
        assertEquals(0.0, inMemoryListener.finalDistance, 1e-6, "Końcowy dystans raportowany przez listener musi wynosić 0.0");

        List<Tree> trajectory = inMemoryListener.collectedTrees;
        assertTrue(trajectory.size() >= 2, "Trajektoria inkrementalna musi zawierać co najmniej drzewo startowe i docelowe");

        // Asercja 2: Każdy krok musi być czystym 1-NNI (DendroPy RF == 2.0)
        for (int i = 0; i < trajectory.size() - 1; i++) {
            Tree curr = trajectory.get(i);
            Tree nxt = trajectory.get(i + 1);

            Tree uCurr = TreeCmpUtils.unrootTreeIfNeeded(curr);
            Tree uNxt = TreeCmpUtils.unrootTreeIfNeeded(nxt);

            double dendropyRf = RF.getDistance(uCurr, uNxt) * 2.0;

            assertEquals(2.0, dendropyRf, String.format(
                    "[INC_%s] Naruszenie ciągłości 1-NNI w kroku %d -> %d! Wykryto RF=%.0f zamiast 2.0",
                    metricCode, i, i + 1, dendropyRf
            ));
        }

        // Asercja 3: Drzewo końcowe musi odpowiadać celowi
        Tree uLast = TreeCmpUtils.unrootTreeIfNeeded(trajectory.get(trajectory.size() - 1));
        Tree uTarget = TreeCmpUtils.unrootTreeIfNeeded(targetTree);
        assertEquals(0.0, RF.getDistance(uLast, uTarget), "Końcowe drzewo w trajektorii inkrementalnej musi mieć RF=0 względem celu");
    }
}