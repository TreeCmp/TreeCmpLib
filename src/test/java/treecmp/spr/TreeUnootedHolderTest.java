package treecmp.spr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pal.misc.IdGroup;
import pal.tree.Node;
import pal.tree.Tree;
import pal.tree.TreeUtils;
import treecmp.common.TreeCmpException;
import treecmp.heuristics.spr.SprUtils;
import treecmp.heuristics.TreeUnootedHolder;
import treecmp.metrics.Metric;
import treecmp.metrics.topological.RFMetric;
import treecmp.util.TreeCreator;

import static org.junit.jupiter.api.Assertions.*;

class TreeUnootedHolderTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }
    @Test
    public void testUnrootrdHolderSameTreesShouldHaveSameHashes() {

        SprUtils instance = new SprUtils();
        Tree baseTree1 = TreeCreator.getTreeFromString("((3,4),5,(6,(1,2)));");
        Tree baseTree2 = TreeCreator.getTreeFromString("((1,2),(5,(3,4)),6);");
        IdGroup idGroup1 = TreeUtils.getLeafIdGroup(baseTree1);
        IdGroup idGroup2 = TreeUtils.getLeafIdGroup(baseTree1);
        TreeUnootedHolder trHdr1 = new TreeUnootedHolder(baseTree1, idGroup1);
        TreeUnootedHolder trHdr2 = new TreeUnootedHolder(baseTree2, idGroup2);
        assertEquals(trHdr1.hash, trHdr2.hash);
    }

    /**
     * Test of constructor , of class UnrootrdHolder.
     */
    @Test
    public void testUnrootrdHolderAllLeafToLeafNeighboursShouldBeUnique() {

        SprUtils instance = new SprUtils();
        //Tree baseTree = TreeCreator.getTreeFromString("((0,((1,2),3)),4,5);");
        //Tree baseTree = TreeCreator.getTreeFromString("((4,((2,5),(3,6))),1,7);");
        //Tree baseTree = TreeCreator.getTreesWith_100_Labels();
        Tree baseTree = TreeCreator.getUnrootedTreeWith_200_Labels();
        int extNum = baseTree.getExternalNodeCount();
        int intNum = baseTree.getInternalNodeCount();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
        int neighSize = instance.calcUsprNeighbours(baseTree);
        Node s, t;
        Tree resultTree;
        //leaf to leaf
        TreeUnootedHolder[] usprTreeArray = new TreeUnootedHolder[(4 * neighSize) / 3];
        int index = 0;
        for (int i = 0; i < extNum; i++) {
            s = baseTree.getExternalNode(i);
            for (int j = 0; j < extNum; j++) {
                t = baseTree.getExternalNode(j);
                if (!instance.sameParent(s, t)) {
                    resultTree = instance.createUsprTree(baseTree, s, t);
                    usprTreeArray[index] = new TreeUnootedHolder(resultTree, idGroup);
                    index++;
                }
            }
        }
        Metric rf = new RFMetric();
        int n = index;
        for (int i = 0; i < n; i++) {
            for (int j = i+1; i < n; i++) {
                if (usprTreeArray[i].hash == usprTreeArray[j].hash) {
                    try {
                        assertEquals(0.0, rf.getDistance(usprTreeArray[i].tree, usprTreeArray[j].tree));
                    } catch (TreeCmpException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    if (rf.getDistance(usprTreeArray[i].tree, usprTreeArray[j].tree) == 0.0) {
                            assertEquals(usprTreeArray[i].hash, usprTreeArray[j].hash);
                    }
                } catch (TreeCmpException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Test of constructor , of class UnrootrdHolder.
     */
    @Test
    public void testUnrootrdHolderAllNonLeafNonRootToLeafNeighboursShouldBeUnique() {

        SprUtils instance = new SprUtils();
        //Tree baseTree = TreeCreator.getTreeFromString("((0,((1,2),3)),4,5);");
        //Tree baseTree = TreeCreator.getTreeFromString("((4,((2,5),(3,6))),1,7);");
        //Tree baseTree = TreeCreator.getTreeFromString("((((((9,((((((33,((47,52),(((28,72),((3,70),75)),76))),57),((12,((((32,(20,84)),((27,(45,87)),49)),(56,(38,65))),82)),80)),(1,99)),(13,40)),61)),(14,89)),15),94),((7,(35,(((((((4,44),86),(18,100)),(10,(((22,55),60),78))),(30,34)),43),((53,64),79)))),97)),66,((17,((69,(41,90)),98)),((((37,(11,(50,51))),42),(((48,59),(((24,(8,25)),(((6,(((39,(((((31,(16,91)),((5,((2,62),67)),68)),(((((21,(26,(23,29))),(36,46)),74),77),96)),54),81)),92),(71,95))),(19,(73,85))),(58,93))),88)),83)),63)));");
        Tree baseTree = TreeCreator.getTreeFromString("(((((88,(96,186)),(((19,((((((((18,63),((103,(70,190)),(57,138))),(((7,167),172),200)),(26,83)),(20,163)),(72,((34,(58,((102,176),((59,157),196)))),100))),71),179)),(91,94)),185)),((((((4,80),(123,(152,154))),(((15,(((((29,(38,55)),(118,(((37,(((43,(139,((61,107),181))),((3,119),147)),(135,145))),(127,(117,191))),159))),((56,(((25,(13,(121,160))),(128,((((((((((8,((28,39),(85,143))),32),(9,174)),((((((50,151),155),((11,(66,(48,67))),192)),(((((42,(44,168)),(((17,(47,77)),(51,60)),86)),(23,198)),(148,188)),153)),((76,(((141,(142,170)),((40,(35,(81,87))),(149,178))),184)),(((21,105),(112,((14,16),116))),144))),(78,((49,((22,(79,(((89,101),(169,(99,171))),156))),150)),(124,130))))),(27,125)),(68,((6,133),(74,137)))),(75,98)),(41,177)),(10,(53,(((110,111),(136,(106,189))),134)))),166))),129)),114)),(69,162)),104)),((97,108),175)),161)),((45,131),180)),(31,120)),113)),((((1,165),(193,(((52,(36,93)),84),194))),((24,((46,146),((164,183),199))),195)),(54,((90,(92,((30,132),187))),122)))),126,((((73,(12,82)),((2,(64,((95,(65,173)),((109,(5,197)),182)))),140)),((33,115),158)),62));");
        int extNum = baseTree.getExternalNodeCount();
        int intNum = baseTree.getInternalNodeCount();
        IdGroup idGroup = TreeUtils.getLeafIdGroup(baseTree);
        int neighSize = instance.calcUsprNeighbours(baseTree);
        Node s, t;
        Tree resultTree;
        //non-leaf and non-root to leaf
        TreeUnootedHolder[] usprTreeArray = new TreeUnootedHolder[(4 * neighSize) / 3];
        int index = 0;
        for (int i=0; i<intNum; i++){
            s = baseTree.getInternalNode(i);
            if(s.isRoot())
                continue;
            for (int j=0; j<extNum; j++){
                t = baseTree.getExternalNode(j);
                if (instance.isValidUsprMove(s,t)){
                    resultTree = instance.createUsprTree(baseTree,s,t);
                    usprTreeArray[index] = new TreeUnootedHolder(resultTree, idGroup);
                    index++;
                }
            }
        }
        Metric rf = new RFMetric();
        int n = index;
        for (int i = 0; i < n; i++) {
            for (int j = i+1; i < n; i++) {
                if (usprTreeArray[i].hash == usprTreeArray[j].hash) {
                    try {
                        assertEquals(0.0, rf.getDistance(usprTreeArray[i].tree, usprTreeArray[j].tree));
                    } catch (TreeCmpException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    if (rf.getDistance(usprTreeArray[i].tree, usprTreeArray[j].tree) == 0.0) {
                        assertEquals(usprTreeArray[i].hash, usprTreeArray[j].hash);
                    }
                } catch (TreeCmpException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}