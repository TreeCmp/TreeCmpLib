package treecmp.heuristics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
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
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Równoważność Classic vs Incremental (Plateau Tie-Breaking, Zbieżność i Ruchy przy Korzeniu)")
public class HeuristicClassicVsIncrementalEquivalenceTest {

    /*
     * Para drzew nieukorzenionych z klasyczną pułapką Plateau RF:
     * W T1 mamy poddrzewo ((1,2),(3,4)), a w T2 mamy ((1,3),(2,4)).
     * Żaden pojedynczy ruch 1-NNI z T1 nie tworzy od razu bipartycji {1,3} ani {2,4}:
     * pierwszy ruch 1-NNI zmienia ((1,2),(3,4)) -> (1,(2,3),4) [RF pozostaje bez zmian: Delta RF = 0!],
     * ale metryka M3 oraz MS ściśle maleją!
     * Bez poprawnej obsługi plateau na filtrze RF wariant Incremental + RF (Tie) natychmiast zwraca Infinity!
     */
    private static final String UNROOTED_PLATEAU_T1 = "(5,6,(((1,2),(3,4)),(7,8)));";
    private static final String UNROOTED_PLATEAU_T2 = "(5,6,(((1,3),(2,4)),(7,8)));";

    private static final String ROOTED_PLATEAU_T1 = "((5,6),(((1,2),(3,4)),(7,8)));";
    private static final String ROOTED_PLATEAU_T2 = "((5,6),(((1,3),(2,4)),(7,8)));";

    private static Tree prepareTree(Tree raw) {
        SimpleTree st = new SimpleTree(raw);
        st.createNodeList();
        TreeUtils.computeParentPointers(st.getRoot());
        return st;
    }

    private static Tree parseTree(String newick) {
        return prepareTree(TreeCreator.getTreeFromString(newick));
    }

    // =========================================================================
    // 1. TEST PUŁAPKI PLATEAU DLA WSZYSTKICH HEURYSTYK Z FILTREM "+ RF (Tie)"
    // =========================================================================

    static Stream<Arguments> provideTieBreakerHeuristicPairs() {
        return Stream.of(
                // --- UNROOTED (+ RF Tie) ---
                Arguments.of("NNI + RF (M3)", false,
                        new NniClassicHeuristic(new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                        new NniIncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3_RF")),
                Arguments.of("NNI + RF (MS)", false,
                        new NniClassicHeuristic(new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                        new NniIncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MS_RF")),
                Arguments.of("2-sECR + RF (MS)", false,
                        new Ecr2ClassicHeuristic(new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                        new Ecr2IncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MS_RF")),
                Arguments.of("2-sECR + RF (M3)", false,
                        new Ecr2ClassicHeuristic(new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                        new Ecr2IncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3_RF")),
                Arguments.of("3-sECR + RF (MS)", false,
                        new Ecr3ClassicHeuristic(new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                        new Ecr3IncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MS_RF")),
                Arguments.of("3-sECR + RF (M3)", false,
                        new Ecr3ClassicHeuristic(new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                        new Ecr3IncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3_RF")),
                Arguments.of("uSPR + RF (M3)", false,
                        new UsprHeuristicMetric(new MatchingTripletMetric(), new RFMetric(), "M3_RF"),
                        new UsprIncrementalHeuristicMetric(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3_RF")),
                Arguments.of("uSPR + RF (MS)", false,
                        new UsprHeuristicMetric(new MatchingSplitMetric(), new RFMetric(), "MS_RF"),
                        new UsprIncrementalHeuristicMetric(new MSIncrementalMetric(), new RFIncrementalMetric(), "MS_RF")),
                Arguments.of("uTBR + RF (M3)", false,
                        new TbrHeuristicMetric(new MatchingTripletMetric(), new RFMetric(), false, "M3_RF"),
                        new UtbrIncrementalHeuristic(new M3IncrementalMetric(), new RFIncrementalMetric(), "M3_RF")),
                Arguments.of("uTBR + RF (MS)", false,
                        new TbrHeuristicMetric(new MatchingSplitMetric(), new RFMetric(), false, "MS_RF"),
                        new UtbrIncrementalHeuristic(new MSIncrementalMetric(), new RFIncrementalMetric(), "MS_RF")),

                // --- ROOTED (+ RFC Tie) ---
                Arguments.of("NNI + RFC (MC)", true,
                        new NniClassicHeuristic(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                        new NniIncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MC_RF")),
                Arguments.of("NNI + RFC (MP)", true,
                        new NniClassicHeuristic(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                        new NniIncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MP_RF")),
                Arguments.of("2-sECR + RFC (MC)", true,
                        new Ecr2ClassicHeuristic(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                        new Ecr2IncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MC_RF")),
                Arguments.of("2-sECR + RFC (MP)", true,
                        new Ecr2ClassicHeuristic(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                        new Ecr2IncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MP_RF")),
                Arguments.of("3-sECR + RFC (MC)", true,
                        new Ecr3ClassicHeuristic(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                        new Ecr3IncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MC_RF")),
                Arguments.of("3-sECR + RFC (MP)", true,
                        new Ecr3ClassicHeuristic(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                        new Ecr3IncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MP_RF")),
                Arguments.of("rSPR + RFC (MC)", true,
                        new SprHeuristicMetric(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                        new SprIncrementalHeuristicMetric(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MC_RF")),
                Arguments.of("rSPR + RFC (MP)", true,
                        new SprHeuristicMetric(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                        new SprIncrementalHeuristicMetric(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MP_RF")),
                Arguments.of("rTBR + RFC (MC)", true,
                        new TbrHeuristicMetric(new MatchingClusterMetric(), new RFClusterMetric(), true, "MC_RF"),
                        new TbrIncrementalHeuristic(new MCIncrementalMetric(), new RFClusterIncrementalMetric(), "MC_RF")),
                Arguments.of("rTBR + RFC (MP)", true,
                        new TbrHeuristicMetric(new MatchingPairMetric(), new RFClusterMetric(), true, "MP_RF"),
                        new TbrIncrementalHeuristic(new MPIncrementalMetric(), new RFClusterIncrementalMetric(), "MP_RF"))
        );
    }

    @ParameterizedTest(name = "Plateau Escape: {0}")
    @MethodSource("provideTieBreakerHeuristicPairs")
    @DisplayName("Wariant Incremental + RF (Tie) musi pokonywać plateau RF dokładnie tak samo jak Classic + RF (Tie)")
    void testPlateauEscapeWithTieBreaker(String name, boolean isRooted, Metric classicHeuristic, Metric incHeuristic)
            throws TreeCmpException {
        String s1 = isRooted ? ROOTED_PLATEAU_T1 : UNROOTED_PLATEAU_T1;
        String s2 = isRooted ? ROOTED_PLATEAU_T2 : UNROOTED_PLATEAU_T2;

        double distClassic = classicHeuristic.getDistance(parseTree(s1), parseTree(s2));
        double distInc = incHeuristic.getDistance(parseTree(s1), parseTree(s2));

        assertFalse(Double.isInfinite(distClassic),
                () -> name + " [Classic]: powinien pokonać plateau RF, a zwrócił Infinity!");
        assertFalse(Double.isInfinite(distInc),
                () -> name + " [Incremental]: NIE POKONAŁ plateau RF (zwrócił Infinity, podczas gdy Classic zwrócił " + distClassic + ")!");

        assertEquals(distClassic, distInc, 1e-9,
                () -> name + ": Niezgodna liczba kroków po przejściu przez plateau RF (Classic=" + distClassic + " vs Inc=" + distInc + ")");
    }

    // =========================================================================
    // 2. TEST ZBIEŻNOŚCI NA LOSOWYCH DRZEWACH N=12 (CLASSIC VS INCREMENTAL)
    // =========================================================================

    static Stream<Arguments> provideRandomTreeConvergenceCases() {
        long[][] seeds = {{101L, 202L}, {303L, 404L}, {505L, 606L}};
        List<Arguments> list = new ArrayList<>();

        provideTieBreakerHeuristicPairs().forEach(arg -> {
            Object[] p = arg.get();
            for (long[] s : seeds) {
                list.add(Arguments.of(p[0], p[1], p[2], p[3], 12, s[0], s[1]));
            }
        });
        return list.stream();
    }

    @ParameterizedTest(name = "{0} | N={4} | seeds=[{5},{6}]")
    @MethodSource("provideRandomTreeConvergenceCases")
    @DisplayName("Weryfikacja poprawności wielokrokowego zejścia na parach losowych drzew")
    void testMultiStepConvergenceParityOnRandomTrees(String name, boolean isRooted,
                                                     Metric classicHeuristic, Metric incHeuristic,
                                                     int size, long seed1, long seed2) throws TreeCmpException {
        Tree t1Classic = prepareTree(isRooted
                ? TestTreeFactory.randomRootedBinaryTree(size, seed1)
                : TestTreeFactory.randomUnrootedBinaryTree(size, seed1));
        Tree t2Classic = prepareTree(isRooted
                ? TestTreeFactory.randomRootedBinaryTree(size, seed2)
                : TestTreeFactory.randomUnrootedBinaryTree(size, seed2));

        Tree t1Inc = prepareTree(isRooted
                ? TestTreeFactory.randomRootedBinaryTree(size, seed1)
                : TestTreeFactory.randomUnrootedBinaryTree(size, seed1));
        Tree t2Inc = prepareTree(isRooted
                ? TestTreeFactory.randomRootedBinaryTree(size, seed2)
                : TestTreeFactory.randomUnrootedBinaryTree(size, seed2));

        double distClassic = classicHeuristic.getDistance(t1Classic, t2Classic);
        double distInc = incHeuristic.getDistance(t1Inc, t2Inc);

        boolean classicConverged = !Double.isInfinite(distClassic) && !Double.isNaN(distClassic);
        boolean incConverged = !Double.isInfinite(distInc) && !Double.isNaN(distInc);

        // 1. Jeśli oba zbiegły do celu, ich długości ścieżek powinny być zbliżone (różnice wynikają wyłącznie z kolejności remisów)
        if (classicConverged && incConverged) {
            assertTrue(Math.abs(distClassic - distInc) <= 8.0,
                    () -> String.format("%s (N=%d, seeds=%d/%d): Zbyt duża rozbieżność kroków! Classic=%.2f vs Inc=%.2f",
                            name, size, seed1, seed2, distClassic, distInc));
        }

        // 2. Weryfikacja kontraktu Incremental: jeśli zbiegł, getDistance() musi być równe liczbie kroków
        if (incConverged) {
            IncrementalHeuristicBaseMetric ihbm = (IncrementalHeuristicBaseMetric) incHeuristic;
            assertEquals(distInc, (double) ihbm.getAccumulatedSteps(), 1e-9,
                    () -> name + ": getDistance() musi być równe accumulatedSteps w wariancie Incremental");
            assertNotNull(ihbm.getLastNativeOptimumTrajectory(),
                    () -> name + ": Trajektoria natywna nie może być null");
            assertEquals(ihbm.getAccumulatedSteps(), ihbm.getLastNativeOptimumTrajectory().size(),
                    () -> name + ": Rozmiar trajektorii natywnej musi odpowiadać liczbie kroków");
        }

        // 3. Weryfikacja kontraktu Classic: jeśli zbiegł, getDistance() musi być równe liczbie kroków
        if (classicConverged) {
            HeuristicBaseMetric hbm = (HeuristicBaseMetric) classicHeuristic;
            assertEquals(distClassic, (double) hbm.getAccumulatedSteps(), 1e-9,
                    () -> name + ": getDistance() musi być równe accumulatedSteps w wariancie Classic");
        }
    }

    // =========================================================================
    // 3. TEST RUCHÓW NNI PRZY TRÓJSTOPNIOWYM KORZENIU DRZEWA NIEUKORZENIONEGO
    // =========================================================================

    @Test
    @DisplayName("NniIncrementalHeuristic musi uwzględniać wymianę NNI pomiędzy child(0) a child(2) korzenia nieukorzenionego")
    void testUnrootedRootEdgeMovesIncludedInNniIncremental() throws TreeCmpException {
        /*
         * Drzewo nieukorzenione z korzeniem o 3 synach:
         * child(0) = (1,2)
         * child(1) = (3,4)
         * child(2) = ((5,6),(7,8))
         * Jedyny ruch 1-NNI prowadzący z T1 do T2 wymienia poddrzewo (1,2) [czyli child(0) korzenia]
         * z poddrzewem (7,8) [wewnątrz child(2) korzenia]!
         * Stare findSibling() zwracało tylko child(0) dla child(1) i child(0) dla child(2),
         * ale z odwrotną kolejnością węzłów w NniMove.
         */
        String t1Str = "((1,2),(3,4),((5,6),(7,8)));";
        String t2Str = "((7,8),(3,4),((5,6),(1,2)));";

        NniClassicHeuristic classicRf = new NniClassicHeuristic(new RFMetric(), false, "RF");
        NniIncrementalHeuristic incRf = new NniIncrementalHeuristic(new RFIncrementalMetric(), "RF");

        double dClassic = classicRf.getDistance(parseTree(t1Str), parseTree(t2Str));
        double dInc = incRf.getDistance(parseTree(t1Str), parseTree(t2Str));

        assertEquals(1.0, dClassic, 1e-9, "Classic NNI musi wykonać ten ruch przy korzeniu w 1 kroku");
        assertEquals(1.0, dInc, 1e-9, "Incremental NNI musi wykonać ten ruch przy korzeniu w 1 kroku");
    }
}