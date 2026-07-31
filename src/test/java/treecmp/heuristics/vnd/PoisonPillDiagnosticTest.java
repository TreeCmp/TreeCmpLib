package treecmp.heuristics.vnd;

import org.junit.jupiter.api.Test;
import pal.tree.SimpleTree;
import pal.tree.Tree;
import treecmp.heuristics.base.IncrementalHeuristicBaseMetric;
import treecmp.heuristics.nni.acc.NniIncrementalHeuristic;
import treecmp.heuristics.spr.acc.UsprIncrementalHeuristicMetric;
import treecmp.heuristics.vnd.acc.NniVndIncrementalHeuristic;
import treecmp.metrics.topological.acc.RFIncrementalMetric;
import treecmp.util.TreeCreator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PoisonPillDiagnosticTest {

    @Test
    public void testPair13() throws Exception {
        System.out.println("--- TEST ZATRUTEJ PIGULKI (PARA 13) ---");
        String file = "datasets/n10y200ub.newick";
        List<Tree> trees = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty() && !line.startsWith("#")) {
                    trees.add(TreeCreator.getTreeFromString(line.trim()));
                }
            }
        }

        // Drzewo 13 to index 26 i 27 w pliku wejściowym
        Tree t1 = new SimpleTree(trees.get(26));
        Tree t2 = new SimpleTree(trees.get(27));
        ((SimpleTree) t1).createNodeList();
        ((SimpleTree) t2).createNodeList();

        RFIncrementalMetric rfInc = new RFIncrementalMetric();

        List<IncrementalHeuristicBaseMetric> chain = Arrays.asList(
                new NniIncrementalHeuristic(rfInc, "RF"),
                new UsprIncrementalHeuristicMetric(rfInc, "RF")
        );
        NniVndIncrementalHeuristic vnd = new NniVndIncrementalHeuristic(chain, null, "RF");

        double dist = vnd.getDistance(t1, t2);
        System.out.println("ZAKONCZONO. Dystans: " + dist);
    }
}