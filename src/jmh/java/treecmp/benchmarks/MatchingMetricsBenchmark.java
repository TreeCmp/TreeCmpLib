package treecmp.benchmarks;

import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.moves.NniMove;
import treecmp.heuristics.spr.SprUtils;
import treecmp.util.TestTreeFactory;

// Importy klasycznych metryk
import treecmp.metrics.topological.MatchingClusterMetric;
import treecmp.metrics.topological.MatchingSplitMetric;
import treecmp.metrics.topological.MatchingPairMetric;
import treecmp.metrics.topological.MatchingTripletMetric;

// Importy Twoich nowych, inkrementalnych hybryd
import treecmp.metrics.topological.acc.MCIncrementalMetric;
import treecmp.metrics.topological.acc.MSIncrementalMetric;
import treecmp.metrics.topological.acc.MatchingPairIncrementalMetric;
import treecmp.metrics.topological.acc.MatchingTripletIncrementalMetric;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 1)
@Fork(1)
public class MatchingMetricsBenchmark {

    @Param({"10", "20", "30", "40", "50", "60", "70", "80", "90", "100"})
    public int treeSize;

    private Tree baseTree;
    private Tree targetTree;
    private Tree neighborTree;
    private NniMove testMove;

    private SprUtils sprUtils = new SprUtils();

    // Metryki MC (Matching Cluster)
    private MatchingClusterMetric mcClassic;
    private MCIncrementalMetric mcInc;

    // Metryki MS (Matching Split)
    private MatchingSplitMetric msClassic;
    private MSIncrementalMetric msInc;

    // Metryki MP (Matching Pair)
    private MatchingPairMetric mpClassic;
    private MatchingPairIncrementalMetric mpInc;

    // Metryki MT (Matching Triplet)
    private MatchingTripletMetric mtClassic;
    private MatchingTripletIncrementalMetric mtInc;

    @Setup(Level.Trial)
    public void setup() {
        // 1. Generowanie drzew bazowych
        this.baseTree = TestTreeFactory.randomRootedBinaryTree(treeSize, 12345L);
        this.targetTree = TestTreeFactory.randomRootedBinaryTree(treeSize, 67890L);

        // 2. Inicjalizacja metryk
        this.mcClassic = new MatchingClusterMetric();
        this.mcInc = new MCIncrementalMetric();

        this.msClassic = new MatchingSplitMetric();
        this.msInc = new MSIncrementalMetric();

        this.mpClassic = new MatchingPairMetric();
        this.mpInc = new MatchingPairIncrementalMetric();

        this.mtClassic = new MatchingTripletMetric();
        this.mtInc = new MatchingTripletIncrementalMetric();

        // 3. Rozgrzewka stanu inkrementalnego
        this.mcInc.initCalculationState(baseTree, targetTree);
        this.msInc.initCalculationState(baseTree, targetTree);
        this.mpInc.initCalculationState(baseTree, targetTree);
        this.mtInc.initCalculationState(baseTree, targetTree);

        // 4. Generowanie deterministycznego ruchu NNI dla benchmarku
        List<NniMove> validMoves = getDeterministicValidMoves(baseTree);
        if (validMoves.isEmpty()) {
            throw new RuntimeException("Nie można wygenerować ruchu NNI dla drzewa o rozmiarze " + treeSize);
        }
        this.testMove = validMoves.get(0);

        // 5. Budowa fizycznego sąsiada (wymagane dla klasycznych metryk, które przeliczają wszystko od zera)
        Node sibling = getSibling(this.testMove.movingSubtree);
        this.neighborTree = sprUtils.createSprTree(baseTree, sibling, this.testMove.swapPartner);

        // 6. WERYFIKACJA SPÓJNOŚCI PRZED TESTEM (Tylko dla mniejszych drzew, by nie blokować startu)
        if (treeSize <= 100) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("WERYFIKACJA METRYK MATCHINGOWYCH DLA ROZMIARU: " + treeSize);

            verifyMetric("Matching Cluster (MC)",
                    mcClassic.getDistance(neighborTree, targetTree),
                    mcInc.applyNni(testMove));
            mcInc.undoNni(testMove); // Obowiązkowy rollback dla zachowania czystości stanu

            verifyMetric("Matching Split (MS)",
                    msClassic.getDistance(neighborTree, targetTree),
                    msInc.applyNni(testMove));
            msInc.undoNni(testMove);

            verifyMetric("Matching Pair (MP)",
                    mpClassic.getDistance(neighborTree, targetTree),
                    mpInc.applyNni(testMove));
            mpInc.undoNni(testMove);

            verifyMetric("Matching Triplet (MT)",
                    mtClassic.getDistance(neighborTree, targetTree),
                    mtInc.applyNni(testMove));
            mtInc.undoNni(testMove);

            System.out.println("=".repeat(60) + "\n");
        }
    }

    private void verifyMetric(String name, double classicDist, double incDist) {
        if (Math.abs(classicDist - incDist) < 0.0001) {
            System.out.printf("[OK] %-25s : %8.1f == %8.1f\n", name, classicDist, incDist);
        } else {
            System.out.printf("[FAIL] %-23s : %8.1f != %8.1f !!!\n", name, classicDist, incDist);
        }
    }

    // ==========================================================
    // BENCHMARKI MATCHING CLUSTER (MC)
    // ==========================================================
    @Benchmark
    public double benchmarkClassicMC() {
        return mcClassic.getDistance(neighborTree, targetTree);
    }

    @Benchmark
    public double benchmarkIncrementalMC() {
        double dist = mcInc.applyNni(testMove);
        mcInc.undoNni(testMove);
        return dist;
    }

    // ==========================================================
    // BENCHMARKI MATCHING SPLIT (MS)
    // ==========================================================
    @Benchmark
    public double benchmarkClassicMS() {
        return msClassic.getDistance(neighborTree, targetTree);
    }

    @Benchmark
    public double benchmarkIncrementalMS() {
        double dist = msInc.applyNni(testMove);
        msInc.undoNni(testMove);
        return dist;
    }

    // ==========================================================
    // BENCHMARKI MATCHING PAIR (MP)
    // ==========================================================
    @Benchmark
    public double benchmarkClassicMP() {
        return mpClassic.getDistance(neighborTree, targetTree);
    }

    @Benchmark
    public double benchmarkIncrementalMP() {
        double dist = mpInc.applyNni(testMove);
        mpInc.undoNni(testMove);
        return dist;
    }

    // ==========================================================
    // BENCHMARKI MATCHING TRIPLET (MT)
    // ==========================================================
    @Benchmark
    public double benchmarkClassicMT() {
        return mtClassic.getDistance(neighborTree, targetTree);
    }

    @Benchmark
    public double benchmarkIncrementalMT() {
        double dist = mtInc.applyNni(testMove);
        mtInc.undoNni(testMove);
        return dist;
    }

    // ==========================================================
    // HELPERS
    // ==========================================================
    private List<NniMove> getDeterministicValidMoves(Tree tree) {
        List<NniMove> validMoves = new ArrayList<>();
        Node[] allNodes = TreeCmpUtils.getAllNodes(tree);

        for (Node node : allNodes) {
            if (!node.isRoot() && !node.isLeaf()) {
                Node parent = node.getParent();
                if (parent != null) {
                    Node uncle = getSibling(node);
                    if (uncle != null && node.getChildCount() >= 2) {
                        validMoves.add(new NniMove(node.getChild(0), uncle));
                        validMoves.add(new NniMove(node.getChild(1), uncle));
                    }
                }
            }
        }
        return validMoves;
    }

    private Node getSibling(Node node) {
        Node parent = node.getParent();
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) != node) {
                return parent.getChild(i);
            }
        }
        return null;
    }

    public static void main(String[] args) throws org.openjdk.jmh.runner.RunnerException {
        org.openjdk.jmh.runner.options.Options opt = new org.openjdk.jmh.runner.options.OptionsBuilder()
                .include(MatchingMetricsBenchmark.class.getSimpleName())
                .build();

        new org.openjdk.jmh.runner.Runner(opt).run();
    }
}