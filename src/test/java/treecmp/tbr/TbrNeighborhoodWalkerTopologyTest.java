package treecmp.tbr;

import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeHolder;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.tbr.TbrNeighborhoodWalker;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.util.CoverageMockMetric;
import treecmp.util.TestTreeFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TbrNeighborhoodWalkerTopologyTest {

    // ==========================================
    // ROOTED TREES TESTS (rTBR)
    // ==========================================

    @Test
    public void shouldVisitRootedTbrNeighborhood_FourLeavesCaterpillar() {
        verifyRootedNeighborhood(TestTreeFactory.fourLeavesRootedCaterpillarTree());
    }

    @Test
    public void shouldVisitRootedTbrNeighborhood_SixLeavesBalanced() {
        // Zrównoważone drzewa testują, czy Walker poprawnie radzi sobie z izomorficznymi gałęziami
        verifyRootedNeighborhood(TestTreeFactory.sixLeavesRootedBalancedTree());
    }

    @Test
    public void shouldVisitRootedTbrNeighborhood_TenLeavesTree1() {
        verifyRootedNeighborhood(TestTreeFactory.tenLeavesRootedTree1());
    }

    @Test
    public void shouldVisitRootedTbrNeighborhood_FifteenLeavesComplex() {
        // Stress test: sprawdza przemieszczanie w bardzo głębokim otoczeniu TBR
        verifyRootedNeighborhood(TestTreeFactory.fifteenLeavesRootedComplexTree());
    }

    // ==========================================
    // DRY HELPER ENGINE: ROOTED (rTBR)
    // ==========================================

    private void verifyRootedNeighborhood(Tree baseTree) {
        TbrUtils oracle = new TbrUtils();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        // Haszujemy drzewo startowe, aby wykluczyć je ze zbioru (Wyrocznia go nie zwraca)
        TreeRootedHolder baseHolder = new TreeRootedHolder(baseTree, idGroup);

        // 1. Zbieramy referencyjne otoczenie (Prawda Absolutna) z generatora Naiwnego
        Tree[] oracleNeighbors = oracle.generateNeighbours(baseTree);
        Set<TreeHolder> expectedTopologies = Arrays.stream(oracleNeighbors)
                .map(tree -> new TreeRootedHolder(tree, idGroup))
                .collect(Collectors.toSet());

        // Inicjalizujemy pustego Mocka
        CoverageMockMetric mockMetric = new CoverageMockMetric(true);
        mockMetric.initCalculationState(baseTree, null);

        TbrNeighborhoodWalker walker = new TbrNeighborhoodWalker();
        Set<TreeHolder> walkerTopologies = new HashSet<>();

        // 2. Walker nawiguje, a my fizycznie rekonstruujemy ułożenia, które nam zgłasza
        walker.walk(baseTree, mockMetric, (distance, pruneNode, rerootNode, targetNode) -> {
            Tree visitedTree = oracle.createTbrTree(baseTree, pruneNode, rerootNode, targetNode);
            if (visitedTree != null) {
                TreeRootedHolder visitedHolder = new TreeRootedHolder(visitedTree, idGroup);

                // ODRZUCAMY RUCH TOŻSAMOŚCIOWY (Dystans 0.0)
                if (!visitedHolder.equals(baseHolder)) {
                    walkerTopologies.add(visitedHolder);
                }
            }
        });

        // 3. Sprawdzamy pokrycie (Coverage)
        assertEquals(expectedTopologies.size(), walkerTopologies.size(),
                "Walker wygenerował inną liczbę unikalnych topologii rTBR niż Wyrocznia (TbrUtils)!");

        assertEquals(expectedTopologies, walkerTopologies,
                "Walker pominął niektóre drzewa z matematycznego otoczenia rTBR!");
    }
}