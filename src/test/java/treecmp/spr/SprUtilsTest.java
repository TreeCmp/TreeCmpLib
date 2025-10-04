package treecmp.spr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import pal.tree.Node;
import pal.tree.Tree;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.spr.SprUtils;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFMetric;
import treecmp.util.TreeCreator;

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
        Tree baseTree = TreeCreator.getTreeFromString("((1,2),(3,4));");
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcSprNeighbours(baseTree);
        int neighSizeExpResult = 12;
        treeList = SprUtils.generateRSprNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateRSprNeighboursShouldReturnExactly_26_Neighbours_testing_one_5_labels_tree() {
        SprUtils instance = new SprUtils();
        Tree baseTree = TreeCreator.getTreeFromString("(((1,2),(3,4)),5);");
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcSprNeighbours(baseTree);
        int neighSizeExpResult = 26;
        treeList = SprUtils.generateRSprNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateRSprNeighboursShouldReturnExactly_24_Neighbours_testing_one_5_labels_tree() {
        SprUtils instance = new SprUtils();
        Tree baseTree = TreeCreator.getTreeFromString("((((1,2),3),4),5);");
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcSprNeighbours(baseTree);
        int neighSizeExpResult = 24;
        treeList = SprUtils.generateRSprNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateRSprNeighboursShouldReturnExactly_34812_Neighbours_testing_one_100_labels_tree() {
        SprUtils instance = new SprUtils();
        Tree baseTree = TreeCreator.getrootrdTreeWith_100_Labels();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcSprNeighbours(baseTree);
        int neighSizeExpResult = 34812;
        treeList = SprUtils.generateRSprNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    /**
     * Tests of generateUSprNeighbours method, of class SprUtils.
     */

    @Test
    public void testGenerateUSprNeighboursShouldReturnExactly_12_Neighbours_testing_all_5_labels_trees() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTree = TreeCreator.getTreeFromString("(((0,1),2),3,4);");
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcUsprNeighbours(baseTree);

        int neighSizeExpResult = 12;
        treeList = SprUtils.generateUSprNeighbours(baseTree);
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
            treeList = SprUtils.generateUSprNeighbours(bt);
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
            treeList = SprUtils.generateUSprNeighbours(bt);
            assertEquals(neighSizeExpResult, treeList.length);
        }
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnExactly_30_Neighbours_testing_all_6_labels_trees() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        //Tree baseTree = TreeCreator.getTreeFromString("(((1,4),(2,5)),3,6);");
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcUsprNeighbours(baseTree);
        int neighSizeExpResult = 30;
        treeList = SprUtils.generateUSprNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnExactly_56_Neighbours_testing_one_7_labels_tree() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTree = TreeCreator.getTreeFromString("((0,((1,2),(3,4))),5,6);");
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcUsprNeighbours(baseTree);
        int neighSizeExpResult = 56;
        treeList = SprUtils.generateUSprNeighbours(baseTree);
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
            treeList = SprUtils.generateUSprNeighbours(bt);
            assertEquals(neighSizeExpResult, treeList.length);
        }
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnExactly_90_Neighbours_testing_one_8_labels_tree() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTree = TreeCreator.getTreeFromString("(1,2,(3,(4,(5,(6,(7,8))))));");
        //Tree baseTree = TreeCreator.getTreeFromString("((4,(((2,5),(3,6)),7)),1,8);");
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcUsprNeighbours(baseTree);
        int neighSizeExpResult = 90;
        treeList = SprUtils.generateUSprNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnExactly_37442_Neighbours_testing_one_100_labels_tree() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTree = TreeCreator.getUnrootrdTreeWith_100_Labels();
        Tree[] treeList;
        //int neighSizeExpResult = instance.calcUsprNeighbours(baseTree);
        int neighSizeExpResult = 37442;
        treeList = SprUtils.generateUSprNeighbours(baseTree);
        assertEquals(neighSizeExpResult, treeList.length);
    }

    @Test
    public void testGenerateUSprNeighboursShouldReturnTreesWithRoot3Degree_testing_some_7_labels_trees() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Tree baseTrees[] = TreeCreator.getSomeUnrootedTreesWith_7_Labels();
        Tree[] treeList;
        for(Tree bt: baseTrees) {
            treeList = SprUtils.generateUSprNeighbours(bt);
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
        treeList = SprUtils.generateUSprNeighbours(baseTree);
        for (Tree t : treeList) {
            assertEquals(3, t.getRoot().getChildCount());
        }
    }

    @Test
    public void testGenerateUSprNeighboursShoudReturnUniqueTrees() throws TreeCmpException {
        SprUtils instance = new SprUtils();
        Metric rf = new RFMetric();
        Tree baseTree = TreeCreator.getTreeFromString("(((1,2),(3,4)),5,6);");
        Tree[] treeList;
        treeList = SprUtils.generateUSprNeighbours(baseTree);
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
        Tree baseTree = TreeCreator.getTreeFromString("(1,2,(3,(4,(5,6))));");
        Tree neightbourTree = TreeCreator.getTreeFromString("(((1,2),5),6,(3,4));");
        Tree[] treeList;
        treeList = SprUtils.generateUSprNeighbours(baseTree);
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
        Tree baseTree = TreeCreator.getTreeFromString("(1,2,(3,(4,(5,6))));");
        Tree neightbours[] = TreeCreator.getAll_30_NeightboursOfSome_6_Labels_Tree();
        Tree[] treeList;
        treeList = SprUtils.generateUSprNeighbours(baseTree);
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
        Tree baseTree = TreeCreator.getTreeFromString("((4,(((2,5),(3,6)),7)),1,8);");
        Node s = baseTree.getExternalNode(0);
        Node t = baseTree.getExternalNode(1);
        Tree expTree = TreeCreator.getTreeFromString("(((((2,4),5),(3,6)),7),1,8);");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_0_toLeaf_2_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getExternalNode(0);
        Node t = baseTree.getExternalNode(2);
        Tree expTree = TreeCreator.getTreeFromString("(2,0,(1,(3,(4,5))));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_0_toLeaf_3_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getExternalNode(0);
        Node t = baseTree.getExternalNode(3);
        Tree expTree = TreeCreator.getTreeFromString("(2,1,((0,3),(4,5)));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_3_toLeaf_0_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(1,2,(3,(4,(5,6))));");
        Node s = baseTree.getExternalNode(3);
        Node t = baseTree.getExternalNode(0);
        Tree expTree = TreeCreator.getTreeFromString("(((2,(1,4)),3),5,6);");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_0_toLeaf_0_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getInternalNode(0);
        Node t = baseTree.getExternalNode(0);
        Tree expTree = TreeCreator.getTreeFromString("(4,5,(0,(1,(2,3))));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_1_toLeaf_0_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getInternalNode(1);
        Node t = baseTree.getExternalNode(0);
        Tree expTree = TreeCreator.getTreeFromString("(4,5,(3,(0,(1,2))));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_2_toLeaf_4_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getInternalNode(2);
        Node t = baseTree.getExternalNode(4);
        Tree expTree = TreeCreator.getTreeFromString("(((0,1),4),(2,3),5);");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_2_toLeaf_5_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getInternalNode(2);
        Node t = baseTree.getExternalNode(5);
        Tree expTree = TreeCreator.getTreeFromString("(((0,1),5),(2,3),4);");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateBytoLeaf_4_NonRootNonLeaf_2_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getExternalNode(4);
        Node t = baseTree.getInternalNode(2);
        Tree expTree = TreeCreator.getTreeFromString("(((0,1),4),(5,3),2);");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_0_toLeaf_1_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getInternalNode(0);
        Node t = baseTree.getExternalNode(1);
        Tree expTree = TreeCreator.getTreeFromString("(4,5,(1,(0,(3,2))));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_7_to_Leaf_3_exchange_on_12_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0:0.0000000,(((2:0.0000000,3:0.0000000):0.0000000,((5:0.0000000,6:0.0000000):0.0000000,(1:0.0000000,((7:0.0000000,8:0.0000000):0.0000000,(9:0.0000000,10:0.0000000):0.0000000):0.0000000):0.0000000):0.0000000):0.0000000,4:0.0000000):0.0000000,11:0.0000000);");
        Node s = baseTree.getInternalNode(7); //((2,3),((5,6),(1,((7,8),(9,10)))))
        Node t = baseTree.getExternalNode(3); //5.0
        Tree expTree = TreeCreator.getTreeFromString("(0,(((6,((1,((7,8),(9,10))),(2,3))),5),4),11);");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_0_toNonRootNonLeaf_0_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(1,2,(3,(4,(5,6))));");
        Node s = baseTree.getExternalNode(0);
        Node t = baseTree.getInternalNode(0);
        Tree expTree = TreeCreator.getTreeFromString("((1,((2,3),4)),5,6);");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_1_toNonRootNonLeaf_0_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getExternalNode(1);
        Node t = baseTree.getInternalNode(0);
        Tree expTree = TreeCreator.getTreeFromString("(2,0,(3,(1,(4,5))));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_2_toNonRootNonLeaf_0_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getExternalNode(2);
        Node t = baseTree.getInternalNode(0);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,(3,(2,(4,5))));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_0_toNonRootNonLeaf_1_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getExternalNode(0);
        Node t = baseTree.getInternalNode(1);
        Tree expTree = TreeCreator.getTreeFromString("(2,1,(0,(3,(4,5))));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_1_toNonRootNonLeaf_1_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getExternalNode(1);
        Node t = baseTree.getInternalNode(1);
        Tree expTree = TreeCreator.getTreeFromString("(2,0,(1,(3,(4,5))));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_4_toNonRootNonLeaf_1_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getExternalNode(4);
        Node t = baseTree.getInternalNode(1);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,(2,(4,(3,5))));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_5_toNonRootNonLeaf_1_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getExternalNode(5);
        Node t = baseTree.getInternalNode(1);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,(2,(5,(3,4))));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_3_toNonRootNonLeaf_2_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getExternalNode(3);
        Node t = baseTree.getInternalNode(2);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,(3,(2,(4,5))));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_4_toNonRootNonLeaf_2_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getExternalNode(4);
        Node t = baseTree.getInternalNode(2);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,(4,(2,(3,5))));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByLeaf_5_toNonRootNonLeaf_2_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getExternalNode(5);
        Node t = baseTree.getInternalNode(2);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,(5,(2,(3,4))));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_CreateByNonRootNonLeaf_0_toNonRootNonLeaf_2_exchange_on_6_labels_tree() {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Node s = baseTree.getInternalNode(0);
        Node t = baseTree.getInternalNode(2);
        Tree expTree = TreeCreator.getTreeFromString("(0,1,((2,3),(4,5)));");
        Tree result = SprUtils.createUsprTree(baseTree, s, t);
        Metric rf = new RFMetric();
        try {
            assertEquals(0.0, rf.getDistance(expTree, result));
        } catch (TreeCmpException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateUsprTree_LookingForSomeUnwantedTrees() throws TreeCmpException {
        Tree baseTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Tree unwantedTree = TreeCreator.getTreeFromString("(0,1,(2,(3,(4,5))));");
        Tree[] treeList;
        treeList = SprUtils.generateUSprNeighbours(baseTree);

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
        Tree baseTree = TreeCreator.getTreeFromString("(1,2,(3,(4,(5,6))));");
        Tree wantedTrees[] = TreeCreator.getAll_30_NeightboursOfSome_6_Labels_Tree();
        Tree[] treeList;
        treeList = SprUtils.generateUSprNeighbours(baseTree);

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
}