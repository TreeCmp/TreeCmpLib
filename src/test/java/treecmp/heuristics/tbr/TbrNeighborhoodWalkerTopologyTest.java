package treecmp.heuristics.tbr;

import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.heuristics.TreeHolder;
import treecmp.heuristics.TreeRootedHolder;
import treecmp.heuristics.TreeUnrootedHolder;
import treecmp.heuristics.tbr.acc.TbrNeighborhoodWalker;
import treecmp.heuristics.tbr.acc.UtbrNeighborhoodWalker;
import treecmp.heuristics.tbr.TbrUtils;
import treecmp.heuristics.tbr.UTbrUtils;
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
    // UNROOTED TREES TESTS (uTBR)
    // ==========================================

    @Test
    public void shouldVisitUnrootedTbrNeighborhood_FourLeavesStarTree() {
        verifyUnrootedNeighborhood(TestTreeFactory.fourLeavesUnrootedStarTree());
    }

    @Test
    public void shouldVisitUnrootedTbrNeighborhood_SixLeavesBalancedTree() {
        verifyUnrootedNeighborhood(TestTreeFactory.sixLeavesUnrootedBalancedTree());
    }

    @Test
    public void shouldVisitUnrootedTbrNeighborhood_EightLeavesCaterpillarTree() {
        // Asymetria na drzewach nieukorzenionych
        verifyUnrootedNeighborhood(TestTreeFactory.eightLeavesUnrootedCaterpillarTree());
    }

    @Test
    public void shouldVisitUnrootedTbrNeighborhood_FifteenLeavesComplexTree() {
        // Stress test otoczenia uTBR (ogromna wielkość)
        verifyUnrootedNeighborhood(TestTreeFactory.fifteenLeavesUnrootedComplexTree());
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

        // Inicjalizujemy pustego Mocka dla drzew UKORZENIONYCH
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

    // ==========================================
    // DRY HELPER ENGINE: UNROOTED (uTBR)
    // ==========================================

    private void verifyUnrootedNeighborhood(Tree baseTree) {
        UTbrUtils oracle = new UTbrUtils();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);

        // Haszujemy drzewo startowe używając TreeUnrootedHolder (drzewa nieukorzenione)
        TreeUnrootedHolder baseHolder = new TreeUnrootedHolder(baseTree, idGroup);

        // 1. Zbieramy referencyjne otoczenie uTBR (Prawda Absolutna)
        Tree[] oracleNeighbors = oracle.generateNeighbours(baseTree);
        Set<TreeHolder> expectedTopologies = Arrays.stream(oracleNeighbors)
                .map(tree -> new TreeUnrootedHolder(tree, idGroup))
                .collect(Collectors.toSet());

        // Inicjalizujemy pustego Mocka dla drzew NIEUKORZENIONYCH (false)
        CoverageMockMetric mockMetric = new CoverageMockMetric(false);
        mockMetric.initCalculationState(baseTree, null);

        UtbrNeighborhoodWalker walker = new UtbrNeighborhoodWalker();
        Set<TreeHolder> walkerTopologies = new HashSet<>();

        // 2. Walker nawiguje, wykorzystując UTbrUtils do fizycznej rekonstrukcji zjawisk
        walker.walk(baseTree, mockMetric, (distance, pruneNode, rerootNode, targetNode) -> {

            // Wykorzystujemy nową, publiczną metodę createUtbrTree
            Tree visitedTree = oracle.createUtbrTree(baseTree, pruneNode, rerootNode, targetNode);

            if (visitedTree != null) {
                TreeUnrootedHolder visitedHolder = new TreeUnrootedHolder(visitedTree, idGroup);

                // ODRZUCAMY RUCH TOŻSAMOŚCIOWY
                if (!visitedHolder.equals(baseHolder)) {
                    walkerTopologies.add(visitedHolder);
                }
            }
        });

        // 3. Sprawdzamy pokrycie (Coverage)
        assertEquals(expectedTopologies.size(), walkerTopologies.size(),
                "UtbrNeighborhoodWalker wygenerował inną liczbę unikalnych topologii uTBR niż Wyrocznia (UTbrUtils)!");

        assertEquals(expectedTopologies, walkerTopologies,
                "UtbrNeighborhoodWalker pominął niektóre drzewa z matematycznego otoczenia uTBR!");
    }
}