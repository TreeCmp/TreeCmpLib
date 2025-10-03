/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package treecmp.util;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import pal.io.InputSource;
import pal.tree.ReadTree;
import pal.tree.Tree;
import pal.tree.TreeParseException;

/**
 *
 * @author Damian
 */
public class TreeCreator {

    private final static Logger LOG = Logger.getLogger(TreeCreator.class.getName());

    public static Tree getTreeFromString(String treeStr) {
        Tree tree = null;
        InputSource is = InputSource.openString(treeStr);
        try {
            tree = new ReadTree(is);
            is.close();
        } catch (TreeParseException ex) {
            LOG.log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return tree;
    }

    /* The four trees for "On Matching Distances for Rooted and Weighted Phylogenetic Trees"
     * unpublished work by Damian Bogdanowicz and Krzysztof Giaro
     */
    public static Tree getWeightedT1() {
        String treeStr = "((a:1,b:1,c:1,d:1):10,e:1,(f:1,g:1,h:1,i:1,j:1):1);";
        return getTreeFromString(treeStr);
    }

    public static Tree getWeightedT2() {
        String treeStr = "((a:1,b:1,c:1,d:1,e:1):10,f:1,(g:1,h:1,i:1,j:1):1);";
        return getTreeFromString(treeStr);
    }

    public static Tree getWeightedT3() {
        String treeStr = "((a:1,b:1,c:1,d:1):10,e:1,f:1,g:1,h:1,i:1,j:1);";
        return getTreeFromString(treeStr);
    }

    public static Tree getWeightedT4() {
        String treeStr = "((a:1,b:1,c:1,d:1,e:1):9,f:1,(g:1,h:1,i:1,j:1):1);";
        return getTreeFromString(treeStr);
    }

    public static Tree getWeightedSimpleUnitT1() {
        String treeStr = "((a:1,b:1):1,(c:1,d:1):1);";
        return getTreeFromString(treeStr);
    }

    public static Tree getWeightedSimpleUnitT2() {
        String treeStr = "(((a:1,b:1):1,c:1):1,d:1);";
        return getTreeFromString(treeStr);
    }

     public static Tree getWeightedSimple10UnitT1() {
        String treeStr = "((a:10,b:10):10,(c:10,d:10):10);";
        return getTreeFromString(treeStr);
    }

    public static Tree getWeightedSimple10UnitT2() {
        String treeStr = "(((a:10,b:10):10,c:10):10,d:10);";
        return getTreeFromString(treeStr);
    }
    
    public static Tree getWeightedUnitT1() {
        String treeStr = "((a:1,b:1,c:1,d:1):1,e:1,(f:1,g:1,h:1,i:1,j:1):1);";
        return getTreeFromString(treeStr);
    }

    public static Tree getWeightedUnitT2() {
        String treeStr = "((a:1,b:1,c:1,d:1,e:1):1,f:1,(g:1,h:1,i:1,j:1):1);";
        return getTreeFromString(treeStr);
    }

    public static Tree getWeightedUnitT3() {
        String treeStr = "((a:1,b:1,c:1,d:1):1,e:1,f:1,g:1,h:1,i:1,j:1);";
        return getTreeFromString(treeStr);
    }

      public static Tree getWeighted10UnitT1() {
        String treeStr = "((a:10,b:10,c:10,d:10):10,e:10,(f:10,g:10,h:10,i:10,j:10):10);";
        return getTreeFromString(treeStr);
    }

    public static Tree getWeighted10UnitT2() {
        String treeStr = "((a:10,b:10,c:10,d:10,e:10):10,f:10,(g:10,h:10,i:10,j:10):10);";
        return getTreeFromString(treeStr);
    }

    public static Tree getWeighted10UnitT3() {
        String treeStr = "((a:10,b:10,c:10,d:10):10,e:10,f:10,g:10,h:10,i:10,j:10);";
        return getTreeFromString(treeStr);
    }
    
    /* The two trees given as an example with gtp application: 
     * http://www.unc.edu/depts/stat-or/miscellaneous/provan/treespace/
     * They should be at the distance of 2.844225
     */
    public static Tree getWeightedGtpT1() {
        String treeStr = "(((((4:1,5:1):0.88,(3a:1,3b:1):1):0.47,2:1):0.73,1:1):0.83,6:1);";
        return getTreeFromString(treeStr);
    }

    public static Tree getWeightedGtpT2() {
        String treeStr = "(((((3a:0.2,3b:1):0.5,4:1):0.15,2:1):0.87,(5:1,6:1):0.42):0.7,1:1);";
        return getTreeFromString(treeStr);
    }

    public static Tree getrootrdTreeWith_100_Labels() {
        String treeStr = ("((((((28,((((50,(((51,54),((12,23),91)),63)),66),(((9,13),(22,71)),87)),37)),45),(62,(((14,((15,(90,(((53,(72,(((57,(((((5,(25,52)),((6,((8,((24,((32,(31,39)),96)),((42,((76,80),98)),((20,27),86)))),68)),29)),(33,34)),(35,70)),(88,95))),(3,84)),83))),((1,(((((((((4,(((((61,64),(2,97)),(((7,((21,(41,((18,47),85))),59)),(30,55)),81)),(38,93)),49)),11),77),(((40,(10,(43,73))),75),82)),48),67),((46,(16,65)),89)),19),(44,(78,(79,(17,100)))))),60)),94))),99)),(26,56)),(69,92)))),36),58),74);");
        return getTreeFromString(treeStr);
    }

    public static Tree getUnrootedTreeWith_50_Labels() {
        String treeStr = ("((((8,(((4,(29,((1,((22,30),38)),36))),((13,37),(23,45))),19)),27),((15,((((14,((11,12),35)),(((18,((3,33),43)),(31,(44,(24,49)))),50)),21),47)),48)),((16,34),(9,39)),(2,((26,(((7,20),(((6,((10,28),41)),((5,(17,42)),25)),46)),40)),32)));");
        return getTreeFromString(treeStr);
    }

    public static Tree getUnrootrdTreeWith_100_Labels() {
        String treeStr = ("((((((9,((((((33,((47,52),(((28,72),((3,70),75)),76))),57),((12,((((32,(20,84)),((27,(45,87)),49)),(56,(38,65))),82)),80)),(1,99)),(13,40)),61)),(14,89)),15),94),((7,(35,(((((((4,44),86),(18,100)),(10,(((22,55),60),78))),(30,34)),43),((53,64),79)))),97)),66,((17,((69,(41,90)),98)),((((37,(11,(50,51))),42),(((48,59),(((24,(8,25)),(((6,(((39,(((((31,(16,91)),((5,((2,62),67)),68)),(((((21,(26,(23,29))),(36,46)),74),77),96)),54),81)),92),(71,95))),(19,(73,85))),(58,93))),88)),83)),63)));");
        return getTreeFromString(treeStr);
    }

    public static Tree getUnrootedTreeWith_200_Labels() {
        String treeStr = ("(((((88,(96,186)),(((19,((((((((18,63),((103,(70,190)),(57,138))),(((7,167),172),200)),(26,83)),(20,163)),(72,((34,(58,((102,176),((59,157),196)))),100))),71),179)),(91,94)),185)),((((((4,80),(123,(152,154))),(((15,(((((29,(38,55)),(118,(((37,(((43,(139,((61,107),181))),((3,119),147)),(135,145))),(127,(117,191))),159))),((56,(((25,(13,(121,160))),(128,((((((((((8,((28,39),(85,143))),32),(9,174)),((((((50,151),155),((11,(66,(48,67))),192)),(((((42,(44,168)),(((17,(47,77)),(51,60)),86)),(23,198)),(148,188)),153)),((76,(((141,(142,170)),((40,(35,(81,87))),(149,178))),184)),(((21,105),(112,((14,16),116))),144))),(78,((49,((22,(79,(((89,101),(169,(99,171))),156))),150)),(124,130))))),(27,125)),(68,((6,133),(74,137)))),(75,98)),(41,177)),(10,(53,(((110,111),(136,(106,189))),134)))),166))),129)),114)),(69,162)),104)),((97,108),175)),161)),((45,131),180)),(31,120)),113)),((((1,165),(193,(((52,(36,93)),84),194))),((24,((46,146),((164,183),199))),195)),(54,((90,(92,((30,132),187))),122)))),126,((((73,(12,82)),((2,(64,((95,(65,173)),((109,(5,197)),182)))),140)),((33,115),158)),62));");
        return getTreeFromString(treeStr);
    }

    public static Tree[] getAllUnrootedTreesWith_5_Labels() {
        Tree treeStr[] = new Tree[15];
        treeStr[0] = getTreeFromString("((3,(1,4)),2,5);");
        treeStr[1] = getTreeFromString("(((1,4),2),3,5);");
        treeStr[2] = getTreeFromString("((1,4),(2,3),5);");
        treeStr[3] = getTreeFromString("((4,(2,3)),1,5);");
        treeStr[4] = getTreeFromString("(((2,3),1),4,5);");
        treeStr[5] = getTreeFromString("((3,(2,4)),1,5);");
        treeStr[6] = getTreeFromString("(((2,4),1),3,5);");
        treeStr[7] = getTreeFromString("((2,4),(1,3),5);");
        treeStr[8] = getTreeFromString("((4,(1,3)),2,5);");
        treeStr[9] = getTreeFromString("(((1,3),2),4,5);");
        treeStr[10] = getTreeFromString("((2,(3,4)),1,5);");
        treeStr[11] = getTreeFromString("(((3,4),1),2,5);");
        treeStr[12] = getTreeFromString("((3,4),(1,2),5);");
        treeStr[13] = getTreeFromString("((4,(1,2)),3,5);");
        treeStr[14] = getTreeFromString("(((1,2),3),4,5);");
        return treeStr;
    }

    public static Tree[] getAll_30_NeightboursOfSome_6_Labels_Tree() {
        // all 30 neightbours of (1,2,(3,(4,(5,6)))); tree
        Tree treeStr[] = new Tree[30];
        treeStr[0] = getTreeFromString("(((2,(1,4)),3),5,6);");
        treeStr[1] = getTreeFromString("(((1,4),(2,3)),5,6);");
        treeStr[2] = getTreeFromString("((1,5),((2,3),4),6);");
        treeStr[3] = getTreeFromString("((5,((2,3),4)),1,6);");
        treeStr[4] = getTreeFromString("((((2,3),4),1),5,6);");
        treeStr[5] = getTreeFromString("((((2,3),1),4),5,6);");
        treeStr[6] = getTreeFromString("(((1,(2,4)),3),5,6);");
        treeStr[7] = getTreeFromString("(((2,4),(1,3)),5,6);");
        treeStr[8] = getTreeFromString("((2,5),((1,3),4),6);");
        treeStr[9] = getTreeFromString("((5,((1,3),4)),2,6);");
        treeStr[10] = getTreeFromString("((((1,3),4),2),5,6);");
        treeStr[11] = getTreeFromString("((((1,3),2),4),5,6);");
        treeStr[12] = getTreeFromString("((((1,5),2),3),4,6);");
        treeStr[13] = getTreeFromString("(((2,(3,4)),1),5,6);");
        treeStr[14] = getTreeFromString("((((2,5),1),3),4,6);");
        treeStr[15] = getTreeFromString("(((1,(3,4)),2),5,6);");
        treeStr[16] = getTreeFromString("((((1,2),5),3),4,6);");
        treeStr[17] = getTreeFromString("(((1,2),5),(3,4),6);");
        treeStr[18] = getTreeFromString("((5,(3,4)),(1,2),6);");
        treeStr[19] = getTreeFromString("(((3,4),(1,2)),5,6);");
        treeStr[20] = getTreeFromString("(((3,5),(1,2)),4,6);");
        treeStr[21] = getTreeFromString("((3,5),((1,2),4),6);");
        treeStr[22] = getTreeFromString("((5,((1,2),4)),3,6);");
        treeStr[23] = getTreeFromString("((((1,2),4),3),5,6);");
        treeStr[24] = getTreeFromString("((2,(3,(4,5))),1,6);");
        treeStr[25] = getTreeFromString("(((3,(4,5)),1),2,6);");
        treeStr[26] = getTreeFromString("((3,(4,5)),(1,2),6);");
        treeStr[27] = getTreeFromString("(((4,5),(1,2)),3,6);");
        treeStr[28] = getTreeFromString("((4,5),((1,2),3),6);");
        treeStr[29] = getTreeFromString("((5,((1,2),3)),4,6);");
        return treeStr;
    }

    public static Tree[] getAllUnrootedTreesWith_6_Labels() {
        Tree treeStr[] = new Tree[105];
        treeStr[0] = getTreeFromString("(((1,4),(2,5)),3,6);");
        treeStr[1] = getTreeFromString("((1,4),((2,5),3),6);");
        treeStr[2] = getTreeFromString("((4,((2,5),3)),1,6);");
        treeStr[3] = getTreeFromString("((((2,5),3),1),4,6);");
        treeStr[4] = getTreeFromString("((2,5),(3,(1,4)),6);");
        treeStr[5] = getTreeFromString("((5,(3,(1,4))),2,6);");
        treeStr[6] = getTreeFromString("(((3,(1,4)),2),5,6);");
        treeStr[7] = getTreeFromString("(((1,4),(3,5)),2,6);");
        treeStr[8] = getTreeFromString("((1,4),((3,5),2),6);");
        treeStr[9] = getTreeFromString("((4,((3,5),2)),1,6);");
        treeStr[10] = getTreeFromString("((((3,5),2),1),4,6);");
        treeStr[11] = getTreeFromString("((3,5),(2,(1,4)),6);");
        treeStr[12] = getTreeFromString("((5,(2,(1,4))),3,6);");
        treeStr[13] = getTreeFromString("(((2,(1,4)),3),5,6);");
        treeStr[14] = getTreeFromString("((3,((1,4),5)),2,6);");
        treeStr[15] = getTreeFromString("((((1,4),5),2),3,6);");
        treeStr[16] = getTreeFromString("((4,((2,3),5)),1,6);");
        treeStr[17] = getTreeFromString("((((2,3),5),1),4,6);");
        treeStr[18] = getTreeFromString("(((2,3),5),(1,4),6);");
        treeStr[19] = getTreeFromString("((5,(1,4)),(2,3),6);");
        treeStr[20] = getTreeFromString("(((1,4),(2,3)),5,6);");
        treeStr[21] = getTreeFromString("((3,(4,(1,5))),2,6);");
        treeStr[22] = getTreeFromString("(((4,(1,5)),2),3,6);");
        treeStr[23] = getTreeFromString("((4,(1,5)),(2,3),6);");
        treeStr[24] = getTreeFromString("(((1,5),(2,3)),4,6);");
        treeStr[25] = getTreeFromString("((1,5),((2,3),4),6);");
        treeStr[26] = getTreeFromString("((5,((2,3),4)),1,6);");
        treeStr[27] = getTreeFromString("((((2,3),4),1),5,6);");
        treeStr[28] = getTreeFromString("((3,(1,(4,5))),2,6);");
        treeStr[29] = getTreeFromString("(((1,(4,5)),2),3,6);");
        treeStr[30] = getTreeFromString("((1,(4,5)),(2,3),6);");
        treeStr[31] = getTreeFromString("(((4,5),(2,3)),1,6);");
        treeStr[32] = getTreeFromString("((4,5),((2,3),1),6);");
        treeStr[33] = getTreeFromString("((5,((2,3),1)),4,6);");
        treeStr[34] = getTreeFromString("((((2,3),1),4),5,6);");
        treeStr[35] = getTreeFromString("(((2,4),(1,5)),3,6);");
        treeStr[36] = getTreeFromString("((2,4),((1,5),3),6);");
        treeStr[37] = getTreeFromString("((4,((1,5),3)),2,6);");
        treeStr[38] = getTreeFromString("((((1,5),3),2),4,6);");
        treeStr[39] = getTreeFromString("((1,5),(3,(2,4)),6);");
        treeStr[40] = getTreeFromString("((5,(3,(2,4))),1,6);");
        treeStr[41] = getTreeFromString("(((3,(2,4)),1),5,6);");
        treeStr[42] = getTreeFromString("(((2,4),(3,5)),1,6);");
        treeStr[43] = getTreeFromString("((2,4),((3,5),1),6);");
        treeStr[44] = getTreeFromString("((4,((3,5),1)),2,6);");
        treeStr[45] = getTreeFromString("((((3,5),1),2),4,6);");
        treeStr[46] = getTreeFromString("((3,5),(1,(2,4)),6);");
        treeStr[47] = getTreeFromString("((5,(1,(2,4))),3,6);");
        treeStr[48] = getTreeFromString("(((1,(2,4)),3),5,6);");
        treeStr[49] = getTreeFromString("((3,((2,4),5)),1,6);");
        treeStr[50] = getTreeFromString("((((2,4),5),1),3,6);");
        treeStr[51] = getTreeFromString("((4,((1,3),5)),2,6);");
        treeStr[52] = getTreeFromString("((((1,3),5),2),4,6);");
        treeStr[53] = getTreeFromString("(((1,3),5),(2,4),6);");
        treeStr[54] = getTreeFromString("((5,(2,4)),(1,3),6);");
        treeStr[55] = getTreeFromString("(((2,4),(1,3)),5,6);");
        treeStr[56] = getTreeFromString("((3,(4,(2,5))),1,6);");
        treeStr[57] = getTreeFromString("(((4,(2,5)),1),3,6);");
        treeStr[58] = getTreeFromString("((4,(2,5)),(1,3),6);");
        treeStr[59] = getTreeFromString("(((2,5),(1,3)),4,6);");
        treeStr[60] = getTreeFromString("((2,5),((1,3),4),6);");
        treeStr[61] = getTreeFromString("((5,((1,3),4)),2,6);");
        treeStr[62] = getTreeFromString("((((1,3),4),2),5,6);");
        treeStr[63] = getTreeFromString("((3,(2,(4,5))),1,6);");
        treeStr[64] = getTreeFromString("(((2,(4,5)),1),3,6);");
        treeStr[65] = getTreeFromString("((2,(4,5)),(1,3),6);");
        treeStr[66] = getTreeFromString("(((4,5),(1,3)),2,6);");
        treeStr[67] = getTreeFromString("((4,5),((1,3),2),6);");
        treeStr[68] = getTreeFromString("((5,((1,3),2)),4,6);");
        treeStr[69] = getTreeFromString("((((1,3),2),4),5,6);");
        treeStr[70] = getTreeFromString("(((3,4),(1,5)),2,6);");
        treeStr[71] = getTreeFromString("((3,4),((1,5),2),6);");
        treeStr[72] = getTreeFromString("((4,((1,5),2)),3,6);");
        treeStr[73] = getTreeFromString("((((1,5),2),3),4,6);");
        treeStr[74] = getTreeFromString("((1,5),(2,(3,4)),6);");
        treeStr[75] = getTreeFromString("((5,(2,(3,4))),1,6);");
        treeStr[76] = getTreeFromString("(((2,(3,4)),1),5,6);");
        treeStr[77] = getTreeFromString("(((3,4),(2,5)),1,6);");
        treeStr[78] = getTreeFromString("((3,4),((2,5),1),6);");
        treeStr[79] = getTreeFromString("((4,((2,5),1)),3,6);");
        treeStr[80] = getTreeFromString("((((2,5),1),3),4,6);");
        treeStr[81] = getTreeFromString("((2,5),(1,(3,4)),6);");
        treeStr[82] = getTreeFromString("((5,(1,(3,4))),2,6);");
        treeStr[83] = getTreeFromString("(((1,(3,4)),2),5,6);");
        treeStr[84] = getTreeFromString("((2,((3,4),5)),1,6);");
        treeStr[85] = getTreeFromString("((((3,4),5),1),2,6);");
        treeStr[86] = getTreeFromString("((4,((1,2),5)),3,6);");
        treeStr[87] = getTreeFromString("((((1,2),5),3),4,6);");
        treeStr[88] = getTreeFromString("(((1,2),5),(3,4),6);");
        treeStr[89] = getTreeFromString("((5,(3,4)),(1,2),6);");
        treeStr[90] = getTreeFromString("(((3,4),(1,2)),5,6);");
        treeStr[91] = getTreeFromString("((2,(4,(3,5))),1,6);");
        treeStr[92] = getTreeFromString("(((4,(3,5)),1),2,6);");
        treeStr[93] = getTreeFromString("((4,(3,5)),(1,2),6);");
        treeStr[94] = getTreeFromString("(((3,5),(1,2)),4,6);");
        treeStr[95] = getTreeFromString("((3,5),((1,2),4),6);");
        treeStr[96] = getTreeFromString("((5,((1,2),4)),3,6);");
        treeStr[97] = getTreeFromString("((((1,2),4),3),5,6);");
        treeStr[98] = getTreeFromString("((2,(3,(4,5))),1,6);");
        treeStr[99] = getTreeFromString("(((3,(4,5)),1),2,6);");
        treeStr[100] = getTreeFromString("((3,(4,5)),(1,2),6);");
        treeStr[101] = getTreeFromString("(((4,5),(1,2)),3,6);");
        treeStr[102] = getTreeFromString("((4,5),((1,2),3),6);");
        treeStr[103] = getTreeFromString("((5,((1,2),3)),4,6);");
        treeStr[104] = getTreeFromString("((((1,2),3),4),5,6);");
        return treeStr;
    }

    public static Tree[] getSomeUnrootedTreesWith_7_Labels() {
        Tree treeStr[] = new Tree[20];
        treeStr[0] = getTreeFromString("((1,4),((2,5),(3,6)),7);");
        treeStr[1] = getTreeFromString("((4,((2,5),(3,6))),1,7);");
        treeStr[2] = getTreeFromString("((((2,5),(3,6)),1),4,7);");
        treeStr[3] = getTreeFromString("((2,5),((3,6),(1,4)),7);");
        treeStr[4] = getTreeFromString("((5,((3,6),(1,4))),2,7);");
        treeStr[5] = getTreeFromString("((((3,6),(1,4)),2),5,7);");
        treeStr[6] = getTreeFromString("((3,6),((1,4),(2,5)),7);");
        treeStr[7] = getTreeFromString("((6,((1,4),(2,5))),3,7);");
        treeStr[8] = getTreeFromString("((((1,4),(2,5)),3),6,7);");
        treeStr[9] = getTreeFromString("(((2,5),((1,4),6)),3,7);");
        treeStr[10] = getTreeFromString("((4,(((2,5),3),6)),1,7);");
        treeStr[11] = getTreeFromString("(((((2,5),3),6),1),4,7);");
        treeStr[12] = getTreeFromString("((2,5),(((1,4),6),3),7);");
        treeStr[13] = getTreeFromString("((5,(((1,4),6),3)),2,7);");
        treeStr[14] = getTreeFromString("(((((1,4),6),3),2),5,7);");
        treeStr[15] = getTreeFromString("(((3,(2,5)),6),(1,4),7);");
        treeStr[16] = getTreeFromString("((6,(1,4)),(3,(2,5)),7);");
        treeStr[17] = getTreeFromString("(((1,4),(3,(2,5))),6,7);");
        treeStr[18] = getTreeFromString("(((2,5),(4,(1,6))),3,7);");
        treeStr[19] = getTreeFromString("((4,(1,6)),((2,5),3),7);");
        return treeStr;
    }

    public static Tree[] getTwoMarsupialsSPR_1_distance_trees() {
        Tree treeStr[] = new Tree[2];
        treeStr[0] = getTreeFromString("((Phalanger_matanim,Strigocuscus_gymnotis),(Phalanger_carmelitae,Phalanger_orientalis),Phalanger_vestitus);");
        treeStr[1] = getTreeFromString("(Phalanger_matanim,Strigocuscus_gymnotis,(Phalanger_orientalis,(Phalanger_carmelitae,Phalanger_vestitus)));");
        return treeStr;
    }

    public static Tree[] getTwoMarsupialsSPR_2_distance_trees() {
        Tree treeStr[] = new Tree[2];
        treeStr[0] = getTreeFromString("(Acrobates_pygmaeus,((Trichosurus_vulpecula,(Cercartetus_caudatus,Burramys_parvus)),((Dactylopsila_trivirgata,Petauroides_volans),Distoechurus_pennatus)),Tarsipes_rostratus);");
        treeStr[1] = getTreeFromString("(Trichosurus_vulpecula,(Burramys_parvus,Cercartetus_caudatus),((Acrobates_pygmaeus,Distoechurus_pennatus),(Petauroides_volans,(Tarsipes_rostratus,Dactylopsila_trivirgata))));");
        return treeStr;
    }

    public static Tree[] getTwoMarsupialsSPR_3_distance_trees() {
        Tree treeStr[] = new Tree[2];
        treeStr[0] = getTreeFromString("(Caluromys_philander,(((((Didelphis_virginiana,Philander_opossum),Lutreolina_crassicaudata),Chironectes_minimus),Metachirus_nudicaudatus),((Gracilinanus_agilis,((Thylamys_pallidior,(Thylamys_macrura,Thylamys_pusilla)),Lestodelphys_halli)),(Marmosops_parvidens,Marmosops_dorothea))),((Monodelphis_dimidiata,Monodelphis_domestica),((Marmosa_murina,Marmosa_robinsoni),Micoureus_constantiae)));");
        treeStr[1] = getTreeFromString("(Caluromys_philander,((Monodelphis_domestica,Monodelphis_dimidiata),(Marmosa_robinsoni,(Micoureus_constantiae,Marmosa_murina))),((Metachirus_nudicaudatus,(Chironectes_minimus,(Lutreolina_crassicaudata,(Didelphis_virginiana,Philander_opossum)))),((Marmosops_parvidens,(Gracilinanus_agilis,Marmosops_dorothea)),(Lestodelphys_halli,(Thylamys_pusilla,(Thylamys_macrura,Thylamys_pallidior))))));");
        return treeStr;
    }

    public static Tree[] getTwoMarsupialsSPR_4_distance_trees() {
        Tree treeStr[] = new Tree[2];
        treeStr[0] = getTreeFromString("(Acrobates_pygmaeus,((((Macropus_eugenii,(((Trichosurus_vulpecula,Cercartetus_nanus),Petaurus_breviceps),(Vombatus_ursinus,Phascolarctos_cinereus))),(Isoodon_macrourus,Macrotis_lagotis)),Sminthopsis_macroura),Dasyurus_viverrinus),Tarsipes_rostratus);");
        treeStr[1] = getTreeFromString("((Cercartetus_nanus,((Vombatus_ursinus,Phascolarctos_cinereus),((Macropus_eugenii,Trichosurus_vulpecula),(Tarsipes_rostratus,(Acrobates_pygmaeus,Petaurus_breviceps))))),(Isoodon_macrourus,Macrotis_lagotis),(Sminthopsis_macroura,Dasyurus_viverrinus));");
        return treeStr;
    }

    public static Tree[] getTwoMarsupialsSPR_4_distance_trees_withoutLabels() {
        Tree treeStr[] = new Tree[2];
        treeStr[0] = getTreeFromString("(0,((((1,(((2,3),4),(5,6))),(7,8)),9),10),11);");
        treeStr[1] = getTreeFromString("((3,((5,6),((1,2),(11,(0,4))))),(7,8),(9,10));");
        return treeStr;
    }
}
