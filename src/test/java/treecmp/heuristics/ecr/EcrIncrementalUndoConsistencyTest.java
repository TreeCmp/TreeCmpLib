package treecmp.heuristics.ecr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.io.InputSource;
import pal.tree.Node;
import pal.tree.ReadTree;
import pal.tree.Tree;
import treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR;
import treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR;
import treecmp.heuristics.moves.NniMove;
import treecmp.metrics.topological.acc.RFClusterIncrementalMetric;
import treecmp.metrics.topological.acc.RFIncrementalMetric;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testy integralności i atomowości stosów Undo dla 2-sECR i 3-sECR")
public class EcrIncrementalUndoConsistencyTest {

    private static final double EPSILON = 1e-9;

    private Tree parseNewick(String newick) {
        try (InputSource is = InputSource.openString(newick)) {
            return new ReadTree(is);
        } catch (IOException | pal.tree.TreeParseException e) {
            throw new RuntimeException("Błąd parsowania drzewa Newick: " + newick, e);
        }
    }

    private Map<Node, BitSet> snapshotClusters(Tree tree, RFClusterIncrementalMetric metric) {
        Map<Node, BitSet> map = new HashMap<>();
        for (int i = 0; i < tree.getInternalNodeCount(); i++) {
            Node n = tree.getInternalNode(i);
            map.put(n, (BitSet) metric.getCluster(n).clone());
        }
        return map;
    }

    @Test
    @DisplayName("2-sECR: commit + undo musi przywrócić identyczny dystans i klastry (Rooted & Unrooted)")
    void test2sEcrCommitAndUndo_RestoresExactState() {
        Tree t1 = parseNewick("((((A:1,B:1):1,C:1):1,D:1):1,E:1);");
        Tree t2 = parseNewick("((((B:1,C:1):1,A:1):1,E:1):1,D:1);");

        RFClusterIncrementalMetric metric = new RFClusterIncrementalMetric();
        metric.initCalculationState(t1, t2);

        double initialDist = metric.getCurrentDistance();
        Map<Node, BitSet> initialClusters = snapshotClusters(t1, metric);

        // Znajdź łańcuch top -> m1 -> m2
        Node top = t1.getRoot();
        Node m1 = top.getChild(0);
        Node m2 = m1.getChild(0);
        Node[] s = new Node[]{ top.getChild(1), m1.getChild(1), m2.getChild(0), m2.getChild(1) };

        List<TopologyTemplate2sECR> templates = SubtreeEcr2Utils.getTemplates();
        for (TopologyTemplate2sECR template : templates) {
            // Act: Commit
            double committedDist = metric.commit2sEcrMove(top, m1, m2, s, template);
            double currentDistAfterCommit = metric.getCurrentDistance();
            assertEquals(committedDist, currentDistAfterCommit, EPSILON);

            // Act: Undo
            metric.undoNniStep();

            // Assert: Pełne przywrócenie stanu początkowego
            assertEquals(initialDist, metric.getCurrentDistance(), EPSILON,
                    "Dystans po commit + undo w 2-sECR musi być identyczny ze stanem początkowym!");

            for (Map.Entry<Node, BitSet> entry : initialClusters.entrySet()) {
                assertEquals(entry.getValue(), metric.getCluster(entry.getKey()),
                        "Klaster węzła " + entry.getKey() + " nie został prawidłowo przywrócony po undo 2-sECR!");
            }
        }
    }

    @Test
    @DisplayName("3-sECR: commit + undo musi w pełni przywrócić wszystkie klastry dla 105 szablonów")
    void test3sEcrCommitAndUndo_RestoresExactState_All105Templates() {
        Tree t1 = parseNewick("(((((L1:1,L2:1):1,L3:1):1,L4:1):1,L5:1):1,L6:1);");
        Tree t2 = parseNewick("(((((L2:1,L3:1):1,L1:1):1,L5:1):1,L4:1):1,L6:1);");

        RFClusterIncrementalMetric metric = new RFClusterIncrementalMetric();
        metric.initCalculationState(t1, t2);

        SubtreeEcr3Utils utils = new SubtreeEcr3Utils(false);
        List<Node> cluster = utils.getClusters(t1.getRoot(), 4).get(0);
        Node[] s = utils.getBoundarySubtrees(cluster).toArray(new Node[0]);

        double initialDist = metric.getCurrentDistance();
        Map<Node, BitSet> initialClusters = snapshotClusters(t1, metric);

        for (TopologyTemplate3sECR template : SubtreeEcr3Utils.getTemplates()) {
            // Act: Commit
            metric.commit3sEcrMove(cluster, s, template);

            // Act: Undo
            metric.undoNniStep();

            // Assert
            assertEquals(initialDist, metric.getCurrentDistance(), EPSILON,
                    "Dystans po undo 3-sECR nie zgadza się ze stanem bazowym!");

            for (Map.Entry<Node, BitSet> entry : initialClusters.entrySet()) {
                assertEquals(entry.getValue(), metric.getCluster(entry.getKey()),
                        "Klaster węzła po undo 3-sECR nie został poprawnie odtworzony!");
            }
        }
    }

    @Test
    @DisplayName("evaluate2sEcrMove oraz evaluate3sEcrMove muszą zwracać identyczny wynik jak commit i nie modyfikować stanu")
    void testEvaluateMatchesCommitAndLeavesNoSideEffects() {
        Tree t1 = parseNewick("(((((A:1,B:1):1,C:1):1,D:1):1,E:1):1,F:1);");
        Tree t2 = parseNewick("(((((B:1,D:1):1,A:1):1,C:1):1,F:1):1,E:1);");

        RFClusterIncrementalMetric metric = new RFClusterIncrementalMetric();
        metric.initCalculationState(t1, t2);

        double baseDist = metric.getCurrentDistance();

        Node top = t1.getRoot();
        Node m1 = top.getChild(0);
        Node m2 = m1.getChild(0);
        Node[] s = new Node[]{ top.getChild(1), m1.getChild(1), m2.getChild(0), m2.getChild(1) };

        TopologyTemplate2sECR template2s = SubtreeEcr2Utils.getTemplates().get(3);

        // 1. Sprawdzenie evaluate vs commit dla 2-sECR
        double evalDist2s = metric.evaluate2sEcrMove(top, m1, m2, s, template2s);
        assertEquals(baseDist, metric.getCurrentDistance(), EPSILON,
                "evaluate2sEcrMove nie może pozostawiać skutków ubocznych w bieżącym dystansie!");

        double commitDist2s = metric.commit2sEcrMove(top, m1, m2, s, template2s);
        assertEquals(evalDist2s, commitDist2s, EPSILON,
                "Wynik evaluate2sEcrMove musi być w 100% zgodny z commit2sEcrMove!");
        metric.undoNniStep();

        // 2. Sprawdzenie evaluate vs commit dla 3-sECR
        SubtreeEcr3Utils utils = new SubtreeEcr3Utils(false);
        List<Node> cluster = utils.getClusters(t1.getRoot(), 4).get(0);
        Node[] s3 = utils.getBoundarySubtrees(cluster).toArray(new Node[0]);
        TopologyTemplate3sECR template3s = SubtreeEcr3Utils.getTemplates().get(7);

        double evalDist3s = metric.evaluate3sEcrMove(cluster, s3, template3s);
        assertEquals(baseDist, metric.getCurrentDistance(), EPSILON,
                "evaluate3sEcrMove nie może pozostawiać skutków ubocznych w bieżącym dystansie!");

        double commitDist3s = metric.commit3sEcrMove(cluster, s3, template3s);
        assertEquals(evalDist3s, commitDist3s, EPSILON,
                "Wynik evaluate3sEcrMove musi być w 100% zgodny z commit3sEcrMove!");
        metric.undoNniStep();

        assertEquals(baseDist, metric.getCurrentDistance(), EPSILON);
    }

    @Test
    @DisplayName("Sekwencja wielopoziomowa: LIFO poprawnie cofa naprzemienne operacje NNI -> 2sECR -> NNI -> 3sECR")
    void testInterleavedNniAndEcrSequence_ExactFifoRestoration() {
        Tree t1 = parseNewick("((((((A:1,B:1):1,C:1):1,D:1):1,E:1):1,F:1):1,G:1);");
        Tree t2 = parseNewick("((((((C:1,D:1):1,A:1):1,B:1):1,G:1):1,F:1):1,E:1);");

        RFClusterIncrementalMetric metric = new RFClusterIncrementalMetric();
        metric.initCalculationState(t1, t2);

        double d0 = metric.getCurrentDistance();

        // Krok 1: NNI
        Node nniA = t1.getRoot().getChild(0);
        Node nniB = t1.getRoot().getChild(1);
        NniMove move1 = new NniMove(nniA, nniB);
        double d1 = metric.applyNni(move1);

        // Krok 2: 2-sECR
        Node top = t1.getRoot().getChild(0);
        Node m1 = top.getChild(0);
        Node m2 = m1.getChild(0);
        Node[] s2 = new Node[]{ top.getChild(1), m1.getChild(1), m2.getChild(0), m2.getChild(1) };
        TopologyTemplate2sECR t2s = SubtreeEcr2Utils.getTemplates().get(1);
        double d2 = metric.commit2sEcrMove(top, m1, m2, s2, t2s);

        // Krok 3: NNI
        Node nniC = m2.getChild(0);
        Node nniD = m2.getChild(1);
        NniMove move2 = new NniMove(nniC, nniD);
        double d3 = metric.applyNni(move2);

        // Krok 4: 3-sECR
        SubtreeEcr3Utils utils = new SubtreeEcr3Utils(false);
        List<Node> cluster = utils.getClusters(t1.getRoot(), 4).get(0);
        Node[] s3 = utils.getBoundarySubtrees(cluster).toArray(new Node[0]);
        TopologyTemplate3sECR t3s = SubtreeEcr3Utils.getTemplates().get(5);
        double d4 = metric.commit3sEcrMove(cluster, s3, t3s);

        // Cofanie w porządku LIFO i weryfikacja każdego kroku pośredniego
        metric.undoNniStep(); // Cofnięcie 3-sECR
        assertEquals(d3, metric.getCurrentDistance(), EPSILON, "Błąd cofnięcia 3-sECR w sekwencji!");

        metric.undoNniStep(); // Cofnięcie drugiego NNI
        assertEquals(d2, metric.getCurrentDistance(), EPSILON, "Błąd cofnięcia NNI #2 w sekwencji!");

        metric.undoNniStep(); // Cofnięcie 2-sECR
        assertEquals(d1, metric.getCurrentDistance(), EPSILON, "Błąd cofnięcia 2-sECR w sekwencji!");

        metric.undoNniStep(); // Cofnięcie pierwszego NNI
        assertEquals(d0, metric.getCurrentDistance(), EPSILON, "Błąd powrotu do stanu początkowego!");
    }
}