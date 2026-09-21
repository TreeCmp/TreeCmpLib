package treecmp.heuristics.moves;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.tree.Node;
import pal.tree.ReadTree;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils;
import treecmp.heuristics.ecr.SubtreeEcr2Utils.TopologyTemplate2sECR;
import treecmp.heuristics.ecr.SubtreeEcr3Utils;
import treecmp.heuristics.ecr.SubtreeEcr3Utils.TopologyTemplate3sECR;
import treecmp.metrics.topological.RFMetric;

import java.io.PushbackReader;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TrajectoryCertificationIntegrityTest {

    private static final RFMetric RF = new RFMetric();

    private Tree parseNewick(String newick) {
        try {
            PushbackReader pbr = new PushbackReader(new StringReader(newick));
            ReadTree readTree = new ReadTree(pbr);
            SimpleTree st = new SimpleTree(readTree);
            st.createNodeList();
            TreeUtils.computeParentPointers(st.getRoot());
            return st;
        } catch (Exception e) {
            throw new RuntimeException("Błąd parsowania Newick w teście: " + newick, e);
        }
    }

    private Node findLeafByName(Tree tree, String name) {
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            Node leaf = tree.getExternalNode(i);
            if (leaf != null && leaf.getIdentifier() != null && name.equals(leaf.getIdentifier().getName())) {
                return leaf;
            }
        }
        throw new IllegalArgumentException("Nie odnaleziono liścia o nazwie: " + name);
    }

    /**
     * Zwraca dystans symetryczny splitów w notacji DendroPy (|S1 \ S2| + |S2 \ S1|).
     * RFMetric w TreeCmp zwraca wartość dzieloną przez 2 (liczbę różnych krawędzi),
     * stąd mnożenie przez 2.0.
     */
    private double getUnrootedDendropyRf(Tree t1, Tree t2) {
        Tree u1 = TreeCmpUtils.unrootTreeIfNeeded(t1);
        Tree u2 = TreeCmpUtils.unrootTreeIfNeeded(t2);
        return RF.getDistance(u1, u2) * 2.0;
    }

    // =========================================================================
    // 1. TESTY DLA Ecr2Move (GRAF 1-NNI I KROKI POŚREDNIE)
    // =========================================================================

    @Test
    @DisplayName("Ecr2Move: Ruch o koszcie 2 NNI tworzy ciągłą trajektorię o krokach dokładnie RF=2")
    void testEcr2Move_ContinuousTrajectoryForCost2() {
        Tree startTree = parseNewick("((((1:1,2:1):1,3:1):1,4:1):1,5:1);");

        Node top = startTree.getRoot().getChild(0);
        Node m1 = top.getChild(0);
        Node m2 = m1.getChild(0);

        Node[] s = new Node[4];
        s[0] = top.getChild(1); // 4
        s[1] = m1.getChild(1);  // 3
        s[2] = m2.getChild(0);  // 1
        s[3] = m2.getChild(1);  // 2

        TopologyTemplate2sECR template2Nni = new TopologyTemplate2sECR(false, new int[]{2, 3, 0, 1});
        Ecr2Move move = new Ecr2Move(top, m1, m2, s, template2Nni);

        assertEquals(2, move.getNniEquivalentCost(), "Koszt NNI dla zamiany par klastra powinien wynosić dokładnie 2");

        List<Tree> trajectory = move.getNniTrajectory(startTree);
        assertEquals(2, trajectory.size(), "Trajektoria musi zawierać dokładnie 2 drzewa (krok pośredni + cel)");

        Tree t0 = startTree;
        Tree t1 = trajectory.get(0);
        Tree t2 = trajectory.get(1);

        assertEquals(2.0, getUnrootedDendropyRf(t0, t1), "Krok 0 -> 1 musi być elementarnym ruchem 1-NNI (DendroPy RF=2)");
        assertEquals(2.0, getUnrootedDendropyRf(t1, t2), "Krok 1 -> 2 musi być elementarnym ruchem 1-NNI (DendroPy RF=2)");
    }

    // =========================================================================
    // 2. TESTY DLA Ecr3Move (GRAF 105 SZABLONÓW I MASKI KLASTRÓW)
    // =========================================================================

    @Test
    @DisplayName("Ecr3Move: Weryfikacja ciągłości NNI przy automatycznym doborze sygnatury wyjściowej")
    void testEcr3Move_StepContinuityFromExtractedSignature() {
        Tree startTree = parseNewick("(((((1:1,2:1):1,3:1):1,4:1):1,5:1):1,6:1);");

        SubtreeEcr3Utils utils = new SubtreeEcr3Utils(false);
        List<List<Node>> clusters = utils.getClusters(startTree.getRoot(), 4);
        assertFalse(clusters.isEmpty(), "Powinien zostać znaleziony przynajmniej jeden klaster 4-węzłowy");

        List<Node> cluster = clusters.get(0);
        List<Node> boundaryList = utils.getBoundarySubtrees(cluster);
        Node[] s = boundaryList.toArray(new Node[0]);

        TopologyTemplate3sECR originalSig = utils.extractSignature(cluster.get(0), cluster, boundaryList);

        TopologyTemplate3sECR targetTemplate = SubtreeEcr3Utils.getTemplates().stream()
                .filter(t -> t.nniCost == 2 && !t.isIsomorphic(originalSig))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Brak odpowiedniego szablonu docelowego"));

        Ecr3Move move = new Ecr3Move(cluster, s, originalSig, targetTemplate);
        List<Tree> trajectory = move.getNniTrajectory(startTree);

        assertFalse(trajectory.isEmpty(), "Trajektoria nie może być pusta");

        Tree prev = startTree;
        for (int i = 0; i < trajectory.size(); i++) {
            Tree step = trajectory.get(i);
            assertNotNull(step, "Drzewo pośrednie nie może być null");
            double rf = getUnrootedDendropyRf(prev, step);
            assertEquals(2.0, rf, String.format("Podkrok %d -> %d w 3s-ECR musi różnić się dokładnie o DendroPy RF=2 (1-NNI)", i, i + 1));
            prev = step;
        }
    }

    // =========================================================================
    // 3. TESTY DLA SprMove (ELIMINACJA KROKÓW NO-OP)
    // =========================================================================

    @Test
    @DisplayName("SprMove: Pomijanie pozycji wpięcia w rodzeństwo (brak duplikatów RF=0)")
    void testSprMove_EliminatesSiblingNoOp() {
        Tree startTree = parseNewick("(((A:1,B:1):1,C:1):1,(D:1,E:1):1);");

        Node nodeA = findLeafByName(startTree, "A");
        Node nodeD = findLeafByName(startTree, "D");

        SprMove move = new SprMove(nodeA, nodeD);
        List<Tree> trajectory = move.getNniTrajectory(startTree);

        assertFalse(trajectory.isEmpty(), "Trajektoria SPR powinna zawierać kroki NNI");

        Tree prev = startTree;
        for (int i = 0; i < trajectory.size(); i++) {
            Tree current = trajectory.get(i);
            double rf = getUnrootedDendropyRf(prev, current);
            assertNotEquals(0.0, rf, String.format("Krok %d w SprMove nie może być pustym ruchem (RF == 0)", i + 1));
            assertEquals(2.0, rf, String.format("Krok %d w SprMove musi być czystym ruchem 1-NNI (DendroPy RF == 2)", i + 1));
            prev = current;
        }
    }

    // =========================================================================
    // 4. TESTY DLA TbrMove (SEPARACJA REROOTINGU I ATTACHMENTU)
    // =========================================================================

    @Test
    @DisplayName("TbrMove: Rerooting poddrzewa nie powoduje przedwczesnego skoku do węzła docelowego")
    void testTbrMove_PhaseSeparationIntegrity() {
        Tree startTree = parseNewick("(((A:1,(B:1,C:1):1):1,D:1):1,E:1);");

        Node pruneNode = findLeafByName(startTree, "A").getParent();
        Node rerootNode = findLeafByName(startTree, "C");
        Node targetNode = findLeafByName(startTree, "D");

        TbrMove move = new TbrMove(pruneNode, rerootNode, targetNode);
        List<Tree> trajectory = move.getNniTrajectory(startTree);

        assertFalse(trajectory.isEmpty(), "Trajektoria TBR musi zawierać stany pośrednie");

        Tree prev = startTree;
        for (int i = 0; i < trajectory.size(); i++) {
            Tree current = trajectory.get(i);
            double rf = getUnrootedDendropyRf(prev, current);
            assertEquals(2.0, rf, String.format("Krok TBR #%d -> #%d musi wynosić DendroPy RF=2 (wykryto skok RF=%.0f)", i, i + 1, rf));
            prev = current;
        }
    }

    // =========================================================================
    // 5. TESTY ZGODNOŚCI NORMALIZACJI BEZKORZENNEJ (UNROOT CHECK)
    // =========================================================================

    @Test
    @DisplayName("TreeCmpUtils: Wykrywanie pozornych zmian w korzeniu jako tożsamych bezkorzennie")
    void testUnrootTree_PreventsFalseZeroRf() {
        Tree tRooted1 = parseNewick("((A:1,B:1):1,(C:1,D:1):1);");
        Tree tRooted2 = parseNewick("(A:1,(B:1,(C:1,D:1):1):1);");

        double rfRooted = RF.getDistance(tRooted1, tRooted2);
        assertTrue(rfRooted > 0, "W przestrzeni ukorzenionej drzewa różnią się układem klastrów");

        double rfUnrooted = getUnrootedDendropyRf(tRooted1, tRooted2);
        assertEquals(0.0, rfUnrooted, "Po odkorzenieniu topologie bezkorzenne muszą być identyczne (RF=0)");
    }
}