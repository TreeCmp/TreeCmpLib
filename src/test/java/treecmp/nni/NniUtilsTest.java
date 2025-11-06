package treecmp.nni;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Tree;
import pal.tree.TreeTool;
import treecmp.heuristics.nni.NniUtils;
import treecmp.util.TreeCreator;

public class NniUtilsTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * Tests of generateRNniNeighbours method, of class NniUtils.
     */
    @Test
    public void testGenerateRNniNeighboursShouldReturnExactly_12_Neighbours_testing_one_4_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("((1,2),(3,4));");
        NniUtils nniUtils = new NniUtils(false);
        Tree[] treeList = nniUtils.generateNeighbours(baseTree);
        int neighSizeExpResult = 4;
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateNniNeighboursShouldReturnExactly_12_Neighbours_testing_one_4_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("((1,2),3,4);");
        NniUtils nniUtils = new NniUtils(true);
        Tree[] treeList = nniUtils.generateNeighbours(baseTree);
        int neighSizeExpResult = 2;
        assertEquals(neighSizeExpResult, treeList.length);
    }

}
