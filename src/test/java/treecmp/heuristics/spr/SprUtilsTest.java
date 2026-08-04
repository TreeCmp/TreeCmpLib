package treecmp.heuristics.spr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFMetric;
import treecmp.util.TestTreeFactory;
import treecmp.util.TreeCreator;

import java.util.HashSet;
import java.util.Set;

class SprUtilsTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * Tests of generateRSprNeighbours method, of class SprUtils.
     */
    @Test
    public void testGenerateRSprNeighboursShouldReturnExactly_12_Neighbours_testing_one_4_labels_tree() {
        SprUtils instance = new SprUtils();
        Tree baseTree = TestTreeFactory.fourLeavesBalancedTree1();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcSprNeighbours(baseTree);
        int neighSizeExpResult = 12;
        SprUtils sprUtils = new SprUtils();
        treeList = sprUtils.generateNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateRSprNeighboursShouldReturnExactly_26_Neighbours_testing_one_5_labels_tree() {
        SprUtils instance = new SprUtils();
        Tree baseTree = TestTreeFactory.fiveLeavesRootedBalancedTree();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcSprNeighbours(baseTree);
        int neighSizeExpResult = 26;
        SprUtils sprUtils = new SprUtils();
        treeList = sprUtils.generateNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateRSprNeighboursShouldReturnExactly_24_Neighbours_testing_one_5_labels_tree() {
        SprUtils instance = new SprUtils();
        Tree baseTree = TestTreeFactory.fiveLeavesRootedCaterpillarTree();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcSprNeighbours(baseTree);
        int neighSizeExpResult = 24;
                SprUtils sprUtils = new SprUtils();
        treeList = sprUtils.generateNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateRSprNeighboursShouldReturnExactly_34812_Neighbours_testing_one_100_labels_tree() {
        SprUtils instance = new SprUtils();
        Tree baseTree = TreeCreator.getrootrdTreeWith_100_Labels();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcSprNeighbours(baseTree);
        int neighSizeExpResult = 34812;
        SprUtils sprUtils = new SprUtils();
        treeList = sprUtils.generateNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    /**
     * Tests of generateUSprNeighbours method, of class SprUtils.
     */

    @Test
    public void testGenerateUSprNeighboursShouldReturnExactly_12_Neighbours_testing_all_5_labels_trees() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTree = TestTreeFactory.fiveLeavesUnrooted0Based();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcUsprNeighbours(baseTree);

        int neighSizeExpResult = 12;
        UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnExactly_12_Neighbours_testing_one_5_labels_tree() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTrees[] = TreeCreator.getAllUnrootedTreesWith_5_Labels();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcUsprNeighbours(baseTrees[0]);
        int neighSizeExpResult = 12;
        for(Tree bt: baseTrees) {
                    UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(bt);
            assertEquals(neighSizeExpResult, treeList.length);
        }
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnExactly_30_Neighbours_testing_one_6_labels_tree() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTrees[] = TreeCreator.getAllUnrootedTreesWith_6_Labels();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcUsprNeighbours(baseTrees[0]);
        int neighSizeExpResult = 30;
        for(Tree bt: baseTrees) {
                    UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(bt);
            assertEquals(neighSizeExpResult, treeList.length);
        }
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnExactly_30_Neighbours_testing_all_6_labels_trees() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        //Tree baseTree = TreeCreator.getTreeFromString("(((1,4),(2,5)),3,6);");
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcUsprNeighbours(baseTree);
        int neighSizeExpResult = 30;
                UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnExactly_56_Neighbours_testing_one_7_labels_tree() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTree = TestTreeFactory.sevenLeavesUnrooted0Based();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcUsprNeighbours(baseTree);
        int neighSizeExpResult = 56;
                UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnExactly_56_Neighbours_testing_some_7_labels_trees() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTrees[] = TreeCreator.getSomeUnrootedTreesWith_7_Labels();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcUsprNeighbours(baseTrees[0]);
        int neighSizeExpResult = 56;
        for(Tree bt: baseTrees) {
                    UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(bt);
            assertEquals(neighSizeExpResult, treeList.length);
        }
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnExactly_90_Neighbours_testing_one_8_labels_tree() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTree = TreeCreator.getTreeFromString("(1,2,(3,(4,(5,(6,(7,8))))));");
        //Tree baseTree = TestTreeFactory.eightLeavesUnrootedComplex1();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcUsprNeighbours(baseTree);
        int neighSizeExpResult = 90;
                UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnExactly_37442_Neighbours_testing_one_100_labels_tree() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTree = TreeCreator.getUnrootrdTreeWith_100_Labels();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcUsprNeighbours(baseTree);
        int neighSizeExpResult = 37442;
                UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnTreesWithRoot3Degree_testing_some_7_labels_trees() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTrees[] = TreeCreator.getSomeUnrootedTreesWith_7_Labels();
        Tree[] treeList;
        for(Tree bt: baseTrees) {
                    UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(bt);
            for (Tree t : treeList) {
                assertEquals(3, t.getRoot().getChildCount());
            }
        }
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnTreesWithRoot3Degree_testing_100_labels_tree() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTree = TreeCreator.getUnrootedTreeWith_50_Labels();
        //Tree baseTree = TreeCreator.getUnrootrdTreeWith_100_Labels();
        Tree[] treeList;
                UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(baseTree);
        for (Tree t : treeList) {
            assertEquals(3, t.getRoot().getChildCount());
        }
    }

    @Test
    public void testGenerateUSprNeighboursShoudReturnUniqueTrees() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Metric rf = new RFMetric();
        Tree baseTree = TestTreeFactory.sixLeavesUnrootedBalancedTree();
        Tree[] treeList;
                UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(baseTree);
        for (int i = 0; i < treeList.length; i++) {
            for (int j = 0; j < treeList.length; j++) {
                if (i != j) {
                    try {
                        double dist = rf.getDistance(treeList[i], treeList[j]);
                        assertNotEquals(0.0, dist, "trees " + i + " " + treeList[i].toString() + "\nand " + j + " " + treeList[j].toString() + " are the same");
                    } catch (TreeCmpException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Test
    public void testGenerateUSprNeighboursFookingFor_1_Neightbour() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Metric rf = new RFMetric();
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted1BasedBaseTree();
        Tree neightbourTree = TreeCreator.getTreeFromString("(((1,2),5),6,(3,4));");
        Tree[] treeList;
                UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(baseTree);
        boolean foundWantedTree = false;
        for (Tree tree : treeList) {
            try {
                if (rf.getDistance(tree, neightbourTree) == 0.0) {
                    foundWantedTree = true;
                }
            } catch (TreeCmpException e) {
                e.printStackTrace();
            }
        }
        assertTrue(foundWantedTree, "Neightbour tree " + neightbourTree + " not found");
    }

    @Test
    public void testGenerateUSprNeighboursFookingFor_30_Neightbours() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Metric rf = new RFMetric();
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted1BasedBaseTree();
        Tree neightbours[] = TreeCreator.getAll_30_NeightboursOfSome_6_Labels_Tree();
        Tree[] treeList;
                UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(baseTree);
        for (Tree neightbourTree : neightbours) {
            boolean foundWantedTree = false;
            for (Tree tree : treeList) {
                try {
                    if (rf.getDistance(tree, neightbourTree) == 0.0) {
                        foundWantedTree = true;
                    }
                } catch (TreeCmpException e) {
                    e.printStackTrace();
                }
            }
            assertTrue(foundWantedTree, "Neightbour tree " + neightbourTree + " not found");
        }
    }

    /**
     * Tests of createUsprTree method, of class SprUtils.
     */
    @Test
    public void testCreateUsprTree_CreateByLeaf_0_toLeaf_1_exchange_on_8_labels_tree() {
        Tree baseTree = TestTreeFactory.eightLeavesUnrootedComplex1();
        Node s = baseTree.getExternalNode(0);
        Node t = baseTree.getExternalNode(1);
        Tree expTree = TreeCreator.getTreeFromString("(((((2,4),5),(3,6)),7),1,8);");
        SprUtils sprUtils = new SprUtils();
        Tree result = sprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_0_toLeaf_2_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getExternalNode(0);
        Node t = baseTree.getExternalNode(2);
        Tree expTree = TreeCreator.getTreeFromString("(2,0,(1,(3,(4,5))));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_0_toLeaf_3_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getExternalNode(0);
        Node t = baseTree.getExternalNode(3);
        Tree expTree = TreeCreator.getTreeFromString("(2,1,((0,3),(4,5)));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_3_toLeaf_0_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted1BasedBaseTree();
        Node s = baseTree.getExternalNode(3);
        Node t = baseTree.getExternalNode(0);
        Tree expTree = TreeCreator.getTreeFromString("(((2,(1,4)),3),5,6);");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_0_toLeaf_0_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getInternalNode(0);
        Node t = baseTree.getExternalNode(0);
        Tree expTree = TreeCreator.getTreeFromString("(4,5,(0,(1,(2,3))));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_1_toLeaf_0_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getInternalNode(1);
        Node t = baseTree.getExternalNode(0);
        Tree expTree = TreeCreator.getTreeFromString("(4,5,(3,(0,(1,2))));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_2_toLeaf_4_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getInternalNode(2);
        Node t = baseTree.getExternalNode(4);
        Tree expTree = TreeCreator.getTreeFromString("(((0,1),4),(2,3),5);");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_2_toLeaf_5_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getInternalNode(2);
        Node t = baseTree.getExternalNode(5);
        Tree expTree = TreeCreator.getTreeFromString("(((0,1),5),(2,3),4);");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateBytoLeaf_4_NonRootNonLeaf_2_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getExternalNode(4);
        Node t = baseTree.getInternalNode(2);
        Tree expTree = TreeCreator.getTreeFromString("(((0,1),4),(5,3),2);");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_0_toLeaf_1_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getInternalNode(0);
        Node t = baseTree.getExternalNode(1);
        Tree expTree = TreeCreator.getTreeFromString("(4,5,(1,(0,(3,2))));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_7_to_Leaf_3_exchange_on_12_labels_tree() {
        Tree baseTree = TestTreeFactory.twelveLeavesUnrootedZeroLengths();
        Node s = baseTree.getInternalNode(7); //((2,3),((5,6),(1,((7,8),(9,10)))))
        Node t = baseTree.getExternalNode(3); //5.0
        Tree expTree = TreeCreator.getTreeFromString("(0,(((6,((1,((7,8),(9,10))),(2,3))),5),4),11);");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_0_toNonRootNonLeaf_0_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted1BasedBaseTree();
        Node s = baseTree.getExternalNode(0);
        Node t = baseTree.getInternalNode(0);
        Tree expTree = TreeCreator.getTreeFromString("((1,((2,3),4)),5,6);");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_1_toNonRootNonLeaf_0_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getExternalNode(1);
        Node t = baseTree.getInternalNode(0);
        Tree expTree = TreeCreator.getTreeFromString("(2,0,(3,(1,(4,5))));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_2_toNonRootNonLeaf_0_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getExternalNode(2);
        Node t = baseTree.getInternalNode(0);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,(3,(2,(4,5))));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_0_toNonRootNonLeaf_1_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getExternalNode(0);
        Node t = baseTree.getInternalNode(1);
        Tree expTree = TreeCreator.getTreeFromString("(2,1,(0,(3,(4,5))));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_1_toNonRootNonLeaf_1_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getExternalNode(1);
        Node t = baseTree.getInternalNode(1);
        Tree expTree = TreeCreator.getTreeFromString("(2,0,(1,(3,(4,5))));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_4_toNonRootNonLeaf_1_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getExternalNode(4);
        Node t = baseTree.getInternalNode(1);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,(2,(4,(3,5))));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_5_toNonRootNonLeaf_1_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getExternalNode(5);
        Node t = baseTree.getInternalNode(1);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,(2,(5,(3,4))));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_3_toNonRootNonLeaf_2_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getExternalNode(3);
        Node t = baseTree.getInternalNode(2);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,(3,(2,(4,5))));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_4_toNonRootNonLeaf_2_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getExternalNode(4);
        Node t = baseTree.getInternalNode(2);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,(4,(2,(3,5))));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_5_toNonRootNonLeaf_2_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getExternalNode(5);
        Node t = baseTree.getInternalNode(2);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,(5,(2,(3,4))));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_0_toNonRootNonLeaf_2_exchange_on_6_labels_tree() {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Node s = baseTree.getInternalNode(0);
        Node t = baseTree.getInternalNode(2);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,((2,3),(4,5)));");
        UsprUtils usprUtils = new UsprUtils();
        Tree result = usprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_LookingForSomeUnwantedTrees() throws TreeCmpException {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Tree unwantedTree = TestTreeFactory.sixLeavesUnrooted0BasedBaseTree();
        Tree[] treeList;
                UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(baseTree);

        boolean foundWantedTree = false;
        Metric rf = new RFMetric();
        for (Tree tree : treeList) {
            try {
                if (rf.getDistance(tree, unwantedTree) == 0.0) {
                    foundWantedTree = true;
                }
            } catch (TreeCmpException e) {
                e.printStackTrace();
            }
        }
        assertFalse(foundWantedTree);
    }

    @Test
    public void testCreateUsprTree_LookingForSomeWantedTrees() throws TreeCmpException {
        Tree baseTree = TestTreeFactory.sixLeavesUnrooted1BasedBaseTree();
        Tree wantedTrees[] = TreeCreator.getAll_30_NeightboursOfSome_6_Labels_Tree();
        Tree[] treeList;
        UsprUtils usprUtils = new UsprUtils();
        treeList = usprUtils.generateNeighbours(baseTree);

        for(Tree wantedTree : wantedTrees) {

            boolean foundWantedTree = false;
            Metric rf = new RFMetric();
            for (Tree tree : treeList) {
                try {
                    if (rf.getDistance(tree, wantedTree) == 0.0) {
                        foundWantedTree = true;
                    }
                } catch (TreeCmpException e) {
                    e.printStackTrace();
                }
            }
            assertTrue(foundWantedTree, "Tree " + wantedTree + " not found");
        }
    }

    @Test
    public void testAllUsprNeighborsShouldHaveUniqueLeavesWithoutDuplicates() throws TreeCmpException {
        // Bierzemy wymagające drzewo nieukorzenione (12 liści)
        Tree baseTree = TestTreeFactory.twelveLeavesUnrootedZeroLengths();
        UsprUtils usprUtils = new UsprUtils();

        Tree[] neighbors = usprUtils.generateNeighbours(baseTree);
        int expectedLeafCount = baseTree.getExternalNodeCount();

        assertNotNull(neighbors, "Lista sąsiadów nie może być null");
        assertTrue(neighbors.length > 0, "Lista sąsiadów nie może być pusta");

        for (int idx = 0; idx < neighbors.length; idx++) {
            Tree t = neighbors[idx];

            // 1. Sprawdzenie liczby liści w obiekcie Tree
            assertEquals(expectedLeafCount, t.getExternalNodeCount(),
                    "Sąsiad nr " + idx + " ma nieprawidłową liczbę liści w strukturze!");

            // 2. Weryfikacja unikalności nazw liści w obiekcie Tree
            Set<String> uniqueLeafNames = new HashSet<>();
            for (int i = 0; i < t.getExternalNodeCount(); i++) {
                String leafName = t.getExternalNode(i).getIdentifier().getName();
                assertTrue(uniqueLeafNames.add(leafName),
                        "Sąsiad nr " + idx + " ma zduplikowany liść w strukturze: " + leafName);
            }

            // 3. Najważniejsze: weryfikacja wygenerowanego Newicka (bez długości krawędzi!)
            // Usuwamy długości krawędzi np. ":0.0000000", aby zera po przecinku nie fałszowały wyniku dla liścia "0"
            String cleanNewick = t.toString().replaceAll(":[0-9.Ee+-]+", "");
            String[] tokens = cleanNewick.split("[(),;\\s]+");

            for (int i = 0; i < expectedLeafCount; i++) {
                String leafName = baseTree.getExternalNode(i).getIdentifier().getName();
                int count = 0;
                for (String token : tokens) {
                    if (token.equals(leafName)) {
                        count++;
                    }
                }
                assertEquals(1, count,
                        "BŁĄD NEWICKA w sąsiedzie nr " + idx + "! Liść [" + leafName + "] występuje " + count + " raz(y) w: " + cleanNewick);
            }
        }
    }

    @Test
    @DisplayName("Każde wygenerowane drzewo z createUsprTree musi mieć unikalne liście i poprawną liczbę przecinków")
    public void testCreateUsprTreeNeverReturnsDuplicatedLeavesOrBrokenNewick() throws Exception {
        // 10-liściowe drzewo testowe o nietrywialnej strukturze
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),(4,5)),((6,7),(8,9)),10);");
        assertNotNull(baseTree, "Drzewo bazowe nie może być null");

        UsprUtils usprUtils = new UsprUtils();
        int expectedLeaves = baseTree.getExternalNodeCount();
        int expectedCommas = expectedLeaves - 1; // W poprawnym Newicku zawsze L - 1 przecinków

        int extCount = baseTree.getExternalNodeCount();
        int intCount = baseTree.getInternalNodeCount();
        int validMovesCount = 0;

        // Sprawdzamy ABSOLUTNIE WSZYSTKIE kombinacje par węzłów (liście i węzły wewnętrzne)
        for (int i = 0; i < extCount + intCount; i++) {
            Node s = getNodeByIndex(baseTree, i, extCount);
            if (s.isRoot()) continue;

            for (int j = 0; j < extCount + intCount; j++) {
                Node t = getNodeByIndex(baseTree, j, extCount);
                if (t.isRoot() || s == t) continue;

                Tree resultTree = usprUtils.createUsprTree(baseTree, s, t);

                // Jeśli ruch był topologicznie nielegalny lub odrzucony przez bezpiecznik - pomijamy
                if (resultTree == null) continue;
                validMovesCount++;

                String newick = resultTree.toString();

                // 1. Weryfikacja liczby liści
                assertEquals(expectedLeaves, resultTree.getExternalNodeCount(),
                        "Błędna liczba liści po ruchu uSPR (" + s.getNumber() + " -> " + t.getNumber() + "): " + newick);

                // 2. Weryfikacja unikalności etykiet liści (brak duplikacji poddrzew!)
                Set<String> uniqueLeafNames = new HashSet<>();
                for (int k = 0; k < resultTree.getExternalNodeCount(); k++) {
                    String leafName = resultTree.getExternalNode(k).getIdentifier().getName();
                    assertTrue(uniqueLeafNames.add(leafName),
                            "WYKRYTO DUPLIKAT LIŚCIA '" + leafName + "' w Newicku: " + newick);
                }

                // 3. Weryfikacja matematyczna Newicka (Liczba przecinków == L - 1)
                int commaCount = countChar(newick, ',');
                assertEquals(expectedCommas, commaCount,
                        "USZKODZONY NEWICK (zła liczba przecinków: " + commaCount + " zamiast " + expectedCommas + "): " + newick);

                // 4. Weryfikacja braku wiszących wskaźników null w napisie
                assertFalse(newick.contains("null"),
                        "Newick zawiera niedozwolony wskaźnik 'null': " + newick);

                // 5. Weryfikacja stopni węzłów wewnętrznych (żaden węzeł nie może mieć tylko 1 dziecka)
                for (int k = 0; k < resultTree.getInternalNodeCount(); k++) {
                    Node internalNode = resultTree.getInternalNode(k);
                    if (!internalNode.isRoot()) {
                        assertTrue(internalNode.getChildCount() >= 2,
                                "Węzeł wewnętrzny ma stopień < 2 (zdegenerowane drzewo): " + newick);
                    }
                }
            }
        }

        assertTrue(validMovesCount > 0, "Test powinien zweryfikować przynajmniej kilkadziesiąt poprawnych ruchów uSPR");
    }

    @Test
    @DisplayName("generateNeighbours nie ma prawa zwrócić ani jednego zduplikowanego lub uszkodzonego sąsiada")
    public void testGenerateNeighboursIntegrity() throws Exception {
        Tree baseTree = TreeCreator.getTreeFromString("(((A,B),(C,D)),(E,(F,G)));");
        UsprUtils usprUtils = new UsprUtils();

        Tree[] neighbors = usprUtils.generateNeighbours(baseTree);
        assertNotNull(neighbors);
        assertTrue(neighbors.length > 0, "Lista sąsiadów uSPR nie może być pusta");

        int expectedCommas = baseTree.getExternalNodeCount() - 1;

        for (int i = 0; i < neighbors.length; i++) {
            Tree neighbor = neighbors[i];
            assertNotNull(neighbor, "Sąsiad na indeksie " + i + " jest null");

            String newick = neighbor.toString();
            assertEquals(expectedCommas, countChar(newick, ','),
                    "Sąsiad uSPR #" + i + " ma nieprawidłową strukturę Newick: " + newick);
        }
    }

    // =========================================================================
    // METODY POMOCNICZE
    // =========================================================================

    private Node getNodeByIndex(Tree tree, int index, int extCount) {
        if (index < extCount) {
            return tree.getExternalNode(index);
        } else {
            return tree.getInternalNode(index - extCount);
        }
    }

    private int countChar(String str, char ch) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch) count++;
        }
        return count;
    }
}