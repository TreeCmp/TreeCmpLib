package treecmp.heuristics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.base.HeuristicBaseMetric;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.ecr.Ecr2ClassicHeuristic;
import treecmp.heuristics.ecr.Ecr3ClassicHeuristic;
import treecmp.heuristics.ecr.acc.Ecr2IncrementalHeuristic;
import treecmp.heuristics.ecr.acc.Ecr3IncrementalHeuristic;
import treecmp.heuristics.nni.NniClassicHeuristic;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.heuristics.spr.SprHeuristicMetric;
import treecmp.heuristics.spr.UsprHeuristicMetric;
import treecmp.heuristics.spr.acc.SprIncrementalHeuristicMetric;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.heuristics.tbr.TbrHeuristicMetric;
import treecmp.heuristics.tbr.acc.TbrIncrementalHeuristic;
import treecmp.heuristics.tbr.acc.UtbrIncrementalHeuristic;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.*;
import treecmp.metrics.topological.acc.*;
import treecmp.util.TreeCreator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Native vs NNI-Decomposed Trajectory Contract Tests")
public class NativeTrajectoryContractTest {

    /*
     * Konstrukcja drzew testowych o ściśle malejącym gradiencie (bez plateau) w KAŻDEJ metryce (RF, RFC, MC, MP, MS, M3):
     * - W lewym poddrzewie liść '2' wędruje w górę o 2 poziomy: (((1,2),3),4) -> (((1,3),4),2)  [2 kroki NNI, każdy zmniejsza RF o 1]
     * - W prawym poddrzewie liść '7' wędruje w górę o 2 poziomy: (((6,7),8),9) -> (((6,8),9),7)  [2 kroki NNI, każdy zmniejsza RF o 1]
     * Dzięki temu:
     * - NNI wykonuje 4 kroki (każdy zmniejsza RF dokładnie o 1.0),
     * - 2-sECR, 3-sECR, SPR/uSPR oraz TBR/uTBR wykonują >= 2 natywne kroki (po jednym na każde poddrzewo),
     *   a ich zdekomponowana trajektoria NNI ma zawsze 4 kroki (> 2 natywne kroki!).
     */
    private static final String UNROOTED_T1 = "(5,10,((((1,2),3),4),(((6,7),8),9)));";
    private static final String UNROOTED_T2 = "(5,10,((((1,3),4),2),(((6,8),9),7)));";

    private static final String ROOTED_T1 = "((5,10),((((1,2),3),4),(((6,7),8),9)));";
    private static final String ROOTED_T2 = "((5,10),((((1,3),4),2),(((6,8),9),7)));";

    private Tree parseIndexedTree(String newick) {
        Tree t = TreeCreator.getTreeFromString(newick);
        assertNotNull(t, "Failed to parse Newick string: " + newick);
        SimpleTree st = new SimpleTree(t);
        st.createNodeList();
        return st;
    }

    @ParameterizedTest(name = "Unrooted Heuristic: {0}")
    @ValueSource(strings = {
            "NNI_Classic_RF",
            "NNI_Inc_RF",
            "ECR2_Classic_MS",
            "ECR2_Inc_MS",
            "ECR3_Classic_MS",
            "ECR3_Inc_MS",
            "uSPR_Classic_M3",
            "uSPR_Inc_M3",
            "uTBR_Classic_MS",
            "uTBR_Inc_MS"
    })
    @DisplayName("Should strictly match native step count and trajectory for unrooted heuristics")
    void shouldVerifyUnrootedNativeTrajectoryContract(String variantId) throws TreeCmpException {
        Metric heuristic = createUnrootedHeuristic(variantId);
        Metric rfValidator = new RFMetric();

        verifyHeuristicContract(variantId, heuristic, UNROOTED_T1, UNROOTED_T2, rfValidator);
    }

    @ParameterizedTest(name = "Rooted Heuristic: {0}")
    @ValueSource(strings = {
            "NNI_Classic_RFC",
            "NNI_Inc_RFC",
            "ECR2_Classic_MC",
            "ECR2_Inc_MC",
            "ECR3_Classic_MC",
            "ECR3_Inc_MC",
            "rSPR_Classic_MC",
            "rSPR_Inc_MC",
            "rTBR_Classic_MC",
            "rTBR_Inc_MC"
    })
    @DisplayName("Should strictly match native step count and trajectory for rooted heuristics")
    void shouldVerifyRootedNativeTrajectoryContract(String variantId) throws TreeCmpException {
        Metric heuristic = createRootedHeuristic(variantId);
        Metric rfcValidator = new RFClusterMetric();

        verifyHeuristicContract(variantId, heuristic, ROOTED_T1, ROOTED_T2, rfcValidator);
    }

    @Test
    @DisplayName("Should return 0 steps and empty native trajectory for identical trees")
    void shouldHandleIdenticalTreesCorrectly() {
        SprIncrementalHeuristicMetric spr = new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), "rSPR_Inc_MC");
        Tree t1 = parseIndexedTree(ROOTED_T1);
        Tree t2 = parseIndexedTree(ROOTED_T1);

        double dist = spr.getDistance(t1, t2);
        assertEquals(0.0, dist, 1e-9, "Distance for identical trees must be 0.0");
        assertEquals(0, spr.getAccumulatedSteps(), "Accumulated steps for identical trees must be 0");
    }

    private Metric createUnrootedHeuristic(String variantId) {
        switch (variantId) {
            case "NNI_Classic_RF":  return new NniClassicHeuristic(new RFMetric(), false, variantId);
            case "NNI_Inc_RF":      return new NniIncrementalHeuristic(new RFIncrementalMetric(), variantId);
            case "ECR2_Classic_MS": return new Ecr2ClassicHeuristic(new MatchingSplitMetric(), false, variantId);
            case "ECR2_Inc_MS":     return new Ecr2IncrementalHeuristic(new MSIncrementalMetric(), variantId);
            case "ECR3_Classic_MS": return new Ecr3ClassicHeuristic(new MatchingSplitMetric(), false, variantId);
            case "ECR3_Inc_MS":     return new Ecr3IncrementalHeuristic(new MSIncrementalMetric(), variantId);
            case "uSPR_Classic_M3": return new UsprHeuristicMetric(new MatchingTripletMetric(), variantId);
            case "uSPR_Inc_M3":     return new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), variantId);
            case "uTBR_Classic_MS": return new TbrHeuristicMetric(new MatchingSplitMetric(), false, variantId);
            case "uTBR_Inc_MS":     return new UtbrIncrementalHeuristic(new MSIncrementalMetric(), variantId);
            default: throw new IllegalArgumentException("Unknown unrooted variant: " + variantId);
        }
    }

    private Metric createRootedHeuristic(String variantId) {
        switch (variantId) {
            case "NNI_Classic_RFC": return new NniClassicHeuristic(new RFClusterMetric(), true, variantId);
            case "NNI_Inc_RFC":     return new NniIncrementalHeuristic(new RFClusterIncrementalMetric(), variantId);
            case "ECR2_Classic_MC": return new Ecr2ClassicHeuristic(new MatchingClusterMetric(), true, variantId);
            case "ECR2_Inc_MC":     return new Ecr2IncrementalHeuristic(new MCIncrementalMetric(), variantId);
            case "ECR3_Classic_MC": return new Ecr3ClassicHeuristic(new MatchingClusterMetric(), true, variantId);
            case "ECR3_Inc_MC":     return new Ecr3IncrementalHeuristic(new MCIncrementalMetric(), variantId);
            case "rSPR_Classic_MC": return new SprHeuristicMetric(new MatchingClusterMetric(), true, variantId);
            case "rSPR_Inc_MC":     return new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), variantId);
            case "rTBR_Classic_MC": return new TbrHeuristicMetric(new MatchingClusterMetric(), true, variantId);
            case "rTBR_Inc_MC":     return new TbrIncrementalHeuristic(new MCIncrementalMetric(), variantId);
            default: throw new IllegalArgumentException("Unknown rooted variant: " + variantId);
        }
    }

    private void verifyHeuristicContract(String label, Metric heuristic, String startNewick, String targetNewick, Metric topoValidator)
            throws TreeCmpException {
        Tree t1 = parseIndexedTree(startNewick);
        Tree t2 = parseIndexedTree(targetNewick);
        Tree startCopy = parseIndexedTree(startNewick);

        double reportedDistance = heuristic.getDistance(t1, t2);
        assertFalse(Double.isInfinite(reportedDistance) || Double.isNaN(reportedDistance),
                () -> label + ": Heuristic failed to converge (returned Infinity/NaN)");
        assertTrue(reportedDistance >= 2.0,
                () -> label + ": Expected at least 2 native steps for two independent subtree moves, got: " + reportedDistance);

        int accumulatedSteps;
        List<Tree> nativeTraj;
        List<Tree> nniTraj;

        if (heuristic instanceof HeuristicBaseMetric) {
            HeuristicBaseMetric hbm = (HeuristicBaseMetric) heuristic;
            accumulatedSteps = hbm.getAccumulatedSteps();
            nativeTraj = hbm.getLastNativeOptimumTrajectory();
            nniTraj = hbm.getLastOptimumTrajectory(startCopy);
        } else if (heuristic instanceof IncrementalHeuristicBaseMetric) {
            IncrementalHeuristicBaseMetric ihbm = (IncrementalHeuristicBaseMetric) heuristic;
            accumulatedSteps = ihbm.getAccumulatedSteps();
            nativeTraj = ihbm.getLastNativeOptimumTrajectory();
            nniTraj = ihbm.getLastOptimumTrajectory(startCopy);
        } else {
            fail("Unknown heuristic base class for: " + label);
            return;
        }

        // 1. Kontrakt rozmiaru: getDistance() == getAccumulatedSteps() == nativeTrajectory.size()
        assertEquals((double) accumulatedSteps, reportedDistance, 1e-9,
                () -> label + ": getDistance() must equal getAccumulatedSteps()");
        assertNotNull(nativeTraj, () -> label + ": Native trajectory must not be null");
        assertEquals(accumulatedSteps, nativeTraj.size(),
                () -> label + ": Native trajectory size must strictly match accumulatedSteps (not NNI-decomposed steps!)");

        // 2. Kontrakt relacji z trajektorią NNI (dekompozycja NNI >= liczba kroków natywnych)
        assertNotNull(nniTraj, () -> label + ": NNI trajectory must not be null");
        assertTrue(nniTraj.size() >= nativeTraj.size(),
                () -> label + ": Decomposed NNI trajectory (" + nniTraj.size()
                        + ") must be >= native trajectory (" + nativeTraj.size() + ")");

        // 3. Weryfikacja każdego pojedynczego kroku w nativeOptimumTrajectory
        Tree prev = startCopy;
        for (int i = 0; i < nativeTraj.size(); i++) {
            final int stepIdx = i + 1;
            Tree curr = nativeTraj.get(i);
            assertNotNull(curr, () -> label + ": Null tree at native step " + stepIdx);

            double stepRf = topoValidator.getDistance(prev, curr);
            assertTrue(stepRf > 0.0,
                    () -> label + ": Step " + (stepIdx - 1) + " -> " + stepIdx + " is a no-op (identical topology, RF=0)");

            // RFMetric i RFClusterMetric w TreeCmp dzielą różnicę symetryczną przez 2 (1-NNI => RF = 1.0)
            if (label.startsWith("NNI_")) {
                assertEquals(1.0, stepRf, 1e-9,
                        () -> label + ": 1-NNI step must change RF by 1.0");
            } else if (label.startsWith("ECR2_")) {
                assertTrue(stepRf >= 1.0 && stepRf <= 2.0,
                        () -> label + ": 2-sECR step must change RF by 1.0 or 2.0, got: " + stepRf);
            } else if (label.startsWith("ECR3_")) {
                assertTrue(stepRf >= 1.0 && stepRf <= 3.0,
                        () -> label + ": 3-sECR step must change RF by 1.0..3.0, got: " + stepRf);
            }

            prev = curr;
        }

        // 4. Ostatnie drzewo w obu trajektoriach musi być izomorficzne z drzewem docelowym t2 (RF == 0.0)
        Tree finalNativeTree = nativeTraj.get(nativeTraj.size() - 1);
        double finalNativeDist = topoValidator.getDistance(finalNativeTree, t2);
        assertEquals(0.0, finalNativeDist, 1e-9,
                () -> label + ": Final tree in native trajectory does not match target tree!");

        Tree finalNniTree = nniTraj.get(nniTraj.size() - 1);
        double finalNniDist = topoValidator.getDistance(finalNniTree, t2);
        assertEquals(0.0, finalNniDist, 1e-9,
                () -> label + ": Final tree in NNI trajectory does not match target tree!");
    }
}