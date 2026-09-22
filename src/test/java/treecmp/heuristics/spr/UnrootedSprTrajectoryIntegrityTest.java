package treecmp.heuristics.spr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pal.tree.*;
import treecmp.common.TreeCmpUtils;
import treecmp.heuristics.moves.SprMove;
import treecmp.heuristics.vnd.DetailedTrajectoryVndLogger;
import treecmp.metrics.topological.RFMetric;

import java.io.File;
import java.io.PushbackReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UnrootedSprTrajectoryIntegrityTest {

    private static final RFMetric RF = new RFMetric();

    private Tree parseTree(String newick) throws Exception {
        ReadTree rt = new ReadTree(new PushbackReader(new StringReader(newick)));
        SimpleTree st = new SimpleTree(rt);
        st.createNodeList();
        TreeUtils.computeParentPointers(st.getRoot());
        return st;
    }

    private int computeUnrootedRf(Tree t1, Tree t2) {
        Tree u1 = TreeCmpUtils.unrootTreeIfNeeded(t1);
        Tree u2 = TreeCmpUtils.unrootTreeIfNeeded(t2);
        return (int) Math.round(RF.getDistance(u1, u2) * 2.0);
    }

    private void assertPureNniPath(Tree start, Tree target, List<Tree> trajectory) {
        assertNotNull(trajectory, "Trajectory must not be null");
        assertFalse(trajectory.isEmpty(), "Trajectory must contain at least one step");

        Tree prev = start;
        for (int i = 0; i < trajectory.size(); i++) {
            Tree curr = trajectory.get(i);
            int stepRf = computeUnrootedRf(prev, curr);
            assertEquals(2, stepRf, String.format("Step %d must be exactly 1-NNI (DendroPy RF == 2)", i + 1));
            prev = curr;
        }

        assertEquals(0, computeUnrootedRf(prev, target), "Final step in trajectory must reach target topology");
    }

    @Test
    @DisplayName("Pair 69: MS Classic RF=6 jump bridging (Step 8 -> Step 9)")
    void testPair69_MS_Classic_BridgeRF6() throws Exception {
        Tree step8 = parseTree("(10,(7,(((9,1),(5,4)),((6,8),2))),3);");
        Tree step9 = parseTree("(10,3,(1,(9,((5,4),(((6,8),2),7)))));");

        assertEquals(6, computeUnrootedRf(step8, step9));
        SprMove move = new SprMove(step8.getExternalNode(0), step8.getExternalNode(1));
        List<Tree> bridged = move.bridgeGap(step8, step9, true);

        assertNotNull(bridged);
        assertPureNniPath(step8, step9, bridged);
    }

    @Test
    @DisplayName("Pair 81: MS Inc Tie RF=6 jump bridging (Step 3 -> Step 4)")
    void testPair81_MS_IncTie_BridgeRF6() throws Exception {
        Tree step3 = parseTree("(6,((((9,8),(7,2)),(4,(3,10))),5),1);");
        Tree step4 = parseTree("(6,1,(4,((3,10),(((8,(7,2)),9),5))));");

        assertEquals(6, computeUnrootedRf(step3, step4));
        SprMove move = new SprMove(step3.getExternalNode(0), step3.getExternalNode(1));
        List<Tree> bridged = move.bridgeGap(step3, step4, true);

        assertNotNull(bridged);
        assertPureNniPath(step3, step4, bridged);
    }

    @Test
    @DisplayName("Pair 16: RF Inc RF=6 jump bridging (Step 12 -> Step 13)")
    void testPair16_RF_Inc_BridgeRF6() throws Exception {
        Tree step12 = parseTree("(4,1,(10,((7,8),(9,(6,((3,2),5))))));");
        Tree step13 = parseTree("(9,(((6,((3,2),5)),((7,8),10)),4),1);");

        assertEquals(6, computeUnrootedRf(step12, step13));
        SprMove move = new SprMove(step12.getExternalNode(0), step12.getExternalNode(1));
        List<Tree> bridged = move.bridgeGap(step12, step13, true);

        assertNotNull(bridged);
        assertPureNniPath(step12, step13, bridged);
    }

    @Test
    @DisplayName("Logger cleanToken: Prevents duplicated '_Tie_Tie_' and replaces arrows without '-_'")
    void testLogger_TokenSanitization_CleanOutput() {
        String rawVariant1 = "10. VND NNI->SPR->TBR (Classic + Tie)";
        String clean1 = DetailedTrajectoryVndLogger.cleanToken(rawVariant1);
        assertEquals("VND_NNI_SPR_TBR_Classic_Tie", clean1);
        assertFalse(clean1.contains("Tie_Tie"));
        assertFalse(clean1.contains("-_"));

        String rawVariant2 = "12. VND NNI->ECR->SPR->TBR (Inc + Tie)";
        String clean2 = DetailedTrajectoryVndLogger.cleanToken(rawVariant2);
        assertEquals("VND_NNI_ECR_SPR_TBR_Inc_Tie", clean2);
        assertFalse(clean2.contains("Tie_Tie"));

        String rawVariant3 = "3. VND NNI->ECR->SPR->TBR (Classic)";
        String clean3 = DetailedTrajectoryVndLogger.cleanToken(rawVariant3);
        assertEquals("VND_NNI_ECR_SPR_TBR_Classic", clean3);
    }

    @Test
    @DisplayName("PAL Clone Invariant: Path-based node lookup preserves node identity across SimpleTree copy")
    void testPathBasedNodeMapping_InvariantAcrossClones() throws Exception {
        Tree tree = parseTree("(3,(7,(((9,1),(5,4)),((6,8),2))),8);");
        Node originalNode = TreeUtils.getNodeByName(tree, "9").getParent();

        // Klonujemy drzewo przez konstruktor PAL, który reindeksuje wierzchołki
        SimpleTree copy = new SimpleTree(tree);
        copy.createNodeList();
        TreeUtils.computeParentPointers(copy.getRoot());

        // Odczyt przez numery węzłów mógłby trafić w inny wierzchołek
        Node matchedByPath = findNodeByStructure(copy.getRoot(), findStructurePath(tree.getRoot(), originalNode));
        assertNotNull(matchedByPath);
        assertEquals(2, matchedByPath.getChildCount());
        assertTrue(matchedByPath.getChild(0).isLeaf() || matchedByPath.getChild(1).isLeaf());
    }

    @Test
    @DisplayName("Logger auto-bridging: Injects 1-NNI steps when fed with an external RF=6 jump")
    void testDetailedTrajectoryLogger_AutoBridgesExternalJump() throws Exception {
        Tree tStart = parseTree("(10,(7,(((9,1),(5,4)),((6,8),2))),3);");
        Tree tJump = parseTree("(10,3,(1,(9,((5,4),(((6,8),2),7)))));");

        DetailedTrajectoryVndLogger.LogContext.set("MS", "Test_Jump_Resolution", 10, 999);
        File expectedDir = new File("logs/10");
        if (!expectedDir.exists()) expectedDir.mkdirs();

        DetailedTrajectoryVndLogger logger = new DetailedTrajectoryVndLogger(
                "Test_Jump", "MS", (a, b) -> 0.0
        );

        logger.onStart("Test Run", tStart, 6.0);
        // Przekazujemy bezpośredni skok RF=6 do loggera
        logger.onStep("TestOperator", List.of(tJump), 0.0, tJump);
        logger.onFinish(0.0);

        File[] files = expectedDir.listFiles((d, n) -> n.contains("Test_Jump_Resolution") && n.contains("pair999"));
        assertNotNull(files);
        assertTrue(files.length > 0, "Logger must produce the certified proof file");

        File logFile = files[0];
        List<String> lines = Files.readAllLines(logFile.toPath());

        // Sprawdzamy czy logger rozbił przeskok RF=6 na podkroki NNI_Substep
        long substepCount = lines.stream().filter(l -> l.contains("NNI_Substep")).count();
        assertTrue(substepCount >= 3, "Logger must expand RF=6 jump into at least 3 elementary NNI steps");

        logFile.delete();
        DetailedTrajectoryVndLogger.LogContext.clear();
    }

    private List<Integer> findStructurePath(Node root, Node target) {
        java.util.List<Integer> path = new java.util.ArrayList<>();
        Node curr = target;
        while (curr.getParent() != null) {
            Node p = curr.getParent();
            int idx = -1;
            for (int i = 0; i < p.getChildCount(); i++) {
                if (p.getChild(i) == curr) {
                    idx = i;
                    break;
                }
            }
            path.add(idx);
            curr = p;
        }
        java.util.Collections.reverse(path);
        return path;
    }

    private Node findNodeByStructure(Node root, List<Integer> path) {
        Node curr = root;
        for (int idx : path) {
            if (curr == null || idx < 0 || idx >= curr.getChildCount()) return null;
            curr = curr.getChild(idx);
        }
        return curr;
    }
}